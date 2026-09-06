package io.github.kusoroadeolu.cbs.bench.baseline;

import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.JvmArgs;
import io.github.kusoroadeolu.cbs.bench.factory.PQFactory;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
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
    private PQ<Integer> queue;
    private volatile boolean dontUnroll = true;

    final static int RANGE = 1_000_000;

    @Param({PQFactory.MOUNDS, PQFactory.PBQ})
    public String type;


    @Setup
    public void setup() {
        queue = PQFactory.createPQ(type, MiscUtils.defaultCmp());
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
    public boolean offer(PQ<Integer> queue, Integer i) {
        return queue.offer(i);
    }


    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(boolean o) {

    }

}

/*

╭ io.github.kusoroadeolu.cbs.bench.baseline.SingleThreadedOffer.add ─╮
│  Type                  Score   Error   Unit                        │
│  --------------------- ------- ------- -----                       │
│  Mounds                112.580 ± 7.118 ns/op                       │
╰────────────────────────────────────────────────────────────────────╯

* */

