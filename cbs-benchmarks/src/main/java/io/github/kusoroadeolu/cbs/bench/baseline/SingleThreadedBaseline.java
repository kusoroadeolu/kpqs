package io.github.kusoroadeolu.cbs.bench.baseline;

import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
@BenchmarkMode({Mode.AverageTime})
@State(Scope.Thread)
public class SingleThreadedBaseline {

    private static final int OPS = 1 << 15;
    private Queue<Integer> queue = new PriorityQueue<>();
    private volatile boolean dontUnroll = true;
    private static final boolean OFFER_RESULT = Boolean.TRUE;
    private static final int POLL_RESULT = 1;
    private static final Integer OFFER_TOKEN = 1;

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void add() { // ~
        var lq = queue;
        for (int i = 0; i < OPS && dontUnroll; ++i) {
            blackhole(offer(lq, OFFER_TOKEN)); //~3ns
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean offer(Queue<Integer> queue, Integer i) {
        return OFFER_RESULT;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void poll() {
        for (int i = 0; i < OPS && dontUnroll; ++i) {
            blackhole(POLL_RESULT); //~1.8ns
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void intGen() {
        for (int i = 0; i < OPS && dontUnroll; ++i) {
            blackhole(ThreadLocalRandom.current().nextInt()); //~1.8ns
        }
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int poll(Queue<Integer> queue) {
        return POLL_RESULT;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(boolean o) {

    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(int i) {

    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(SingleThreadedBaseline.class.getSimpleName())

                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}

/*
 * Benchmark                      Mode  Cnt  Score   Error  Units
 * SingleThreadedBaseline.add     avgt    5  3.069 ± 0.213  ns/op
 * SingleThreadedBaseline.intGen  avgt    5  3.138 ± 0.459  ns/op
 * SingleThreadedBaseline.poll    avgt    5  1.807 ± 0.396  ns/op
 * */