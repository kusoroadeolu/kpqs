package io.github.kusoroadeolu.cbs.rmq;

import io.github.kusoroadeolu.cbs.utils.VHUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static io.github.kusoroadeolu.cbs.utils.MiscUtils.offset;
import static io.github.kusoroadeolu.cbs.utils.MiscUtils.roundToPowerOfTwo;

class LCircularArrayLPad {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}


class LCircularArray extends LCircularArrayLPad{
    final int[] buffer;
    final long mask;
    static final VarHandle BUFFER = VHUtils.arrayVarHandle(int[].class);

    public LCircularArray(int capacity) {
        int actualCapacity = roundToPowerOfTwo(capacity);
        mask = actualCapacity - 1;
        buffer = new int[actualCapacity];
        for (int i = 0; i < actualCapacity; ++i) {
            buffer[i] = -1;
        }
    }

    void soElem(int[] buf, int index , int i) {
        BUFFER.setRelease(buf, index, i);
    }

    void spElem(int[] buf, int index , int i) {
        BUFFER.set(buf, index, i);
    }

    int lvElem(int[] buf, int index) {
        return (int) BUFFER.getVolatile(buf, index);
    }
}

class LCircularArrayRPad extends LCircularArray {

    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b

    public LCircularArrayRPad(int capacity) {
        super(capacity);
    }
}

class LProducerLimitField extends LCircularArrayRPad {
    long producerLimit;
    static final VarHandle P_LIMIT = VHUtils.fieldVarHandle(MethodHandles.lookup(), LProducerLimitField.class, "producerLimit", long.class);

    LProducerLimitField(int capacity) {
        super(capacity);
    }

    void soProducerLimit(long limit) {
        P_LIMIT.setRelease(this, limit);
    }

    long lvProducerLimit() {
        return (long) P_LIMIT.getVolatile(this);
    }
}


class LProducerRPad extends LProducerLimitField {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    public LProducerRPad(int capacity) {
        super(capacity);
    }
}

class LProducerIndexField extends LProducerRPad {
    long producerIndex;
    static final VarHandle P_INDEX = VHUtils.fieldVarHandle(MethodHandles.lookup(), LProducerIndexField.class, "producerIndex", long.class);

    public LProducerIndexField(int capacity) {
        super(capacity);
    }

    long lvProducerIndex() {
        return (long) P_INDEX.getVolatile(this);
    }

    boolean casProducerIndex(long seen, long newIndex) {
        return P_INDEX.compareAndSet(this, seen, newIndex);
    }
}

class LProducerIndexRPad extends LProducerIndexField {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b

    public LProducerIndexRPad(int capacity) {
        super(capacity);
    }
}

class LConsumerIndexField extends LProducerIndexRPad {

    long consumerIndex;

    static final VarHandle C_INDEX = VHUtils.fieldVarHandle(MethodHandles.lookup(), LConsumerIndexField.class, "consumerIndex", long.class);

    public LConsumerIndexField(int capacity) {
        super(capacity);
    }

    public long lpConsumerIndex() {
        return consumerIndex;
    }

    public long lvConsumerIndex() {
        return (long) C_INDEX.getVolatile(this);
    }

    public void soConsumerIndex(long index) {
        C_INDEX.setRelease(this, index);
    }
}

class LConsumerIndexLPad extends LConsumerIndexField {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    public LConsumerIndexLPad(int capacity) {
        super(capacity);
    }
}


public class MpscLeaderQueue extends LConsumerIndexLPad{

    public MpscLeaderQueue(int capacity) {
        super(capacity);
    }

    public void offer(int value) {
        int[] buffer = this.buffer;
        long mask = this.mask;
        long capacity = mask + 1;

        long pLimit = lvProducerLimit();
        long pIndex;
        long cIndex;

        do {
            pIndex = lvProducerIndex();

            if (pIndex >= pLimit) { //possibly stale pLimit
                cIndex = lvConsumerIndex();
                pLimit = cIndex + capacity; //available slots
                //i.e. cIndex = 0, pIndex = 0; pLimit should == (0 + cap)

                if (pIndex >= pLimit) return;
                else soProducerLimit(pLimit);
            }

        }while (!casProducerIndex(pIndex, pIndex + 1));

        soElem(buffer, offset(pIndex, mask), value);
    }

    //Only correct for single threaded usage
    public int poll() {
        int[] buffer = this.buffer;
        long mask = this.mask;

        long cIndex = lpConsumerIndex();
        int offset = offset(cIndex, mask);

        int elem = lvElem(buffer, offset); //we could use an acquire here no?

        if (elem == -1) {
            if (lvProducerIndex() == cIndex) return -1;
            while ((elem = lvElem(buffer, offset)) == -1) Thread.onSpinWait();
        }

        spElem(buffer, offset, -1);
        soConsumerIndex(cIndex + 1);

        return elem;
    }

    public boolean isEmpty() {
        return (lvProducerIndex() - lvConsumerIndex()) == 0;
    }

}
