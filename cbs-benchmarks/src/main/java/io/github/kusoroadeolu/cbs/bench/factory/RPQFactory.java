package io.github.kusoroadeolu.cbs.bench.factory;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.PBQ;
import io.github.kusoroadeolu.cbs.PIPQ;

import java.util.Comparator;

public final class RPQFactory {

    public static final String PBQ = "PriorityBlockingQueue";
    public static final String KQ = "PIPQ";
    private static final int SEGMENT_COUNT = Runtime.getRuntime().availableProcessors();

    public static <E>RPQ<E> createRPQ(String s, int segmentCount, int initialCapacity) {
        if (PBQ.equals(s)) return new PBQ<>(initialCapacity);
        else if (KQ.equals(s)) return new PIPQ<>(segmentCount, (Comparator<? super E>) Comparator.naturalOrder());
        else throw new IllegalArgumentException("??");
    }

    public static <E>RPQ<E> createRPQ(String s, int initialCapacity) {
        return createRPQ(s, SEGMENT_COUNT, initialCapacity);
    }

}
