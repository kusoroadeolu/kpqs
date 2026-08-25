package io.github.kusoroadeolu.cbs.hopper;

public abstract class HopperItem<E extends HopperItem<E>> {

    //allow us to walk upward from our node so we have a FIFO-ish structure rather than lifo
    //could use weaker memory modes but honestly doesnt matter yet
    volatile E next;
    volatile boolean applied;

    public boolean isApplied() {
        return applied;
    }

    protected void apply() {
        applied = true;
    }
}
