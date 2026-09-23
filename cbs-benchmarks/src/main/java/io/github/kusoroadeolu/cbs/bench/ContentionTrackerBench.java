package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.ContentionCounter;
import io.github.kusoroadeolu.cbs.SkipPQ;
import org.openjdk.jmh.annotations.*;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgs = {JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG})
public class ContentionTrackerBench {
    private SkipPQ<Integer> queue;

    final static int RANGE = 10;


    @Setup(Level.Trial)
    public void setup() {
        queue = new SkipPQ<>(Comparator.naturalOrder());
    }


    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    public static class LocalContentionCounter {

        final ContentionCounter counter = new ContentionCounter();

        public long offersNearHead()      { return counter.offersNearHead; }
        public long pollCas()  { return counter.pollCases; }
        public long failedOffers()      { return counter.failedOffers; }
        public long failedPollCas()  { return counter.failedPollCas; }

        @Setup(Level.Iteration)
        public void reset() {
            counter.reset();
        }
    }


    @Group("ratio_50_50")
    @GroupThreads(4)
    @Benchmark
    public void fifty_add(Blackhole bh, LocalContentionCounter counter) {
        bh.consume(queue.offer(nextInt(), counter.counter));
    }

    @Group("ratio_50_50")
    @GroupThreads(4)
    @Benchmark
    public void fifty_poll(Blackhole bh, LocalContentionCounter counter) {
        Integer result = queue.poll(counter.counter);
        bh.consume(result);
    }

    int nextInt() {
        return ThreadLocalRandom.current().nextInt(0, RANGE);
    }


    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(ContentionTrackerBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();

        }
    }
}
