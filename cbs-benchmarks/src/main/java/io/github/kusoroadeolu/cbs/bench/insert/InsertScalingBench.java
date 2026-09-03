package io.github.kusoroadeolu.cbs.bench.insert;

import io.github.kusoroadeolu.cbs.ConcurrentMound;
import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
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
    private PQ<Integer> queue;

    @Param({PQFactory.MOUNDS})
    private String type;

    final static int RANGE = 10_000_000;


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


    @Threads(8)
    @Benchmark
    public void eight_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);
    }

    @Threads(6)
    @Benchmark
    public void six_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);

    }

    @Threads(4)
    @Benchmark
    public void four_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);
    }

    @Threads(2)
    @Benchmark
    public void two_full_insert(Blackhole bh) {
        boolean offer = queue.offer(nextInt());
        bh.consume(offer);
    }

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
