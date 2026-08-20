package io.github.kusoroadeolu.cbs;

import java.util.Comparator;

public class Box<T> {
        public final T t;
        public final long seq; // tie-breaker for identity, unique per Box
        private static final FaaInt SEQ = new FaaInt();

        public Box(T t) {
            this.t = t;
            this.seq = SEQ.fetchAndAdd();
        }

        public static <T> Comparator<? super T> comparator(Comparator<? super T> cmp) {
            if (cmp == null) return (a, b) -> ((Comparable<? super T>)a).compareTo(b);
            return cmp;
        }
    }