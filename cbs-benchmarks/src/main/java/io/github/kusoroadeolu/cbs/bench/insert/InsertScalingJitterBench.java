package io.github.kusoroadeolu.cbs.bench.insert;

import io.github.kusoroadeolu.cbs.ConcurrentMound;
import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})

//Monotonic values with some random jitter
public class InsertScalingJitterBench {
    private PQ<Integer> queue;

    @Param({PQFactory.MOUNDS})
    private String type;

    final static int JITTER_RANGE = 100;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createPQ(type, MiscUtils.defaultCmp());
    }



    @TearDown(Level.Iteration)
    public void emptyQ() {
        synchronized (queue)
        {
            if (PQFactory.MOUNDS.equals(type)) ((ConcurrentMound<Integer>)queue).clearUnsafe();
            else queue.clear();
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
}

/*
* ╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.eight_full_insert ─╮
│  Type                  Score  Error   Unit                                          │
│  --------------------- ------ ------- ------                                        │
│  PriorityBlockingQueue 17.283 ± 0.949 ops/us                                        │
╰─────────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.four_full_insert ─╮
│  Type                  Score  Error   Unit                                         │
│  --------------------- ------ ------- ------                                       │
│  PriorityBlockingQueue 18.221 ± 0.874 ops/us                                       │
╰────────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.six_full_insert ─╮
│  Type                  Score  Error   Unit                                        │
│  --------------------- ------ ------- ------                                      │
│  PriorityBlockingQueue 17.343 ± 0.884 ops/us                                      │
╰───────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.two_full_insert ─╮
│  Type                  Score  Error   Unit                                        │
│  --------------------- ------ ------- ------                                      │
│  PriorityBlockingQueue 13.740 ± 0.488 ops/us                                      │
╰───────────────────────────────────────────────────────────────────────────────────╯
*
* ╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.eight_full_insert ─╮
│  Type   Score  Error   Unit                                                         │
│  ------ ------ ------- ------                                                       │
│  KQueue 39.887 ± 2.022 ops/us                                                       │
╰─────────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.four_full_insert ─╮
│  Type   Score  Error   Unit                                                        │
│  ------ ------ ------- ------                                                      │
│  KQueue 35.239 ± 2.091 ops/us                                                      │
╰────────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.six_full_insert ─╮
│  Type   Score  Error   Unit                                                       │
│  ------ ------ ------- ------                                                     │
│  KQueue 38.337 ± 2.061 ops/us                                                     │
╰───────────────────────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.insert.InsertScalingJitterBench.two_full_insert ─╮
│  Type   Score  Error   Unit                                                       │
│  ------ ------ ------- ------                                                     │
│  KQueue 27.676 ± 1.360 ops/us                                                     │
╰───────────────────────────────────────────────────────────────────────────────────╯

*
* */
