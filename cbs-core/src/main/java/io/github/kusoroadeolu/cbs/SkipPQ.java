package io.github.kusoroadeolu.cbs;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;


public class SkipPQ<K> implements PQ<K> {

    final Comparator<? super K> comparator;
    /** Lazily initialized topmost index of the skiplist. */
    private transient Index<K> head;


    public SkipPQ(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    static final class Node<K> {
        final K key; // currently, never detached
        boolean marked;
        Node<K> next;
        Node(K key, boolean marked ,Node<K> next) {
            this.key = key;
            this.marked = marked;
            this.next = next;
        }
    }

    /**
     * Index nodes represent the levels of the skip list.
     */
    static final class Index<K> {
        final Node<K> node;  // currently, never detached
        final Index<K> down;
        Index<K> right;
        Index(Node<K> node, Index<K> down, Index<K> right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }
    }

    static int cpr(Comparator c, Object x, Object y) {
        return (c != null) ? c.compare(x, y) : ((Comparable)x).compareTo(y);
    }

    /**
     * Returns the header for base node list, or null if uninitialized
     */
    final Node<K> baseHead() {
        Index<K> h;
        VarHandle.acquireFence();
        return ((h = head) == null) ? null : h.node;
    }

    static <K> void unlinkNode(Node<K> b, Node<K> n) {
        if (b != null && n != null) {
            Node<K> f, p;
            for (;;) {
                if ((f = n.next) != null && f.key == null) {
                    p = f.next;               // already marked
                    break;
                } else if (NEXT.compareAndSet(n, f, new Node<>(null, true, f))) {
                    p = f;                    // add marker
                    break;
                }
            }

            NEXT.compareAndSet(b, n, p);
        }
    }

    static <K> Node<K> casMarker(Node<K> n) {
        Node<K> f, p;
        for (;;) {
            if ((f = n.next) != null && f.key == null) {
                p = f.next;               // already marked
                return p;
            } else if (NEXT.compareAndSet(n, f, new Node<>(null, true, f))) {
                p = f;                    // add marker
                return p;
            }
        }
    }

    private void cleanIndices(Object key, Comparator<? super K> cmp) {
        Index<K> q;
        VarHandle.acquireFence();
        if ((q = head) != null && key != null) {
            for (Index<K> r, d;;) {
                while ((r = q.right) != null) {
                    Node<K> p; K k;
                    if ((p = r.node) == null || (k = p.key) == null ||
                            p.marked)  // unlink index to deleted node
                        RIGHT.compareAndSet(q, r, r.right);
                    else if (cpr(cmp, key, k) >= 0) //walk until key < k, since this queue allows duplicates, to ensure we don't leave stale indices
                        q = r;
                    else
                        break;
                }
                if ((d = q.down) != null)
                    q = d;
                else
                    return;
            }
        }

    }

    public boolean offer(K key) {
        Comparator<? super K> cmp = comparator;
        for (;;) {
            Index<K> h; Node<K> b;
            VarHandle.acquireFence();
            int levels = 0;                    // number of levels descended
            if ((h = head) == null) {          // try to initialize
                Node<K> base = new Node<>(null, false, null);
                h = new Index<>(base, null, null);
                b = (HEAD.compareAndSet(this, null, h)) ? base : null;
            }
            else {
                for (Index<K> q = h, r, d;;) { // count while descending
                    while ((r = q.right) != null) {
                        Node<K> p; K k;
                        if ((p = r.node) == null || (k = p.key) == null ||
                                p.marked)
                            RIGHT.compareAndSet(q, r, r.right);
                        else if (cpr(cmp, key, k) > 0)
                            q = r;
                        else
                            break;
                    }

                    if ((d = q.down) != null) {
                        ++levels;
                        q = d;
                    }
                    else {
                        b = q.node;
                        break;
                    }
                }
            }
            if (b != null) {
                Node<K> z = null;              // new node, if inserted
                for (;;) {                       // find insertion point
                    Node<K> n, p; K k; int c;
                    if ((n = b.next) == null) {
                        if (b.key == null)       // if empty, type check key now
                            cpr(cmp, key, key);
                        c = -1;
                    }
                    else if ((k = n.key) == null)
                        break;                   // can't append; restart
                    else if (n.marked) {
                        unlinkNode(b, n);
                        c = 1;
                    }
                    else if ((c = cpr(cmp, key, k)) > 0) //since we allow duplicates, walk only up to duplicates of k, we want to avoid extra pointer derefs
                        b = n;
                    if (c <= 0 && // avoid extra derefs due to duplicates
                            NEXT.compareAndSet(b, n,
                                    p = new Node<>(key, false, n))) {
                        z = p;
                        break;
                    }
                }

                if (z != null) {
                    // add indices with some prob
                        long rnd = ThreadLocalRandom.current().nextLong();
                        int skips = levels;
                        Index<K> x = null;
                        for (;;) {
                            x = new Index<>(z, x, null);
                            if (rnd >= 0L || --skips < 0)
                                break;
                            else
                                rnd <<= 1;
                        }
                        if (addIndices(h, skips, x, cmp) && skips < 0 &&
                                head == h) {         // try to add new level
                            Index<K> hx = new Index<>(z, x, null);
                            Index<K> nh = new Index<>(h.node, h, hx);
                            HEAD.compareAndSet(this, h, nh);
                        }
                        if (z.marked)       // deleted while adding indices
                            cleanIndices(key, cmp); // clean

                    return true;
                }
            }
        }
    }


    //augmented version of the JDK's skip list poll, avoid extra cas's next when we find marked nodes
    //rather we batch marked nodes and try to cas them out using a single cas
    @Override
    public K poll() {
        Node<K> b, n, p;
        if ((b = baseHead()) != null) {
            for (;;) {
                p = b.next; //initial predecessor
                n = p; //n - next
                if (n == null) break;

                for (;;) {
                    if (n != null && n.marked) {
                        n = casMarker(n); //avoid extra cas's on unlink
                        continue;
                    }

                    NEXT.compareAndSet(b, p, n);
                    break;
                }

                if (n == null) return null;

                if (MARKED.compareAndSet(n, false, true)) {
                    K k = n.key;
                    unlinkNode(b, n);
                    tryReduceLevel();
                    cleanIndices(k, comparator); // clean indices
                    return k;
                }
            }

        }
        return null;
    }

    @Override
    public void unsafeClear() {
            Index<K> h, r, d; Node<K> b; boolean m;
            VarHandle.acquireFence();
            while ((h = head) != null) {
                if ((r = h.right) != null)        // remove indices
                    RIGHT.compareAndSet(h, r, null);
                else if ((d = h.down) != null)    // remove levels
                    HEAD.compareAndSet(this, h, d);
                else {
                    long count = 0L;
                    if ((b = h.node) != null) {    // remove nodes
                        Node<K> n;
                        while ((n = b.next) != null) {
                            if (!(m = n.marked)&&
                                    MARKED.compareAndSet(n, false, true)) {
                                --count;
                            }
                            if (m)
                                unlinkNode(b, n);
                        }
                    }
                    if (count == 0L) break;
                }
            }
    }

    private void tryReduceLevel() {
        Index<K> h, d, e;
        if ((h = head) != null && h.right == null &&
                (d = h.down) != null && d.right == null &&
                (e = d.down) != null && e.right == null &&
                HEAD.compareAndSet(this, h, d) &&
                h.right != null)   // recheck
            HEAD.compareAndSet(this, d, h);  // try to backout
    }


    static <K> boolean addIndices(Index<K> q, int skips, Index<K> x,
                                    Comparator<? super K> cmp) {
        Node<K> z; K key;
        if (x != null && (z = x.node) != null && (key = z.key) != null &&
                q != null) {                            // hoist checks
            boolean retrying = false;
            for (;;) {                              // find splice point
                Index<K> r, d; int c;
                if ((r = q.right) != null) {
                    Node<K> p; K k;
                    if ((p = r.node) == null || (k = p.key) == null ||
                            p.marked) {
                        RIGHT.compareAndSet(q, r, r.right);
                        c = 0;
                    }
                    else if ((c = cpr(cmp, key, k)) > 0) {
                        q = r;
                    }
                } else {
                    c = -1;
                }

                if (c <= 0) {
                    if ((d = q.down) != null && skips > 0) {
                        --skips;
                        q = d;
                    }
                    else if (d != null && !retrying &&
                            !addIndices(d, 0, x.down, cmp))
                        break;
                    else {
                        x.right = r;
                        if (RIGHT.compareAndSet(q, r, x))
                            return true;
                        else
                            retrying = true;         // re-find splice point
                    }
                }
            }
        }
        return false;
    }


    // VarHandle mechanics
    private static final VarHandle HEAD;
    private static final VarHandle NEXT;
    private static final VarHandle MARKED;
    private static final VarHandle RIGHT;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(SkipPQ.class, "head",
                    Index.class);
            NEXT = l.findVarHandle(Node.class, "next", Node.class);
            MARKED = l.findVarHandle(Node.class, "marked", boolean.class);
            RIGHT = l.findVarHandle(Index.class, "right", Index.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}