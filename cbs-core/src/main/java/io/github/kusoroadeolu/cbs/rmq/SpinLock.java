package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class SpinLock implements Lock {
    private static final VarHandle STATE = VHUtils.fieldVarHandle(MethodHandles.lookup(), SpinLock.class, "state", int.class);
    volatile int state;
    private static final int FREE = 0;

    @Override
    public void lock() {
        while (!tryLock()) Thread.onSpinWait();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();

    }

    public boolean isHeld() {
        return state != FREE;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    public boolean tryLock() {
        return state == FREE && (int) STATE.getAndAdd(this, 1) == FREE;
    }


    public void unlock() {
        STATE.setRelease(this, FREE);
    }

}
