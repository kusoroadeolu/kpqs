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


@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3)
public class InsertThrptBench {
    private RPQ<Integer> queue;

    @Param({"KSLQ"}) //base line priority blocking queue
    private String type;



    final static int STEADY_STATE_SIZE = 500_000;
    final static int RANGE = 100_000_000;

    @TearDown(Level.Iteration)
    public void teardown() {
        queue = null;
    }


    @Setup(Level.Iteration)
    public void setup() {
        queue = switch (type) {
            case "KSLQ" -> new KSkipListQueue<>(8);
            default -> throw new RuntimeException();
        };

        for (int i = 0; i < STEADY_STATE_SIZE; i++) {
            queue.add(ThreadLocalRandom.current().nextInt(RANGE));
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
    public void two_poll(Blackhole bh) {
        bh.consume(queue.poll());
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
    public void four_poll(Blackhole bh) {
        bh.consume(queue.poll());
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