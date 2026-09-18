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

    @Param({PQFactory.CBQ})
    public String type;

    @Setup(Level.Iteration)
    public void fill() {
        queue = PQFactory.createPQ(type);

        for (int i = 0; i < FILL_SIZE; i++) {
            int v = ThreadLocalRandom.current().nextInt();
            queue.offer(v);
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
    public void pollQueue8(Blackhole bh, PollCounters counters) {
        var res = queue.poll();
        bh.consume(res);
        if (res == null) counters.pollMiss++;
        else counters.pollHit++;
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
* ╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue1 ─╮
│  Type      Score Error   Unit                                        │
│  --------- ----- ------- ------                                      │
│  ChunkedPQ 9.483 ± 0.499 ops/us                                      │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue2 ─╮
│  Type      Score Error   Unit                                        │
│  --------- ----- ------- ------                                      │
│  ChunkedPQ 8.301 ± 0.713 ops/us                                      │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue4 ─╮
│  Type      Score Error   Unit                                        │
│  --------- ----- ------- ------                                      │
│  ChunkedPQ 8.589 ± 0.457 ops/us                                      │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.PollContentionBenchmark.pollQueue8 ─╮
│  Type      Role      Score Error   Unit                              │
│  --------- --------- ----- ------- ------                            │
│  ChunkedPQ pollHit   8.484 ± 0.393 ops/us                            │
│  ChunkedPQ pollMiss  0.000 ± 0.000 ops/us                            │
│  ChunkedPQ aggregate 8.450 ± 0.412 ops/us                            │
╰──────────────────────────────────────────────────────────────────────╯
* */