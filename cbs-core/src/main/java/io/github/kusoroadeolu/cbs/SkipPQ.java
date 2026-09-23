package io.github.kusoroadeolu.cbs;

import io.github.kusoroadeolu.cbs.utils.MiscUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;


/*
* A skip list based priority queue which uses the JDK priority queue as a base (this queue allows duplicates)
* Some optimizations include:
* 1. To prevent unnecessary pointer derefs, we traverse up until key > k, we intentionally don't traverse past duplicates cause of dereferences
*  which isn't an issue if there were no duplicates
*
* 2. This queue borrows an optimization similar though not a 1 to 1 to the Linden-Johnson Priority Queue to batch physical deletions at the head of the queue
* rather than immediately physical deleting a node once it has been logically deleted
*
* 3 This queue also includes elimination as an optimization (borrowed from The Adaptive Priority Queue with Elimination and Combining) allow offers which fail near the head of the queue to cancel
* out polls which fail to mark the left most node
* */
public class SkipPQ<K> implements PQ<K> {

    final Comparator<? super K> comparator;
    /** Lazily initialized topmost index of the skiplist. */
    private transient Index<K> head;

    private static final Object WAITER = new Object();
    private static final int NCPU = Runtime.getRuntime().availableProcessors();
    private static final int ARENA_SIZE = MiscUtils.roundToPowerOfTwo((NCPU + 1) >>> 1);
    private static final int ARENA_MASK = ARENA_SIZE - 1;
    private static final int LOOKAHEAD = Math.min(4, ARENA_SIZE >>> 1);
    private static final int TOTAL_SPINS = 2048;
    private static final int BACKOFF_SPINS = 64;
    private static final int SPINS_PER_SLOT = TOTAL_SPINS / LOOKAHEAD;

    private final ArenaObject[] arena;

    public SkipPQ(Comparator<? super K> comparator) {
        this.comparator = comparator;
        this.arena = new ArenaObject[ARENA_SIZE];

        for (int i = 0; i < ARENA_SIZE; ++i) arena[i] = new ArenaObject();
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

        Node(K key, boolean marked) {
            this.key = key;
            this.marked = marked;
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
            Node<K> p = casMarker(n);
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
        Node<K> node = new Node<>(key, false);
        for (;;) {
            if (doOffer(key, node, null)) return true;
        }
    }

    public boolean offer(K key, ContentionCounter counter) {
        Node<K> node = new Node<>(key, false);
        for (;;) {
            if (doOffer(key, node, counter)) return true;
        }
    }


    boolean doOffer(K key, Node<K> node, ContentionCounter counter) {
        Comparator<? super K> cmp = comparator;
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
                Node<K> n; K k; int c;
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
                } else if ((c = cpr(cmp, key, k)) > 0) //since we allow duplicates, walk only up to duplicates of k, we want to avoid extra pointer derefs
                    b = n;

                // avoid extra derefs due to duplicates
                if (c <= 0) {
                    node.next = n;
                    boolean nearHead = b == h.node;
                    if (counter != null && nearHead) counter.offersNearHead++; //if our predecessor is the left most sentinel node
                    if (NEXT.compareAndSet(b, n, node)) {
                        z = node;
                        break;
                    } else {
                        if (nearHead && tryTransfer(ThreadLocalRandom.current().nextInt(), key)) return true;
                        if (counter != null)  counter.failedOffers++;
                    }
                }
            }

            if (z != null) {
                // add indices with some prob
                int rnd = ThreadLocalRandom.current().nextInt();
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

        return false;
    }


    //augmented version of the JDK's skip list poll, avoid extra cas's next when we find marked nodes
    //rather we batch marked nodes and try to cas them out using a single cas
    @Override
    public K poll() {
        return doPoll(null);
    }

    public K poll(ContentionCounter counter) {
        return doPoll(counter);
    }


    K doPoll(ContentionCounter counter) {
        Node<K> b, n;
        int start = ThreadLocalRandom.current().nextInt();
        if ((b = baseHead()) != null) {
            outer: for (;;) {
                var p = b.next; //initial predecessor
                n = p; //n - next

                if (n == null) break;

                if (n.marked) {
                    for (;;) {
                        if (n != null && n.marked) {
                            n = casMarker(n); //avoid extra cas's to b's next
                            //instead we batch it up into one cas to b's next
                            continue;
                        }

                        NEXT.compareAndSet(b, p, n);
                        continue outer;
                    }

                } else {
                    if (counter != null) counter.pollCases++;

                    if (MARKED.compareAndSet(n, false, true)) {
                        K k = n.key;
                        unlinkNode(b, n);
                        tryReduceLevel();
                        cleanIndices(k, comparator); // clean indices
                        return k;
                    } else {
                        K k = tryMatch(start);

                        if (k != null) return k;

                        if (counter != null) counter.failedPollCas++;
                    }
                }
            }
        }

        return null;
    }



