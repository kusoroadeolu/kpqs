package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.rmq.KSkipListQueue;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class InsertThrptBench {
    private RPQ<Integer> queue;

    @Param({"KQ"})
    private String type;

    final static int STEADY_STATE_SIZE = 10_000_000;
    final static int RANGE = 100_000_000;

    @TearDown(Level.Iteration)
    public void teardown() {
        queue.clear();
    }

    @Setup(Level.Trial)
    public void setup() {
        queue = switch (type) {
            case "KQ" -> new KSkipListQueue<>(8);
            case "PBQ" -> new PBQ<>();
            default -> throw new RuntimeException();
        };

        for (int i = 0; i < STEADY_STATE_SIZE; i++) {
            queue.add(ThreadLocalRandom.current().nextInt(RANGE));
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

    @Threads(8)
    @Benchmark
    public void eight_insert(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
    }

    @Group("ratio_6_2")
    @GroupThreads(6)
    @Benchmark
    public void six_add(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
    }

    @Group("ratio_6_2")
    @GroupThreads(2)
    @Benchmark
    public void two_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.poll();
        bh.consume(result);
        if (result == null) {
            counters.pollMiss++;
        } else {
            counters.pollHit++;
        }
    }

    @Group("ratio_4_4")
    @GroupThreads(4)
    @Benchmark
    public void four_add(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
    }

    @Group("ratio_4_4")
    @GroupThreads(4)
    @Benchmark
    public void four_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.poll();
        bh.consume(result);
        if (result == null) {
            counters.pollMiss++;
        } else {
            counters.pollHit++;
        }
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(InsertThrptBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();
        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.InsertThrptBench.eight_insert ─╮
│  Type Score Error   Unit                                        │
│  ---- ----- ------- ------                                      │
│  KQ   2.732 ± 0.199 ops/us                                      │
╰─────────────────────────────────────────────────────────────────╯
╭ io.github.kusoroadeolu.cbs.bench.InsertThrptBench.ratio_4_4 ─╮
│  Type Role      Score Error   Unit                           │
│  ---- --------- ----- ------- ------                         │
│  KQ   four_add  4.499 ± 0.148 ops/us                         │
│  KQ   four_poll 3.614 ± 0.117 ops/us                         │
│  KQ   pollHit   3.629 ± 0.111 ops/us                         │
│  KQ   pollMiss  0.000 ± 0.000 ops/us                         │
│  KQ   aggregate 8.113 ± 0.263 ops/us                         │
╰──────────────────────────────────────────────────────────────╯
╭ io.github.kusoroadeolu.cbs.bench.InsertThrptBench.ratio_6_2 ─╮
│  Type Role      Score Error   Unit                           │
│  ---- --------- ----- ------- ------                         │
│  KQ   pollHit   2.902 ± 0.096 ops/us                         │
│  KQ   pollMiss  0.000 ± 0.000 ops/us                         │
│  KQ   six_add   4.281 ± 0.081 ops/us                         │
│  KQ   two_poll  2.888 ± 0.094 ops/us                         │
│  KQ   aggregate 7.169 ± 0.169 ops/us                         │
╰──────────────────────────────────────────────────────────────╯
* */


