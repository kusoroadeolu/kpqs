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


//    @Group("ratio_75_25")
//    @GroupThreads(6)
//    @Benchmark
//    public void seventy_five_add(Blackhole bh) {
//        bh.consume(queue.offer(nextInt()));
//    }
//
//    @Group("ratio_75_25")
//    @GroupThreads(2)
//    @Benchmark
//    public void twenty_five_poll(Blackhole bh, PollCounters counters) {
//        Integer result = queue.poll();
//        bh.consume(result);
//        if (result == null) {
//            counters.pollMiss++;
//        } else {
//            counters.pollHit++;
//        }
//    }

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
│  Type   Role       Score  Error   Unit                        │
│  ------ ---------- ------ ------- ------                      │
│  SkipPQ fifty_add  2.815  ± 0.061 ops/us                      │
│  SkipPQ fifty_poll 49.511 ± 5.919 ops/us                      │
│  SkipPQ pollHit    2.817  ± 0.060 ops/us                      │
│  SkipPQ pollMiss   46.702 ± 5.899 ops/us                      │
│  SkipPQ aggregate  52.326 ± 5.939 ops/us                      │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_75_25 ─╮
│  Type   Role             Score Error   Unit                   │
│  ------ ---------------- ----- ------- ------                 │
│  SkipPQ pollHit          3.047 ± 0.032 ops/us                 │
│  SkipPQ pollMiss         0.002 ± 0.003 ops/us                 │
│  SkipPQ seventy_five_add 3.688 ± 0.050 ops/us                 │
│  SkipPQ twenty_five_poll 3.043 ± 0.032 ops/us                 │
│  SkipPQ aggregate        6.732 ± 0.064 ops/us                 │
╰───────────────────────────────────────────────────────────────╯
*/
