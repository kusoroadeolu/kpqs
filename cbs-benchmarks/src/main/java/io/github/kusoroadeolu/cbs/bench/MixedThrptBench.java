package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class MixedThrptBench {
    private PQ<Integer> queue;

    @Param({PQFactory.CBQ})
    private String type;

    final static int RANGE = 10_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createPQ(type);
        //for (int i = 0; i < 500_000; ++i) queue.offer(nextInt());
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            queue.unsafeClear();
        }
    }


    @AuxCounters(AuxCounters.Type.OPERATIONS)
    @State(Scope.Thread)
    public static class PollCounters {
        public long pollHit;
        public long pollMiss;

        @Setup(Level.Iteration)
        public void reset() {
            pollHit = 0;
            pollMiss = 0;
        }
    }


    @Group("ratio_75_25")
    @GroupThreads(6)
    @Benchmark
    public void seventy_five_add(Blackhole bh) {
        bh.consume(queue.offer(nextInt()));
    }

    @Group("ratio_75_25")
    @GroupThreads(2)
    @Benchmark
    public void twenty_five_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.poll();
        bh.consume(result);
        if (result == null) {
            counters.pollMiss++;
        } else {
            counters.pollHit++;
        }
    }

    @Group("ratio_50_50")
    @GroupThreads(4)
    @Benchmark
    public void fifty_add(Blackhole bh) {
       bh.consume(queue.offer(nextInt()));
    }

    @Group("ratio_50_50")
    @GroupThreads(4)
    @Benchmark
    public void fifty_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.poll();
        bh.consume(result);
        if (result == null) {
            counters.pollMiss++;
        } else {
            counters.pollHit++;
        }
    }

    int nextInt() {
        return ThreadLocalRandom.current().nextInt(0, RANGE);
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(MixedThrptBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_50_50 ─╮
│  Type      Role       Score  Error   Unit                     │
│  --------- ---------- ------ ------- ------                   │
│  ChunkedPQ fifty_add  1.177  ± 0.043 ops/us                   │
│  ChunkedPQ fifty_poll 17.075 ± 2.630 ops/us                   │
│  ChunkedPQ pollHit    1.177  ± 0.043 ops/us                   │
│  ChunkedPQ pollMiss   15.901 ± 2.660 ops/us                   │
│  ChunkedPQ aggregate  18.253 ± 2.602 ops/us                   │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_75_25 ─╮
│  Type      Role             Score Error   Unit                │
│  --------- ---------------- ----- ------- ------              │
│  ChunkedPQ pollHit          1.492 ± 0.017 ops/us              │
│  ChunkedPQ pollMiss         2.918 ± 0.420 ops/us              │
│  ChunkedPQ seventy_five_add 1.492 ± 0.016 ops/us              │
│  ChunkedPQ twenty_five_poll 4.410 ± 0.425 ops/us              │
│  ChunkedPQ aggregate        5.902 ± 0.431 ops/us              │
╰───────────────────────────────────────────────────────────────╯
* */
