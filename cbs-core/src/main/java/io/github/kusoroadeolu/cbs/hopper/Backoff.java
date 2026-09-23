package io.github.kusoroadeolu.cbs.hopper;

import java.util.concurrent.locks.LockSupport;

public class Backoff {

    private static final int SPIN_LIMIT = 6;
    private static final int YIELD_LIMIT = 10;
    private static final int MAX_PARK_NANOS = 1_000_000; //park for 1ms
    private static final int[] PARK_NANOS = {1_000, 10_000, 100_000, MAX_PARK_NANOS};



    /*
    * Exponentially spins before yielding and eventually parks briefly and continuing as so
    * */
    public int snooze(int step) {
        if (step <= SPIN_LIMIT) {
            int spins = 1 << step;
            for (int i = 0; i < spins; i++) Thread.onSpinWait();
        } else if (step <= YIELD_LIMIT){
            Thread.yield();
        } else {
            int idx = step - YIELD_LIMIT - 1;
            LockSupport.parkNanos(PARK_NANOS[idx]);
            if (idx == 3) return 0;
        }

        return ++step;
    }

}
