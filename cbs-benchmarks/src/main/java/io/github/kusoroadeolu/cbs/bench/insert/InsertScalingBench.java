package io.github.kusoroadeolu.cbs.bench.insert;

import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 2, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class InsertScalingBench {
    private ConcurrentSkipListMap<Integer, Boolean> queue;

    @Param({PQFactory.SKIP_PQ})
    private String type;

    final static int RANGE = 10_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = new ConcurrentSkipListMap<>();
    }

    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            queue.clear();
        }
    }


    @Threads(8)
    @Benchmark
    public void eight_full_insert(Blackhole bh) {
        var offer = queue.put(nextInt(), Boolean.TRUE);
        bh.consume(offer);
    }

    @Threads(6)
    @Benchmark
    public void six_full_insert(Blackhole bh) {
        var offer = queue.put(nextInt(), Boolean.TRUE);
        bh.consume(offer);

    }

    @Threads(4)
    @Benchmark
    public void four_full_insert(Blackhole bh) {
        var offer = queue.put(nextInt(), Boolean.TRUE);
        bh.consume(offer);
    }

    @Threads(2)
    @Benchmark
    public void two_full_insert(Blackhole bh) {
        var offer = queue.put(nextInt(), Boolean.TRUE);
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
│  Type   Score Error   Unit                                                    │
│  ------ ----- ------- ------                                                  │
│  SkipPQ 3.601 ± 0.078 ops/us                                                  │
╰───────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.four_full_insert ─╮
│  Type   Score Error   Unit                                                   │
│  ------ ----- ------- ------                                                 │
│  SkipPQ 2.414 ± 0.071 ops/us                                                 │
╰──────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.six_full_insert ─╮
│  Type   Score Error   Unit                                                  │
│  ------ ----- ------- ------                                                │
│  SkipPQ 3.038 ± 0.079 ops/us                                                │
╰─────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.two_full_insert ─╮
│  Type   Score Error   Unit                                                  │
│  ------ ----- ------- ------                                                │
│  SkipPQ 1.536 ± 0.097 ops/us                                                │
╰─────────────────────────────────────────────────────────────────────────────╯
**/


