## Hopper
A structure similar in spirit to flat combining. 
Incoming threads publish their requests to a stack 

The tail of the "current" requests in a stack is identified as the request whose next request pointer
points to null. There can only be one of this at any given time

To choose the "owner" of the current requests in the stack,
the thread whose request is the tail of the stack is identified as the 
owner of the current requests in the stack

When it's time to apply the requests, the owner detaches all requests
from the head of the stack using an atomic get then set operation

We use a simple treiber stack for this

I want to make this structure pretty flexible as it'd be the main plumbing
for basically all the `poll` methods in this priority queue lib


My sketch
```java
abstract class HopperItem<T> {
    HopperItem<T> next;
    final HopperItem next() {} //uses a plain memory access as well
    final void setNext(HopperItem next) {} //uses a plain memory access or stronger
}

```

Then for the hopper

```java
class Hopper {
    volatile HopperItem head;

    public boolean push(HopperItem item) {} //pushes onto the stack, returns true if we're the combiner, else false
    public Iterator<HopperItem> dump() {} //detaches the hopper from the head, and all it's nodes
}
```




---
# K-Queue – An Unbounded Relaxed Priority Queue

**Keywords:** `F` (Fixed Capacity), `U` (Unbounded Capacity)

---

## LEADER STRUCTURE

```
+------------------------+
| Worker Structure Array | ------> (Shared array of worker structures. Each worker structure is padded to ensure it fits on its own cache line) F
+------------------------+

+------------------------+
|      Leader Queue      | ------> (MPMC queue for threads to publish and claim IDs. An empty slot is identified with an ID of -1) F
+------------------------+
```
## WORKER STRUCTURE

**Variables & Mutexes:**
* `MAX_D` - Max capacity of the delete buffer
* `MIN_D` - Min capacity of the delete buffer before trying to pull an element from the heap
* `ID` - Index in the worker structure array (non-negative)
* `Lock` - Serializes access to this structure
* `Spin Lock` - Held when resizing the array for main heap (to allow concurrent deletes when the main heap is full and needs resizing; though I'd add this later if the array creation speed becomes an issue)

### Data structures

```
+-------------------+
| Ordered Vector    | ----> Delete buffer (for quick deletions. Maintains sorted order on inserts and contains the top k highest priority elements in the worker structure) [F]
|      or LL        |
+-------------------+

+-------------------+
|  Unordered array  | ----> Insert buffer (unsorted, provides slack to prevent heapifying for inserts which get removed from the delete buffer) [F]
+-------------------+

+-------------------+
|  Binary/8-ary     | ----> Main heap (always obeys the heap invariant. Could be a binary or 8-ary heap.) [U]
|       heap        |
+-------------------+
```

---

## Pseudocode

**Definitions:**
* `D` = Delete Buffer
* `I` = Insert Buffer
* `E` = Element to be inserted
* `H` = Main heap
* `L` = Current lowest priority value in `D`

### Insertions
1. To insert elem `E`, acquire the lock
2. If `D` is empty, insert `E`, publish the worker structure's ID in the leader queue, then return
3. If `D` is not empty BUT not full, if `E` has a lower priority than `L`, insert into `I` else insert into `D`
4. If `D#size = MAX_D` (`D` is full) and `E` has a higher priority than `L`, insert `L` into `I` then insert `E` into `D`
5. If `I` is full, merge `I` with `H` and then insert into `I`
6. If `H` is full, release the lock, try to hold the spin lock, resize the array, then reacquire the lock

*Note: To publish simply means inserting a worker structure's ID into the leader queue.*

### Deletions (Assuming single threaded usage)
1. Poll an ID from the leader queue, if `ID != -1` acquire the lock on worker structure correlating to the ID
2. Poll `D`, if `D#size > MIN_D`, return
3. Else, if `H` is empty, move the contents `I` into `H` (to heapify) and then pull the highest priority value in `H` into `D`
4. Else if `H` is not empty, pull the highest priority value of `H` into `D`, then compare `L` to all the contents of `I` updating swapping the value of `I` (at that index) when that value has a higher priority than `L`
5. If `I` is empty and `H` is not empty, simply pull the highest priority value of `H` into `D`
6. Publish the worker structure's ID, return
7. If both `H` and `I` are empty, return

---

## Important Stuff

1. To find an index to insert to, a thread generates a random number, calculates the offset of that number bound within the worker structure array length and then tries to acquire the lock on that worker structure (I should probably look at LongAdder cause I think it has some nice tricks in there)
2. If it fails to acquire the lock, repeatedly 1-increments the number, calculates the offset at that index, then tries to acquire the lock at that index up to a bounded number of increments before generating a new number and trying again.
3. For deletion, we use a MPSC queue with flat combining on the consumer side to make it MPMC. We could use a standard MPMC queue, however the reason for this is to prevent multiple deleting threads waiting and contending on the same lock for a worker queue to allow.
4. One idea: if upcoming queue entries share the current ID, we could batch them under one lock instead of releasing and reacquiring for each.


# KSkipListQueue - An unbounded relaxed priority queue

A simple segmented relaxed priority based on the multi queue, skip list priority queues and some ideas from the CBPQ paper.
It consists of two levels, similar to my initial KQueue priority queue.

## Delete Array

**Atomic Index:** A simple atomic integer published alongside the delete buffer (starting from zero). To claim an index in the delete array, threads simply perform a fetch and add on this index.

**Try Lock:** A simple lock deleting threads try to acquire when the delete buffer is empty (to refill the buffer). If a thread fails to acquire this lock, it backoffs to the elimination arena.

### Data structures

```
+---------------------+             +-------------------------------------------------------------+
| Array(Could be a    | ----------> | An immutable (one use) buffer which deleting threads can    |
| heap)               |             | claim items from                                            |
+---------------------+             +-------------------------------------------------------------+
```

---

## Segment (N)

**Try Lock:** A simple lock threads can try and claim to insert into the concurrent skip lists. Deletions from the segments however don't need to try to acquire this lock. The major reason for this try lock, even though the skip list is thread safe is to enable elimination (as I'll discuss later) and prevent unlucky incidents where majority threads pile up on a single segment (i.e. spreading contention among segments).

