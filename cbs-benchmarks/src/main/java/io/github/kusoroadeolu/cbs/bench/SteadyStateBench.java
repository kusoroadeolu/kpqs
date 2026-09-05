package io.github.kusoroadeolu.cbs.bench;

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

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.xorShift;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class SteadyStateBench {
    @Param({RPQFactory.MQ})
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
╭───────────────────────── io.github.kusoroadeolu.cbs.bench.SteadyStateBench.decKey ─────────────────────────╮
│  Type       Score Error   P00   P50   P90   P95   P99   P99.9  P99.99  P99.999  P99.9999  Max       Unit   │
│  ---------- ----- ------- ----- ----- ----- ----- ----- ------ ------- -------- --------- --------- -----  │
│  MultiQueue 0.896 ± 0.027 0.100 0.700 1.200 1.400 1.800 10.096 111.104 1382.401 12114.341 17661.952 us/op  │
╰────────────────────────────────────────────────────────────────────────────────────────────────────────────╯
* */

