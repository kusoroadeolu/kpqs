package io.github.kusoroadeolu.cbs;


import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Comparator;

import static io.github.kusoroadeolu.cbs.ChunkedPQ.Chunk.decodeState;
import static io.github.kusoroadeolu.cbs.ChunkedPQ.ChunkState.FROZEN;

/*
* A concurrent priority queue which uses an unrolled linked list as its base structure.
* The structure consists of three levels:
* 1. The first chunk which is logically immutable and handles deletions. Deleting threads simply claim values using fetch and add
* to prevent the issue of CAS storms on the head of the buffer
* 2. The buffer chunk allows insertions to the first chunk when a key < the anchor of the first chunk,
* these elements can later be merged into the delete buffer
* 3. The chunked list handles insertions in general and allows deleters to fill from the top of the list when the delete buffer is empty
*
* Each chunk contains
* an array which handles insertions
* a long array which allows a thread which inserted into the chunk late to know if its value was consumed
* a lock protecting the "next" pointer of a chunk
* a status field which packs three states into it
* 1. The state of the chunk (NONE, FREEZING, FROZEN)
* 2. The frozen index of the chunk (used when freezing the first chunk)
* 3. The current index of the chunk (index to insert into)
*
* To allow for greater concurrency while inserting,
* insertions into chunks are essentially lock free until a split is needed (i.e. dividing one chunk into two), in which the lock is held
* */
public class ChunkedPQ<E> implements PQ<E> {

    private static final int CHUNK_CAPACITY = 64;
    private static final int MIN_F_CHUNK_SIZE = 16;
    private final Chunk<E> head;
    private final Comparator<? super E> cmp;


    public ChunkedPQ() {
        head = new Chunk<>(null, null);

        cmp = (Comparator<? super E>) Comparator.naturalOrder();
    }

    @Override
    public boolean offer(E e) {
        var cs = new Chunks<E>();
        var head = this.head;
        var comparator = cmp;

        for (;;) {
           findNode(e, head, cs, comparator);

           var pred = cs.pred;
           var curr = cs.curr;

           if (pred == head) {
                if (!insertIntoFirstChunk(e, pred, (FirstChunk<E>) curr)) continue;
                return true;
           }

           if (curr == null || compare(e, curr.anchor, cmp) < 0) {
               Object[] o = new Object[CHUNK_CAPACITY];
               synchronized (pred) {
                   if (pred.lpNext() != curr) continue;

                   o[0] = e;
                   var chunk = new Chunk<>(e, o);
                   chunk.spNext(pred.lpNext());
                   pred.soNext(chunk);
               }

               continue;
           }

           var status = curr.fetchAndAddStatus();

           if (decodeState(status) >= ChunkState.FREEZING) continue;

           int index = Chunk.decodeIndex(status);
           if (index < CHUNK_CAPACITY) {
               curr.spArray(index, e);
               VarHandle.fullFence();
               Bitmap bm = null;
               if (decodeState(curr.lpStatus()) == ChunkState.FREEZING) {
                   while ((bm = curr.bitmap) != null) {
                       if (bm.isFrozen(index)) return true;
                   }
               } else if (curr.bitmap.isFrozen(index)) return true;
           } else {
               //TODO, when implementing poll, when we need to update the status of a chunk to frozen, always hold its lock

               Object[] o = new Object[CHUNK_CAPACITY];
               byte[] bits = new byte[CHUNK_CAPACITY];
               synchronized (pred) {

                   //TODO implement split algorithm, pretty simple tbf
                   var predStatus = pred.lpStatus();
                   var currStatus = curr.lpStatus();
                   if (decodeState(predStatus) == FROZEN && decodeState(currStatus) == FROZEN && pred.lpNext() != curr) continue;

                   synchronized (curr) {

                   }

               }
           }

        }
    }


