package io.github.kusoroadeolu.cbs.bench.insert;

import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
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
@Fork(value = 2, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class InsertScalingBench {
    private PQ<Integer> queue;

    @Param({PQFactory.PIPQ})
    private String type;

    final static int RANGE = 1_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createPQ(type, 128_000);
    }

    @TearDown(Level.Iteration)
    public void fill() {
        for (int i = 0; i < 5_000; ++i) {
            queue.offer(ThreadLocalRandom.current().nextInt(0, 50));
        }
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            queue.unsafeClear();
        }
    }


    @Threads(8)
    @Benchmark
    public void eight_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);
    }

    @Threads(6)
    @Benchmark
    public void six_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);

    }

    @Threads(4)
    @Benchmark
    public void four_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);
    }

    @Threads(2)
    @Benchmark
    public void two_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);
    }


    int nextInt() {
        return ThreadLocalRandom.current().nextInt(0, RANGE);
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(InsertScalingBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();
        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.eight_full_insert ─╮
│  Type Score  Error   Unit                                                     │
│  ---- ------ ------- ------                                                   │
│  PIPQ 38.302 ± 3.045 ops/us                                                   │
╰───────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.four_full_insert ─╮
│  Type Score  Error   Unit                                                    │
│  ---- ------ ------- ------                                                  │
│  PIPQ 33.940 ± 3.021 ops/us                                                  │
╰──────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.six_full_insert ─╮
│  Type Score  Error   Unit                                                   │
│  ---- ------ ------- ------                                                 │
│  PIPQ 37.805 ± 2.560 ops/us                                                 │
╰─────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.two_full_insert ─╮
│  Type Score  Error   Unit                                                   │
│  ---- ------ ------- ------                                                 │
│  PIPQ 27.839 ± 2.160 ops/us                                                 │
╰─────────────────────────────────────────────────────────────────────────────╯
**/


