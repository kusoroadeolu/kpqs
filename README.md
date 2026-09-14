# cbs — Concurrent Priority Queues

A collection of concurrent priority queue implementations in Java, built as an exploration of different strategies for reducing contention on the classic "many producers, many consumers, one ordered structure" problem.

Each implementation lives on its own branch, with its own README covering the specifics of that design (invariants, known issues, benchmarks where available). This top-level README is just an index and a map of the territory.

## Implementations

### `PIPQ`
A segmented queue where each worker owns a local heap, and the smallest candidates from each segment are tracked in a shared, lock-free sorted linked list (`LeaderList`). Uses CAS-based marking and unlinking with dummy nodes for the shared list, and a local per-segment linked list to approximate the leader list's shape for cheaper scans.

Known issue: there is a race between a concurrent insert at the left sentinel and a `poll`, which can affect which node is treated as a segment's tail. No benchmark numbers are included for PIPQ cause of this

### `KQueue`
A simpler alternative to `PIPQ`. Each segment keeps a small sorted delete buffer, a plain insert buffer for staging, and a fallback heap for overflow. A shared MPSC queue tracks which segment IDs currently have a viable candidate in their delete buffer, which is what `poll()` consults instead of walking a shared sorted list.

Design images included on this branch.

### `ConcurrentMound`
Based on the Mound structure: a concurrently accessed tree where each node holds a small local priority queue (a bucket) instead of a single value. Insertion uses randomized probing plus fine-grained per-node locking to find an insertion point. Deletion restores heap order by swapping whole buckets between parent and child rather than moving individual values.

Design images included on this branch.

### `ChunkedPQ`
An unrolled linked list of fixed-size sorted chunks. The first chunk is delete-only and handles polls via fetch-and-add, avoiding CAS contention at the head. A buffer chunk absorbs inserts that are smaller than the first chunk's anchor and gets merged in later. Regular chunks split when full, similar to a B-tree. The most heavily documented implementation in the codebase (see the class-level comment on that branch).

### `MultiQueue`
The simplest of the set. Segments are just locked local heaps, and `poll()` uses power-of-two-choices: sample two random segments, take the one with the smaller peeked minimum, lock it, revalidate, and poll. No shared coordinating structure at all. Weaker ordering guarantees than the others, but the easiest to reason about and likely the most robust under load.

### `KSkipListQueue`
Each segment is backed by a `ConcurrentSkipListSet` instead of a hand-rolled structure. Adds an elimination-style rendezvous mechanism: a producer that fails to lock a segment can hand its element directly to a parked poller via an arena slot, skipping the underlying set entirely. Polling is batched: a thread periodically drains the top-K elements from every segment, sorts them once, and hands them out from a shared drain array until it's exhausted, at which point the next thread refills it.

Design images included on the kqueue branch.

## Branches

Each implementation above has its own branch containing:
- The implementation itself


Design images are included for `KQueue`, `KSkipListQueue`, and `ConcurrentMound`. The other implementations do not have accompanying diagrams.

## A note on correctness

Some of the designs (particularly `PIPQ`) have open correctness questions that would need proper concurrent testing (jcstress), which I could do but too lazy to. Treat them as reference implementations and design studies rather than drop-in queues.