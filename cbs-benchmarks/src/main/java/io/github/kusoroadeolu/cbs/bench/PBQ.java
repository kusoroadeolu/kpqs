package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;

import java.util.concurrent.PriorityBlockingQueue;

public class PBQ<E> implements RPQ<E> {

    private final PriorityBlockingQueue<E> pq = new PriorityBlockingQueue<>();

    @Override
    public boolean offer(E e) {
        return pq.add(e);
    }

    @Override
    public E relaxedPoll() {
        return pq.poll();
    }

    @Override
    public void unsafeClear() {

    }
}
