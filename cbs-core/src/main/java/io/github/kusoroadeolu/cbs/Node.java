package io.github.kusoroadeolu.cbs;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class Node<T> {
        final int id;
        public final T value;
        int state = NONE;
        static final int NONE = 0;
        static final int MARKED = 1;
        static final int MARKING = 2;
        Node<T> next;

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


        public void svNext(Node<T> next) {
            NEXT.setVolatile(this, next);
        }

        public boolean isMarked(){
            return state == MARKED;
        }

        public boolean laMarked() {
            return (int) STATE.getAcquire(this) == MARKED;
        }

        public void spNext(Node<T> next) {
            NEXT.set(this, next);
        }

        public boolean casMarked() {
            return STATE.compareAndSet(this, NONE, MARKED);
        }

        public boolean casMarking() {
            return STATE.compareAndSet(this, NONE, MARKING);
        }

        public void setNone() {
            STATE.setVolatile(this, NONE);
        }

    @Override
    public String toString() {
        return "Value: %s, Marked: %s".formatted(value, (int) STATE.getVolatile(this));
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