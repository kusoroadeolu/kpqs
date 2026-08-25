package io.github.kusoroadeolu.cbs.bench.baseline;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.PhaseBench;
import io.github.kusoroadeolu.cbs.bench.factory.RPQFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
@BenchmarkMode({Mode.AverageTime})
@State(Scope.Thread)
public class SingleThreadedPoll {
    private static final int OPS = 1 << 15;
    private RPQ<Integer> queue;
    private volatile boolean dontUnroll = true;


    @Param({RPQFactory.KQ})
    public String type;


    @Setup
    public void setup() {
        queue = RPQFactory.createRPQ(type, OPS * 2);
    }

    @Setup(Level.Invocation)
    public void prefill() {
        for (int i = 0; i < OPS; ++i) {
            queue.offer(ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE));
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void add() {
        var lq = queue;
        for (int i = 0; i < OPS && dontUnroll; ++i) {
            blackhole(poll(lq));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int poll(RPQ<Integer> queue) {
        return queue.poll();
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(int i) {

    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(SingleThreadedPoll.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}



