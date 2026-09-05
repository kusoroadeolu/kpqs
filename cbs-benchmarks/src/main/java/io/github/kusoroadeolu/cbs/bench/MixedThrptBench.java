package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.factory.RPQFactory;
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
    private RPQ<Integer> queue;

    @Param({RPQFactory.MQ})
    private String type;

    final static int RANGE = 1_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = RPQFactory.createRPQ(type, 128_000);
    }


    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            queue.clear();
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
│  Type       Role       Score  Error   Unit                    │
│  ---------- ---------- ------ ------- ------                  │
│  MultiQueue fifty_add  6.900  ± 0.588 ops/us                  │
│  MultiQueue fifty_poll 5.647  ± 1.081 ops/us                  │
│  MultiQueue pollHit    5.668  ± 1.067 ops/us                  │
│  MultiQueue pollMiss   0.017  ± 0.010 ops/us                  │
│  MultiQueue aggregate  12.547 ± 1.378 ops/us                  │
╰───────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_75_25 ─╮
│  Type       Role             Score  Error   Unit              │
│  ---------- ---------------- ------ ------- ------            │
│  MultiQueue pollHit          1.372  ± 0.313 ops/us            │
│  MultiQueue pollMiss         0.000  ± 0.000 ops/us            │
│  MultiQueue seventy_five_add 18.794 ± 1.425 ops/us            │
│  MultiQueue twenty_five_poll 1.362  ± 0.315 ops/us            │
│  MultiQueue aggregate        20.156 ± 1.458 ops/us            │
╰───────────────────────────────────────────────────────────────╯

* */
