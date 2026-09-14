package io.github.kusoroadeolu.cbs;

public interface RPQ<E> {
    boolean offer(E e);

    E relaxedPoll();

    void unsafeClear();
}
