package io.github.kusoroadeolu.cbs;

import io.github.kusoroadeolu.cbs.hopper.Hopper;
import io.github.kusoroadeolu.cbs.hopper.HopperItem;
import io.github.kusoroadeolu.cbs.hopper.IdleStrategy;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import io.github.kusoroadeolu.cbs.utils.PIPQConstants;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
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

    final Object lock;

    PollFields() {
        lock = new Object();
    }

    static class PollRequest extends HopperItem<PollRequest> {
        int id = -1;
        int size = -1;
        Object value;
    }
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

public class PIPQ<E> extends KLPad implements RPQ<E> {

    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int PROBE_DISTANCE = NCPU >>> 1; //max length to probe for a worker to acquire before retrying


    private final Segment<E>[] segments;
    private final int mask;
    private final LeaderList<E> list;
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


    public PIPQ(int concurrency, Comparator<? super E> comparator) {
        int segmentSize = MiscUtils.roundToPowerOfTwo(concurrency <= 0 ? NCPU : concurrency);
        mask = segmentSize - 1;
        list = new LeaderList<>(comparator);
        segments = new Segment[segmentSize];
        for (int id = 0; id < segmentSize; ++id)
            segments[id] = new Segment<>(id, list ,null);
    }

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
        var list = this.list;
        var segments = this.segments;
        int leaderListSize = -1;
        int id = -1;
        E value;
        //The simple lock approach is actually much faster and has a lower latency combined to the combining approach
        synchronized (lock) {
            var polled = list.poll();
            if (polled == null) return null;
            leaderListSize = segments[(id = polled.id)].decrementLeaderListSize();
            if (leaderListSize <= PIPQConstants.MIN_LEADER_LIST_ELEMS) {
                forceUpsert(segments[id]);
                return polled.value;
            }

            value = polled.value;
        }


        if (leaderListSize <= PIPQConstants.UPSERT_THRESHOLD) {
            tryUpsert(segments[id]);
        }

        return value;

//        var h =  hopper;
//        var list = this.list;
//        var segments = this.segments;
//        PollRequest request = new PollRequest();
//        boolean combine = h.add(request);
//        if (combine) {
//            var items = h.dump(request);
//            try {
//                while (items.hasNext()) {
//                    var item = items.next();
//                    var polled = list.poll();
//
//                    if (polled == null) {
//                        item.value = null;
//                        item.apply();
//                        continue;
//                    }
//
//                    int id = polled.id;
//                    var segment = segments[id];
//                    var leaderListSize = segment.decrementLeaderListSize();
//
//                    item.id = id;
//                    item.size = leaderListSize;
//                    item.value = polled.value;
//                    item.apply();
//
//                    if (leaderListSize <= PIPQConstants.MIN_LEADER_LIST_ELEMS) forceUpsert(segment);
//                }
//
//                return (E) request.value;
//            }finally {
//                h.unlock();
//            }
//        }
//
//        var strategy = this.strategy;
//        int spins = 0;
//        while (!request.isApplied()) {
//            spins = strategy.idle(spins);
//        }
//
//        E val = (E) request.value;
//        int size = request.size;
//        if (size != -1 && size <= PIPQConstants.UPSERT_THRESHOLD) tryUpsert(segments[request.id]);
//        return val;
    }

    void forceUpsert(Segment<E> segment) {
        segment.acquire();
        try {
            segment.forceUpsert();
        }finally {
            segment.release();
        }
    }

    void tryUpsert(Segment<E> segment) {
        if (segment.boundedTryAcquire()) {
            try {
                segment.helpUpsert();
            }finally {
                segment.release();
            }
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


    //only for benchmarks (per iteration)
    public void clear() {
        var segments = this.segments;
        for (int i = 0; i < (mask + 1); ++i) {
            segments[i].clear();
        }

        while (list.poll() != null);
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