package io.github.kusoroadeolu.cbs.utils;

import java.util.Comparator;

public class MiscUtils {
    public static final int MAX_POW2 = 1 << 30;

    public static final int NCPU = Runtime.getRuntime().availableProcessors();
    public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;



    public static int roundToPowerOfTwo(final int value) {
        if (value > MAX_POW2) {
            throw new IllegalArgumentException("There is no larger power of 2 int for value:" + value +
                    " since it exceeds 2^31.");
        }

        if (value < 0) {
            throw new IllegalArgumentException("Given value:" + value + ". Expecting value >= 0.");
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    public static int xorShift(int i) {
        int r = i;
        r ^= r << 13;
        r ^= r >>> 7;
        r ^= r << 17;;
        return r;
    }

    public static long xorShift(long i) {
        long r = i;
        r ^= r << 13;
        r ^= r >>> 7;
        r ^= r << 17;;
        return r;
    }



    public static int offset(long index, long mask) {
        return (int) (index & mask);
    }

    public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        // preconditions not checked because of inlining
        // assert oldLength >= 0
        // assert minGrowth > 0

        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
            return prefLength;
        } else {
            // put code cold in a separate method
            return hugeLength(oldLength, minGrowth);
        }
    }

    private static int hugeLength(int oldLength, int minGrowth) {
        int minLength = oldLength + minGrowth;
        if (minLength < 0) { // overflow
            throw new OutOfMemoryError(
                    "Required array length " + oldLength + " + " + minGrowth + " is too large");
        } else return Math.max(minLength, SOFT_MAX_ARRAY_LENGTH);
    }

    public static  <E>E[] allocateArray(int size) {
        return (E[]) new Object[size];
    }

    public static <E> Comparator<? super E> comparator(Comparator<? super E> cmp) {
        if (cmp == null) return (a, b) -> ((Comparable<? super E>) a).compareTo(b);
        return cmp;
    }
}
