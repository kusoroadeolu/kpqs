package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.rmq.Heap;
import io.github.kusoroadeolu.cbs.rmq.KQueue;
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
//
@Fork(value = 1)
public class InsertThrptBench {
    private KQueue<Integer> queue;

    @Param({"KQ"})
    private String type;

    @Param({"GROWABLE"})
    private String heapKind;


    final static int STEADY_STATE_SIZE = 65536;

    @TearDown(Level.Iteration)
    public void teardown() {
        queue.logSegmentSizes();
    }


    @Setup(Level.Trial)
    public void setup() {
        queue = switch (type) {
            case "KQ" -> {
                Heap.Kind kind = Heap.Kind.valueOf(heapKind);
                yield new KQueue<>(-1, kind);
            }
           // case "PBQ" -> new PBQ<>();
            default -> throw new RuntimeException();
        };

        for (int i = 0; i < STEADY_STATE_SIZE; i++) {
            queue.add(ThreadLocalRandom.current().nextInt(1_000_000));
        }
    }

    @Group("ratio_5_3")
    @GroupThreads(5)
    @Benchmark
    public void insert(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(1_000_000);
        bh.consume(queue.add(val));
    }

    @Group("ratio_5_3")
    @GroupThreads(3)
    @Benchmark
    public void poll(Blackhole bh) {
        bh.consume(queue.poll());
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(InsertThrptBench.class.getSimpleName())
//                    .result("results.json")
//                    .resultFormat(ResultFormatType.JSON)
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}
