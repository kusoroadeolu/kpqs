package io.github.kusoroadeolu.cbs;


import java.lang.invoke.VarHandle;
import java.util.Comparator;

import static io.github.kusoroadeolu.cbs.Node.DELETED;


/**
 * @author kusoroadeolu
 * */
//A lock free ordered linked list which supports an api for interacting with PIPQ
public class LeaderList<T> {
    private final Node<T> left;
    private final Node<T> right;
    private final Comparator<Node<T>> comparator;

    public LeaderList(Comparator<? super T> comparator) {
        this.left = new SentinelNode<>();
        this.right = new SentinelNode<>();
        left.next = right;
        VarHandle.releaseFence();
        this.comparator = (a, b) -> {
            int cmp = comparator.compare(a.value, b.value);
            if (a.id == b.id && cmp == 0) return 0;
            else return cmp;
        } ;
    }

    //reads are acquired transitively through an acq fence placed by the caller
    public boolean addFrom(Node<T> start, Node<T> node) {
        var l = start == null ? left : start;
        var right = this.right;

        var cmp = comparator;

         restartFromLeft: for (;;) {
            var pred = l;
            var curr = pred.lpNext();
            for (;;) {
                if (curr.isDummy()) {
                    if (pred == l) return false;
                    continue restartFromLeft;
                }

                if (curr.isMarked()) {
                    curr = helpUnlink(pred, curr); //Only shift curr
                    continue;
                }

                int res = compare(node, curr, right,cmp);

                if (res > 0) pred = curr;
                else {
                    //set curr = next; backed by volatile cas
                    node.spNext(curr);
                    if (pred.casNext(curr, node)) return true; //Linearization point
                }

                curr = pred.lpNext();
            }
        }
    }


    // 0 failed (the deleter deleted the node, no need to pull down),
    // 1 succeeded,
    // -1 we landed on a dummy node (our from could be deleted, so we'll need to rescan our local list)
    //reads are acquired transitively through the acq fence
    public int moveFromLeaderList(Node<T> start, Node<T> node) {
        var l = start == null ? left : start;
        var right = this.right;
        var cmp = comparator;

        restartFromLeft: for (; ;) {
            var pred = l;
            var curr = pred.lpNext();

            for (;;) {
                if (curr.isDummy()) {
                    if (pred == l) return -1;
                    continue restartFromLeft;
                }

                if (curr.isMarked()) {
                    curr = helpUnlink(pred, curr);
                    continue;
                }

                int res = compare(node, curr, right,cmp);
                if (res < 0) return 0; //someone deleted our node
                else if (res == 0) {
                    boolean marked = curr.casMoving();
                    helpUnlink(pred, curr);
                    return marked ? 1 : 0;
                }

                pred = curr; curr = pred.lpNext();
            }

        }
    }

    public Node<T> poll() {
        var l = left;
        var right = this.right;
        restartFromLeft: for (; ;) {
            VarHandle.acquireFence(); //next reads are acquired transitively through this fence
            var pred = l;
            var curr = pred.lpNext();

            for (;;) {
                if (curr.isDummy()) {
                    continue restartFromLeft; //If we find a dummy node, restart from left
                }

                if (curr.isMarked()) {
                    curr = helpUnlink(pred, curr);
                    continue;
                }

                if (curr == right) return null;

                boolean marked = curr.casDeleted();
                helpUnlink(pred, curr);
                if (marked) return curr;
                else continue restartFromLeft;
            }

        }
    }

    //Returns the next undead node
    Node<T> helpUnlink(Node<T> pred, Node<T> curr) {
        Node<T> n = curr.lpNext();
        Node<T> d = allocateDummyNode();

        for (;;) {
            if (n.isDummy()) {
                n = n.lpNext();
                break;
            } else {
                d.spNext(n);
                if (curr.casNext(n, d)) break;
            }

            n = curr.lpNext();
        }

        pred.casNext(curr, n); //try to link. failure is alright, another node has unlinked this , all we need is the new unmarked (at this point) curr node
        return n;
    }

    static <E>Node<E> allocateDummyNode() {
        return new Node<>(null, DELETED);
    }

    int compare(Node<T> node, Node<T> curr, Node<T> right, Comparator<Node<T>> comparator) {
        if (curr == right) return -1;       // right sentinel, stop
        return comparator.compare(node, curr);
    }


    static class SentinelNode<T> extends Node<T> {

        public SentinelNode() {
            super(-1, null);
        }

        @Override
        boolean isDummy() {
            return false;
        }
    }
}