package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.FaaInt;
import io.github.kusoroadeolu.cbs.RPQ;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class SBQ<T> implements RPQ<T> {

    private final ConcurrentSkipListSet<Box<T>> nvs;

    public SBQ(Comparator<T> comparator) {

        var cmp = Box.comparator(comparator);
        Comparator<Box<T>> boxCmp = (a, b) -> {
            if (a == b) return 0;
            int c = cmp.compare(a.t, b.t);
            if (c != 0) return c;
            // same value, different instances -> break tie by identity, never 0
            return Long.compare(a.seq, b.seq);
        };

        this.nvs = new ConcurrentSkipListSet<>(boxCmp);
    }

    @Override
    public boolean add(T t) {
        return nvs.add(new Box<>(t));
    }

    @Override
    public T poll() {
        Box<T> e = null;
        return (e = nvs.pollFirst()) == null ? null : e.t;
    }

    @Override
    public T peek() {
        return nvs.getFirst().t;
    }

    @Override
    public int size() {
        return nvs.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public void clear() {

    }

    static class Box<T> {
        final T t;
        final long seq; // tie-breaker for identity, unique per Box
        private static final FaaInt SEQ = new FaaInt();

        Box(T t) {
            this.t = t;
            this.seq = SEQ.fetchAndAdd();
        }

        static <T>Comparator<? super T> comparator(Comparator<? super T> cmp) {
            if (cmp == null) return (a, b) -> ((Comparable<? super T>)a).compareTo(b);
            return cmp;
        }



    }
}
