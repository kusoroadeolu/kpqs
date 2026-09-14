# Concurrent Priority Queues (CBS)

This repo contains several different implementations of concurrent priority queues, each exploring a different design tradeoff between throughput, contention, and strict ordering guarantees.

Each implementation lives on its own branch in this repo.

Design doc images for some of the queues (KQueue, KSkipListQueue, and Mound) are available in the main branch.

## Implementations

### KQueue
A sharded priority queue. Elements are spread across an array of segments (each with a sorted delete buffer, an insert buffer, and an overflow heap) using randomized probing with per segment spin locks. A lock free MPSC "leader queue" tracks which segments currently have promising elements, so a single consumer thread knows where to poll from without scanning every segment. Approximate ordering, optimized for low contention on the write path.

### KSkipListQueue
Each segment is backed by a `ConcurrentSkipListSet` instead of a custom buffer/heap stack. Producers use randomized probing plus an "elimination" arena, if a producer can't lock a segment, it checks for a waiting consumer and hands the element directly to it, skipping the underlying structure entirely. Consumers periodically harvest the top elements from every segment into a single sorted batch (`DeleteArray`) and serve poll requests out of that batch until it's exhausted, at which point a new harvest happens.

### MultiQueue
The simplest of the sharded designs. Same segment structure as KQueue (delete buffer, insert buffer, heap), but each segment also caches its own minimum value for lock free reads. Polling uses randomized "power of two choices": pick two random segments, compare their cached minimums, and try to lock and poll from whichever looks smaller. No auxiliary coordination structure (no leader queue, no arena), just per segment caching and random sampling.

### ChunkedPQ
A strictly ordered (non approximate) concurrent priority queue built on an unrolled linked list of fixed size chunks. The first chunk in the list handles deletions, a paired buffer chunk allows fast inserts of small values without contending on the first chunk directly, and the rest of the list handles general inserts. Deleting threads claim slots via fetch and add for a lock free fast path, and chunks are "frozen" and merged/split (with helping from concurrent threads) when they need to be reorganized.

### ConcurrentMound
A strictly ordered concurrent priority queue shaped like a classic binary heap, but where each node holds a small local bucket (a `PriorityQueue`) instead of a single value, based on the "mound" data structure. The heap array grows level by level via a segmented array structure. Inserts use a randomized starting point plus a binary search up the tree to reduce contention at the root, and polling pops from the root bucket then restores the heap invariant by swapping whole buckets down the tree (similar in spirit to sift down, but bucket by bucket).

### PIPQ
A sharded design (same segment/probing skeleton as KQueue and MultiQueue) built around a single shared lock free sorted linked list, the "leader list". Each segment publishes a bounded window of its smallest elements into this shared list and keeps the rest in a local heap, refilling the list as it drains. The leader list itself uses a custom 3 phase deletion protocol (NONE, MARKING, MARKED plus a dummy node splice) intended to close a race where a concurrent insert could land next to a node mid deletion and get lost. Poll requests can optionally be combined through a flat combining structure (`Hopper`) so one thread does the leader list traversal work for a batch of concurrent pollers.

