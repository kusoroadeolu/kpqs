package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.ConcurrentMound;
import io.github.kusoroadeolu.cbs.RPQ;
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
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class MixedThrptBench {
    private ConcurrentMound<Integer> queue;

    private String type;

    final static int RANGE = 10_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = new ConcurrentMound<>(null);
        for (int i = 0; i < 1_000_000; ++i) queue.add(ThreadLocalRandom.current().nextInt(0, RANGE));
    }


//    @TearDown(Level.Iteration)
//    public void emptyQ() {
//        synchronized (queue)
//        {
//            queue.clear();
//        }
//    }


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

//    @Threads(8)
//    @Benchmark
//    public void full_insert(Blackhole bh) {
//        bh.consume(queue.add(nextInt()));
//    }

//    @Group("ratio_75_25")
//    @GroupThreads(6)
//    @Benchmark
//    public void seventy_five_add(Blackhole bh) {
//        bh.consume(queue.add(nextInt()));
//    }
//
//    @Group("ratio_75_25")
//    @GroupThreads(2)
//    @Benchmark
//    public void twenty_five_poll(Blackhole bh, PollCounters counters) {
//        Integer result = queue.poll();
//        bh.consume(result);
//        if (result == null) {
//            counters.pollMiss++;
//        } else {
//            counters.pollHit++;
//        }
//    }

    @Group("ratio_50_50")
    @GroupThreads(4)
    @Benchmark
    public void fifty_add(Blackhole bh) {
       bh.consume(queue.add(nextInt()));
    }

    @Group("ratio_50_50")
    @GroupThreads(4)
    @Benchmark
    public void fifty_poll(Blackhole bh, PollCounters counters) {
        Integer result = queue.poll();
        bh.consume(result);
        if (result == null) {
            counters.pollMiss++;
        } else {
            counters.pollHit++;
        }
    }

    int nextInt() {
        return ThreadLocalRandom.current().nextInt(0, RANGE);
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(MixedThrptBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}
/*
╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.full_insert ─╮
│  Type                  Score  Error   Unit                     │
│  --------------------- ------ ------- ------                   │
│  KQueue                31.616 ± 2.375 ops/us                   │
│  PriorityBlockingQueue 14.472 ± 0.621 ops/us                   │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_50_50 ─╮
│  Type                  Role       Score  Error   Unit          │
│  --------------------- ---------- ------ ------- ------        │
│  KQueue                fifty_add  22.384 ± 2.047 ops/us        │
│  KQueue                fifty_poll 0.229  ± 0.032 ops/us        │
│  KQueue                pollHit    0.230  ± 0.033 ops/us        │
│  KQueue                pollMiss   0.001  ± 0.002 ops/us        │
│  KQueue                aggregate  22.614 ± 2.035 ops/us        │
│  PriorityBlockingQueue fifty_add  4.519  ± 0.899 ops/us        │
│  PriorityBlockingQueue fifty_poll 6.861  ± 2.437 ops/us        │
│  PriorityBlockingQueue pollHit    4.415  ± 0.999 ops/us        │
│  PriorityBlockingQueue pollMiss   2.447  ± 1.521 ops/us        │
│  PriorityBlockingQueue aggregate  11.380 ± 3.308 ops/us        │
╰────────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.MixedThrptBench.ratio_75_25 ─╮
│  Type                  Role             Score  Error   Unit    │
│  --------------------- ---------------- ------ ------- ------  │
│  KQueue                pollHit          0.105  ± 0.014 ops/us  │
│  KQueue                pollMiss         0.000  ± 0.000 ops/us  │
│  KQueue                seventy_five_add 26.379 ± 1.776 ops/us  │
│  KQueue                twenty_five_poll 0.105  ± 0.014 ops/us  │
│  KQueue                aggregate        26.484 ± 1.780 ops/us  │
│  PriorityBlockingQueue pollHit          2.506  ± 0.112 ops/us  │
│  PriorityBlockingQueue pollMiss         0.001  ± 0.003 ops/us  │
│  PriorityBlockingQueue seventy_five_add 3.015  ± 0.140 ops/us  │
│  PriorityBlockingQueue twenty_five_poll 2.507  ± 0.112 ops/us  │
│  PriorityBlockingQueue aggregate        5.522  ± 0.091 ops/us  │
╰────────────────────────────────────────────────────────────────╯


* */


/*
 *     /*
 *     * Profile notes:
 *     * Most of the time is spent doing work in "add" is in the segment.add() method which is nice
 *     * The slow path which involves cache coherence traffic (insert to del buffer -> publish id) is taken less than 0% of the time
 *     * Which indicates the queue is usually full, which lines up with the low thrpt of poll operations.
 *     *
 *     * Around 40% of time, (the slower path) is spent draining the insert buffer into the main heap. Which does make sense
 *     * as the insert buffer amortizes the cost of heapifying everytime by some constant. Though when it's full draining is quite expensive
 *     * which is a tradeoff. The capacity of the ins buffer is also a tradeoff, two little and there's no point in keeping it, too large and it becomes a bottleneck
 *     * Right now, I use the capacity of the delete buffer to determine the capacity of the insert buffer
 *     *
 *     * Simply adding to the insert buffer (without draining) takes about 4% of the add method which shows the insert buffer is correctly doing its job of amortizing cost
 *     *
 *     * For the poll side, most of the time is actually spent idling which does make sense given the combining strategy used.
 *     * Waiting and sometimes blocking to acquire a lock for the combiner to delete an item from the segment does increase the total time taken to actually delete
 *     * the item and then actually notify the waiters. Ideally this is a key flaw in this design, though it doesnt matter too much as it's optimized for
 *     * scaling insert thrpt and high insert thrpt under contention
 *     *
 *     * Right now, trying to lock a segment (while probing) takes about 40% of the time in KQueue#add, which means threads are more frequently landing on segments
 *     * which are locked. Right now I'm using a naive strategy to calculate the start index to start probing from which leads to more collisions ideally.
 *     * Next goal is to find a good hash to calculate a good start index and probably also, a better probing mechanism
 *     *
 *     * Hmm looks like my current approach is pretty alright, major issue being threads can walk in lockstep but yeah
 *     * */
