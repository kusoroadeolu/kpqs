package io.github.kusoroadeolu.cbs;

import io.github.kusoroadeolu.cbs.hopper.Hopper;
import io.github.kusoroadeolu.cbs.hopper.HopperItem;
import io.github.kusoroadeolu.cbs.hopper.Backoff;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import io.github.kusoroadeolu.cbs.utils.PIPQConstants;

import java.util.Comparator;
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

    final Hopper<PollRequest> hopper;
    final Backoff backoff;

    PollFields() {
       hopper = new Hopper<>();
       backoff = new Backoff();
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

/*
* This is my shot at building the structure from the paper PIPQ
* The major issue with my version is that the structure leader list delete min operation is will fail if a concurrent
* insert happens at the left sentinel right before a delete min occurs. This prevents the issue where the largest node in the leader list for a segment
* is deleted when there are smaller elements for that node in the leader list. This however can happen with my 2 phase deletion mechanism
*
* The mark bit mechanism they used doesnt cleanly translate to Java.
* I think you can replicate it in java using AtomicMarkableReference, but honestly its API is genuinely bad and I'd honestly rather not
*
* In my case we maintain a local linked list which we periodically clean and use to determine the actual shape of the leader list
* Though honestly this still has its issues and im not even sure if its fully correct.
* Besides that this is a pretty promising structure but unfortunately can't port it into java without shoehorning some things
*
*
*
* Later:
* Ok so I ditched the local linked list approach and  I decided to come up with a new 3 phase deletion mechanism that solves the issue of an insert sneaking past next
* This fully solves the issue of the largest value in the list for a segment getting deleted before smaller values
* There is doesnt solve one issue where the there's only one value for a segment in the leader list (which would ideally be the tail),
* a deleter marks it and then removes it, then a bunch of insertions flood in and then we're one unlucky insertion only notices the stale tail
* when trying to pull it down from the leader list. To solve this we force insertions/upserts to scan the leader list in the case we notice
* the tail is marked due to this situation
*
* I think the local linked list approach does still make sense though as a probable optimization to find a good starting point for traversing the leader
* list though that optimization isn't warranted yet
* */
public class PIPQ<E> extends KLPad implements PQ<E> {

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

    public PIPQ(int concurrency, Comparator<? super E> comparator, int initialCapacity) {
        int segmentSize = MiscUtils.roundToPowerOfTwo(concurrency <= 0 ? NCPU : concurrency);
        mask = segmentSize - 1;
        list = new LeaderList<>(comparator);
        segments = new Segment[segmentSize];
        for (int id = 0; id < segmentSize; ++id)
            segments[id] = new Segment<>(id, list ,null, initialCapacity);
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
        var h =  hopper;
        var list = this.list;
        var segments = this.segments;
        PollRequest ours = new PollRequest();
        boolean combine = h.add(ours);
        if (combine) {
            var requests = h.dump(ours);
            try {
                while (requests.hasNext()) {
                    var request = requests.next();
                    var polled = list.poll();

                    if (polled == null) {
                        request.value = null;
                        request.apply();
                        continue;
                    }

                    int id = polled.id;
                    var segment = segments[id];
                    var size = segment.decrementLeaderListSize();

                    request.id = id;
                    request.size = size;
                    request.value = polled.value;
                    request.apply();

                    if (size <= PIPQConstants.FORCE_UPSERT_THRESHOLD) forceUpsert(segment);
                }

                return (E) ours.value;
            }finally {
                h.unlock();
            }
        }

        var backoff = this.backoff;
        int step = 0;
        while (!ours.isApplied()) step = backoff.snooze(step);

        E val = (E) ours.value;
        int size = ours.size;
        if (ours.id != -1 && (size > PIPQConstants.FORCE_UPSERT_THRESHOLD && size <= PIPQConstants.HELP_UPSERT_THRESHOLD)) tryUpsert(segments[ours.id]);
        return val;
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
        sb.append("List: ").append(list).append("\n");
        for (int i = 0; i < segments.length; ++i) {
            sb.append("Worker %s: %s\n".formatted(i, segments[i]));
        }

        return sb.toString();
    }


    //only for benchmarks (per iteration)
    public void unsafeClear() {
        var segments = this.segments;
        for (int i = 0; i < (mask + 1); ++i) {
            segments[i].clear();
        }

        while (list.poll() != null);
    }
}