### Data structures

```
+------+     +------+               +-------------------------------------------------------------+
| Node | --> | Node | ------------> | A concurrent skip list handling insertions and deletions    |
+------+     +------+               +-------------------------------------------------------------+
```

---

## Elimination arena

**Try Lock:** A simple lock threads can try and claim to insert into the concurrent skip lists. Deletions from the segments however don't need to try to acquire this lock. The major reason for this try lock, even though the skip list is thread safe is to enable elimination (as I'll discuss later) and prevent unlucky incidents where majority threads pile up on a single segment (i.e. spreading contention among segments).

### Data structures

```
+---------------------+             +-------------------------------------------------------------+
| Elimination Arena   | ----------> | A fixed capacity array where deleting threads can publish   |
|                     |             | themselves to request for items in the priority queue and   |
|                     |             | inserting threads can claim those requests                  |
+---------------------+             +-------------------------------------------------------------+
```

---

## Pseudocode

`S` - Segment, `L` - Skip List, `D` - Delete Array, `A` - Elimination arena

### Insertions
1. To insert an element `E`, a thread generates a random index (`I`) within the bounds of the segment array.
2. Then try to acquire the lock for `S` at `I`, if this fails, we peek at the max element (`M`) in `L` in `S`.
3. If $S < M$, we check the status of the index `I` in `A`, if there's a present waiter, it swaps its value, return otherwise, it retries from 1.
4. If a thread successfully acquires the lock, it inserts into `L`, releases the lock and returns.

### Deletions
1. To delete an element, a thread checks if `D` is null or exhausted, if so it tries to acquire the lock on `D`.
2. If it fails to acquire the lock, it publishes itself into `A` to allow greater concurrency and potentially return early if a concurrent insert swaps its value.
3. If it succeeds, it traverses the segment array draining the top `K` elements without acquiring the lock (determined by the user) in `L` for each `S` into a new `D`.
4. It then publishes new `D` before claiming an index, returning the value at `D` before returning.
5. If `D` is present, it simply claims an index and returns the value in `D` at that index.