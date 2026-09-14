package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.KSkipListQueue;
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
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class MixedThrptBench {
    private RPQ<Integer> queue;

    @Param({"KSkipListQueue"})
    private String type;

    final static int RANGE = 10_000_000;

    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    @TearDown(Level.Iteration)
    public void teardown() {
        synchronized (queue) {
            queue.unsafeClear();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        queue = switch (type) {
            case "KSkipListQueue" -> new KSkipListQueue<>(NCPU);
            default -> throw new RuntimeException();
        };
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

    @Group("ratio_6_2")
    @GroupThreads(6)
    @Benchmark
    public void six_add(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.offer(val));
    }

    @Group("ratio_6_2")
    @GroupThreads(2)
    @Benchmark
    public void two_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.relaxedPoll();
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
        bh.consume(queue.offer(val));
    }

    @Group("ratio_4_4")
    @GroupThreads(4)
    @Benchmark
    public void four_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.relaxedPoll();
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
                    .include(MixedThrptBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();
        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_4_4 ─╮
│  Type           Role      Score Error   Unit                │
│  -------------- --------- ----- ------- ------              │
│  KSkipListQueue four_add  4.314 ± 0.122 ops/us              │
│  KSkipListQueue four_poll 3.223 ± 0.111 ops/us              │
│  KSkipListQueue pollHit   3.237 ± 0.112 ops/us              │
│  KSkipListQueue pollMiss  0.000 ± 0.000 ops/us              │
│  KSkipListQueue aggregate 7.536 ± 0.231 ops/us              │
╰─────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_6_2 ─╮
│  Type           Role      Score Error   Unit                │
│  -------------- --------- ----- ------- ------              │
│  KSkipListQueue pollHit   2.525 ± 0.079 ops/us              │
│  KSkipListQueue pollMiss  0.000 ± 0.000 ops/us              │
│  KSkipListQueue six_add   4.137 ± 0.091 ops/us              │
│  KSkipListQueue two_poll  2.518 ± 0.079 ops/us              │
│  KSkipListQueue aggregate 6.654 ± 0.167 ops/us              │
╰─────────────────────────────────────────────────────────────╯
* */


