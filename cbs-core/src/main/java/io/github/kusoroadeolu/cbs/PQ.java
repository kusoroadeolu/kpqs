package io.github.kusoroadeolu.cbs;

public interface PQ<E> {
    boolean offer(E e);

    E poll();

    E peek();

    int size();

    boolean isEmpty();

    void clear();
}
