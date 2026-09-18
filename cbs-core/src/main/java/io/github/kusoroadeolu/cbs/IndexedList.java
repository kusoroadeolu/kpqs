package io.github.kusoroadeolu.cbs;

import io.github.kusoroadeolu.cbs.utils.MiscUtils;

import java.util.Arrays;


public class IndexedList<E> {
        Object[] elements;
        int capacity;
        int size;
        static final int INITIAL_ENTRIES_SIZE = 32;


    public IndexedList() {
            elements = MiscUtils.allocateArray(INITIAL_ENTRIES_SIZE);
            capacity = INITIAL_ENTRIES_SIZE;
        }

        void add(E e) {
            int s = size;


            if (s == capacity) {
                var elems = elements;
                elements = grow();
                System.arraycopy(elems, 0, elements, 0, size);
            }

            elements[s] = e;
            size = s + 1;
        }
        
        void clear() {
            for (int i = 0; i < size; ++i) elements[i] = null;
            size = 0;
        }

        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a, int len) {
            if (len < size) return (T[]) Arrays.copyOf(elements, size, a.getClass());
            System.arraycopy(elements, 0, a, 0, size);
            if (len > size) a[size] = null;
            return a;
        }

        private Object[] grow() {
            int oldCapacity = this.capacity;
            int newCapacity = oldCapacity + INITIAL_ENTRIES_SIZE;
            capacity = newCapacity;
            return MiscUtils.allocateArray(newCapacity);
        }
    }