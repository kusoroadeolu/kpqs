package io.github.kusoroadeolu.cbs.bench.baseline;

import io.github.kusoroadeolu.cbs.ChunkedPQ;
import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
@BenchmarkMode({Mode.AverageTime})
@State(Scope.Thread)
public class SingleThreadedOffer {
    private static final int OPS = 1 << 15;
    private ChunkedPQ<Integer> queue;
    private volatile boolean dontUnroll = true;

    final static int RANGE = 1_000_000;

    @Param({PQFactory.CBQ})
    public String type;


    @Setup
    public void setup() {
        queue = new ChunkedPQ<>();
    }

    @TearDown(Level.Invocation)
    public void teardown() {
        queue.unsafeClear();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void add() {
        var lq = queue;
        for (int i = 0; i < OPS && dontUnroll; ++i) {
            blackhole(offer(lq, ThreadLocalRandom.current().nextInt(0, RANGE)));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean offer(PQ<Integer> queue, Integer i) {
        return queue.offer(i);
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(boolean o) {

    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(SingleThreadedOffer.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }

}
