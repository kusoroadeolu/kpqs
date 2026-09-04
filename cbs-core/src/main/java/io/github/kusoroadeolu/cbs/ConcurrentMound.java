package io.github.kusoroadeolu.cbs;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.comparator;

public class ConcurrentMound<E> implements PQ<E> {
    static final int INITIALIZED_ARRAY_DEPTH = 3;
    static final int MAX_DEPTH = 32;
    static final int MAX_TRIES = 8;

    final SegmentedArray<MoundNode<E>> heap;
    final Comparator<? super E> comparator;
    volatile int depth;
    private static final VarHandle DEPTH = VHUtils.fieldVarHandle(MethodHandles.lookup(), ConcurrentMound.class, "depth", int.class);

    public ConcurrentMound(Comparator<? super E> cmp){
        this.heap = new SegmentedArray<>();
        this.comparator = comparator(cmp);
    }

    public int depth() {
        return depth + 1;
    }


    //Note: tries is only incremented when we find an ins point where
    public boolean offer(E e) {
        int depth = this.depth;
        var heap = this.heap;
        var cmp = this.comparator;

        //This branch is to handle the case where depth is 0 but we might not be able to insert into the first level
        //The other branch a thread will just spin uselessly for 8
        if (depth == 0) {
            for (;;) {
                var node = heap.get(0, 1); //volatile read

                if (node == null || node.laDeleted()) {
                    if (heap.cas(0, 1, node, new MoundNode<>(e))) return true;
                } else if (compare(e, node.laMax(), cmp) > 0) break;
                else {
                    node.lock();
                    try {
                        if (!node.lpDeleted()) {
                            if (compare(e, node.lpMax(), cmp) > 0) break;
                            else {
                                node.add(e, cmp);
                                return true;
                            }
                        }
                        //if deleted we'll just retry

                    }finally {
                        node.unlock();
                    }
                }
            }

            if (casDepth(depth, depth + 1)) depth++;
            else depth = this.depth;
        }


        int tries = 0;
        int origin = origin(depth), bound = bound(depth);
        var startIndex = ThreadLocalRandom.current().nextInt(origin, bound);
        for(;;) {
            var start = heap.get(depth, startIndex);
            if (compareMound(e, start , cmp) > 0) {

                if (++tries >= MAX_TRIES) {
                    depth = tryIncreaseDepth(heap, depth);
                    origin = origin(depth);
                    bound = bound(depth);
                    tries = 0;
                }

                startIndex = ThreadLocalRandom.current().nextInt(origin, bound);
                continue;
            }

            int heapIndex = binarySearch(heap, e, startIndex, depth);

            if (heapIndex == 1) {
                var node = heap.get(0, heapIndex);
                if (node == null || node.laDeleted()) {
                    if (heap.cas(0, 1, node, new MoundNode<>(e))) return true; //fast path
                } else if (compare(e, node.laMax(), cmp) <= 0) { //check optimistically then validate
                    node.lock();
                    try {
                        if (!node.lpDeleted() && compare(e, node.lpMax(), cmp) <= 0) {
                            node.add(e, cmp);
                            return true;
                        }
                    }finally {
                        node.unlock();
                    }
                }

            } else {
                var parentIndex = heapIndex >>> 1;
                int level = level(heapIndex);
                assert level > 0;
                var parent = heap.get(level - 1, parentIndex);

                //parent should be >= e, avoid the acquire read unless needed
                if (parent == null || compare(e, parent.laMax(), cmp) < 0 || parent.laDeleted()) continue;
                parent.lock();
                try {
                    if (parent.lpDeleted() || compare(e, parent.lpMax(), cmp) < 0) continue;
                    int offset = offset(heapIndex, level);
                    var curr = heap.getOffset(level, offset);
                    if (curr == null) { //if curr is null, just insert a new mound node
                        heap.putOffset(level, offset, new MoundNode<>(e)); //volatile write
                        return true;
                    } else if (curr.laDeleted()) { //if curr is deleted, wait till the deleter nulls out its slot, then insert a new mound node
                        var node = new MoundNode<>(e);
                        assert heap.casOffset(level, offset, curr, node) || heap.casOffset(level, offset, null, node);
                        return true;
                    } else {
                        // e <= curr
                        if (compare(e, curr.laMax(), cmp) > 0) continue;
                        curr.lock();  //else hold lock then revalidate
                        try {
                            if (!curr.lpDeleted() && compare(e, curr.lpMax(), cmp) <= 0) {
                                curr.add(e, cmp);
                                return true;
                            }
                        }finally {
                            curr.unlock();
                        }

                    }
                }finally {
                    parent.unlock();
                }
            }

        }

    }

