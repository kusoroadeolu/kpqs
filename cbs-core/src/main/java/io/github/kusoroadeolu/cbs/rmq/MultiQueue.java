package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.comparator;
import static io.github.kusoroadeolu.cbs.utils.MiscUtils.offset;


public class MultiQueue<E> implements RPQ<E> {
    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int PROBE_DISTANCE = NCPU >>> 1; //max length to probe for a worker to acquire before retrying
    private static final int POLL_TRIES = 16;

    private final Comparator<? super E> comparator;
    private final Segment<E>[] segments;
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

    public MultiQueue(int concurrency) {
        this(concurrency, 7);
    }


    public MultiQueue(int concurrency, int initialHeapSize) {
        int segmentSize = MiscUtils.roundToPowerOfTwo(concurrency <= 0 ? NCPU : concurrency);
        mask = segmentSize - 1;
        this.comparator = comparator(null);
        segments = new Segment[segmentSize];
        for (int id = 0; id < segmentSize; ++id)
            segments[id] = new Segment<>(id, initialHeapSize, comparator);
    }


    public MultiQueue(int concurrency, int initialHeapSize, int bufferSize) {
        int segmentSize = MiscUtils.roundToPowerOfTwo(concurrency <= 0 ? NCPU : concurrency);
        mask = segmentSize - 1;
        this.comparator = comparator(null);
        segments = new Segment[segmentSize];
        for (int id = 0; id < segmentSize; ++id)
            segments[id] = new Segment<>(MiscUtils.roundToPowerOfTwo(bufferSize), id, initialHeapSize ,comparator);
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
        var segments = this.segments;
        var cmp = this.comparator;
        var mask = this.mask;

        int tries = 0;
        for (;;) {
            int first = ThreadLocalRandom.current().nextInt() & mask;
            int second = ThreadLocalRandom.current().nextInt() & mask;
            var fSegment = segments[first];
            var sSegment = segments[second];
            E fMin = fSegment.min();
            E sMin = sSegment.min();


            if (fMin == null && sMin == null) {
                if (++tries >= POLL_TRIES) return null;
                continue;
            }

            Segment<E> toLock;

            if (compare(fMin, sMin, cmp) <= 0) toLock = fSegment;
            else toLock = sSegment;

            if (toLock.tryAcquire()) {
                try {
                    E poll = toLock.poll();
                    if (poll != null) return poll;
                }finally {
                    toLock.release();
                }
            }
        }
    }

    static <E>int compare(E e, E other, Comparator<? super E> cmp) {
        if (e == null) return 1;
        if (other == null) return -1;
        return cmp.compare(e, other);
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
