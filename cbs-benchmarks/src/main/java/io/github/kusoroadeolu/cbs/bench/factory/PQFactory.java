package io.github.kusoroadeolu.cbs.bench.factory;

import io.github.kusoroadeolu.cbs.ConcurrentMound;
import io.github.kusoroadeolu.cbs.PQ;
import io.github.kusoroadeolu.cbs.bench.PBQ;

import java.util.Comparator;

public final class PQFactory {

    public static final String PBQ = "PriorityBlockingQueue";
    public static final String MOUNDS = "Mounds";

    public static <E> PQ<E> createPQ(String s , Comparator<E> cmp) {
        if (PBQ.equals(s)) return new PBQ<>(7, cmp);
        else if (MOUNDS.equals(s)) return new ConcurrentMound<>(cmp);
        else throw new IllegalArgumentException("??");
    }

    public static <E> PQ<E> createPQ(String s, Comparator<E> cmp , int initialCapacity) {
        if (PBQ.equals(s)) return new PBQ<>(initialCapacity, cmp);
        else if (MOUNDS.equals(s)) return new ConcurrentMound<>(cmp);
        else throw new IllegalArgumentException("??");
    }

}
