package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
@Fork(value = 2, jvmArgsAppend = {"-Xms2g", "-Xmx2g"})
public class PQBench {

    static final int TOTAL_OPS = 100_000;

    public record CDNData(String key, String value, int priority) implements Comparable<CDNData> {
        @Override
        public int compareTo(CDNData o) {
            return Integer.compare(priority, o.priority);
        }
    }

    static CDNData[] generateData(int n) {
        Random r = new Random(0x9E3779B97F4A7C15L);
        CDNData[] data = new CDNData[n];
        for (int i = 0; i < n; i++) {
            data[i] = new CDNData("key-" + i, "value-" + i, r.nextInt(1 << 30));
        }
        return data;
    }

    static PQ<CDNData> newQueue(String impl) {
        return PQFactory.createPQ(impl);
    }

    @State(Scope.Thread)
    public static class ChunkState {
        int start;
        int end;

        @Setup(Level.Trial)
        public void setup(ThreadParams tp) {
            int perWorker = TOTAL_OPS / tp.getThreadCount();
            start = tp.getThreadIndex() * perWorker;
            end = start + perWorker;
        }
    }

    @State(Scope.Benchmark)
    public static class InsertState {

        @Param({PQFactory.SKIP_PQ})
        public String impl;

        PQ<CDNData> queue;
        CDNData[] data;

        @Setup(Level.Trial)
        public void setupTrial() {
            data = generateData(TOTAL_OPS);
            queue = newQueue(impl);
        }

        @Setup(Level.Iteration)
        public void resetEmpty() {
            queue.unsafeClear();
        }
    }

    @State(Scope.Benchmark)
    public static class PollState {

        @Param({PQFactory.SKIP_PQ})
        public String impl;

        PQ<CDNData> queue;
        CDNData[] data;

        @Setup(Level.Trial)
        public void setupTrial() {
            data = generateData(TOTAL_OPS);
            queue = newQueue(impl);
        }

        @Setup(Level.Iteration)
        public void refill() {
            queue.unsafeClear();
            for (CDNData d : data) {
                queue.offer(d);
            }
        }
    }

    void doInsert(InsertState st, ChunkState cs, Blackhole bh) {
        PQ<CDNData> q = st.queue;
        CDNData[] data = st.data;
        for (int i = cs.start; i < cs.end; i++) {
            bh.consume(q.offer(data[i]));
        }
    }

    void doPoll(PollState st, ChunkState cs, Blackhole bh) {
        PQ<CDNData> q = st.queue;
        for (int i = cs.start; i < cs.end; i++) {
            bh.consume(q.poll());
        }
    }


    // ---- benchmarks ----------------------------------------------------------

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    public void insert1(InsertState st, ChunkState cs, Blackhole bh) {
        doInsert(st, cs, bh);
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    public void poll1(PollState st, ChunkState cs, Blackhole bh) {
        doPoll(st, cs, bh);
    }


    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    @Threads(2)
    public void insert2(InsertState st, ChunkState cs, Blackhole bh) {
        doInsert(st, cs, bh);
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    @Threads(2)
    public void poll2(PollState st, ChunkState cs, Blackhole bh) {
        doPoll(st, cs, bh);
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    @Threads(4)
    public void insert4(InsertState st, ChunkState cs, Blackhole bh) {
        doInsert(st, cs, bh);
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    @Threads(4)
    public void poll4(PollState st, ChunkState cs, Blackhole bh) {
        doPoll(st, cs, bh);
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    @Threads(8)
    public void insert8(InsertState st, ChunkState cs, Blackhole bh) {
        doInsert(st, cs, bh);
    }

    @Benchmark
    @OperationsPerInvocation(TOTAL_OPS)
    @Threads(8)
    public void poll8(PollState st, ChunkState cs, Blackhole bh) {
        doPoll(st, cs, bh);
    }
}

/*
* ╭ io.github.kusoroadeolu.cbs.bench.PQBench.insert1 ─╮
│  Impl   Score   Error    Unit                     │
│  ------ ------- -------- -----                    │
│  SkipPQ 932.375 ± 20.527 ns/op                    │
╰───────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.insert2 ─╮
│  Impl   Score   Error    Unit                     │
│  ------ ------- -------- -----                    │
│  SkipPQ 510.889 ± 23.528 ns/op                    │
╰───────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.insert4 ─╮
│  Impl   Score   Error    Unit                     │
│  ------ ------- -------- -----                    │
│  SkipPQ 296.381 ± 20.103 ns/op                    │
╰───────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.insert8 ─╮
│  Impl   Score   Error    Unit                     │
│  ------ ------- -------- -----                    │
│  SkipPQ 188.380 ± 16.425 ns/op                    │
╰───────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.poll1 ─╮
│  Impl   Score   Error    Unit                   │
│  ------ ------- -------- -----                  │
│  SkipPQ 188.243 ± 16.885 ns/op                  │
╰─────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.poll2 ─╮
│  Impl   Score   Error   Unit                    │
│  ------ ------- ------- -----                   │
│  SkipPQ 239.482 ± 7.176 ns/op                   │
╰─────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.poll4 ─╮
│  Impl   Score   Error   Unit                    │
│  ------ ------- ------- -----                   │
│  SkipPQ 263.547 ± 6.346 ns/op                   │
╰─────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PQBench.poll8 ─╮
│  Impl   Score   Error    Unit                   │
│  ------ ------- -------- -----                  │
│  SkipPQ 250.524 ± 23.584 ns/op                  │
╰─────────────────────────────────────────────────╯

* */