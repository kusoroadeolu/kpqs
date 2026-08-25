package io.github.kusoroadeolu.cbs.hopper;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Hopper<E extends HopperItem<E>> {
    volatile HopperItem<E> head;
    private final Lock lock;
    private static final VarHandle HEAD = VHUtils.fieldVarHandle(MethodHandles.lookup(), Hopper.class, "head", HopperItem.class);

    public Hopper() {
        this.lock = new ReentrantLock();
    }

    //Returns true if head == null
    public boolean add(E item) {
        E prev = (E) HEAD.getAndSet(this, item);
        if (prev == null) return true;
        prev.next = item;
        return false;
    }

    public Iterator<E> dump(E start) {
        lock.lock();
        E stop = (E) HEAD.getAndSet(this, null);
        return new HopperIterator<>(start, stop);
    }

    public void unlock() {
        lock.unlock();
    }


    static class HopperIterator<E extends HopperItem<E>> implements Iterator<E> {
        E current;
        final E stop;

        public HopperIterator(E start, E stop) {
            this.current = start;
            this.stop = stop;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public E next() {
            E res = current;
            E next = null;
            while (res != stop && (next = res.next) == null) Thread.onSpinWait();
            current = next;
            return res;
        }
    }

}
