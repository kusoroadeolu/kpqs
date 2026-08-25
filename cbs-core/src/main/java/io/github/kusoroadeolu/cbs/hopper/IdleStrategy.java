package io.github.kusoroadeolu.cbs.hopper;

import java.util.concurrent.locks.LockSupport;

public interface IdleStrategy {
    int idle(int idleCount);

    static IdleStrategy spin() {
        return new SpinStrategy();
    }

    static IdleStrategy adaptive() {
        return new AdaptiveStrategy();
    }

    class AdaptiveStrategy implements IdleStrategy {
        private static final int MAX_SPINS = 1024;

        @Override
        public int idle(int idleCount) {
            if (idleCount < MAX_SPINS) {
                Thread.onSpinWait();
                return ++idleCount;
            }
            else LockSupport.park();
            return idleCount;
        }
    }

    class SpinStrategy implements IdleStrategy {
        private static final int MAX_SPINS = 1024;

        @Override
        public int idle(int idleCount) {
            Thread.onSpinWait();
            return idleCount;
        }
    }
}
