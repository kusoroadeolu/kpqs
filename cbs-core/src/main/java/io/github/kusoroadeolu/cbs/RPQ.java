package io.github.kusoroadeolu.cbs;

public interface RPQ<E> {
    boolean add(E e);

    E poll();

    E peek();

    int size();

    boolean isEmpty();

    void clear();
}
