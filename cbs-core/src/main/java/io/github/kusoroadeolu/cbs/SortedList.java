package io.github.kusoroadeolu.cbs;

import java.util.Arrays;
import java.util.Comparator;

public interface SortedList<E> {
        void add(E e);
        Object[] toArray();
        E peekLast();

    //A sorted fixed capacity vector.
    static class SortedBuffer<E> implements SortedList<E>{
        final E[] buffer;
        final Comparator<? super E> comparator;
        final int capacity;
        final int mask;
        int size;

        public SortedBuffer(int capacity, Comparator<? super E> cmp) {
            this.buffer = (E[]) new Object[capacity];
            comparator = cmp;
            this.capacity = capacity;
            mask = capacity - 1;
        }


        public void add(E e) {
            int s = size;
            if (size == capacity)  throw new RuntimeException();

            if (s == 0) {
                buffer[0] = e;
            } else {
                //only fail if full
                int index = binarySearch(e);
                shiftRight(e, index);

            }

            ++size;
        }

        @Override
        public E peekLast() {
            return buffer[size - 1];
        }

        @Override
        public Object[] toArray() {
            return buffer;
        }


        public int size() {
            return size;
        }



        //Returns added if buffer is not full, otherwise returns the previous "last" element
        void shiftRight(E elem, int index) {
            E seen;
            E toAdd = elem;

            for (int i = index; i <= size; i++) {
                seen = buffer[i];
                buffer[i] = toAdd;
                toAdd = seen;
            }
        }



        /*
         * v = cmp (e, i)
         * v > 0 ? e is greater than i
         * v < 0 ? e is less than i
         * returns the raw long index
         * */
        int binarySearch(E elem) {
            int low = 0, high = size;
            int mid;
            while (low < high) {
                mid = (low + high) >>> 1;
                int cmp = comparator.compare(elem, buffer[mid]);
                if (cmp > 0) low = mid + 1;
                else high = mid;
            }

            return low;
        }

        @Override
        public String toString() {
            return Arrays.toString(buffer);
        }
    }

    static void main() {
        var buffer = new SortedBuffer<Integer>(20, Comparator.naturalOrder());
        buffer.add(130);
        buffer.add(48);
        System.out.println(Arrays.toString(buffer.buffer));
    }
}
