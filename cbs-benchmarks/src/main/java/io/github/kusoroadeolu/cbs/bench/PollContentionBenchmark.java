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
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class PollContentionBenchmark {


    static final int FILL_SIZE = 10_000_000;

    PQ<Integer> queue;

    @Param({PQFactory.SKIP_PQ, PQFactory.PBQ})
    public String type;

    @Setup(Level.Iteration)
    public void fill() {
        queue = PQFactory.createPQ(type,  Runtime.getRuntime().availableProcessors(), 500_000);

        for (int i = 0; i < FILL_SIZE; i++) {
            int v = ThreadLocalRandom.current().nextInt();
            queue.offer(v);
        }
    }

    @Benchmark
    @Threads(1)
    public void pollQueue1(Blackhole bh) {
        bh.consume(queue.poll());
    }

    @Benchmark
    @Threads(2)
    public void pollQueue2(Blackhole bh) {
        bh.consume(queue.poll());
    }

    @Benchmark
    @Threads(4)
    public void pollQueue4(Blackhole bh) {
        bh.consume(queue.poll());
    }

    @Benchmark
    @Threads(8)
    public void pollQueue8(Blackhole bh) {
        bh.consume(queue.poll());
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(PollContentionBenchmark.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }

}

/*
╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue1 ─╮
│  Type                  Score Error   Unit                            │
│  --------------------- ----- ------- ------                          │
│  SkipPQ                  0.828 ± 0.158 ops/us                          │
│  PriorityBlockingQueue 1.078 ± 0.036 ops/us                          │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue2 ─╮
│  Type                  Score Error   Unit                            │
│  --------------------- ----- ------- ------                          │
│  SkipPQ                  0.740 ± 0.156 ops/us                          │
│  PriorityBlockingQueue 0.925 ± 0.020 ops/us                          │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue4 ─╮
│  Type                  Score Error   Unit                            │
│  --------------------- ----- ------- ------                          │
│  SkipPQ                  0.821 ± 0.134 ops/us                          │
│  PriorityBlockingQueue 0.910 ± 0.027 ops/us                          │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue8 ─╮
│  Type                  Score Error   Unit                            │
│  --------------------- ----- ------- ------                          │
│  SkipPQ                  0.777 ± 0.142 ops/us                          │
│  PriorityBlockingQueue 0.893 ± 0.052 ops/us                          │
╰──────────────────────────────────────────────────────────────────────╯
* */