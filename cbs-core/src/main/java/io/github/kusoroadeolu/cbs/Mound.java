package io.github.kusoroadeolu.cbs;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

public class Mound<E> {
    final SegmentedArray<MoundNode> heap;
    final Random random;
    final Comparator<E> comparator;
    static final int TRIES = 5;
    int depth = 1;

    public Mound() {
        this.heap = new SegmentedArray<>(MoundNode::new);
        this.comparator = (Comparator<E>) Comparator.naturalOrder();
        random = new Random();
    }

    void put(E e) {
        var heap = this.heap;
        var cmp = comparator;
        if (depth == 1) {
            var node = heap.get(depth, 1);
            E first = node.peek();
            if (compare(e, first, cmp) <= 0) node.add(e);
            else {
                depth++;
                int index = randLeaf(origin(depth), bound(depth));
                heap.get(depth, index).add(e);
            }
            return;
        }

        int origin = origin(depth);
        int bound = bound(depth);

        for(int i = 0; i < TRIES; ++i) {
            int rand = randLeaf(origin, bound);
            var leaf = heap.get(depth, rand);
            if (compare(e, leaf.peek(), cmp) <= 0) { //found a suitable node
                int index = binarySearch(e, rand);
                heap.getI(level(index), index).add(e);
                return;
            }
        }

        if (depth < 32) depth++;

        int index = binarySearch(e, randLeaf(origin(depth), bound(depth)));
        heap.getI(level(index), index).add(e);
    }

    E pop() {
        var heap = this.heap;
        var first = heap.get(1, 1);
        E result = first.pop();
        int origin = origin(depth);
        int bound = bound(depth);
        moundify(1, 0 ,origin, bound);
        return result;
    }

    void moundify(int index, final int depth ,int origin, int bound) {
        if (index >= origin && index < bound) return; //bound is offset by one to accommodate java's rand, hence <

        var heap = this.heap;

        int leftIndex = 2 * index;
        int rightIndex = leftIndex + 1;
        int childDepth = depth + 1;
        var parent = heap.getI(depth, index);
        var left = heap.getI(childDepth, leftIndex);
        var right = heap.getI(childDepth, rightIndex);
        var pList = parent.list;

        if (compare(parent.peek(), left.peek(), comparator) > 0 && compare(left.peek(), right.peek(), comparator) <= 0) {
            var lList = left.list;
            parent.list = lList;
            left.list = pList;
            moundify(leftIndex, childDepth ,origin, bound);
        } else if (compare(parent.peek(), right.peek(), comparator) > 0) {
            var rList = right.list;
            parent.list = rList;
            right.list = pList;
            moundify(rightIndex, childDepth ,origin, bound);
        }
    }



    public String treeString() {
        StringBuilder sb = new StringBuilder();
        int maxIndex = (1 << depth) - 1; // last valid index at current depth
        buildTree(1, 1 ,maxIndex, "", "", sb);
        return sb.toString();
    }

