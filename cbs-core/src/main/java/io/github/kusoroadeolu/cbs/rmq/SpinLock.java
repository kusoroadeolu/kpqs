package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

public class SpinLock implements Lock {
    private static final VarHandle STATE = VHUtils.fieldVarHandle(MethodHandles.lookup(), SpinLock.class, "state", int.class);
    volatile int state;
    private static final int FREE = 0;
    private static final int SPINS_BEFORE_PARK = 8;
    private static final int NANOS_PARK_TIME = 1_000_000;

    @Override
    public void lock() {
        for (int spins = 0; !canAcquire(); ++spins) {
            if (canAcquire()) return;
            if (canAcquire()) return;
            if (canAcquire()) return;
            if (canAcquire()) return;
            if (canAcquire()) return;
            if (canAcquire()) return;
            if (canAcquire()) return;
            if (canAcquire()) return;

            if (spins < SPINS_BEFORE_PARK) Thread.onSpinWait();
            else {
                spins = 0;
                LockSupport.parkNanos(NANOS_PARK_TIME);
            }
        }
    }

    public void unlock() {
        STATE.setRelease(this, FREE);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();

    }

    //racy hint, don't trust this
    public boolean isHeld() {
        return loState() != FREE;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    public int loState() {
        return (int) STATE.getOpaque(this);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    //bounded try lock
    public boolean tryLock() {
        return canAcquire();
    }

    public boolean canAcquire() {
        return loState() == FREE && (int) STATE.getAndAdd(this, 1) == FREE;
    }




}
