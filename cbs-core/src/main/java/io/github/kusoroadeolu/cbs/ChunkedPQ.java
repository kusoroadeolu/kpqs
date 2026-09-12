package io.github.kusoroadeolu.cbs;


import io.github.kusoroadeolu.cbs.SortedList.SortedBuffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.github.kusoroadeolu.cbs.ChunkedPQ.Chunk.decodeState;
import static io.github.kusoroadeolu.cbs.ChunkedPQ.ChunkState.*;

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
* Each chunk contains an anchor which is the largest value in that chunk. The major invariant
* for inserting into a chunk is that the chunk's anchor must be greater than or equal that value
*
* Each chunk also contains a lock, which protects access to it's next pointer
*
* To find a chunk into insert into, a thread simply scans the list
* If the chunk is the first chunk, since inserts are forbidden here, it writes into the chunk's buffer and tries to
* trigger a merge with the first chunk, concurrent threads inserting into the first chunk try to help speed up the merge process
* as well, otherwise, we simply hold the lock and insert, triggering a split that if chunk is full
*
* Polls on the other hand simply increment a shared counter using FAA to claim a value from the first chunk
* If the thread notices that the chunk is freezing it then checks if its claimed index was also frozen as well, if not, it returns
* Otherwise it tries to merge the first chunk with the buffer, and the next chunk after the first chunk if needed
*
* */
public class ChunkedPQ<E> implements PQ<E> {

    private static final int CHUNK_CAPACITY = 64;
    private static final int MIN_FIRST_CHUNK_CAPACITY = 16;
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

