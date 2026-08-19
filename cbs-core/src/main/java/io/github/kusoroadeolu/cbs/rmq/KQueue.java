package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

class KLPad {
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

public class KQueue<E>  extends KLPad implements RPQ<E> {

    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int MAX_PUBLICATIONS_PER_SEGMENT = 32; //max number of publications a segment can make in the queue
    private static final int PROBE_LENGTH = NCPU >>> 1; //max length to probe for a worker to acquire before retrying


    private final Segment<E>[] segments;
    private final MpscLeaderQueue queue;
    private final int mask;
    private final SpinLock combiningSpinLock;
    private final ArenaObject[] arena;


    static final Object WAITER = new Object();
    static final Object AWAIT = new Object();
    static final Object NONE = new Object();

    static final int BACKOFF_SPINS = 64;
    static final int SPINS_PER_SLOT = 128;
    static final int MAX_SPINS = 1024;



    public KQueue(int concurrency, Heap.Kind kind) {
        int segmentSize = MiscUtils.roundToPowerOfTwo(concurrency <= 0 ? NCPU : concurrency);
        mask = segmentSize - 1;
        segments = new Segment[segmentSize];
        queue = new MpscLeaderQueue(segmentSize * MAX_PUBLICATIONS_PER_SEGMENT);
        for (int i = 0; i < segmentSize; ++i) {
            segments[i] = new Segment<>(MAX_PUBLICATIONS_PER_SEGMENT, queue, i, null, kind); //null for now cause im testing on ints
        }

        combiningSpinLock = new SpinLock();
        arena = new ArenaObject[segmentSize];
        for (int i = 0; i < segmentSize; ++i) {
            arena[i] = new ArenaObject();
        }
    }

    @Override
    public boolean add(E e) {
        Objects.requireNonNull(e);
        int mask = this.mask;
        var segments = this.segments;
        Segment<E> segment;
        for (;;) {
            if ((segment = tryProbe(mask, segments)) != null) {
                try {
                    segment.add(e);
                    return true;
                }finally {
                    segment.release();
                }
            }

            Thread.onSpinWait();
        }
    }

    Segment<E> tryProbe(int mask , Segment<E>[] segments) {
        int startIndex = ThreadLocalRandom.current().nextInt();
        for (int i = 0; i < PROBE_LENGTH; ++i) {
            int offset =  MiscUtils.offset(startIndex + i, mask);
            var segment = segments[offset];
            if (segment.tryAcquire()) return segment;
        }

        return null;
    }

    public E poll() {
        var arena = this.arena;
        var mask = this.mask;
        var segments = this.segments;
        var csl = this.combiningSpinLock;
        var q = queue;

        for (;;) {
            if (csl.tryLock()) {
                try {
                    int id = q.poll();
                    E elem = doPoll(id, segments);
                    for (int i = 0; i <= mask; ++i) {
                        ArenaObject o = arena[i];
                        if (o.loValue() == WAITER && o.casValue(WAITER, AWAIT)) {
                            E value = doPoll(id, segments);
                            o.soValue(value == null ? NONE : value);
                        }
                    }

                    return elem;
                }finally {
                    csl.unlock();
                }
            }


            int start = ThreadLocalRandom.current().nextInt();
            inner: for (int step = 0, totalSpins = 0; (step < (mask + 1)) && (totalSpins < MAX_SPINS) && csl.isHeld(); step++) {
                int index = (step + start) & mask;
                var arenaObject = arena[index];
                var seen = arenaObject.loValue();
                if (seen == null && arenaObject.casValue(null, WAITER)) {
                    int spins = 0;

                    for (int backoffSpins = 0; ;) {
                        seen = arenaObject.loValue();
                        if (seen != WAITER) {
                            Object elem;
                            while ((elem = arenaObject.loValue()) == AWAIT) Thread.onSpinWait();
                            arenaObject.soValue(null);
                            return elem == NONE ? null : (E) elem;
                        } else if ((spins >= SPINS_PER_SLOT) && arenaObject.casValue(WAITER, null)) {
                            totalSpins += spins;
                            continue inner;
                        }

                        while (++backoffSpins <= BACKOFF_SPINS) Thread.onSpinWait(); //avoid repeated spins on index to prevent cache line thrashing

                        spins += backoffSpins;
                        backoffSpins = 0;
                    }
                }
            }

        }
    }

    E doPoll(int id,  Segment<E>[] segments) {
        if (id == -1) return null;
        var segment = segments[id];
        segment.acquire();
        try {
            return segment.poll();
        }finally {
            segment.release();
        }

    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; ++i) {
            sb.append("Worker %s: %s\n".formatted(i, segments[i].size()));
        }

        return sb.toString();
    }

    @Override
    public E peek() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }


    static class ArenaObjectLPad {
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

    }

    static class ArenaObjectField extends ArenaObjectLPad{
        Object value;
        static final VarHandle VALUE = VHUtils.fieldVarHandle(MethodHandles.lookup(), ArenaObjectField.class, "value", Object.class);

        public boolean casValue(Object seen, Object to) {
            return VALUE.compareAndSet(this, seen, to);
        }

        public void soValue(Object value) {
            VALUE.setRelease(this, value);
        }

        public Object loValue() {
            return VALUE.getAcquire(this);
        }
    }

    static class ArenaObject extends ArenaObjectField{
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
    }

}