    public E poll() {
        var heap = this.heap;
        var first = heap.get(0, 1);
        if (first == null || first.laDeleted()) return null;
        first.lock();

        if (first.lpDeleted()) {
            assert first.poll() == null;
            first.unlock();
            return null;
        }

        E val = first.poll();
        assert val != null;
        moundify(heap, first);
        return val;
    }

    @Override
    public E peek() {
        var heap = this.heap;
        var first = heap.get(0, 1);
        if (first == null || first.laDeleted()) return null;
        else return first.laMax();
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
        var heap = this.heap;
        while (true) {
            var first = heap.get(0, 1);
            if (first == null || first.laDeleted()) return;
            first.lock();

            if (first.lpDeleted()) {
                assert first.poll() == null;
                first.unlock();
                return;
            }

            first.clear();
            moundify(heap, first);
        }
    }

    //only for use in jmh teardown benchmarks
    public void clearUnsafe() {
        var heap = this.heap;
        for (int level = 0; level < depth; ++level) {
            var a = heap.lvArray(level);
            heap.casArray(level, a, new ZeroIndexedArray<>(1 << level));
        }
    }

    void moundify(SegmentedArray<MoundNode<E>> heap, MoundNode<E> start) {
        MoundNode<E> parent = start;
        int parentIndex = 1;
        int parentLevel = 0;
        var cmp = this.comparator;

        for (;;) {
            int childLevel = parentLevel + 1;

            if (childLevel >= MAX_DEPTH) break;

            var array = heap.lvArray(childLevel);

            if (array == null) break;

            var leftIndex = parentIndex * 2;
            var rightIndex = leftIndex + 1;
            var left = heap.get(childLevel, leftIndex);
            var right = heap.get(childLevel, rightIndex);

            boolean leftLocked = left != null;
            boolean rightLocked = right != null;

            if (leftLocked) left.lock();
            if (rightLocked) right.lock();
            if (!leftLocked && !rightLocked) break;

            var parentQueue = parent.queue;

            if (leftLocked && compare(parent.peek(), left.lpMax(), cmp) > 0 && compareMoundPlain(left.peek(), right, cmp) <= 0) {
                if (rightLocked) right.unlock();
                parent.queue = left.queue;
                left.queue = parentQueue;

                parent.srMax(parent.peek());
                left.srMax(left.peek());

                parent.unlock();

                parent = left;
                parentIndex = leftIndex;
                parentLevel = childLevel;
            } else if (compareMoundPlain(parent.peek(), right, cmp) > 0) {
                if (leftLocked) left.unlock();

                parent.queue = right.queue; //can't NPE
                right.queue = parentQueue;

                parent.srMax(parent.peek());
                right.srMax(right.peek());

                parent.unlock();

                parent = right;
                parentIndex = rightIndex;
                parentLevel = childLevel;
            } else {
                if (leftLocked) left.unlock();
                if (rightLocked) right.unlock();
                break;
            }
        }


        if (parent.peek() == null) {
            parent.srDeleted();
            heap.casOffset(parentLevel, offset(parentIndex, parentLevel), parent, null);
        }

        parent.unlock();
    }



    int tryIncreaseDepth(SegmentedArray<MoundNode<E>> heap, int depth) {
        int newDepth = depth + 1;
        if (newDepth >= INITIALIZED_ARRAY_DEPTH) {
            //check then optimistically initialize
            //we want to initialize then attempt to increase depth to avoid the issue where depth is increased but there's no array at that level
            var absent = heap.lvArray(newDepth);
            if (absent == null) heap.casArray(newDepth, null ,new ZeroIndexedArray<>(1 << newDepth)); //might fail, that's alr
        }

        if (newDepth != MAX_DEPTH) {
            casDepth(depth, newDepth);
        }
        return this.depth;
    }

