package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.Box;
import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListSet;

class SegmentLPad {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

class SegmentFields<E> extends SegmentLPad {
    private final ConcurrentSkipListSet<Box<E>> set;
    private static final VarHandle STATE = VHUtils.fieldVarHandle(MethodHandles.lookup(), SegmentFields.class, "state", int.class);
    volatile int state;
    private static final int FREE = 0;


    public boolean tryLock() {
        return state == FREE && (int) STATE.getAndAdd(this, 1) == FREE;
    }

    public void unlock() {
        STATE.setRelease(this, FREE);
    }

    public SegmentFields(Comparator<? super E> comparator) {
        var cmp = Box.comparator(comparator);
        java.util.Comparator<Box<E>> boxCmp = (a, b) -> {
            if (a == b) return 0;
            int c = cmp.compare(a.t, b.t);
            if (c != 0) return c;
            // same value, different instances -> break tie by identity, never 0
            return Long.compare(a.seq, b.seq);
        };

        this.set = new ConcurrentSkipListSet<>(boxCmp);
    }

    public void add(E e) {
        set.add(new Box<>(e));
    }

    public E poll() {
        Box<E> e;
        return (e = set.pollFirst()) == null ? null : e.t;
    }

    public E peek() {
        try {
            Box<E> e = set.getFirst();
            return e == null ? null : e.t;
        }catch (NoSuchElementException _) {
            return null;
        }
    }


    void clear() {
        set.clear();
    }


    static <T>Comparator<? super T> comparator(Comparator<? super T> cmp) {
        if (cmp == null) return (a, b) -> ((Comparable<? super T>)a).compareTo(b);
        return cmp;
    }

}

public class Segment<E> extends SegmentFields<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b


    public Segment(Comparator<? super E> cmp) {
        super(cmp);
    }

}
