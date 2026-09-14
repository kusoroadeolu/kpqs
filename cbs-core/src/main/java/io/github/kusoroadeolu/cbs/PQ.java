package io.github.kusoroadeolu.cbs;

public interface PQ<E> {
    boolean offer(E e);

    E poll();

    void unsafeClear();
}
