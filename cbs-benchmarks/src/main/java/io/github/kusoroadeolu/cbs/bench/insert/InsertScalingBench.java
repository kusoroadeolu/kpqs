package io.github.kusoroadeolu.cbs.bench.insert;

import io.github.kusoroadeolu.cbs.ConcurrentMound;
import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.MixedThrptBench;
import io.github.kusoroadeolu.cbs.bench.factory.RPQFactory;
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
@Measurement(iterations = 15, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class InsertScalingBench {
    private ConcurrentMound<Integer> queue;

    @Param({RPQFactory.KQ})
    private String type;

    final static int RANGE = 1_000_000;


    @Setup(Level.Trial)
    public void setup() {
       // queue = RPQFactory.createRPQ(type, 128_000);
        queue = new ConcurrentMound<>(null);
    }



//    @TearDown(Level.Iteration)
//    public void emptyQ() {
//        synchronized (queue)
//        {
//            queue.clear();
//        }
//    }

    @Threads(8)
    @Benchmark
    public void eight_full_insert(Blackhole bh) {
        boolean offer = queue.add(nextInt());
        bh.consume(offer);
    }
//
//    @Threads(6)
//    @Benchmark
//    public void six_full_insert(Blackhole bh) {
//        boolean offer = queue.offer(nextInt());
//        bh.consume(offer);
//
//    }
//
//    @Threads(4)
//    @Benchmark
//    public void four_full_insert(Blackhole bh) {
//        boolean offer = queue.offer(nextInt());
//        bh.consume(offer);
//    }
//
//    @Threads(2)
//    @Benchmark
//    public void two_full_insert(Blackhole bh) {
//        boolean offer = queue.offer(nextInt());
//        bh.consume(offer);
//    }

    int nextInt() {
        return ThreadLocalRandom.current().nextInt(0, RANGE);
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(InsertScalingBench.class.getSimpleName())
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.eight_full_insert ─╮
│  Type   Score  Error   Unit                                                   │
│  ------ ------ ------- ------                                                 │
│  KQueue 34.516 ± 2.167 ops/us                                                 │
╰───────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.four_full_insert ─╮
│  Type   Score  Error   Unit                                                  │
│  ------ ------ ------- ------                                                │
│  KQueue 28.376 ± 1.436 ops/us                                                │
╰──────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.six_full_insert ─╮
│  Type   Score  Error   Unit                                                 │
│  ------ ------ ------- ------                                               │
│  KQueue 32.076 ± 1.764 ops/us                                               │
╰─────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingBench.two_full_insert ─╮
│  Type   Score  Error   Unit                                                 │
│  ------ ------ ------- ------                                               │
│  KQueue 21.826 ± 1.052 ops/us                                               │
╰─────────────────────────────────────────────────────────────────────────────╯
**/


/*
╭ io.github.kusoroadeolu.cbs.bench.InsertScalingBench.eight_full_insert ─╮
│  Type                  Score  Error   Unit                             │
│  --------------------- ------ ------- ------                           │
│  PriorityBlockingQueue 13.907 ± 0.694 ops/us                           │
╰────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.InsertScalingBench.four_full_insert ─╮
│  Type                  Score  Error   Unit                            │
│  --------------------- ------ ------- ------                          │
│  PriorityBlockingQueue 13.719 ± 0.900 ops/us                          │
╰───────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.InsertScalingBench.six_full_insert ─╮
│  Type                  Score  Error   Unit                           │
│  --------------------- ------ ------- ------                         │
│  PriorityBlockingQueue 13.756 ± 0.695 ops/us                         │
╰──────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.InsertScalingBench.two_full_insert ─╮
│  Type                  Score  Error   Unit                           │
│  --------------------- ------ ------- ------                         │
│  PriorityBlockingQueue 10.982 ± 0.364 ops/us                         │
╰──────────────────────────────────────────────────────────────────────╯
* */
