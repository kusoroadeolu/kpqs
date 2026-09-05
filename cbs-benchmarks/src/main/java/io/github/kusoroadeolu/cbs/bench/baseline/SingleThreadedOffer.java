package io.github.kusoroadeolu.cbs.bench.baseline;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.factory.RPQFactory;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
@BenchmarkMode({Mode.AverageTime})
@State(Scope.Thread)
public class SingleThreadedOffer {
    private static final int OPS = 1 << 15;
    private RPQ<Integer> queue;
    private volatile boolean dontUnroll = true;

    final static int RANGE = 1_000_000;

    @Param({RPQFactory.MQ, RPQFactory.PBQ})
    public String type;


    @Setup
    public void setup() {
        queue = RPQFactory.createRPQ(type, OPS * 2);
    }

    @TearDown(Level.Invocation)
    public void teardown() {
        queue.clear();
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
    public boolean offer(RPQ<Integer> queue, Integer i) {
        return queue.offer(i);
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(boolean o) {

    }

}

/*
* Benchmark                  (skew)                 (type)  Mode  Cnt    Score   Error  Units
SingleThreadedOffer.add      1000                 MultiQueue  avgt   15  205.715 ± 2.775  ns/op
SingleThreadedOffer.add      1000  PriorityBlockingQueue  avgt   15   33.616 ± 1.175  ns/op
SingleThreadedOffer.add    100000                 MultiQueue  avgt   15  205.171 ± 3.701  ns/op
SingleThreadedOffer.add    100000  PriorityBlockingQueue  avgt   15   33.559 ± 0.888  ns/op
SingleThreadedOffer.add  10000000                 MultiQueue  avgt   15  204.423 ± 2.149  ns/op
SingleThreadedOffer.add  10000000  PriorityBlockingQueue  avgt   15   33.696 ± 0.671  ns/op
* */