    boolean insertIntoFirstChunk(E e, Chunk<E> pred, FirstChunk<E> curr) {
        Chunk<E> b;
        boolean createdBuffer = false;
        if (curr != null) {
            if (curr.buffer == null) {
                Object[] o = new Object[CHUNK_CAPACITY];
                o[0] = e;
                if(!curr.casBuffer((b = new Chunk<>(e, o, Chunk.encode(ChunkState.BUFFER, 0, 1))))) b = curr.buffer;
                else createdBuffer = true;
            } else b = curr.buffer;
        } else {
            Object[] o;
            FirstChunk<E> chunk = new FirstChunk<>(e, (o = new Object[1]), 1);
            o[0] = e;
            synchronized (pred) {
                if (pred.lpNext() != null) return false;
                pred.soNext(chunk);
                return true;
            }
        }

        int index = Integer.MAX_VALUE; //bogus value

        if (!createdBuffer) {
            long status = b.fetchAndAddStatus();
            int state = decodeState(status);
            index = Chunk.decodeIndex(status);
            if (index < CHUNK_CAPACITY && state < ChunkState.FREEZING) {
                b.svArray(index, e);
                Bitmap bitmap;
                if ((bitmap = b.bitmap) != null && bitmap.isFrozen(index)) return true; //linearization point (if true)
            }
        }

        Thread.yield(); //yield, allow threads to hopefully progress a bit before trying to merge the buffer and first chunk

        long fStatus = freezeFirstChunk(curr);
        Bitmap bm = null;

        if (decodeState(fStatus) == FROZEN) return b.bitmap.isFrozen(index); //linearization point (if true)
        freezeInsertChunk(b);

        if (b.casBitmap(bm = allocateBitmap(b)) || !(bm = b.bitmap).isFrozen(index)) {
            synchronized (pred) {
                var next = pred.lpNext(); //ideally we could use the buffer's status or first chunk's status but we'd need to perform some math
                //even though the math is pretty fast, this should be cheaper
                if (next != curr) { //first chunk has been replaced
                    //still need to recheck cause we might not have inserted into the buffer cause our index was out of bounds
                    return bm.isFrozen(index); //if our index is frozen, linearization point
                }

                int frozenIndex = Chunk.decodeFrozenIdx(fStatus);
                int remElements = curr.capacity - frozenIndex; //rem elements in the first chunk at the time of freezing
                int total = remElements + bm.size();

                Object[] sorted = new Object[bm.isFrozen(index) ? total : total + 1];
                int sortedPtr = 0;
                for (int i = frozenIndex; i < curr.capacity; ++i) {
                    sorted[sortedPtr++] = curr.lpArray(i);
                }

                var bits = bm.bits;
                for (int i = 0; i < CHUNK_CAPACITY; ++i) {
                    if (bits[i] == Bitmap.CLAIMED) {
                        sorted[sortedPtr++] = b.lpArray(i);
                    }
                }

                if (!bm.isFrozen(index)) sorted[sortedPtr + 1] = e;


                sortArray(sorted, cmp);
                var fs = new FirstChunk<>((E) sorted[0], sorted, total);

                synchronized (curr) {
                    curr.status = Chunk.encode(FROZEN, frozenIndex, Chunk.decodeIndex(fStatus));
                    fs.spNext(curr.lpNext());
                    pred.soNext(fs);
                }

                return true; //if our index is frozen, linearization point

            }
        } else return bm.isFrozen(index);
    }

    @SuppressWarnings("unchecked")
    static <T>void sortArray(Object[] array, Comparator<T> cmp) {
        if (cmp == null) Arrays.sort(array);
        else Arrays.sort((T[]) array, cmp);
    }

    Bitmap allocateBitmap(Chunk<E> chunk) {
        int size = 0;
        byte[] bits = new byte[CHUNK_CAPACITY];
        for (int i = 0; i < CHUNK_CAPACITY; ++i) {
            if (chunk.lvArray(i) != null) {
                bits[i] = Bitmap.CLAIMED;
                ++size;
            }
        }

        return new Bitmap(bits, size);
    }


    void freezeInsertChunk(Chunk<E> chunk) {
        chunk.bitwiseOr((long) ChunkState.FREEZING << 61);
    }

    long freezeFirstChunk(FirstChunk<E> chunk) {
        while (true) {
            long status = chunk.status;
            int state = decodeState(status);

            if (state >= ChunkState.FREEZING) return status;

            int index = Chunk.decodeIndex(status);
            int frozenIndex = Math.max(chunk.capacity, index);


            if (chunk.casStatus(status, (status = Chunk.encode(ChunkState.FREEZING, frozenIndex, index)))) return status;
        }
    }

