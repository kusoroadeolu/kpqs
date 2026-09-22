package io.github.kusoroadeolu.cbs;


import java.lang.invoke.VarHandle;
import java.util.Comparator;

import static io.github.kusoroadeolu.cbs.Node.MARKED;


/**
 * @author kusoroadeolu
 * */
//A lock free ordered linked list which supports an api for interacting with SkipPQ
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
    public boolean add(Node<T> node) {
        var l = left;
        var right = this.right;

        var cmp = comparator;

         restartFromLeft: for (;;) {
            var pred = l;
            VarHandle.acquireFence();
            var curr = pred.lpNext();
            for (;;) {
                if (curr.isDummy()) continue restartFromLeft;

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

    public boolean add(T e) {
        return add(new Node<>(0, e));
    }

    public Node<T> findNewTail(Node<T> old) {
            Node<T> largest = null;
            VarHandle.acquireFence(); //do we really need this or will the one placed earlier suffice?
            var curr = left.lpNext();
            for (;;) {
                if (curr.isMarked() || curr.isDummy()) {
                    curr = curr.lpNext(); //Only shift curr
                    continue;
                }

                int res = compare(old, curr, right, comparator);
                if (res < 0) return largest;
                if (curr.id == old.id) largest = curr;
                curr = curr.lpNext();
            }
    }


    // 0 failed (the deleter deleted the node, no need to pull down),
    // 1 succeeded,
    // -1 we landed on a dummy node (our from could be deleted, so we'll need to rescan our local list)
    //reads are acquired transitively through the acq fence placed by the caller
    public Node<T> moveFromLeaderList(Node<T> start, Node<T> largest, SegmentFields<T> segment) {
        var l = start;
        var right = this.right;
        var cmp = comparator;
        Node<T> prevSegmentNode = start;

        restartFromLeft: for (; ;) {
            VarHandle.acquireFence();
            var pred = l;
            var curr = pred.lpNext();

            for (;;) {
                //here an optimization could be used to start from prevSegmentNode instead
                if (curr.isDummy()) {
                    if (pred == start) l = left;
                    continue restartFromLeft;
                }

                if (curr.isMarked()) {
                    curr = helpUnlink(pred, curr);
                    continue;
                }

                if (curr == largest) {
                    boolean marked = curr.casMarked();
                    if (marked) { // here if we fail to mark, a deleter could have set our node to marking, so we need to retry
                        helpUnlink(pred, curr);
                        return prevSegmentNode;
                    }

                    continue restartFromLeft;
                }

                int res = compare(largest, curr, right,cmp);

                //Debug in case this invariant (tail isn't in the list is violated). I intentionally didn't use print stmts here
                if (res < 0) throw new RuntimeException("Invariant violated. This should never happen: %s\n List: %s\nStart: %s\nSize: %s".formatted(largest, nodes(largest.id), start, segment.leaderListSize)); //someone deleted our tail node
                if (largest.id == curr.id) prevSegmentNode = curr;

                pred = curr; curr = pred.lpNext();
            }

        }
    }


    //A strict poll method, fails if left#next changed in between poll
    public Node<T> poll() {
        var pred = left;
        var right = this.right;
        for (; ;) {
            var curr = pred.laNext();

            if (curr == right) return null;

            if (curr.isDummy()) continue; //If we find a dummy node, restart

            if (curr.laMarked()) {
                helpUnlinkAcquire(pred, curr);
                continue;
            }

            boolean marking = curr.casMarking();

            if (marking) {
                Node<T> n = casNextDummyAcquire(curr); //n is the dummy's next so we should
                if (pred.casNext(curr, n)) {
                    return curr; //fails if another node was inserted before the left most node
                }

                curr.svNext(n);
                curr.setNone();
                //first remove the dummy node, then mark our status as none. Doing it in this order is critical to avoid issues
            }

            //if we failed to mark next, just retry
        }
    }

    public String nodes(int id) {
        var l = left;
        var right = this.right;
        StringBuilder sb = new StringBuilder();
        var pred = l;
        var curr = pred.laNext();
        for (; ;) {
            if (curr == right) break;

            if (!curr.isDummy() && curr.id == id) {
                 sb.append("Node: " ).append(curr).append(", ");
            }


            pred = curr; curr = pred.lpNext();
        }

        return sb.toString();
    }

    public String toString() {
        var l = left;
        var right = this.right;
        StringBuilder sb = new StringBuilder();
        var pred = l;
        var curr = pred.laNext();
        for (; ;) {
            if (curr == right) break;

            sb.append("Node: " ).append(curr).append(", ");

            pred = curr; curr = pred.lpNext();
        }

        return sb.toString();
    }


    Node<T> casNextDummy(Node<T> curr) {
        Node<T> next = curr.lpNext();
        Node<T> dummy = allocateDummyNode();

        for (;;) {
            if (next.isDummy()) {
                next = next.lpNext();
                break;
            } else {
                dummy.spNext(next);
                if (curr.casNext(next, dummy)) break;
            }

            next = curr.lpNext();
        }

        return next; //returns the new next
    }

    Node<T> casNextDummyAcquire(Node<T> curr) {
        Node<T> next = curr.laNext();
        Node<T> dummy = allocateDummyNode();

        for (;;) {
            if (next.isDummy()) {
                next = next.laNext();
                break;
            } else {
                dummy.spNext(next);
                if (curr.casNext(next, dummy)) break;
            }

            next = curr.laNext();
        }

        return next; //returns the new next
    }

    Node<T> helpUnlinkAcquire(Node<T> pred, Node<T> curr) {
        Node<T> n = casNextDummyAcquire(curr);
        pred.casNext(curr, n); //try to link. failure is alright, another node has unlinked this , all we need is the new unmarked (at this point) curr node
        return n;
    }

    //Returns the next undead node
    Node<T> helpUnlink(Node<T> pred, Node<T> curr) {
        Node<T> n = casNextDummy(curr);
        pred.casNext(curr, n); //try to link. failure is alright, another node has unlinked this , all we need is the new unmarked (at this point) curr node
        return n;
    }

    static <E>Node<E> allocateDummyNode() {
        return new Node<>(null, MARKED);
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