package io.github.kusoroadeolu.cbs;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class Node<T> {
        final int id;
        public final T value;
        private int state = NONE;
        static final int NONE = 0;
        static final int MOVING = 1;
        static final int DELETED = 2;
        Node<T> next;
        public Node<T> localNext;

        public Node(int id, T value) {
            this.id = id;
            this.value = value;
        }

        public Node(T value, int state) {
            this.id = -1;
            this.value = value;
            this.state = state;
        }

        boolean isDummy() {
            return value == null;
        }

        public Node<T> lpNext(){
            return next;
        }

        public Node<T> laNext(){
            return (Node<T>) NEXT.getAcquire(this);
        }


    public boolean casNext(Node<T> seen, Node<T> ours) {
            return NEXT.compareAndSet(this, seen, ours);
        }

        public boolean isMarked(){
            return state >= MOVING;
        }

        public boolean loMarked() {
            return (int) STATE.getAcquire(this) >= MOVING;
        }

        public void spNext(Node<T> next) {
            NEXT.set(this, next);
        }

        public boolean casMoving() {
            return STATE.compareAndSet(this, NONE, MOVING);
        }

        public boolean casDeleted() {
            return STATE.compareAndSet(this, NONE, DELETED);
        }

    @Override
    public String toString() {
        return "Value: %s, Marked: %s".formatted(value, isMarked());
    }

    private static final VarHandle NEXT;
        private static final VarHandle STATE;

        static {
            var l = MethodHandles.lookup();
            try {
                NEXT = l.findVarHandle(Node.class, "next", Node.class);
                STATE = l.findVarHandle(Node.class, "state", int.class);
            }catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
}