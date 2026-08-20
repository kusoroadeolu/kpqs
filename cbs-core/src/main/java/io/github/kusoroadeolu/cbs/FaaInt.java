package io.github.kusoroadeolu.cbs;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class FaaInt {
            private static final VarHandle I;
            private volatile int i;

            public FaaInt(int initial) {
                i = initial;
            }

            public FaaInt() {
                this(0);
            }

            public int fetchAndAdd(int i) {
                return (int) I.getAndAdd(this, i);
            }

            public int fetchAndAdd() {
                return fetchAndAdd(1);
            }

            public void setRelease(int i) {
                I.setRelease(this, i);
            }

            public void setVolatile(int i) {
                I.setVolatile(this, i);
            }

            public int getAcquire() {
                return (int) I.getAcquire(this);
            }

            public int getVolatile() {
                return (int) I.getVolatile(this);
            }

            static {
                try {
                    I = MethodHandles.lookup().findVarHandle(FaaInt.class, "i", int.class);
                }catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }