package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.offset;


class PollFieldPad {
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
}

class PollFields extends PollFieldPad {
//    final Hopper<PollRequest> hopper;
//    final IdleStrategy strategy;
    final Object lock;

    PollFields() {
        lock = new Object();
    }
//
//    static class PollRequest extends HopperItem<PollRequest> {
//        final Thread parked;
//        Object o;
//
//        public PollRequest(Thread parked) {
//            this.parked = parked;
//        }
//
//        void apply(Object o) {
//            this.o = o;
//            super.apply();
//        }
//    }

}

class KLPad extends PollFields{
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

    KLPad() {
        super();
    }
}

public class KQueue<E> extends KLPad implements RPQ<E> {

    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int MAX_PUBLICATIONS_PER_SEGMENT = 128; //max number of publications a segment can make in the queue (size of the sorted buffer which is the size of a cache line)
    private static final int PROBE_DISTANCE = NCPU >>> 1; //max length to probe for a worker to acquire before retrying


    private final Segment<E>[] segments;
    private final MpscLeaderQueue queue;
    private final int mask;
    private final ThreadLocal<ProbeState> state = ThreadLocal.withInitial(ProbeState::new);

    static class ProbeState {
        final ThreadLocalRandom tlr = ThreadLocalRandom.current();
        int rand = tlr.nextInt(); //uses the murmur hash underneath

        int rand() {
            return rand;
        }

        void remember(int rand) {
            this.rand = rand;
        }

        void newRand() {
            rand = tlr.nextInt();
        }
    }

    public KQueue(int concurrency) {
        this(concurrency, 7);
    }


    public KQueue(int concurrency, int initialHeapSize) {
        this(concurrency, initialHeapSize, MAX_PUBLICATIONS_PER_SEGMENT);
    }


    public KQueue(int concurrency, int initialHeapSize, int bufferSize) {
        int segmentSize = MiscUtils.roundToPowerOfTwo(concurrency <= 0 ? NCPU : concurrency);
        mask = segmentSize - 1;
        segments = new Segment[segmentSize];
        queue = new MpscLeaderQueue(segmentSize * MAX_PUBLICATIONS_PER_SEGMENT);
        for (int id = 0; id < segmentSize; ++id)
            segments[id] = new Segment<>(bufferSize <= 0 ? MAX_PUBLICATIONS_PER_SEGMENT : MiscUtils.roundToPowerOfTwo(bufferSize), queue, id, initialHeapSize ,null);
    }

//    public void logSegmentSizes() {
//        int min = Integer.MAX_VALUE, max = 0;
//        long sum = 0;
//        for (var s : segments) {
//            int sz = s.size();
//            min = Math.min(min, sz);
//            max = Math.max(max, sz);
//            sum += sz;
//        }
//        double avg = sum / (double) segments.length;
//        System.out.printf("min=%d max=%d avg=%.1f (skew=%.1fx)%n", min, max, avg, max / Math.max(1.0, avg));
//    }

    @Override
    public boolean offer(E e) {
        Objects.requireNonNull(e);
        int mask = this.mask;
        var segments = this.segments;
        var state = this.state.get();
        Segment<E> segment;
        for (;;) {
            if ((segment = tryProbe(mask, segments, state)) != null) {
                try {
                    segment.add(e);
                    return true;
                }finally {
                    segment.release();
                }
            }
        }
    }

    Segment<E> tryProbe(int mask , Segment<E>[] segments, ProbeState state) {
        int start = state.rand();

        for (int steps = 0; steps < PROBE_DISTANCE; ++steps) {
            int index = start + steps;
            int offset =  offset(index, mask);
            var segment = segments[offset];
            if (segment.tryAcquire()) {
                state.remember(index);
                return segment; //retry on fail, don't want to wait on a locked segment
            }
        }

        state.newRand();
        return null;
    }

    public E poll() {
//        var hopper = this.hopper;
//        var strategy = this.strategy;
        var segments = this.segments;
        var q = queue;
//        var req = new PollRequest(Thread.currentThread());

        synchronized (lock) {
            var id = q.poll();
            return doPoll(id, segments);
        }
//
//        boolean combine = hopper.add(req);
//
//        if (combine) {
//            var itr = hopper.dump(req);
//            try {
//                while (itr.hasNext()) {
//                    var currReq = itr.next();
//                    var id = q.poll();
//                    E polled = null;
//                    if (id >= 0) {
//                        polled = doPoll(id, segments);
//                    }
//
//                    currReq.apply(polled);
//                    LockSupport.unpark(currReq.parked);
//                }
//
//                return (E) req.o;
//            }finally {
//                hopper.unlock();
//            }
//        }
//
//
//        for (int count = 0; !req.isApplied();) {
//            count = strategy.idle(count);
//        }
//
//        return (E) req.o;
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
            sb.append("Worker %s: %s\n".formatted(i, segments[i]));
        }

        return sb.toString();
    }

    public void clear() {
        queue.clear();
        for (int i = 0; i <= mask; ++i) {
            segments[i].clear();
        }
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




}
