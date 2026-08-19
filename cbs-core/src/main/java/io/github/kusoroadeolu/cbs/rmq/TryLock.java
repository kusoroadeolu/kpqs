package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class TryLock {
    private static final VarHandle STATE = VHUtils.fieldVarHandle(MethodHandles.lookup(), TryLock.class, "state", int.class);
    volatile int state;
    private static final int FREE = 0;


    public boolean tryLock() {
        return state == FREE && (int) STATE.getAndAdd(this, 1) == FREE;
    }


    public void unlock() {
        STATE.setRelease(this, FREE);
    }

}
