package io.github.kusoroadeolu.cbs.utils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class VHUtils {
    public static VarHandle fieldVarHandle(MethodHandles.Lookup l, Class<?> recv, String name, Class<?> type) {
        try {
           return l.findVarHandle(recv, name, type);
        }catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
