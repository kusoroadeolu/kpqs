package io.github.kusoroadeolu.cbs;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

/**
* The mound, based on the paper "A Lock-Free, Array-Based Priority Queue", is a rooted tree of sorted lists
* which aims at fast inserts under concurrent access. Now, while this is a sequential version,
* the aim of me building this is to understand how the structure behaves before implementing it concurrently
*
* Notes:
* Depth is zero index while insertion point is one indexed
 * The max depth is fixed to avoid the mound deepening unboundedly
* We avoid using a simple flat array as a heap to avoid allocating an array of 2^31 upfront, instead using
* an array of arrays with each index in the 1D corresponding to the depth of the mound and each index in the 2D
* corresponding to the actual insertion point
*
* @author kusoroadeolu
 * */
public class Mound<E> {
    final SegmentedArray<MoundNode> heap;
    final Random random;
    final Comparator<E> comparator;
    static final int TRIES = 8;
    int depth = 0;
    static final int MAX_DEPTH = 4;
    static final int M_MASK = (MAX_DEPTH - 1);

    public Mound() {
        this.heap = new SegmentedArray<>(MoundNode::new);
        this.comparator = (Comparator<E>) Comparator.naturalOrder();
        random = new Random();
    }

    void put(E e) {
        var heap = this.heap;
        var cmp = comparator;
        if (depth == 0) {
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
                int heapIndex = binarySearch(e, rand);
                int level = level(heapIndex);
                var current = heap.get(level, heapIndex);
                current.add(e);
                return;
            }
        }

        if (depth != (MAX_DEPTH - 1)) depth++;

        int heapIndex = binarySearch(e, randLeaf(origin(depth), bound(depth)));
        heap.get(level(heapIndex), heapIndex).add(e);
    }

    E pop() {
        var heap = this.heap;
        var first = heap.get(0, 1);
        E result = first.pop();
        int origin = origin(depth);
        int bound = bound(depth);
        itrMoundify(1, 0 ,origin, bound);
        return result;
    }

    void itrMoundify(int index, int depth, int origin, int bound) {
        var heap = this.heap;

        for (;;) {
            if (index >= origin && index < bound) return;

            int leftIndex = 2 * index;
            int rightIndex = leftIndex + 1;
            int childDepth = depth + 1;
            var parent = heap.get(depth, index);
            var left = heap.get(childDepth, leftIndex);
            var right = heap.get(childDepth, rightIndex);
            var pList = parent.list;

            if (compare(parent.peek(), left.peek(), comparator) > 0 && compare(left.peek(), right.peek(), comparator) <= 0) {
                parent.list = left.list;
                left.list = pList;
                ++depth;
                index = leftIndex;
            } else if (compare(parent.peek(), right.peek(), comparator) > 0) {
                parent.list = right.list;
                right.list = pList;
                ++depth;
                index = rightIndex;
            } else break;
        }


    }

    void moundify(int index, final int depth ,int origin, int bound) {
        if (index >= origin && index < bound) return; //bound is offset by one to accommodate java's rand inclusion, hence < rather than <=

        var heap = this.heap;

        int leftIndex = 2 * index;
        int rightIndex = leftIndex + 1;
        int childDepth = depth + 1;
        var parent = heap.get(depth, index);
        var left = heap.get(childDepth, leftIndex);
        var right = heap.get(childDepth, rightIndex);
        var pList = parent.list;

        if (compare(parent.peek(), left.peek(), comparator) > 0 && compare(left.peek(), right.peek(), comparator) <= 0) {
            parent.list = left.list;
            left.list = pList;
            moundify(leftIndex, childDepth ,origin, bound);
        } else if (compare(parent.peek(), right.peek(), comparator) > 0) {
            parent.list = right.list;
            right.list = pList;
            moundify(rightIndex, childDepth ,origin, bound);
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
        int depth = this.depth; //depth is one-indexed, below code only works when depth is zero-indexed
        //low = 1 (pos), high = start (pos)
        int low = 0, high = depth;
        var heap = this.heap;
        var comparator = this.comparator;
        while (low < high) {
            int mid = (low + high) >>> 1 ;
            int normalizedLevel = depth - mid; //we need to invert mid here so we can assume start >> 0 = 1 (the root of the heap)
            int heapIndex = start >> normalizedLevel; //actual index in array to check
            var leaf = heap.get(mid, heapIndex); //pass mid through as mid already fulfills the start >> 0 precondition
            int cmp = compare(elem, leaf.peek(), comparator);

            if (cmp > 0) low = mid + 1;
            else high = mid;

        }

        return start >> (depth - low);
    }


    static <E>int compare(E e, E other, Comparator<E> comparator) {
        if (e == null) return 1;
        else if (other == null) return -1;
        else return comparator.compare(e, other);
    }

    int randLeaf(int origin, int bound) {
        return random.nextInt(origin, bound);
    }

    //since depth is zero indexed we need to adjust origin and bound
    /**
     * For a one indexed depth
     * origin = 2 ^ (depth - 1)
     * bound = (2 ^ depth) - 1, assuming bound is inclusive
     * */
    static int origin(int depth) {
        return (int) Math.pow(2, depth);
    }

    static int bound(int depth) {
        return (int) Math.pow(2, (depth + 1));
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

        //depth - level (indexed by zero)
        E get(int level, int heapIndex) { //heap index -> logical binary heap index (indexed by 1)
            assert level >= 0;
            assert level < MAX_DEPTH;


            int size = Math.powExact(2, level);
            var a = array[level];
            int offset = heapIndex - (1 << level); //convert heap index to this level's array offset
            if (a == null) {
                a = spawner.apply(size);
                array[level] = a;
            }

            return a.get(offset);
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
    }

    @Override
    public String toString() {
        return Arrays.toString(heap.array);
    }


    static void main() {
        Mound<Integer> m = new Mound<>();
        var r = ThreadLocalRandom.current() ;
        for (int i = 0; i < 1500; ++i) {
            var val = r.nextInt(0, 10000);
            m.put(val);
        }


        System.out.println(m.treeString());
        System.out.println();

        System.out.println("Depth: " + m.depth);
//
//        var list = new ArrayList<Integer>();
//        Integer res;
//        while ((res = m.pop()) != null) {
//            list.add(res);
//        }
//        System.out.println(list);
//        System.out.println("Size: " + list.size());
//        System.out.println("Depth: " + m.depth);
    }

    //Some interleaving reasoning for my concurrent binary search (when i build the concurrent mound) just to wonder if indices can move out of bounds (hoepfully not)
        /*
         * 1 - 10    (0 1 2 3 4)
         * 2
         * 4
         * 6
         * 7
          We want to insert 3
          *
          * first binary search itr
          *  low = 0, high = 2 , mid = 1
          *
          * after a concurrent delete min (in progress), we could see this state
          * 1     2     2
          * 10 or 2 or  10 | the more interesting one is when we have 2 as our mid (1)
          * 4     4     4
          * 6     6     6
          * 7     7     7
          *
          * so ideally to handle swaps
          * we should swap
          * parent to child
          * then child to parent
          *
          * low = 2, high = 2
          * rank to insert at will be 2, we will still validate it under the lock so this seems harmless
          *
          * in a normal resting state after delete min we should see smth like this
          *
          * 2
          * 4
          * 6
          * x  bottom two don't matter as we're dealing with ranks 0 - 2
          * x
         * */

}
