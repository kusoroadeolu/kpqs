# Concurrent Priority Queue Library — Overview

Planned unbounded concurrent priority queue variants, plus context on the bounded ones already built.

## K-PIPQ (K Relaxed concurrent priority queue) my idea
**Semantics:** Relaxed, linearizable. Delete-min always returns `a` (not `the`) global minimum.



**Structure:** Two levels.
- **Worker level structure** — one min-heap worker queue, protected by a single lock. Most inserts land here. 
A local doubly linked list (ordered by priority) holding the top K highest priority elements for that worker heap 
(we could swap this later on for a sorted array list if needed, but im prioritising cheap inserts and removals)
An id (integer) holding the index of the worker heap in the segmented array of worker heaps

- **Leader level** — a single mpsc FIFO queue with each index possibly holding the id to a worker heap.

The concurrent queue itself uses a shared array of worker level structures (mapped by index) any thread can access to perform this insert flow on

**Insert flow:**
- **Fast path** (common case) — new key is worse than your own heap's current min → push into your local heap. No contention with anyone.
- **Slower path** — new key beats your local min -> insert your id into the leader level (shared mpmc queue) and then add that element into your local linked list
- **Slowest path** — local linked list is full for a worker level structure -> remove the lowest priority element in the local linked list and insert it into the 
min heap then add your new element into the local linked list


**Delete-min flow:** Uses combining — one thread becomes "coordinator"  and serves a batch of pending delete-min requests sequentially against the shared mpsc, instead of every thread fighting over the same list node.
If a thread's leader level presence drops too low, elements get "helped" back up from its worker heap.

The serial flow goes as is: Poll a value from the leader level which will give us an index of a worker heap in the worker heap array. Lock that worker heap
Then pop the first value off the linked list and return.
If necessary to upsert a new value from the min heap, remove the top element from the minheap and add it to the local linked list

**Tradeoff:** Insert is pretty parallel in the common case; delete-min stays sequential-ish but is offloaded via combining. Best for insert-dominant workloads.


## Stuff I might still build but probably not
---

## 1. PIPQ (Parallel Insert Priority Queue)

**Semantics:** Strict, linearizable. Delete-min always returns the true global minimum.

**Structure:** Two levels.
- **Worker level** — one min-heap per thread, protected by a single lock. Most inserts land here.
- **Leader level** — a single lock-free sorted linked list holding each thread's highest-priority candidates. Delete-min only ever touches this list.

**Insert flow:**
- **Fast path** (common case) — new key is worse than your own heap's current min → push into your local heap. No contention with anyone.
- **Slower path** — new key beats your local min → insert into the leader list directly.
- **Slowest path** — leader list is full for your thread → insert into leader list *and* demote your own worst leader-list entry back into your local heap.

**Delete-min flow:** Uses combining — one thread becomes "coordinator"  and serves a batch of pending delete-min requests sequentially against the leader list, instead of every thread fighting over the same list node. If a thread's leader-list presence drops too low, elements get "helped" back up from its worker heap.

**Tradeoff:** Insert is nearly embarrassingly parallel in the common case; delete-min stays sequential-ish but is offloaded via combining. Best for insert-dominant workloads.

**Possible extension being considered:** Cross-thread stealing when a worker heap drains (pull from another thread's heap under lock, not a lock-free deque). Not part of v1 — needs correctness care since PIPQ's proof assumes each worker heap is single-writer.

---

## 2. TBB-style Concurrent Priority Queue

**Semantics:** Strict, linearizable (single shared structure, sequential access enforced by combining).

**Structure:** One `vector`-backed array split into two regions:
```
[   binary heap   |  unheapified tail  ]
0 ............. mark .............. size
```
- `[0, mark)` is a real heap.
- `[mark, size)` is raw pushed elements not yet folded in.

**Mechanism — flat combining aggregator:** Threads don't lock the heap directly. Instead they submit push/pop requests to a shared op list; one thread at a time becomes the "combiner" and drains the whole batch, everyone else waits on their own result slot.

**Combiner logic (per batch):**
1. Pass 1: apply all pushes (cheap, tail append). For pops, try a shortcut — if the last pushed element beats the heap top, hand it back directly without touching the heap.
2. Pass 2: any pops that couldn't shortcut do a real delete-min + `reheap()`.
3. Cleanup: `heapify()` folds any leftover unheapified tail elements into the heap before releasing.

**Tradeoff:** Very simple to reason about (no lock-free pointer tricks). Coarser-grained than PIPQ — every op across every thread funnels through one combiner, so it scales worse under many threads, but batching amortizes heap cost nicely under bursty load.

---

## Already built (bounded)
- A bounded variant
  Both `GenerationPQ` are Mpsc fifo queues which use the same insertion algorithm similar to MPSC fifo queues. Threads race to claim an index in the queue
  to insert their elements, only succeeding if their CAS succeeds, boundedness is enforced using a lazily updated producer
  limit (isolated on its own cache line) which an insert thread uses to determine if the queue is full

Polls are serialized through a hopper (combiner) to allow work to be done in batch

---

## Later / not yet scoped to be built anytime soon

- **Segment-selection strategy** for the hybrid and any future multiqueue variants — likely just fast RNG + 2-random-choice comparison (as MultiBucketQueue already does), not a real hash function, since segment picking isn't a keyed lookup.
## Hybrid MultiQueue (heap segments → bucket segments)

**Semantics:** Adaptive — exact order while a segment is small/"cold", delta-bounded approximate order once a segment gets hot enough to convert. Inspired by `ConcurrentHashMap`'s bin treeification. I won't be building this in my first
version though it's a fun one to think about

**Structure:** A multiqueue (array of independent segments, threads pick segments via random / 2-choice, same as MultiBucketQueue). Each segment starts as a plain array-backed heap. Once a segment's size crosses a threshold, it converts in place to a delta-bucketed structure (like MultiBucketQueue's `BucketQueue`) for O(1)-ish batched push/pop instead of O(log n).

**Tradeoff:** Unlike CHM's treeify (pure perf win, identical semantics either way), this is a real semantics change — ordering guarantee gets looser exactly when a segment is under heavy load. Needs to be documented explicitly ("exact under N elements, delta-bounded above N, N dynamic per segment") rather than presented as one uniform guarantee.

---

