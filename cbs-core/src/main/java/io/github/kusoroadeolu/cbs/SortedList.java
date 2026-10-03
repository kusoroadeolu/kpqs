package io.github.kusoroadeolu.cbs;

import java.util.Arrays;
import java.util.Comparator;

public interface SortedList<E> {
        void offer(E e);
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


        public void offer(E e) {
            int index = binarySearch(e);
            shiftRight(e, index);
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

        void shiftRight(E elem, int index) {
            System.arraycopy(buffer, index, buffer, index + 1, size - index);
            buffer[index] = elem;
        }

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
}
