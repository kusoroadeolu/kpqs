package io.github.kusoroadeolu.cbs.bench.factory;

import io.github.kusoroadeolu.cbs.ChunkedPQ;
import io.github.kusoroadeolu.cbs.PQ;

public final class PQFactory {

    public static final String CBQ = "ChunkedPQ";

    public static <E> PQ<E> createPQ(String s) {
        if (CBQ.equals(s)) return new ChunkedPQ<>();
        else throw new IllegalArgumentException("??");
    }

}
