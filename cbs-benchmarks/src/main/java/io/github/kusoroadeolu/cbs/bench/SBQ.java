package io.github.kusoroadeolu.cbs.bench;

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

public class SBQ<T extends Comparable<T>> implements RPQ<T> {

    private final ConcurrentSkipListSet<Box<T>> nvs;
    private final Comparator<Box<T>> boxCmp;

    private static final int SPRAY_DISTANCE = 20;

    public SBQ(Comparator<T> comparator) {

        var cmp = Box.comparator(comparator);
        this.boxCmp = (a, b) -> {
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
//        var nvs = this.nvs;
//        for (;;) {
//            int i = 0;
//            int bound = ThreadLocalRandom.current().nextInt(SPRAY_DISTANCE) + 1;
//            Box<T> box = null;
//            for (Iterator<Box<T>> it = nvs.iterator(); it.hasNext() && i++ <= bound; ) {
//                box = it.next();
//            }
//
//            if (box == null) return null;
//            if (nvs.remove(box)) return box.t;
//        }
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

    static class Box<T extends Comparable<T>> {
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


        static class FaaInt {
            private static final VarHandle I;
            private volatile int i;

            public FaaInt(int initial) {
                i = initial;
            }

            public FaaInt() {
                this(0);
            }

            public int fetchAndAdd(int i) {
                return (int) I.getAndAdd(this, i);
            }

            public int fetchAndAdd() {
                return fetchAndAdd(1);
            }

            public void setRelease(int i) {
                I.setRelease(this, i);
            }

            public void setVolatile(int i) {
                I.setVolatile(this, i);
            }

            public int getAcquire() {
                return (int) I.getAcquire(this);
            }

            public int getVolatile() {
                return (int) I.getVolatile(this);
            }

            static {
                try {
                    I = MethodHandles.lookup().findVarHandle(FaaInt.class, "i", int.class);
                }catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