    boolean tryTransfer(int start, K key) {
        return scanAndTransferToWaiter(start, key) || awaitTransfer(start, key);
    }

    K tryMatch(int start) {
        K k;

        if((k = scanAndMatch(start)) != null) return k;

        return awaitMatch(start);
    }



    boolean scanAndTransferToWaiter(int start, K key) {
        ArenaMarker<K> marker = new ArenaMarker<>(key, true);
        for (int i = 0; i < ARENA_SIZE; ++i) {
            int index = (start + i) & ARENA_MASK;
            var o = arena[index];
            if (o.laItem() == WAITER && o.casItem(WAITER, marker)) return true;
        }

        return false;
    }

    boolean awaitTransfer(int start, K key) {
        var marker = new ArenaMarker<>(key, false);
        for (int step = 0, totalSpins = 0; step < ARENA_SIZE && totalSpins < TOTAL_SPINS; ++step) {
            int index = (start + step) & ARENA_MASK;
            var o = arena[index];
            var item = o.laItem();
            if (item == null) {
                if (o.casItem(null, marker)) {
                    for (int spins = 0, backoff = 0;;) {
                        Object seen = o.loItem();
                        if (seen != marker) return true; //a poller claimed our value
                        else if (spins >= SPINS_PER_SLOT && o.casItem(marker, null)) {
                            totalSpins += spins;
                            break;
                        }

                        while (backoff++ < BACKOFF_SPINS) Thread.onSpinWait(); //avoid repeatedly polling shared memory

                        spins += backoff;
                        backoff = 0;
                    }
                }
            } else if (item == WAITER && o.casItem(WAITER, new ArenaMarker<>(key, true))) {
                return true;
            }
        }

        return false;
    }

    K scanAndMatch(int start) {
        for (int i = 0; i < ARENA_SIZE; ++i) {
            int index = (start + i) & ARENA_MASK;
            var o = arena[index];
            var item = o.laItem();
            if (item != null && item != WAITER) {
                ArenaMarker<K> marker = (ArenaMarker<K>) item;
                if (!marker.hasWaiter && o.casItem(item, null)) {
                    return marker.k;
                }
            }
        }

        return null;
    }

    K awaitMatch(int start) {
        for (int step = 0, totalSpins = 0; step < ARENA_SIZE && totalSpins < TOTAL_SPINS; ++step) {
            int index = (start + step) & ARENA_MASK;
            var o = arena[index];
            var item = o.laItem();
            if (item == null) {
                if (o.casItem(null, WAITER)) {
                    for (int spins = 0, backoff = 0;;) {
                        Object seen = o.loItem();

                        if (seen != WAITER) {
                           var k = ((ArenaMarker<K>) seen).k;
                           o.srItem(null);
                           return k;
                        } else if (spins >= SPINS_PER_SLOT && o.casItem(WAITER, null)) {
                            totalSpins += spins;
                            break;
                        }

                        while (backoff++ < BACKOFF_SPINS) Thread.onSpinWait(); //avoid repeatedly polling shared memory

                        spins += backoff;
                        backoff = 0;
                    }
                }
            } else if (item != WAITER) {
                ArenaMarker<K> marker = (ArenaMarker<K>) item;
                if (!marker.hasWaiter && o.casItem(item, null)) {
                    return marker.k;
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
                            if (!(m = n.marked) &&
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


    @SuppressWarnings("unused")
    static class LArenaObject {
        long l1, l2, l3, l4, l5, l6 ,l7 ,l8;
    }

    static class ArenaObjectData extends LArenaObject {
        Object item;

        boolean casItem(Object from, Object to) {
            return ITEM.compareAndSet(this, from, to);
        }

        Object loItem() {
            return ITEM.getOpaque(this);
        }

        Object laItem() {
            return ITEM.getAcquire(this);
        }

        void srItem(Object o) {
            ITEM.setRelease(this, o);
        }
    }

    @SuppressWarnings("unused")
    static class ArenaObject extends ArenaObjectData {
        long l1, l2, l3, l4, l5, l6 ,l7;
    }

    record ArenaMarker<K>(K k, boolean hasWaiter) {
    }

    // VarHandle mechanics
    private static final VarHandle HEAD;
    private static final VarHandle NEXT;
    private static final VarHandle MARKED;
    private static final VarHandle RIGHT;
    private static final VarHandle ITEM;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(SkipPQ.class, "head",
                    Index.class);
            NEXT = l.findVarHandle(Node.class, "next", Node.class);
            MARKED = l.findVarHandle(Node.class, "marked", boolean.class);
            RIGHT = l.findVarHandle(Index.class, "right", Index.class);
            ITEM = l.findVarHandle(ArenaObjectData.class, "item", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}