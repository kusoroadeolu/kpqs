package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.allocateArray;
import static io.github.kusoroadeolu.cbs.utils.MiscUtils.newLength;

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
    final Heap<E> heap; // 8 + 4 object header to ref size = 12 * 6 = 72 (+ 8 (both ints) = 80)
    final List<E> insertBuffer;
    final SortedList<E> deleteBuffer; //sorted ring buffer (could also use a linked list to prevent shifting but arrays provide better cache locality)
    final Lock lock;
    final Comparator<? super E> comparator;
    final int id;
    final MpscLeaderQueue queue;
    int size;
    private static final VarHandle SIZE = VHUtils.fieldVarHandle(MethodHandles.lookup(), SegmentFields.class, "size", int.class);

    /**
     * Min amount of elements that should be in the leader queue if there are > 1 elements in this segment
     * */
    private static final int MIN_DELETE_BUFFER_SIZE = 2;


    //del capacity should be a pow of 2
    public SegmentFields(int deleteBufferCapacity, MpscLeaderQueue q, int id , Comparator<? super E> cmp, Heap.Kind kind) {
        this.comparator = comparator(cmp);
        deleteBuffer = new SortedList.SortedRingBuffer<>(allocateArray(deleteBufferCapacity), this.comparator);
        insertBuffer = new List<>(allocateArray(deleteBufferCapacity * 3));
        lock = new ReentrantLock();
        queue = q;
        this.id = id;
        lock.lock();
        try {
            heap = Heap.Kind.fromKind(kind, comparator);
        }finally {
            lock.unlock();
        }
    }

    public void add(E e) {
        var insBuffer = insertBuffer;
        var delBuffer = deleteBuffer;

        E result = delBuffer.add(e);

        //Successfully added (not full), publish id in leader queue
        if (result == e) {
            publishId();
            SIZE.getAndAddRelease(this, 1);
            return;
        }

        var heap = this.heap;

        //Did we fail to insert (null) or buffer was full and we evicted the last elem(result)?
        E toAdd = result == null ? e : result;

        //Failed to add, try to add to insert buffer, then try heap
        boolean added = insBuffer.add(toAdd);

         //drain to heap then insert
        if (!added) {
            int s = insBuffer.size();
            for (int i = 0; i < s; ++i) {
                heap.add(insBuffer.valueAt(i));
                insBuffer.removeAt(i);
            }
            insBuffer.add(toAdd);
        }

        SIZE.getAndAddRelease(this, 1);

    }

    public E poll() {
        var insBuffer = insertBuffer;
        var delBuffer = deleteBuffer;

        E result = delBuffer.poll();

        if (result == null) return null;

        int dbSize = delBuffer.size();
        var heap = this.heap;

        if (dbSize < MIN_DELETE_BUFFER_SIZE) { //Try to keep elements in the delete buffer
            E val = heap.poll();

            if (val == null) { //heap is initially empty, drain insert buf into heap, then repoll and add from heap
                int s = insBuffer.size();
                for (int i = 0; i < s; ++i) {
                    heap.add(insBuffer.valueAt(i));
                    insBuffer.removeAt(i);
                }
                val = heap.poll();

                if (val != null) {
                    delBuffer.add(val);
                    publishId(); //ensure to offer a new id when done
                }

            } else {
                delBuffer.add(val);
                int s = insBuffer.size();
                for (int i = 0; i < s; ++i) {
                    E last = delBuffer.peekLast();
                    E toCmp = insBuffer.valueAt(i);
                    int cmp = comparator.compare(last, toCmp);

                    if (cmp > 0) {
                        E seen = delBuffer.add(toCmp);
                        delBuffer.removeLast();
                        assert seen == last;
                        insBuffer.replace(i, last);
                    }
                }

                publishId(); //ensure to offer a new id when done
            }
        }

        SIZE.getAndAddRelease(this, -1);
        return result;
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



    Comparator<? super E> comparator(Comparator<? super E> cmp) {
        if (cmp == null) return (a, b) -> ((Comparable<? super E>) a).compareTo(b);
        return cmp;
    }


    static class List<E> {
        final E[] buffer;
        final int capacity;
        int size;

        List(E[] buffer) {
            this.buffer = buffer;
            capacity = buffer.length;
        }

        public boolean add(E elem) {
            int s = size;
            if (s == capacity) return false;
            buffer[s] = elem;
            ++size;
            return true;
        }

        public void removeAt(int index) {
            buffer[index] = null;
            --size;
        }

        public void replace(int index, E replacement) {
            buffer[index] = replacement;
        }

        public int size() {
            return size;
        }

        public E valueAt(int index) {
            return buffer[index];
        }
    }

    //TODO: Allow for concurrent deletes during resizing (similar to PBQ)





}

public class Segment<E> extends SegmentFields<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b // 48 + 80 = 128

    public Segment(int bufferSize, MpscLeaderQueue q, int id, Comparator<? super E> cmp, Heap.Kind kind) {
        super(bufferSize, q, id, cmp, kind);
    }


}