    @Override
    public E poll() {
        return null;
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

    @Override
    public void clear() {

    }

    static <T>void findNode(T t, Chunk<T> left, Chunks<T> chunks, Comparator<? super T> comparator) {
        Chunk<T> pred = left;
        Chunk<T> curr = pred.loNext();
        while (curr != null) {
            Chunk<T> next = curr.loNext();
            if (next == null || compare(t, next.anchor, comparator) < 0) break;
            pred = curr;
            curr = next;

        }

        chunks.pred = pred; chunks.curr = curr;
    }

    static class Chunks<T> {
        Chunk<T> pred, curr;
    }

    static <T>int compare(T t, T other, Comparator<? super T> cmp) {
        return cmp == null ? ((Comparable<T>)t).compareTo(other) : cmp.compare(t, other);
    }


    static final class ChunkState {
        static final int BUFFER = 0;
        static final int INSERT = 1;
        static final int DELETE = 2;
        static final int FREEZING = 3;
        static final int FROZEN = 7; //really only used for the first & insert chunks
    }


    record Bitmap(byte[] bits, int size) {
        static final int CLAIMED = 1;

        boolean isFrozen(int index) {
            if (index >= CHUNK_CAPACITY) return false;
            return bits[index] == CLAIMED;
        }
    }

    static class FirstChunk<T> extends Chunk<T>{
        volatile Chunk<T> buffer;
        final int capacity;
        private static final VarHandle BUFFER = VHUtils.fieldVarHandle(MethodHandles.lookup(), ChunkedPQ.class, "buffer", Chunk.class);

        public FirstChunk(T anchor, Object[] array, int capacity) {
            super(anchor, array);
            this.capacity = capacity;
        }

        public FirstChunk(T anchor, Object[] array, long status, int capacity) {
            super(anchor, array, status);
            this.capacity = capacity;
        }

        boolean casBuffer(Chunk<T> to) {
            return BUFFER.compareAndSet(this, null, to);
        }
    }

    static class Chunk<T> {
        final T anchor;
        final Object[] array;
        volatile Chunk<T> next;
        volatile long status;
        volatile Bitmap bitmap;


        public Chunk(T anchor, Object[] array) {
            this.anchor = anchor;
            this.array = array;
        }

        public Chunk(T anchor, Object[] array, long status) {
            this.anchor = anchor;
            this.array = array;
            this.status = status;
        }

        boolean casBitmap(Bitmap bitmap) {
           return F_BITMAP.compareAndSet(this, null, bitmap);
        }

        long lpStatus() {
            return (long) STATUS.get(this);
        }

        boolean casStatus(long from, long to) {
            return STATUS.compareAndSet(this, from, to);
        }

        void bitwiseOr(long value) {
            STATUS.getAndBitwiseOr(this, value);
        }

        void svArray(int idx, T t) {
            ARRAY.setVolatile(array, idx, t);
        }

        void spArray(int idx, T t) {
            ARRAY.set(array, idx, t);
        }

        T lvArray(int idx) {
            return (T) ARRAY.getVolatile(array, idx);
        }

        T lpArray(int idx) {
            return (T) ARRAY.get(array, idx);
        }


        void soNext(Chunk<T> chunk) {
            NEXT.setRelease(this, chunk);
        }

        Chunk<T> lpNext() {
            return (Chunk<T>) NEXT.get(this);
        }


        public Chunk<T> loNext() {
            return (Chunk<T>) NEXT.getAcquire(this);
        }

        public void spNext(Chunk<T> chunk) {
            NEXT.set(this, chunk);
        }

        //atomically increments the LSB of the status, and returning the old status
        public long fetchAndAddStatus() {
            return (long) STATUS.getAndAdd(this, 1);
        }

        private static final long FROZEN_INDEX_MASK = 0x1FFFFFFF;
        private static final long INDEX_MASK = 0xFFFFFFFFL;
        private static final long BITS_FOR_STATE = 61;
        private static final long BITS_FOR_F_INDEX = 32;


        static long encode(int state, int frozenIdx, int index) {
            return ((long)state << BITS_FOR_STATE) | ((frozenIdx & FROZEN_INDEX_MASK) << BITS_FOR_F_INDEX   ) | (index & INDEX_MASK);
        }
        static int decodeState(long s) {
            return (int)(s >>> BITS_FOR_STATE);
        }
        static int decodeFrozenIdx(long s){
            return (int)((s >>> BITS_FOR_F_INDEX) & FROZEN_INDEX_MASK);
        }
        static int decodeIndex(long s) {
            return (int)(s & INDEX_MASK);
        }

        private static final VarHandle NEXT;
        private static final VarHandle ARRAY;
        private static final VarHandle STATUS;
        private static final VarHandle F_BITMAP;

        static {
            MethodHandles.Lookup l = MethodHandles.lookup();
            try {
                ARRAY = MethodHandles.arrayElementVarHandle(Object[].class);
                NEXT = l.findVarHandle(Chunk.class, "next", Chunk.class);
                STATUS = l.findVarHandle(Chunk.class, "status", long.class);
                F_BITMAP = l.findVarHandle(Chunk.class, "bitmap", Bitmap.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }


    static void main() {
        Chunk<Integer> chunk = new Chunk<>(null, null);
        Chunk.STATUS.set(chunk, Chunk.encode(ChunkState.BUFFER, 0, 64));
        System.out.println("Initial: " + Chunk.STATUS.get(chunk));
        Chunk.STATUS.getAndBitwiseOr(chunk, (long) ChunkState.FREEZING << 61);
        System.out.println("Later: " + Chunk.STATUS.get(chunk));
        System.out.println("State: " + decodeState((long)Chunk.STATUS.get(chunk)));
        Chunk.STATUS.getAndBitwiseOr(chunk, (long) ChunkState.FREEZING << 61);
        System.out.println("Later 1: " + Chunk.STATUS.get(chunk));
        System.out.println("State 1: " + decodeState((long)Chunk.STATUS.get(chunk)));
    }
}