        for (;;) {
           findNode(e, head, cs, cmp);

           var pred = cs.pred;
           var curr = cs.curr;

           if (pred == head && curr != null) {
                if (!insertIntoFirstChunk(e, pred, (FirstChunk<E>) curr)) continue;
                return true;
           }

           if (Chunk.decodeState(pred.status) >= FREEZING) continue;

           if (curr == null) {
               Object[] o = new Object[CHUNK_CAPACITY];
               o[0] = e;
               synchronized (pred) {
                   var predStatus = pred.lpStatus();
                   if (pred.lpNext() != null || decodeState(predStatus) == FROZEN) continue;
                   Chunk<E> chunk;
                   if (pred == head) chunk = new FirstChunk<>(e, o, 1 ,Chunk.encode(DELETE, 0, 0));
                   else chunk = new Chunk<>(e, o, Chunk.encode(INSERT, 0, 1));
                   pred.srNext(chunk);
                   return true;
               }
           }

           if (Chunk.decodeState(curr.loStatus()) >= FREEZING) continue;

           synchronized (pred) {
               var status = curr.lpStatus();
               if (pred.lpNext() != curr || decodeState(pred.lpStatus()) == FROZEN || decodeState(status) == FROZEN) continue;
               var index = Chunk.decodeIndex(status);
               if (index < CHUNK_CAPACITY) {
                   curr.spArray(index, e);
                   curr.spStatus(status + 1);
               } else {
                   int capacity = CHUNK_CAPACITY + 1;
                   Object[] sorted = new Object[capacity];
                   System.arraycopy(curr.array, 0, sorted, 0, CHUNK_CAPACITY);
                   sorted[CHUNK_CAPACITY] = e;
                   sortArray(sorted, cmp);
                   int half = capacity >>> 1;
                   int rem = capacity - half;
                   Object[] o1 = new Object[CHUNK_CAPACITY];
                   Object[] o2 = new Object[CHUNK_CAPACITY];

                   System.arraycopy(sorted, 0, o1, 0, half);
                   System.arraycopy(sorted, half, o2, 0, rem);

                   var c1 = new Chunk<>((E)o1[half - 1], o1, Chunk.encode(INSERT, 0, half));
                   var c2 = new Chunk<>((E)o2[rem - 1], o2, Chunk.encode(INSERT, 0, rem));

                   synchronized (curr) {
                       curr.status = Chunk.encode(FROZEN, 0, CHUNK_CAPACITY);
                       var next = curr.lpNext();
                       c1.spNext(c2);
                       c2.spNext(next);
                       pred.srNext(c1);
                   }

               }

               return true;
           }

        }
    }



    boolean insertIntoFirstChunk(E e, Chunk<E> pred, FirstChunk<E> curr) {
        Chunk<E> b = curr.buffer;
        long status = b.fetchAndAddStatus();
        int state = decodeState(status);
        int index = Chunk.decodeIndex(status);
        if (index < CHUNK_CAPACITY && state < ChunkState.FREEZING) {
            b.spArray(index, e);
            VarHandle.fullFence(); //prevent array write from being reordered with bitmap read, also prevents use from using ordered accesses for both array and bit map write/read
            //by providing the needed visibility and ordering
            Bitmap bitmap;
            //we can use the bitmap to decide whether to leave or help with freezing
            if ((bitmap = b.lpBitmap()) != null && bitmap.isFrozen(index)) return true; //linearization point (if true)

            Thread.yield();  //yield, allow threads to hopefully progress a bit before trying to merge the buffer and first chunk

            if ((bitmap = b.lpBitmap()) != null && bitmap.isFrozen(index)) return true; //recheck incase another thread has handled the bitmap

        } else if (state == FROZEN) return false; //need to retry



        Bitmap bm;
        long fStatus = -1;

        if (state < FREEZING) {
            fStatus = freezeFirstChunk(curr);
            if (decodeState(fStatus) == FROZEN) return b.bitmap.isFrozen(index); //linearization point (if true)
            freezeBufferChunk(b);
        }

        if (b.casBitmap(bm = allocateBitmap(b)) || !(bm = b.bitmap).isFrozen(index) && Chunk.decodeState((fStatus = curr.status)) < FROZEN) {
            synchronized (pred) {
                var next = pred.lpNext(); //ideally we could use the buffer's status or first chunk's status but we'd need to perform some math
                //even though the math is pretty fast, this should be cheaper
                if (next != curr) { //first chunk has been replaced
                    //still need to recheck cause we might not have inserted into the buffer cause our index was out of bounds
                    return bm.isFrozen(index); //if our index is frozen, linearization point
                }

                long s = fStatus == -1 ? curr.status : fStatus;

                int frozenIndex = Chunk.decodeFrozenIdx(s);
                int remElements = curr.capacity - frozenIndex; //rem elements in the first chunk at the time of freezing
                int total = remElements + bm.size();

                SortedList<E> sortedList = new SortedBuffer<>(bm.isFrozen(index) ? total : ++total, cmp);

                readRemElementsInFChunk(curr, sortedList, frozenIndex);
                readClaimedBitmapIndices(b, sortedList, bm);

                if (!bm.isFrozen(index)) sortedList.add(e);

                Object[] sorted = sortedList.toArray();
                if (total > CHUNK_CAPACITY) {
                    int half = total >>> 1;
                    int rem = total - half;
                    Object[] fArr = new Object[half];
                    Object[] other = new Object[CHUNK_CAPACITY];
                    System.arraycopy(sorted, 0, fArr, 0, half);
                    System.arraycopy(sorted, half, other, 0, rem);
                    var fs = new FirstChunk<>((E)fArr[half - 1], fArr, half ,Chunk.encode(DELETE, 0, 0));
                    var c = new Chunk<>((E)other[rem - 1], other, Chunk.encode(INSERT, 0, rem));
                    synchronized (curr) {
                        curr.status = Chunk.encode(FROZEN, frozenIndex, Chunk.decodeIndex(s));
                        c.spNext(curr.lpNext());
                        fs.spNext(c);
                        pred.srNext(fs);
                    }
                } else {
                    var fs = new FirstChunk<>(sortedList.peekLast(), sorted, total, Chunk.encode(DELETE, 0, 0));
                    synchronized (curr) {
                        curr.status = Chunk.encode(FROZEN, frozenIndex, Chunk.decodeIndex(s));
                        fs.spNext(curr.lpNext());
                        pred.srNext(fs);
                    }
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


    long freezeBufferChunk(Chunk<E> chunk) {
       return chunk.bitwiseOr((long) ChunkState.FREEZING << Chunk.BITS_FOR_STATE);
    }

    long freezeFirstChunk(FirstChunk<E> chunk) {
        while (true) {
            long status = chunk.status;
            int state = decodeState(status);

            if (state >= ChunkState.FREEZING) return status;

            int index = Chunk.decodeIndex(status);
            int frozenIndex = Math.min(index, chunk.capacity);

            if (chunk.casStatus(status, (status = Chunk.encode(ChunkState.FREEZING, frozenIndex, index)))) return status;
        }
    }

    @Override
    public E poll() {
        var pred = this.head;

        for (;;) {
            var curr = (FirstChunk<E>) pred.laNext();
            if (curr == null) return null;

            var status = curr.fetchAndAddStatus();
            int index = Chunk.decodeIndex(status);
            int state = Chunk.decodeState(status);
            int capacity = curr.capacity;
            if (index < capacity) {
                if (state < FREEZING) return curr.lvArray(index);

                if (index < Chunk.decodeFrozenIdx(index)) return curr.lvArray(index);
            }

            if (state == FROZEN) continue;

            var b = curr.buffer;
            long fStatus = -1;

            if (state < FREEZING) {
                fStatus = freezeFirstChunk(curr);
                freezeBufferChunk(b);
            }

            Bitmap bm;

            if (b.casBitmap(bm = allocateBitmap(b)) || !(bm = b.bitmap).isFrozen(index) && Chunk.decodeState((fStatus = curr.status)) < FROZEN) {
                synchronized (pred) {
                    var next = pred.lpNext(); //ideally we could use the buffer's status or first chunk's status but we'd need to perform some math
                    //even though the math is pretty fast, this should be cheaper
                    if (next != curr) { //first chunk has been replaced
                        //still need to recheck cause we might not have inserted into the buffer cause our index was out of bounds
                        continue;
                    }

                    long s = fStatus == -1 ? curr.status : fStatus;

                    int frozenIndex = Chunk.decodeFrozenIdx(s);
                    int remElements = curr.capacity - frozenIndex; //rem elements in the first chunk at the time of freezing
                    int total = remElements + bm.size();

                    //if total < min cap, we need to try and refill from the next chunk, this is pretty expensive
                    if (total < MIN_FIRST_CHUNK_CAPACITY) {
                        fillFromChunk: synchronized (curr) {
                            var n = curr.lpNext();

                            if (n == null) {
                                if (total == 0) {
                                    pred.srNext(null);
                                    return null;
                                } else break fillFromChunk;
                            }

                            int size = Chunk.decodeIndex(n.lpStatus());
                            total += size;

                            if (total == 0) {
                                pred.srNext(null);
                                return null;
                            }

                            SortedList<E> sortedList = new SortedBuffer<>(total, cmp);

                            readElementsInChunk(n, sortedList, size);
                            readRemElementsInFChunk(curr, sortedList, frozenIndex);
                            readClaimedBitmapIndices(b, sortedList, bm);

                            var sorted = sortedList.toArray();
                            var fs = new FirstChunk<>(sortedList.peekLast(), sorted, total, Chunk.encode(DELETE, 0, 1));
                            curr.status = Chunk.encode(FROZEN, frozenIndex, Chunk.decodeIndex(s));

                            synchronized (n) {
                                n.status = Chunk.encode(FROZEN, 0, size);
                                fs.spNext(n.lpNext());
                                pred.srNext(fs);
                            }

                            return (E) sorted[0];
                        }
                    }

                    SortedList<E> sortedList = new SortedBuffer<>(total, cmp);

                    readRemElementsInFChunk(curr, sortedList, frozenIndex);
                    readClaimedBitmapIndices(b, sortedList, bm);

                    Object[] sorted = sortedList.toArray();

                    if (total > CHUNK_CAPACITY) {
                        int half = total >>> 1;
                        int rem = total - half;
                        Object[] fArr = new Object[half];
                        Object[] other = new Object[CHUNK_CAPACITY];

                        System.arraycopy(sorted, 0, fArr, 0, half);
                        System.arraycopy(sorted, half, other, 0, rem);

                        var fs = new FirstChunk<>((E)fArr[half - 1], fArr, half ,Chunk.encode(DELETE, 0, 1));
                        var c = new Chunk<>((E)other[rem - 1], other, Chunk.encode(INSERT, 0, rem));
                        synchronized (curr) {
                            curr.status = Chunk.encode(FROZEN, frozenIndex, Chunk.decodeIndex(s));
                            c.spNext(curr.lpNext());
                            fs.spNext(c);
                            pred.srNext(fs);
                            return (E) fArr[0];
                        }
                    } else {
                        var fs = new FirstChunk<>(sortedList.peekLast(), sorted, total, Chunk.encode(DELETE, 0, 1));
                        synchronized (curr) {
                            curr.status = Chunk.encode(FROZEN, frozenIndex, Chunk.decodeIndex(s));
                            fs.spNext(curr.lpNext());
                            pred.srNext(fs);
                            return (E) sorted[0];
                        }
                    }

                }
            }
        }
    }

    void readElementsInChunk(Chunk<E> chunk, SortedList<E> list, int index) {
        for (int i = 0; i < index; ++i) {
            list.add(chunk.lpArray(i));
        }
    }

    void readRemElementsInFChunk(FirstChunk<E> chunk, SortedList<E> list, int frozenIndex) {
        for (int i = frozenIndex; i < chunk.capacity; ++i) {
            list.add(chunk.lpArray(i));
        }
    }

    void readClaimedBitmapIndices(Chunk<E> buffer, SortedList<E> list, Bitmap bm) {
        var bits = bm.bits;
        for (int i = 0; i < CHUNK_CAPACITY && bm.size() > 0; ++i) {
            if (bits[i] == Bitmap.CLAIMED) {
                var value = buffer.lpArray(i);
                list.add(value);
            }
        }
    }

    @Override
    public E peek() {
        return null;
    }

    @Override
    public int size() {
        var head = this.head;
        Chunk<E> curr = head;
        int size = 0;
        while (true) {
            synchronized (curr) {
                var n = curr.lpNext();
                if (n == null) return size;
                int index = Chunk.decodeIndex(n.lpStatus());
                if (curr == head) {
                    size += ((FirstChunk<E>)n).capacity - index;
                } else size += index;
                curr = n;
            }

        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    //Filler clear method, just for benchmarks rn
    @Override
    public void clear() {
        synchronized (head) {
            head.srNext(null);
        }
    }

    static <T> void findNode(T t, Chunk<T> left, Chunks<T> chunks, Comparator<? super T> comparator) {
        Chunk<T> pred = left;
        Chunk<T> curr = pred.laNext();

        while (curr != null && compare(t, curr.anchor, comparator) > 0) {
            pred = curr;
            curr = curr.laNext();
        }

        chunks.pred = pred; chunks.curr = curr;
    }

    static class Chunks<T> {
        Chunk<T> pred, curr;
    }

    static <T>int compare(T t, T other, Comparator<? super T> cmp) {
        return cmp == null ? ((Comparable<T>)t).compareTo(other) : cmp.compare(t, other);
    }

    public String toString() {
        return head.toString();
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
        final Chunk<T> buffer;
        final int capacity;

        public FirstChunk(T anchor, Object[] array, int capacity, long status) {
            super(anchor, array, status);
            this.capacity = capacity;
            buffer = new Chunk<>(null, new Object[CHUNK_CAPACITY], Chunk.encode(BUFFER, 0, 0));
        }

        @Override
        public String toString() {
            return "Anchor: %s Buffer: %s, Array: %s \n %s".formatted(anchor, Chunk.formatArray(buffer.array), Chunk.formatArray(array), next);
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

        @Override
        public String toString() {
            if (array == null) return "(sentinel) \n %s".formatted(next);
            return "Anchor: %s Array: %s \n %s".formatted(anchor, formatArray(array), next);
        }


        static String formatArray(Object[] arr) {
            return Arrays.stream(arr)
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        boolean casBitmap(Bitmap bitmap) {
           return F_BITMAP.compareAndSet(this, null, bitmap);
        }

        long lpStatus() {
            return (long) STATUS.get(this);
        }

        long loStatus() {
            return (long) STATUS.getOpaque(this);
        }

        void spStatus(long status) {
            STATUS.set(this, status);
        }

        Bitmap lpBitmap() {
            return (Bitmap) F_BITMAP.get(this);
        }

        boolean casStatus(long from, long to) {
            return STATUS.compareAndSet(this, from, to);
        }

        long bitwiseOr(long value) {
           return (long) STATUS.getAndBitwiseOr(this, value);
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


        void srNext(Chunk<T> chunk) {
            NEXT.setRelease(this, chunk);
        }

        Chunk<T> lpNext() {
            return (Chunk<T>) NEXT.get(this);
        }


        public Chunk<T> laNext() {
            return (Chunk<T>) NEXT.getAcquire(this);
        }

        public void spNext(Chunk<T> chunk) {
            NEXT.set(this, chunk);
        }

        //atomically increments the LSB of the status, and returns the old status
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
}