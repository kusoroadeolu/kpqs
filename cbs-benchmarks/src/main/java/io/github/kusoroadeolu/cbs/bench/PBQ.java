package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.PQ;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

public class PBQ<E> implements PQ<E> {

    private final PriorityBlockingQueue<E> pq;

    public PBQ(int initialCapacity) {
        this.pq = new PriorityBlockingQueue<>(initialCapacity);
    }

    public PBQ(int cap, Comparator<E> cmp) {
        this.pq = new PriorityBlockingQueue<>(cap, cmp);
    }

    public PBQ() {
        this.pq = new PriorityBlockingQueue<>();
    }

    @Override
    public boolean offer(E e) {
        return pq.add(e);
    }

    @Override
    public E poll() {
        return pq.poll();
    }

    @Override
    public E peek() {
        return pq.peek();
    }

    @Override
    public int size() {
        return pq.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public void clear() {
        pq.clear();
    }
}
