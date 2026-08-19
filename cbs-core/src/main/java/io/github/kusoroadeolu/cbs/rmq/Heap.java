package io.github.kusoroadeolu.cbs.rmq;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.allocateArray;
import static io.github.kusoroadeolu.cbs.utils.MiscUtils.newLength;

public interface Heap<E> {
    void add(E e);
    E poll();

    default void drainFrom(SegmentFields.List<E> list) {
        int s = list.size();
        for (int i = 0; i < s; ++i) {
            add(list.valueAt(i)); //try to add, then remove, in the case of an oome when growing the array, we don't lose values from the insert buffer due to oome
            list.removeAt(i);
        }
    }


    static class GrowableArrayHeap<E> implements Heap<E>{
        E[] buffer;
        int size;
        int capacity;
        private static final int INITIAL_HEAP_SIZE = 7;
        final Comparator<? super E> comparator;

        public GrowableArrayHeap(Comparator<? super E> comparator) {
            this.comparator = comparator;
            buffer = allocateArray(INITIAL_HEAP_SIZE);
        }

        public void add(E e) {
            int s = size;
            int c = capacity;
            if (s >= c)
                grow(c);
            siftUp(s, e, buffer, comparator);
            size = s + 1;
        }




        public E poll() {
            final E[] es;
            final E result;

            if ((result = (es = buffer)[0]) != null) {
                final int n;
                final E x = es[(n = --size)];
                es[n] = null;
                if (n > 0) siftDown( x, es, n, comparator);
            }

            return result;
        }



        void grow(int oldCap) {
            int growth = (oldCap < 64)
                    ? (oldCap + 2) // grow faster if small
                    : (oldCap >> 2);
            int newCap = newLength(oldCap, 1, growth);
            E[] b;

            //Handle OOMEs gracefully, to prevent corrupting the structure
            try {
                b = allocateArray(newCap);
            }catch (OutOfMemoryError e) {
                throw new IllegalStateException("Out of memory", e);
            }

            System.arraycopy(buffer, 0, b, 0, size);
            buffer = b;
            capacity = newCap;
        }
    }


    class Treap<E> implements Heap<E>{
        private final TreeSet<E> set;

        public Treap(Comparator<? super E> comparator) {
            this.set = new TreeSet<>(comparator);
        }

        @Override
        public void add(E e) {
            set.add(e);
        }

        @Override
        public E poll() {
            return set.pollFirst();
        }
    }

    class ChunkedHeap<E> implements Heap<E> {

        private Node<E> head;
        private final Comparator<? super E> comparator;

        public ChunkedHeap(Comparator<? super E> comparator) {
            this.comparator = comparator;
        }

        @Override
        public void add(E e) {
            var curr = head;
            var cmp = comparator;
            if (curr == null) {
                curr = (head = allocateNode());
                curr.add(e);
            } else {
                E toAdd = e;
                for (;;) {
                    Node<E> addAt = null;

                    while (true) {
                        var next = curr.next;
                        //add at curr
                        if (cmp.compare(toAdd, curr.peekLast()) < 0) {
                            addAt = curr;
                            break;
                        }

                        //didn't find a node to add our value, create a new node
                        if (next == null) {
                            break;
                        }

                        curr = next;
                    }

                    if (addAt == null) {
                        var newNode = allocateNode();
                        newNode.add(toAdd);
                        curr.next = newNode;
                    } else {
                        E old = curr.add(toAdd);
                        if (old != null) {
                            toAdd = old;
                            continue;
                        }

                    }

                    break;
                }


            }
        }

        Node<E> allocateNode() {
            return new Node<>(comparator);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            var curr = head;
            while (curr != null) {
                sb.append("Node: ").append(Arrays.toString(curr.es.buffer)).append("\n");
                curr = curr.next;
            }

            return sb.toString();
        }

        @Override
        public E poll() {
            var curr = head;
            if (curr == null) return null;
            E value = curr.poll();
            if (curr.isEmpty()) head = curr.next;
            return value;
        }

        static class Node<E> {
            Node<E> next;
            final SortedList.SortedRingBuffer<E> es;
            final Comparator<? super E> comparator;
            final static int CAPACITY = 8;


            public Node(Comparator<? super E> comparator) {
                this.comparator = comparator;
                this.es = new SortedList.SortedRingBuffer<>(allocateArray(CAPACITY), comparator);
            }

            //null if no elem evicted, old es[size - 1] if an elem was evicted
            public E add(E e) {
                E res;
                boolean wasFull = es.isFull();
                return ((res = es.add(e)) == e && !wasFull) ? null : res;
            }


            public E poll() {
                return es.poll();
            }

            public boolean isEmpty() {
                return es.isEmpty();
            }

            public E peekLast() {
                return es.peekLast();
            }


        }
    }


    enum Kind {
        GROWABLE, TREAP;

        public static <E>Heap<E> fromKind(Kind kind, Comparator<? super E> cmp) {
            return switch (kind) {
                case TREAP -> new Treap<>(cmp);
                case GROWABLE -> new GrowableArrayHeap<>(cmp);
            };
        }
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

    static void main() {
        var heap = new ChunkedHeap<Integer>(Comparator.naturalOrder());
        for (int i = 0; i < 50; ++i) {
            heap.add(ThreadLocalRandom.current().nextInt(50));
        }

        System.out.println(heap);
    }


}
