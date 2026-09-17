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

//Monotonic values with some random jitter
public class InsertScalingJitterBench {
    private PQ<Integer> queue;

    @Param({PQFactory.PIPQ})
    private String type;

    final static int JITTER_RANGE = 100;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createPQ(type, 128_000);
    }



    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            queue.unsafeClear();
        }
    }

    @State(Scope.Thread)
    public static class Sequence {
        int i;


        @Setup(Level.Iteration)
        public void reset() {
            i = 0;
        }

        int nextInt() {
            return i++ + jitter();
        }

        int jitter() {
           return ThreadLocalRandom.current().nextInt(0, JITTER_RANGE);
        }

    }

    @Threads(8)
    @Benchmark
    public void eight_full_insert(Blackhole bh, Sequence sequence) {
        boolean offer = queue.offer(sequence.nextInt());
        bh.consume(offer);
    }

    @Threads(6)
    @Benchmark
    public void six_full_insert(Blackhole bh, Sequence sequence) {
        boolean offer = queue.offer(sequence.nextInt());
        bh.consume(offer);

    }

    @Threads(4)
    @Benchmark
    public void four_full_insert(Blackhole bh, Sequence sequence) {
        boolean offer = queue.offer(sequence.nextInt());
        bh.consume(offer);
    }

    @Threads(2)
    @Benchmark
    public void two_full_insert(Blackhole bh, Sequence sequence) {
        boolean offer = queue.offer(sequence.nextInt());
        bh.consume(offer);
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(InsertScalingJitterBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();
        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.eight_full_insert ─╮
│  Type Score  Error   Unit                                                           │
│  ---- ------ ------- ------                                                         │
│  PIPQ 45.915 ± 7.910 ops/us                                                         │
╰─────────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.four_full_insert ─╮
│  Type Score  Error   Unit                                                          │
│  ---- ------ ------- ------                                                        │
│  PIPQ 44.220 ± 2.788 ops/us                                                        │
╰────────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.six_full_insert ─╮
│  Type Score  Error   Unit                                                         │
│  ---- ------ ------- ------                                                       │
│  PIPQ 38.906 ± 1.900 ops/us                                                       │
╰───────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.two_full_insert ─╮
│  Type Score  Error   Unit                                                         │
│  ---- ------ ------- ------                                                       │
│  PIPQ 42.434 ± 3.868 ops/us                                                       │
╰───────────────────────────────────────────────────────────────────────────────────╯
* */
