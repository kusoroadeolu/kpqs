package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.SortedList;
import io.github.kusoroadeolu.cbs.SortedList.SortedBuffer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = {
        JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG
})
public class SortingBench {

    @Param({"32", "64", "128", "256"})
    public int size;

    Integer[] sample;
    Integer[] toSort;
    SortedBuffer<Integer> list;

    @Setup(Level.Iteration)
    public void prefill() {
        sample = new Integer[size];

        for (int i = 0; i < size; ++i) {
            sample[i] = ThreadLocalRandom.current().nextInt();
        }
    }


    @Setup(Level.Invocation)
    public void allocate() {
        toSort = Arrays.copyOf(sample, size);
        list = new SortedBuffer<>(size, Comparator.naturalOrder());

    }

    @Benchmark
    public void sortedList(Blackhole bh){
        bh.consume(copy(list));
    }


    @Benchmark
    public void jdkSort(Blackhole bh){
        bh.consume(sort(toSort));

    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int copy(SortedBuffer<Integer> list) {
        for (int i = 0; i < size; ++i) list.add(sample[i]);
        return 1;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    int sort(Integer[] toSort){
        Arrays.sort(toSort);
        return 1;
    }
}

/*
╭── io.github.kusoroadeolu.cbs.bench.SortingBench.jdkSort ───╮
│  Size Score  Error   P99    P99.9  P99.99  Max      Unit   │
│  ---- ------ ------- ------ ------ ------- -------- -----  │
│  32   1.020  ± 0.045 1.500  15.723 41.991  1929.216 us/op  │
│  64   2.056  ± 0.045 5.400  21.088 44.096  1914.880 us/op  │
│  128  5.640  ± 0.045 9.696  28.192 86.888  969.728  us/op  │
│  256  12.358 ± 0.064 24.096 45.376 337.098 710.656  us/op  │
╰────────────────────────────────────────────────────────────╯

╭ io.github.kusoroadeolu.cbs.bench.SortingBench.sortedList ─╮
│  Size Score  Error   P99    P99.9  P99.99  Max     Unit   │
│  ---- ------ ------- ------ ------ ------- ------- -----  │
│  32   0.927  ± 0.021 1.000  7.200  27.207  695.296 us/op  │
│  64   1.920  ± 0.023 2.100  16.000 31.815  959.488 us/op  │
│  128  4.189  ± 0.038 6.096  23.392 58.971  736.256 us/op  │
│  256  10.861 ± 0.071 15.488 34.145 576.216 814.080 us/op  │
╰───────────────────────────────────────────────────────────╯
* */
