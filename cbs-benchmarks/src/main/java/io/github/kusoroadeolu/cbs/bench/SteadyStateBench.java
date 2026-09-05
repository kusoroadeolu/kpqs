package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.factory.RPQFactory;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.xorShift;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class SteadyStateBench {
    @Param({RPQFactory.KQ})
    private String type;

    private RPQ<Integer> queue;

    final static int STEADY_STATE_SIZE = 132_000;
    final static int RANGE = 1_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = RPQFactory.createRPQ(type, STEADY_STATE_SIZE);

        for (int i = 0; i < STEADY_STATE_SIZE; i++) {
            queue.offer(ThreadLocalRandom.current().nextInt(0, RANGE));
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


    @Threads(8)
    @Benchmark
    public void decKey(Blackhole bh, PollCounters counters) {
        bh.consume(doWork(queue, counters));
    }



    boolean doWork(RPQ<Integer> rpq, PollCounters counters) {
        Integer i = rpq.poll();
        if (i == null) {
            counters.pollMiss++;
            return false;
        } else {
            counters.pollHit++;
            return queue.offer(xorShift(i));
        }
    }

    /*
    * Profiling notes:
    * As expected, a lot of the time was spent by threads idling in poll while waiting for the combiner to serve their results
    * Which as I said before is a key bottleneck of the design even though the design is strictly insert focused
    *
    * The benchmarks shows my flat combining approach spikes to 17us/op @ around p50, which tbf is quite high, however, tail latency is better than
    * PBQ though by a small fraction
    *
    * I decided to run this benchmark at 1, 2 & 4 threads as well, and o boy does this approach not scale well lol.
    * Latency jumped from 0.6-7 us/op at 1 thread to 3 us/op at 4 threads to 17us/op at 8 threads which is a
    * 6x jump for no benefit over using a lock which maintains that 3us/op latency at 4 threads up to 8 threads for PBQ
    *
    * Ideally this makes sense when you think about it, the core claim of flat combining is to amortize work through batching.
    * While the combiner does help do work, it actually doesn't batch or amortize the "batched" work for that case.
    * For example if a combiner has 3 requests to do work in it's iterator, it still processes those 3 requests without amortizing the cost
    * that'd naturally come with doing those requests sequentially. It 1. polls the queue 3 times 2. waits on a lock 3 times 3. Does the needed work
    * to maintain the min value invariant and needs to notify waiting threads n times. Seems quite similar to just serializing this under a lock
    * just worse since a thread's progress is determined by another thread lol.
    *
    *
    * So yeah, using a lock here might be the better option
    * */


    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(SteadyStateBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/*
╭──────────────────────────────── io.github.kusoroadeolu.cbs.bench.SteadyStateBench.decKey ─────────────────────────────────╮
│  Type                  Score Error   P00   P50   P90   P95   P99     P99.9   P99.99   P99.999  P99.9999  Max       Unit   │
│  --------------------- ----- ------- ----- ----- ----- ----- ------- ------- -------- -------- --------- --------- -----  │
│  KQueue                6.709 ± 0.080 0.000 0.600 3.500 4.400 177.152 426.496 2857.569 6011.777 9863.168  12107.776 us/op  │
│  PriorityBlockingQueue 3.449 ± 0.030 0.100 0.200 0.300 1.000 96.384  166.656 213.760  866.304  14020.099 14483.456 us/op  │
╰───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────╯

── Generated with JMHPretty ──
* */