    /**
     * For a one indexed depth
     * origin = 2 ^ (depth - 1)
     * bound = (2 ^ depth) - 1, assuming bound is inclusive
     * */
    static int origin(int depth) {
        return Math.powExact(2, depth);
    }

    boolean casDepth(int from, int to) {
       return DEPTH.compareAndSet(this, from, to);
    }

    static int bound(int depth) {
        return Math.powExact(2, (depth + 1));
    }

    int binarySearch(SegmentedArray<MoundNode<E>> heap, E elem, int start, int depth) {
        //low = 1 (pos), high = start (pos)
        int low = 0, high = depth;
        var comparator = this.comparator;
        while (low < high) {
            int mid = (low + high) >>> 1 ;
            int normalizedLevel = depth - mid; //we need to invert mid here so we can assume start >> 0 = 1 (the root of the heap)
            int heapIndex = start >> normalizedLevel; //actual index in array to check
            var leaf = heap.get(mid, heapIndex); //pass mid through as mid already fulfills the start >> 0 precondition
            int cmp = compareMound(elem, leaf, comparator); //cause leaf could be null
            if (cmp > 0) low = mid + 1;
            else high = mid;
        }

        return start >> (depth - low);
    }


    static class MoundNode<E> {
        private static final VarHandle MAX = VHUtils.fieldVarHandle(MethodHandles.lookup(), MoundNode.class, "max", Object.class);
        private static final VarHandle DELETED = VHUtils.fieldVarHandle(MethodHandles.lookup(), MoundNode.class, "deleted", boolean.class);
        volatile E max;
        volatile boolean deleted;
        private static final PriorityQueue<?> EMPTY = new PriorityQueue<>();
        PriorityQueue<E> queue;
        final Lock lock;

        public MoundNode(E e) {
            lock = new SpinLock();
            lock.lock();
            try {
                this.queue = new PriorityQueue<>();
                queue.add(e);
            }finally {
                lock.unlock();
            }
        }

        void lock() {
            lock.lock();
        }

        void unlock() {
            lock.unlock();
        }

        /* Only accessed under the lock */
        void add(E e, Comparator<? super E> cmp) {
            E currMax = lpMax();
            queue.add(e);
            if (compare(e, currMax, cmp) < 0) srMax(e);
        }

        //Removes and returns the topmost value and
        E poll() {
            return queue.poll();
        }

        E peek() {
            return queue.peek();
        }

        void clear() {
            queue = (PriorityQueue<E>) EMPTY;
        }


        E lpMax() {
            return (E) MAX.get(this);
        }

        void srMax(E e) {
            MAX.setRelease(this, e);
        }

        void srDeleted() {
            DELETED.setRelease(this, true);
        }

        boolean lpDeleted() {
            return (boolean) DELETED.get(this);
        }


        /* Accessed outside the lock */
        E laMax() {
           return (E) MAX.getAcquire(this);
        }

        boolean laDeleted() {
            return (boolean) DELETED.getAcquire(this);
        }



        @Override
        public String toString() {
            lock();
            try {
                return queue.toString();
            }finally {
                unlock();
            }
        }

    }

    static class SegmentedArray<E> {
        //private static final VarHandle ARRAY = VHUtils.arrayVarHandle(ZeroIndexedArray[].class);
        final AtomicReferenceArray<ZeroIndexedArray<E>> array;

        public SegmentedArray() {
            this.array = new AtomicReferenceArray<>(MAX_DEPTH);
            for (int i = 0; i < INITIALIZED_ARRAY_DEPTH; ++i) {
                int cap = (1 << i);
                array.setRelease(i, new ZeroIndexedArray<>(cap));
            }
        }

        //depth - level (indexed by zero)
        E get(int level, int heapIndex) { //heap index -> logical binary heap index (indexed by 1)
            return getOffset(level, offset(heapIndex, level));
        }

        E getOffset(int level, int offset) { //heap index -> logical binary heap index (indexed by 1)
            assert level >= 0 && level < MAX_DEPTH;
            var a = lvArray(level);
            return a.get(offset);
        }


