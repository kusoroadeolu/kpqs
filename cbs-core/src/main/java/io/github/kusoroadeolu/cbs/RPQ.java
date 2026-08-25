package io.github.kusoroadeolu.cbs;

public interface RPQ<E> {
    boolean offer(E e);

    E poll();

    E peek();

    int size();

    boolean isEmpty();

    void clear();
}
