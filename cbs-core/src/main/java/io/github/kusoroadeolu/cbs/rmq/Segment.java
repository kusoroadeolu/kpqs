package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.locks.Lock;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.*;

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
    final RingBuffer<E> insertBuffer;
    final SortedList<E> deleteBuffer; //sorted ring buffer (could also use a linked list to prevent shifting but arrays provide better cache locality)
    final Lock lock;
    final Comparator<? super E> comparator;
    final int id;
    final MpscLeaderQueue queue;

    private static final VarHandle SIZE = VHUtils.fieldVarHandle(MethodHandles.lookup(), SegmentFields.class, "size", int.class);
    int size;

    E[] heap;
    int heapSize;
    int heapCapacity;
  // final SpinLock heapAllocationLock;


    //del capacity should be a pow of 2
    public SegmentFields(int deleteBufferCapacity, MpscLeaderQueue q, int id , Comparator<? super E> cmp) {
        this(deleteBufferCapacity, q, id, 0, cmp);
    }

    public SegmentFields(int deleteBufferCapacity, MpscLeaderQueue q, int id, int initialHeapSize ,Comparator<? super E> cmp) {
        this.comparator = comparator(cmp);
        deleteBuffer = new SortedList.SortedRingBuffer<>(allocateArray(deleteBufferCapacity), this.comparator);
        var insBufferCap = roundToPowerOfTwo(deleteBufferCapacity * 3);
        insertBuffer = new RingBuffer<>(allocateArray(insBufferCap));
        lock = new SpinLock();
        queue = q;
        this.id = id;
        heap = allocateArray(Math.max(initialHeapSize, insBufferCap));
        //  heapAllocationLock = new SpinLock();
    }

    public boolean add(E e) {
        var insBuffer = insertBuffer;
        var delBuffer = deleteBuffer;

       // outer: for (;;) {
            E result;
            boolean insEmpty = insBuffer.size() == 0;
            //both ins ahd heap empty
            if (insEmpty && (heapSize == 0 || comparator.compare(e, heap[0]) < 0)) result = delBuffer.add(e); //must add if the del buffer isn't full
            else result = delBuffer.offer(e); //will only add if it's smaller than the largest elem in the del buffer


            //Successfully added (not full), publish id in leader queue
            if (result == SortedList.added()) {
                publishId(); //slowest path due to cache coherence traffic while publishing to the queue
                SIZE.getAndAddRelease(this, 1);
                return true;
            }

            //Did we fail to insert (null) or buffer was full and we evicted the last elem(result)?
            E toAdd = result == null ? e : result;

            //Failed to add, try to add to insert buffer, then try heap
            boolean added = insBuffer.add(toAdd); //fast path, if we don't need to drain the buffer

            //drain to heap then insert
            if (!added) {
                E value;
                while ((value = insBuffer.peek()) != null) {
                    addToHeap(value); //slow path as we have to drain ins buffer to the heap
                    insBuffer.remove();
                }

                insBuffer.add(toAdd);
            }

            SIZE.getAndAddRelease(this, 1);
            return true;
        //}

    }

    public E poll() {
        var insBuffer = insertBuffer;
        var delBuffer = deleteBuffer;

        E result = delBuffer.poll();

        if (result == null) return null;


        if (delBuffer.isEmpty()) { //Try to keep elements in the delete buffer
            E e;

            while ((e = insBuffer.peek()) != null){
                addToHeap(e);
                insBuffer.remove();
            }

//            int cap = delBuffer.capacity();
//            E toAdd;
//            for (int i = 0; i < cap && (toAdd = pollHeap()) != null; ++i) {
//                delBuffer.add(toAdd);
//                publishId();
//            }

            E added = pollHeap();
            if (added != null) {
                delBuffer.add(added);
                publishId();
            }

         }

        SIZE.getAndAddRelease(this, -1);
        return result;
    }

//    boolean tryGrow(Object[] array, int heapCapacity) {
//        release();
//        E[] newArray = null;
//        int newCapacity = growth(heapCapacity);
//        if (heapAllocationLock.tryLock()) {
//            try {
//                if (array == this.heap)
//                    newArray = (E[]) new Object[newCapacity];
//            } finally {
//                heapAllocationLock.unlock();
//            }
//        }
//
//        if (newArray == null) { //another thread is allocating, try another segment
//            Thread.yield();
//            return false;
//        }
//
//        acquire();
//
//        if (array == this.heap) {
//            System.arraycopy(heap, 0, newArray, 0, heapSize);
//            this.heap = newArray;
//            this.heapCapacity = newCapacity;
//        }
//
//        return true;
//    }

    public void addToHeap(E e) {
        int s = heapSize;
        int c = heapCapacity;
        if (s >= c)
            grow(c);
        siftUp(s, e, heap, comparator);
        heapSize = s + 1;
    }



    public E pollHeap() {
        final E[] es;
        final E result;

        if ((result = (es = heap)[0]) != null) {
            final int n;
            final E x = es[(n = --heapSize)];
            es[n] = null;
            if (n > 0) siftDown( x, es, n, comparator);
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
        E[] b;

        //Handle OOMEs gracefully
        try {
            b = allocateArray(newCap);
        }catch (OutOfMemoryError e) {
            throw new IllegalStateException("Out of memory", e);
        }

        System.arraycopy(heap, 0, b, 0, heapSize);
        heap = b;
        heapCapacity = newCap;
    }

    int growth(int oldCap) {
       int growth = (oldCap < 64)
                ? (oldCap + 2) // grow faster if small
                : (oldCap >> 2);
        return newLength(oldCap, 1, growth);
    }

    void publishId() {
        queue.offer(id);
    }

    public void acquire() {
        lock.lock();
    }

    public boolean tryAcquire() {
        return lock.tryLock();
    }

    public void release() {
        lock.unlock();
    }

    public int size() {
        return (int) SIZE.getAcquire(this);
    }


    public void resetSize() {
        SIZE.setRelease(this, 0);
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


    static class RingBuffer<E> {
        final E[] buffer;
        final int mask;
        long pIndex;
        long cIndex;

        RingBuffer(E[] buffer) {
            this.buffer = buffer;
            this.mask = (buffer.length - 1);
        }

        public boolean add(E elem) {
            int s = size();
            int mask = this.mask;
            if (s == (mask + 1))
                return false;

            buffer[offset(pIndex++, mask)] = elem;
            return true;
        }

        public void clear() {
            for (int i = 0; i <= mask; ++i) {
                buffer[i] = null;
            }

            pIndex = 0;
            cIndex = 0;
        }

        int size() {
            return (int) (pIndex - cIndex);
        }

        public E peek() {
            return buffer[offset(cIndex, mask)];
        }

        public void remove() {
            int offset = offset(cIndex++, mask);
            buffer[offset] = null;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Delete Buffer: ").append(deleteBuffer).append("\n");
        sb.append("Insert Buffer: ").append(Arrays.toString(insertBuffer.buffer)).append("\n");
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

    public Segment(int bufferSize, MpscLeaderQueue q, int id, Comparator<? super E> cmp) {
        super(bufferSize, q, id, cmp);
    }

    public Segment(int bufferSize, MpscLeaderQueue q, int id, int initialHeapSize ,Comparator<? super E> cmp) {
        super(bufferSize, q, id, initialHeapSize ,cmp);
    }

    void clear() {
        acquire();
        try {
            deleteBuffer.clear();
            insertBuffer.clear();
            clearHeap();
            resetSize();
        } finally {
          release();
        }
    }


}