    private void buildTree(int index, int depth ,int maxIndex, String prefix, String childPrefix, StringBuilder sb) {
        if (index > maxIndex) return;

        MoundNode node = heap.get(depth, index);
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

    /*
    start = heapIndex
    * start >> 0 = start
    * start >> depth = 1
    * */
    int binarySearch(E elem, int start) {
        int depth = this.depth - 1; //depth is one-indexed, below code only works when depth is zero-indexed
        //low = 1 (pos), high = start (pos)
        int low = 0, high = depth;
        var heap = this.heap;
        var comparator = this.comparator;
        while (low < high) {
            int mid = (low + high) >>> 1 ;
            int level = depth - mid; //we need to invert mid here so we can act as if start >> 0 = 1 (the root of the heap)
            int heapIndex = start >> level; //actual index in array to check
            var leaf = heap.getI(mid, heapIndex); //pass mid through as mid already fulfills the start >> 0 precondition
            int cmp = compare(elem, leaf.peek(), comparator);

            if (cmp > 0) low = mid + 1;
            else high = mid;

        }

        return start >> (depth - low);
    }


//    int linearSearch(E elem, int start) {
//        assert start > 1;
//        int depth = this.depth - 1;
//        int rank = depth; //reversed, recall  15 >> 0 = 15
//        var heap = this.heap;
//        var comparator = this.comparator;
//        //walk from bottom upwards
//        while (rank > 0) {
//            int actual = (depth - rank);
//            int parent = actual + 1;
//            var node = heap.get(start >> actual);
//            var pNode = heap.get(start >> parent);
//
//            int cmp = compare(elem, node.peek(), comparator);
//            int pcmp = compare(elem, pNode.peek(), comparator);
//            if (cmp <= 0 && pcmp >= 0) return start >> actual;
//            rank = rank - 1;
//        }
//
//        return 1;
//    }

    static <E>int compare(E e, E other, Comparator<E> comparator) {
        if (e == null) return 1;
        else if (other == null) return -1;
        else return comparator.compare(e, other);
    }

    int randLeaf(int origin, int bound) {
        return random.nextInt(origin, bound);
    }

    static int origin(int depth) {
        return (int) Math.pow(2, depth - 1);
    }

    static int bound(int depth) {
        return (int) Math.pow(2, depth);
    }


    class MoundNode {
        SLList list;

        public MoundNode() {
            this.list = new SLList();
        }

        void add(E e) {
            list.add(e);
        }

        E pop() {
           return list.pop();
        }

        E peek() {
            return list.peek();
        }

        @Override
        public String toString() {
            return list.toString();
        }
    }

    class SLList {
        Node<E> head;

        void add(E e) {
            var node = new Node<>(e);
            var h = head;
            var cmp = comparator;
            if (h == null) head = node;
            else if (cmp.compare(e, h.e) <= 0) {
                head = node;
                node.next = h;
            } else {
                Node<E> prev = h, curr = h.next;
                while (curr != null && cmp.compare(e, curr.e) > 0) {
                    prev = curr; curr = prev.next;
                }

                prev.next = node; node.next = curr;
            }
        }

        E peek() {
            if (head == null) return null;
            return head.e;
        }

        E pop() {
            Node<E> h = head;
            if (h == null) return null;
            E result = h.e;
            head = h.next;
            return result;
        }

        static class Node<E> {
            final E e;
            Node<E> next;

            Node(E e) {
                this.e = e;
            }

        }

        @Override
        public String toString() {
            var h = head;
            if (h == null) return "(empty)";

            StringBuilder sb = new StringBuilder("[");
            var curr = h;
            while (true) {
                sb.append(curr.e);
                var next = curr.next;
                if (next == null) break;
                else sb.append(", ");
                curr = next;
            }

            return sb.append("]").toString();
        }
    }


    static class SegmentedArray<E> {
        final ZeroIndexedArray<E>[] array;
        static final int MAX_DEPTH = 32;
        static final int INITIAL_DEPTH = 3;
        final Function<Integer, ZeroIndexedArray<E>> spawner;

        public SegmentedArray(Supplier<E> typeSpawner) {
            this.spawner = (capacity) -> new ZeroIndexedArray<>(capacity, typeSpawner);
            this.array = new ZeroIndexedArray[MAX_DEPTH];
            for (int i = 0; i < INITIAL_DEPTH; ++i) {
                int cap = Math.powExact(2, i);
                array[i] = spawner.apply(cap);
            }
        }

        E get(int level, int heapIndex) { //heap index -> logical binary heap index (indexed by 1)
            assert level >= 0;
            assert level <= MAX_DEPTH;


            level = level - 1; //level should be indexed by zero
            int size = Math.powExact(2, level);
            var a = array[level];
            int offset = heapIndex - (1 << level); //convert heap index to the level's array offset
            if (a == null) {
                a = spawner.apply(size);
                array[level] = a;
            }

            return a.get(offset);
        }

        //depth - level
        E getI(int level, int heapIndex) { //heap index -> logical binary heap index (indexed by 1)
            return get(level + 1, heapIndex);
        }

    }

    static int level(int index) {
        return 31 - Integer.numberOfLeadingZeros(index);
    }

    static class ZeroIndexedArray<T> {
        final T[] array;
        final int capacity;
        final Supplier<T> spawner;

        public ZeroIndexedArray(int capacity, Supplier<T> spawner) {
            this.array = (T[]) new Object[capacity];
            this.spawner = spawner;
            this.capacity = capacity;
            for (int i = 0; i < capacity; ++i) array[i] = spawner.get();
        }

        public T get(int index) {
            return array[index];
        }

        public T getFirst() {
            return get(0);
        }

        public void put(T t, int index) {
            int actual = index - 1;
            if (actual < 0 || actual >= capacity) throw new IllegalArgumentException();
            array[actual] = t;
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(heap.array);
    }

    static void main() {
        Mound<Integer> m = new Mound<>();
        var r = ThreadLocalRandom.current() ;
        for (int i = 0; i < 32; ++i) {
            var val = r.nextInt(0, 100);
            m.put(val);
        }

        System.out.println(m.treeString());
        System.out.println();

        var list = new ArrayList<Integer>();
        Integer res;
        while ((res = m.pop()) != null) {
            list.add(res);
        }
        System.out.println(list);

//        for (int i = 0; i < 32; ++i) {
//            var val = r.nextInt(0, 100);
//            m.put(val);
//        }
//
//        System.out.println(m.treeString());
//        System.out.println();

//        System.out.println(m);
//        System.out.println();
//        System.out.println(m.treeString());

    }

    // 0 - 1
    //1 - 2, 3
    //2 - 4,5,6,7
    //3 - 8, 9, 10, 11, 12, 13, 14, 15
}