        void putOffset(int level, int offset, E e) { //heap index -> logical binary heap index (indexed by 1)
            assert level >= 0 && level < MAX_DEPTH;
            var a = lvArray(level);
            a.put(offset, e);
        }

        boolean casArray(int level, ZeroIndexedArray<E> from ,ZeroIndexedArray<E> to) {
            return array.compareAndSet(level, from, to);
        }

        boolean casOffset(int level,int offset ,E from, E to) { //heap index -> logical binary heap index (indexed by 1)
            assert level >= 0 && level < MAX_DEPTH;
            var a = lvArray(level);
            //convert heap index to this level's array offset
            return a.cas(from, to, offset);
        }

        boolean cas(int level, int heapIndex, E from, E to) {
            return casOffset(level, offset(heapIndex, level) ,from, to);
        }

        ZeroIndexedArray<E> lvArray(int index) {
            return array.get(index);
        }

    }

    static int level(int index) {
        return 31 - Integer.numberOfLeadingZeros(index);
    }

    static <E>int compareMound(E e, MoundNode<E> other, Comparator<? super E> comparator) {
        if (other == null) return -1;
        return compare(e, other.laMax(), comparator);
    }

    static <E>int compareMoundPlain(E e, MoundNode<E> other, Comparator<? super E> comparator) {
        if (other == null) return -1;
        return compare(e, other.lpMax(), comparator);
    }

    static <E> int compare(E e, E other, Comparator<? super E> comparator) {
        if (other == null) return -1;
        if (e == null) return 1;
        return comparator.compare(e, other);
    }

    static class ZeroIndexedArrayLPad {
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

    static class ZeroIndexedArrayFields<E> extends ZeroIndexedArrayLPad{
        private static final VarHandle ARRAY = VHUtils.arrayVarHandle();
        final E[] array;
        final int capacity;

        public ZeroIndexedArrayFields(int capacity) {
            this.array = (E[]) new Object[capacity];
            this.capacity = capacity;
        }

        public E get(int index) {
            return (E) ARRAY.getVolatile(array, index);
        }

        public boolean cas(E from, E to, int offset) {
            return ARRAY.compareAndSet(array, offset, from, to);
        }

        public void put(int offset, E e) {
            ARRAY.setVolatile(array, offset ,e);
        }

    }

    static class ZeroIndexedArray<E> extends ZeroIndexedArrayFields<E> {
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
        byte b160,b161,b162,b163;//120b
        public ZeroIndexedArray(int capacity) {
            super(capacity);
        }
    }

    public String treeString() {
        StringBuilder sb = new StringBuilder();
        int maxIndex = (1 << depth); // last valid index at current depth
        buildTree(1, 0 ,maxIndex, "", "", sb);
        return sb.toString();
    }

    private void buildTree(int index, int depth ,int maxIndex, String prefix, String childPrefix, StringBuilder sb) {
        if (index > maxIndex) return;

        MoundNode<E> node = heap.get(depth, index);
        sb.append(prefix).append(node).append("\n");

        int left = index * 2;
        int right = index * 2 + 1;
        boolean hasLeft = left <= maxIndex;
        boolean hasRight = right <= maxIndex;

        if (hasLeft) {
            buildTree(left, depth + 1 ,maxIndex,
                    childPrefix + (hasRight ? "├── " : "└── "),
                    childPrefix + (hasRight ? "│   " : "    "),
                    sb);
        }

        if (hasRight) {
            buildTree(right, depth + 1 ,maxIndex,
                    childPrefix + "└── ",
                    childPrefix + "    ",
                    sb);
        }
    }

    static int offset(int heapIndex, int level) {
        return heapIndex - (1 << level);
    }

    //Sanity check
    static void main() {
        ConcurrentMound<Integer> m = new ConcurrentMound<>(null);
        var r = ThreadLocalRandom.current() ;
        for (int i = 0; i < 1500; ++i) {
            var val = r.nextInt(0, 10000);
            m.offer(val);
        }

        System.out.println(m.treeString());


       // System.out.println(m.treeString());
      //  System.out.println();

//
        var list = new ArrayList<Integer>();
        Integer res;
        while ((res = m.poll()) != null) {
            list.add(res);
        }
        System.out.println(list);
        System.out.println("Size: " + list.size());
    }
}
