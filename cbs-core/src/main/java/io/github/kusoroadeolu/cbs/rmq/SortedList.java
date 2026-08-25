package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.MiscUtils;

import java.util.Arrays;
import java.util.Comparator;

public interface SortedList<E> {
        E add(E e);
        E peekLast();
        int size();
        E poll();
        void removeLast();
        void clear();
        static final Object ADDED = new Object();

        static <E>E added() {
            return (E) ADDED;
        }

    boolean isEmpty();

    //A sorted fixed capacity vector.
    static class SortedRingBuffer<E> implements SortedList<E>{
        final E[] buffer;
        final Comparator<? super E> comparator;
        final int capacity;
        final int mask;
        long pIndex;
        long cIndex;

        public SortedRingBuffer(E[] buffer, Comparator<? super E> cmp) {
            this.buffer = buffer;
            comparator = cmp;
            capacity = buffer.length;
            mask = capacity - 1;
        }

        /*
         * Returns e if successfully added and buffer wasn't full
         * Returns null if failed to add
         * Returns previous maximum if successfully added and buffer was full
         * */
        public E add(E e) {
            int s = size();

            E res;
            if (s == 0) {
                buffer[offset(pIndex++)] = e;
                res = added();
            } else {
                long index = findInsertionPoint(e);
                if (index == -1) return null; //failed to insert (e > buffer[size -1])
                res = shiftRight(e, index);
                if (!isFull()) ++pIndex;

            }

            return res;
        }



        public E peekLast() {
            return buffer[offset(pIndex - 1)];
        }

        public int size() {
            return (int) (pIndex - cIndex);
        }

        public E poll() {
            if (isEmpty()) return null;
            int offset = offset(cIndex);
            E first = buffer[offset];
            buffer[offset] = null;
            cIndex++;
            return first;
        }

        public void removeLast() {
            if (isEmpty()) return;
            int offset = offset(pIndex - 1);
            buffer[offset] = null;
            pIndex--;
        }

        //Returns added if buffer is not full, otherwise returns the previous "last" element
        E shiftRight(E elem, long index) {
            long pIndex = this.pIndex;
            boolean full = isFull();
            long limit = full ? pIndex : pIndex + 1;
            E seen = null;
            E toAdd = elem;

            for (long i = index; i < limit; i++) {
                int offset = offset(i);
                seen = buffer[offset];
                buffer[offset] = toAdd;
                toAdd = seen;
            }

            return full ? seen : added();
        }


        long findInsertionPoint(E elem) {
            if (comparator.compare(elem, buffer[offset(pIndex - 1)]) >= 0) return -1; // (>) too large
            // (==) don't remove the last elem if it's == elem, otherwise for primitives if we evict a value == e and return e
            // when checking if we should add the returned value to the ins buffer in the segment queue,
            return binarySearch(elem);
        }

        boolean isFull() {
            return size() == capacity;
        }

        public boolean isEmpty() {
            return pIndex == cIndex;
        }

        int offset(long index) {
            return MiscUtils.offset(index, mask);
        }


        /*
         * v = cmp (e, i)
         * v > 0 ? e is greater than i
         * v < 0 ? e is less than i
         * returns the raw long index
         * */
        long binarySearch(E elem) {
            long low = cIndex, high = pIndex;
            long mid;
            while (low < high) {
                mid = (low + high) >>> 1;
                int cmp = comparator.compare(elem, buffer[offset(mid)]);
                if (cmp > 0) low = mid + 1;
                else high = mid;
            }

            return low;
        }

        public void clear() {
            for (int i = 0; i < (mask + 1); ++i) {
                buffer[i] = null;
            }

            pIndex = 0;
            cIndex = 0;
        }

        @Override
        public String toString() {
            return Arrays.toString(buffer);
        }
    }
}

