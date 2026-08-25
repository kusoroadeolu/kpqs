package io.github.kusoroadeolu.cbs.rmq;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentTest {
    static final MpscLeaderQueue queue = new MpscLeaderQueue(100);

    @Test
    void onAdds_assertReturnsMin() {
        Segment<Integer> s = new Segment<>(4, queue, 0, null);
        for (int i = 4; i > 0; --i) {
            s.add(i);
        }

        var i = s.poll();
        Assertions.assertEquals(1, i);
    }

    @Test
    void onMixedPushPop_assertMaintainsMinOrder() {
        Segment<Integer> s = new Segment<>(16, queue, 0, null);
        for (int i = 1; i <= 16; ++i) {
            s.add(i * 3);
        }

        s.add(46); //this should knock out 48 to the ins buffer

        s.poll();

        s.add(49);

        //Add a larger value than 30

        //Here, 48 is in the ins buffer while 49 is in the del buffer

        for (int i = 0; i < 8; ++i) {
            s.poll();
        }

        int high = 50;
        for (int i = 0; i < 8; ++i) {
            s.add(high++);
        }

        s.poll(); //remove one of the min which is less than 48

        s.add(58);

        List<Integer> ls = new ArrayList<>();
        for (int i = 0; i < 7; ++i) {
            ls.add(s.poll());
        }

        //here we should get 49 before 48, a bug!
        Assertions.assertFalse(ls.contains(49));
    }

}