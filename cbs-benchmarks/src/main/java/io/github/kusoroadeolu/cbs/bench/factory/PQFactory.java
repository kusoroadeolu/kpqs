package io.github.kusoroadeolu.cbs.bench.factory;

import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.PBQ;
import io.github.kusoroadeolu.cbs.SkipPQ;


import java.util.Comparator;

public final class PQFactory {

    public static final String PBQ = "PriorityBlockingQueue";
    public static final String SKIP_PQ = "SkipPQ";
    private static final int SEGMENT_COUNT = Runtime.getRuntime().availableProcessors();

    public static <E> PQ<E> createPQ(String s) {
        if (SKIP_PQ.equals(s)) return new SkipPQ<>((Comparator<? super E>) Comparator.naturalOrder());
        else throw new IllegalArgumentException("??");
    }
}
