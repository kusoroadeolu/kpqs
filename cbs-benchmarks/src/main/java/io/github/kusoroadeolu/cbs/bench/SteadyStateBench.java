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

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.xorShift;

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class SteadyStateBench {
    @Param({PQFactory.PIPQ})
    private String type;

    private PQ<Integer> queue;

    final static int STEADY_STATE_SIZE = 132_000;
    final static int RANGE = 1_000_000;


    @Setup(Level.Trial)
    public void setup() {
        queue = PQFactory.createRPQ(type, STEADY_STATE_SIZE);

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



    boolean doWork(PQ<Integer> PQ, PollCounters counters) {
        Integer i = PQ.poll();
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
╭────────────────────── io.github.kusoroadeolu.cbs.bench.SteadyStateBench.decKey ──────────────────────╮
│  Type Score Error   P00   P50   P90   P95   P99     P99.9   P99.99  P99.999 P99.9999 Max      Unit   │
│  ---- ----- ------- ----- ----- ----- ----- ------- ------- ------- ------- -------- -------- -----  │
│  PIPQ 2.944 ± 0.022 0.000 0.400 1.100 2.200 113.280 186.112 242.688 814.080 3510.064 5824.512 us/op  │
╰──────────────────────────────────────────────────────────────────────────────────────────────────────╯

* */
