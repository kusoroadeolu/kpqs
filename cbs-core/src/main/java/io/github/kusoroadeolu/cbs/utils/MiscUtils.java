package io.github.kusoroadeolu.cbs.utils;

public class MiscUtils {
    public static final int MAX_POW2 = 1 << 30;

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

    public static  <E>E[] allocateArray(int size) {
        return (E[]) new Object[size];
    }
}
