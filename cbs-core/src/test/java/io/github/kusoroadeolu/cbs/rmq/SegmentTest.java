package io.github.kusoroadeolu.cbs.rmq;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SegmentTest {
    static final MpscLeaderQueue queue = new MpscLeaderQueue(100);

    @Test
    void onAdds_assertReturnsMin() {
        Segment<Integer> s = newSegment(4);
        for (int i = 4; i > 0; --i) {
            s.add(i);
        }

        var i = s.poll();
        Assertions.assertEquals(1, i);
    }

    private Segment<Integer> newSegment(int deleteBufferCapacity) {
        return new Segment<>(deleteBufferCapacity, queue, 0, Integer::compareTo);
    }

    @Test
    void addThenPollSingleElement() {
        Segment<Integer> seg = newSegment(8);
        assertTrue(seg.add(42));
        assertEquals(1, seg.size());
        assertEquals(42, seg.poll());
        assertEquals(0, seg.size());
        assertNull(seg.poll());
    }

    @Test
    void pollReturnsInSortedOrder_smallBatch_staysInDeleteBuffer() {
        Segment<Integer> seg = newSegment(16);
        List<Integer> input = List.of(5, 3, 9, 1, 7);
        input.forEach(seg::add);
        System.out.println(seg);

        List<Integer> out = new ArrayList<>();
        Integer v;
        while ((v = seg.poll()) != null) {
            System.out.println(seg);
            System.out.println();
            out.add(v);
        }

        List<Integer> expected = new ArrayList<>(input);

        Collections.sort(expected);
        assertEquals(expected, out);
    }

    @Test
    void noLostWrites_overflowsIntoInsertBufferAndHeap() {
        // small delete buffer capacity so we're forced past it into insertBuffer/heap
        Segment<Integer> seg = newSegment(8);
        int n = 5000;
        Random r = new Random(1234);
        List<Integer> input = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int val = r.nextInt(1_000_000);
            input.add(val);
            assertTrue(seg.add(val));
        }

        assertEquals(n, seg.size());

        List<Integer> out = new ArrayList<>(n);
        Integer v;
        while ((v = seg.poll()) != null) out.add(v);

        assertEquals(0, seg.size());
        assertEquals(n, out.size(), "lost or duplicated writes");

        List<Integer> expected = new ArrayList<>(input);
        Collections.sort(expected);
        assertEquals(expected, out, "poll order not fully sorted");
    }

    @Test
    void interleavedAddPoll_maintainsSortedInvariant() {
        Segment<Integer> seg = newSegment(8);
        Random r = new Random(99);
        List<Integer> reference = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) {
            if (reference.isEmpty() || r.nextDouble() < 0.6) {
                int val = r.nextInt(1_000_000);
                seg.add(val);
                reference.add(val);
                Collections.sort(reference);
            } else {
                Integer expected = reference.removeFirst();
                Integer actual = seg.poll();
                assertEquals(expected, actual);
            }
        }

        // drain remainder
        Integer v;
        int idx = 0;
        while ((v = seg.poll()) != null) {
            assertEquals(reference.get(idx++), v);
        }
        assertEquals(reference.size(), idx);
    }

    @Test
    void clearResetsEverything() {
        Segment<Integer> seg = newSegment(8);
        for (int i = 0; i < 100; i++) seg.add(i);
        seg.clear();
        assertEquals(0, seg.heapSize);
        assertEquals(0, seg.insertBuffer.size());
        assertEquals(0, seg.deleteBuffer.size());
    }

}