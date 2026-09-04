package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.ConcurrentMound;
import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Comparator;
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

    @Param({PQFactory.MOUNDS})
    private String type;

    final static int RANGE = 10_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createPQ(type, MiscUtils.defaultCmp());
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            if (PQFactory.MOUNDS.equals(type)) ((ConcurrentMound<Integer>)queue).clearUnsafe();
            else queue.clear();
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
* ╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_50_50 ─╮
│  Type   Role       Score Error   Unit                         │
│  ------ ---------- ----- ------- ------                       │
│  Mounds fifty_add  1.552 ± 0.162 ops/us                       │
│  Mounds fifty_poll 1.382 ± 0.543 ops/us                       │
│  Mounds pollHit    0.603 ± 0.039 ops/us                       │
│  Mounds pollMiss   0.793 ± 0.539 ops/us                       │
│  Mounds aggregate  2.934 ± 0.574 ops/us                       │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_75_25 ─╮
│  Type   Role             Score Error   Unit                   │
│  ------ ---------------- ----- ------- ------                 │
│  Mounds pollHit          0.380 ± 0.033 ops/us                 │
│  Mounds pollMiss         0.131 ± 0.184 ops/us                 │
│  Mounds seventy_five_add 2.722 ± 0.201 ops/us                 │
│  Mounds twenty_five_poll 0.507 ± 0.184 ops/us                 │
│  Mounds aggregate        3.229 ± 0.272 ops/us                 │
╰───────────────────────────────────────────────────────────────╯
* */