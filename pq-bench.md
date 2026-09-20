# Priority Queue Benchmark Results

JMH `SingleShotTime` benchmarks, 100,000 ops per run, `ns/op`. Java 17.

## Insert

| Threads | Mounds | ChunkedPQ | PIPQ |
|---|---|---|---|
| 1 | 237.547 ± 10.774 | 307.274 ± 14.072 | 54.375 ± 3.826 |
| 2 | 163.894 ± 10.819 | 199.849 ± 27.564 | 35.079 ± 4.340 |
| 4 | 140.514 ± 10.754 | 139.658 ± 7.933 | 20.723 ± 2.941 |
| 8 | 110.982 ± 8.635 | 120.730 ± 30.048 | 13.717 ± 1.874 |

## Poll (Extract-Min)

| Threads | Mounds | ChunkedPQ | PIPQ |
|---|---|---|---|
| 1 | 897.696 ± 17.940 | 94.040 ± 3.421 | 394.298 ± 23.293 |
| 2 | 998.405 ± 40.631 | 106.898 ± 10.483 | 364.279 ± 26.803 |
| 4 | 973.067 ± 50.409 | 106.679 ± 5.228 | 373.376 ± 23.402 |
| 8 | 824.592 ± 17.464 | 106.227 ± 11.580 | 404.578 ± 35.259 |

## Notes

- **PIPQ** — fastest insert by a wide margin, scales near-linearly with threads (54ns → 14ns, 1→8 threads). Weakest poll of the three, flat across thread counts (~364-405ns), no real scaling either direction. Optimized for inserts, as intended.
- **ChunkedPQ** — fastest poll by a wide margin, stable across thread counts (~94-107ns), barely affected by contention. Middle-of-the-road insert performance (120-307ns), scales reasonably with threads.
- **Mounds** — lock-based (mutex), built as a fast baseline before attempting a lock-free/DCAS version. Slowest poll of the three (824-998ns), moderate insert performance (111-238ns) that scales decently with threads.
- No implementation wins both insert and poll — clear specialization trade-off. PIPQ for insert-heavy workloads, ChunkedPQ for extract-heavy or balanced workloads.
- Relaxed variants of all three exist but haven't been benchmarked yet.