# Concurrent Priority Queues (CBS)

This repo contains several different implementations of concurrent priority queues, each exploring a different design tradeoff between throughput, contention, and strict ordering guarantees.

Each implementation lives on its own branch in this repo.

Design doc images for some of the queues (KQueue, KSkipListQueue, and Mound) are available in the `kqueue` branch.

## Implementations

### KQueue
A sharded priority queue. Elements are spread across an array of segments (each with a sorted delete buffer, an insert buffer, and an overflow heap) using randomized probing with per segment spin locks. A lock free MPSC "leader queue" tracks which segments currently have promising elements, so a single consumer thread knows where to poll from without scanning every segment. Approximate ordering, optimized for low contention on the write path.

### KSkipListQueue
Each segment is backed by a `ConcurrentSkipListSet` instead of a custom buffer/heap stack. Producers use randomized probing plus an "elimination" arena, if a producer can't lock a segment, it checks for a waiting consumer and hands the element directly to it, skipping the underlying structure entirely. Consumers periodically harvest the top elements from every segment into a single sorted batch (`DeleteArray`) and serve poll requests out of that batch until it's exhausted, at which point a new harvest happens.

### MultiQueue
The simplest of the sharded designs. Same segment structure as KQueue (delete buffer, insert buffer, heap), but each segment also caches its own minimum value for lock free reads. Polling uses randomized "power of two choices": pick two random segments, compare their cached minimums, and try to lock and poll from whichever looks smaller. No auxiliary coordination structure (no leader queue, no arena), just per segment caching and random sampling.

### ChunkedPQ
A strictly ordered (non approximate) concurrent priority queue built on an unrolled linked list of fixed size chunks. The first chunk in the list handles deletions, a paired buffer chunk allows fast inserts of small values without contending on the first chunk directly, and the rest of the list handles general inserts. Deleting threads claim slots via fetch and add for a lock free fast path, and chunks are "frozen" and merged/split (with helping from concurrent threads) when they need to be reorganized.
To help insert performance, it includes an approximate index list, rebuilt peroidically that threads can binary search to find a good starting point to start traversing the list

### ConcurrentMound
A strictly ordered concurrent priority queue shaped like a classic binary heap, but where each node holds a small local bucket (a `PriorityQueue`) instead of a single value, based on the "mound" data structure. The heap array grows level by level via a segmented array structure. Inserts use a randomized starting point plus a binary search up the tree to reduce contention at the root, and polling pops from the root bucket then restores the heap invariant by swapping whole buckets down the tree (similar in spirit to sift down, but bucket by bucket).

### PIPQ
A strict sharded design (same segment/probing skeleton as KQueue and MultiQueue) built around a single shared lock free sorted linked list, the "leader list". Each segment publishes a bounded window of its smallest elements into this shared list and keeps the rest in a local heap, refilling the list as it drains. The leader list itself uses a custom 3 phase deletion protocol (NONE, MARKING, MARKED plus a dummy node splice) intended to ensure a deleter always gets the smallest left most node. 
Poll requests are combined through a flat combining structure (`Hopper`) so one thread does the leader list traversal work for a batch of concurrent pollers.

### SkipPQ
A strictly ordered, lock free priority queue built on a skip list adapted from the JDK's `ConcurrentSkipListMap` internals, modified to allow duplicate keys. Polling logically deletes the left most node with a CAS on its `marked` flag, then physically unlinks it and cleans up its indices. Two optimizations sit on top of that:

- **Batched unlinking:** when a poller runs into a run of already marked nodes at the head, it walks past them using marker nodes and removes the whole run with a single CAS on the head's `next`, instead of one CAS per node (in the spirit of the Linden-Jonsson queue).
- **Elimination:** an offer that loses its CAS right next to the head sentinel, or a poll that loses the race to mark the left most node, falls back to a padded elimination arena. Offers try to hand their element directly to a waiting poller, and pollers try to grab a pending offer or park as a waiter for a short spin. Matched pairs skip the skip list entirely, which cuts contention at the head.

Duplicates are handled by only traversing up to the first node with an equal key rather than past all of them, to avoid extra pointer derefs. A `ContentionCounter` can optionally be passed to `offer`/`poll` to track offers near the head, failed offers, poll attempts, and failed marking CASes.

## Benchmarks

All benchmarks were run with JMH on 8 threads (unless noted), using random integer keys. KSkipListQueue was not benchmarked and is omitted from the tables.


### Environment

| |                        |
|---|------------------------|
| CPU | Intel i5               |
| Cores / threads | 4 cores - 8 processors |
| RAM | 16GB                   |
| OS | Windows 11             |
| JDK | 25 (Open JDK)          |
| GC / heap flags | -Xms8g, -Xmx8g, -XX:+UseG1GC       |

### Insert scaling (ops/us, higher is better)

| Queue | 2 threads | 4 threads | 6 threads | 8 threads |
|---|---|---|---|---|
| PIPQ | 27.839 | 33.940 | 37.805 | 38.302 |
| KQueue | 25.247 | 31.981 | 35.115 | 35.687 |
| MultiQueue | 21.826 | 28.376 | 32.076 | 34.516 |
| ChunkedPQ | 2.903 | 4.295 | 4.902 | 5.732 |
| ConcurrentMound | 3.340 | 4.803 | 5.361 | 5.410 |
| SkipPQ | 1.536 | 2.414 | 3.038 | 3.601 |

### Phase (burst of inserts, then full drain; 8 producers, 8 consumers; us, lower is better)

| Queue | Time (us) |
|---|---|
| SkipPQ | 1764 ± 56 |
| KQueue | 2082 ± 317 |
| MultiQueue* | 2413 ± 368 |
| PIPQ | 3787 ± 415 |
| ConcurrentMound | 6921 ± 878 |
| ChunkedPQ | 8109 ± 1867 |

\* MultiQueue's `relaxedPoll()` can return null while elements remain in other segments, so consumers may stop before the queue is fully drained. Its time is likely understated.

### Steady state (8 threads, poll then re-insert, latency in us, lower is better)

| Queue | Mean | P50 | P99 | P99.9 |
|---|---|---|---|---|
| MultiQueue | 0.896 | 0.7 | 1.8 | 10.1 |
| SkipPQ | 2.894 | 1.4 | 30.9 | 69.4 |
| PIPQ | 3.999 | 0.1 | 3.1 | 1871.9 |
| ConcurrentMound | 5.404 | 0.2 | 3.4 | 1966.1 |
| KQueue | 6.709 | 0.6 | 177.2 | 426.5 |
| ChunkedPQ | 18.137 | 7.0 | 196.1 | 1002.5 |
