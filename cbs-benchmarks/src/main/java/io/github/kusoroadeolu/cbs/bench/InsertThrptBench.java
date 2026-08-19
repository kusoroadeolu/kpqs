package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
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
@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3)
public class InsertThrptBench {
    private RPQ<Integer> queue;

    @Param({"KQ", "PBQ"}) //base line priority blocking queue
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
            case "KQ" -> new KQueue<>(8);
            case "PBQ" -> new PBQ<>();
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
    public void six_insert(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
    }

    @Group("ratio_6_2")
    @GroupThreads(2)
    @Benchmark
    public void two_poll(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
    }

    @Group("ratio_4_4")
    @GroupThreads(4)
    @Benchmark
    public void four_insert(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
    }

    @Group("ratio_4_4")
    @GroupThreads(4)
    @Benchmark
    public void four_poll(Blackhole bh) {
        int val = ThreadLocalRandom.current().nextInt(RANGE);
        bh.consume(queue.add(val));
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

/*
*
╭ io.github.kusoroadeolu.cbs.bench.InsertThrptBench.eight_insert ─╮
│  Type Score  Error   Unit                                       │
│  ---- ------ ------- ------                                     │
│  KQ   11.356 ± 0.705 ops/us                                     │
│  PBQ  9.551  ± 0.791 ops/us                                     │
╰─────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.InsertThrptBench.ratio_4_4 ─╮
│  Type Role        Score  Error   Unit                        │
│  ---- ----------- ------ ------- ------                      │
│  KQ   four_insert 5.816  ± 0.395 ops/us                      │
│  KQ   four_poll   5.809  ± 0.401 ops/us                      │
│  KQ   aggregate   11.625 ± 0.774 ops/us                      │
│  PBQ  four_insert 4.705  ± 0.383 ops/us                      │
│  PBQ  four_poll   4.709  ± 0.441 ops/us                      │
│  PBQ  aggregate   9.413  ± 0.813 ops/us                      │
╰──────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.InsertThrptBench.ratio_6_2 ─╮
│  Type Role       Score  Error   Unit                         │
│  ---- ---------- ------ ------- ------                       │
│  KQ   six_insert 8.453  ± 0.623 ops/us                       │
│  KQ   two_poll   2.899  ± 0.227 ops/us                       │
│  KQ   aggregate  11.352 ± 0.795 ops/us                       │
│  PBQ  six_insert 7.191  ± 0.730 ops/us                       │
│  PBQ  two_poll   2.304  ± 0.198 ops/us                       │
│  PBQ  aggregate  9.495  ± 0.920 ops/us                       │
╰──────────────────────────────────────────────────────────────╯
* */
