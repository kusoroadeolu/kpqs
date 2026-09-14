package io.github.kusoroadeolu.cbs;

import io.github.kusoroadeolu.cbs.utils.PIPQConstants;
import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Comparator;

import static io.github.kusoroadeolu.cbs.Node.MARKING;
import static io.github.kusoroadeolu.cbs.utils.MiscUtils.*;
import static io.github.kusoroadeolu.cbs.utils.PIPQConstants.DEFAULT_INITIAL_HEAP_SIZE;

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
    final SpinLock lock;
    final Comparator<? super E> comparator;
    final int id;
    final LeaderList<E> list;
    private static final VarHandle LEADER_LIST_SIZE = VHUtils.fieldVarHandle(MethodHandles.lookup(), SegmentFields.class, "leaderListSize", int.class);
    volatile int leaderListSize;

    E[] heap;
    int heapSize;
    int heapCapacity;
    Node<E> largest;


    //del capacity should be a pow of 2
    public SegmentFields(int id, LeaderList<E> list, Comparator<? super E> cmp) {
        this(id, DEFAULT_INITIAL_HEAP_SIZE, list,cmp);
    }

    public SegmentFields(int id, int initialHeapSize, LeaderList<E> list, Comparator<? super E> cmp) {
        this.comparator = comparator(cmp);
        lock = new SpinLock();
        this.id = id;
        this.list = list;
        heap = allocateArray(initialHeapSize);
    }

    public boolean add(E e) {
        var list = this.list;
        var tail = this.largest;
        if (heapSize == 0 || comparator.compare(e, heap[0]) < 0) {
            int leaderListSize = (int) LEADER_LIST_SIZE.getAcquire(this);
            if (leaderListSize == PIPQConstants.MAX_LEADER_LIST_ELEMS) {
                if (comparator.compare(e, tail.value) < 0) {
                    //Slowest path
                    Node<E> node = new Node<>(id, e);
                    list.add(node);
                    largest = list.moveFromLeaderList(node, tail, this);
                } else {
                    offerHeap(e);
                }

            } else {
                upsert(e);
            }

        } else {
            offerHeap(e);
        }


        return true;
    }



    public void helpUpsert() {
        int leaderListSize = (int) LEADER_LIST_SIZE.getAcquire(this);

        if (leaderListSize == 0) {
            largest = null;
            return;
        }


        if (heapSize == 0 || leaderListSize > PIPQConstants.HELP_UPSERT_THRESHOLD) {
            return;
        }


        upsert(pollHeap());
    }

    public void forceUpsert() {
        int leaderListSize = (int) LEADER_LIST_SIZE.get(this);

        if (leaderListSize == 0) {
            largest = null;
            return;
        }

        if (heapSize == 0 || leaderListSize > PIPQConstants.FORCE_UPSERT_THRESHOLD) return;
        upsert(pollHeap());

    }

    void upsert(E e) {
        Node<E> node = new Node<>(id, e);
        list.add(node);
        if (largest == null || comparator.compare(e, largest.value) > 0) {
            this.largest = node;
        } else if (largest.state == MARKING) {
            largest = list.findNewTail(largest);
            //prevents the issue where we deleted the only element in the leader list though we couldnt update it
            //cause of a concurrent inserter
        }

        incrementLeaderListSize();
    }

    public int decrementLeaderListSize() {
        return (int) LEADER_LIST_SIZE.getAndAdd(this, -1);
    }

    public int incrementLeaderListSize() {
        return (int) LEADER_LIST_SIZE.getAndAddRelease(this, 1);
    }


    public void offerHeap(E e) {
        int s = heapSize;
        int c = heapCapacity;
        if (s >= c) grow(c);
        siftUp(s, e, heap, comparator);
        ++heapSize;
    }

    public E pollHeap() {
        final E[] es;
        final E result;

        if ((result = (es = heap)[0]) != null) {
            final int n;
            final E x = es[(n = --heapSize)];
            es[n] = null;
            if (n > 0) siftDown(x, es, n, comparator);
        }

        return result;
    }

    public void clearHeap() {
        int h = heapSize;
        for (int i = 0; i < h; ++i) {
            heap[i] = null;
        }

        heapSize = 0;
    }



    void grow(int oldCap) {
        int newCap = growth(oldCap);
        heap = Arrays.copyOf(heap, newCap);
        heapCapacity = newCap;
    }

    int growth(int oldCap) {
       int growth = (oldCap < 64)
                ? (oldCap + 2) // grow faster if small
                : (oldCap >> 2);

        return newLength(oldCap, 1, growth);
    }

    public void acquire() {
        lock.lock();
    }

    public boolean tryAcquire() {
        return lock.tryLock();
    }

    public boolean boundedTryAcquire() {
        return lock.boundedTryLock();
    }

    public void release() {
        lock.unlock();
    }

    public void resetSize() {
        LEADER_LIST_SIZE.setVolatile(this, 0);
    }


    static <E> Comparator<? super E> comparator(Comparator<? super E> cmp) {
        if (cmp == null) return (a, b) -> ((Comparable<? super E>) a).compareTo(b);
        return cmp;
    }

    static <E>void siftUp(int k, E x, E[] buffer, Comparator<? super E> comparator) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            E e = buffer[parent];
            if (comparator.compare(x, e) >= 0)
                break;
            buffer[k] = e;
            k = parent;
        }

        buffer[k] = x;
    }

    static <E>void siftDown(E x, E[] es, int n, Comparator<? super E> cmp) {
        int k = 0;
        int half = n >>> 1;
        while (k < half) {
            int child = (k << 1) + 1;
            E c = es[child];
            int right = child + 1;
            if (right < n && cmp.compare(c, es[right]) > 0)
                c = es[child = right];
            if (cmp.compare(x, c) <= 0)
                break;
            es[k] = c;
            k = child;
        }
        es[k] = x;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Heap: ").append(Arrays.toString(heap));
        return sb.toString();
    }

}

public class Segment<E> extends SegmentFields<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b // 48 + 80 = 128

    public Segment(int id, LeaderList<E> list , Comparator<? super E> cmp) {
        super(id, list ,cmp);
    }

    void clear() {
        acquire();
        try {
            largest = null;
            clearHeap();
            resetSize();
        } finally {
          release();
        }
    }


}
