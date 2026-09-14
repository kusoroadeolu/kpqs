package io.github.kusoroadeolu.cbs.bench.factory;

import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.PBQ;
import io.github.kusoroadeolu.cbs.PIPQ;

import java.util.Comparator;

public final class PQFactory {

    public static final String PBQ = "PriorityBlockingQueue";
    public static final String PIPQ = "PIPQ";
    private static final int SEGMENT_COUNT = Runtime.getRuntime().availableProcessors();

    public static <E> PQ<E> createRPQ(String s, int segmentCount, int initialCapacity) {
        if (PBQ.equals(s)) return new PBQ<>(initialCapacity);
        else if (PIPQ.equals(s)) return new PIPQ<>(segmentCount, (Comparator<? super E>) Comparator.naturalOrder());
        else throw new IllegalArgumentException("??");
    }

    public static <E> PQ<E> createRPQ(String s, int initialCapacity) {
        return createRPQ(s, SEGMENT_COUNT, initialCapacity);
    }

}
