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
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class MixedThrptBench {
    private PQ<Integer> queue;

    @Param({PQFactory.SKIP_PQ})
    private String type;

    final static int RANGE = 10;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createPQ(type);
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
* ╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_50_50 ─╮
│  Type   Role       Score  Error   Unit                        │
│  ------ ---------- ------ ------- ------                      │
│  SkipPQ fifty_add  3.800  ± 0.070 ops/us                      │
│  SkipPQ fifty_poll 8.516  ± 1.845 ops/us                      │
│  SkipPQ pollHit    3.825  ± 0.069 ops/us                      │
│  SkipPQ pollMiss   4.801  ± 1.894 ops/us                      │
│  SkipPQ aggregate  12.316 ± 1.836 ops/us                      │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_75_25 ─╮
│  Type   Role             Score  Error   Unit                  │
│  ------ ---------------- ------ ------- ------                │
│  SkipPQ pollHit          4.044  ± 0.064 ops/us                │
│  SkipPQ pollMiss         6.213  ± 1.855 ops/us                │
│  SkipPQ seventy_five_add 4.028  ± 0.063 ops/us                │
│  SkipPQ twenty_five_poll 10.182 ± 1.820 ops/us                │
│  SkipPQ aggregate        14.210 ± 1.805 ops/us                │
╰───────────────────────────────────────────────────────────────╯
* */
