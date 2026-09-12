package io.github.kusoroadeolu.cbs.bench.factory;

import io.github.kusoroadeolu.cbs.ChunkedPQ;
import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.PBQ;

import java.util.Comparator;

public final class PQFactory {

    public static final String PBQ = "PriorityBlockingQueue";
    public static final String CBQ = "ChunkedPQ";

    public static <E> PQ<E> createPQ(String s) {
        if (PBQ.equals(s)) return new PBQ<>(7);
        else if (CBQ.equals(s)) return new ChunkedPQ<>();
        else throw new IllegalArgumentException("??");
    }

    public static <E> PQ<E> createPQ(String s, Comparator<E> cmp , int initialCapacity) {
        if (PBQ.equals(s)) return new PBQ<>(initialCapacity, cmp);
        else if (CBQ.equals(s)) return new ChunkedPQ<>();
        else throw new IllegalArgumentException("??");
    }

}
