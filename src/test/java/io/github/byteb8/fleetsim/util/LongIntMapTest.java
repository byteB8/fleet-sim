package io.github.byteb8.fleetsim.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class LongIntMapTest {
    @Test
    void agreesWithHashMapUnderRandomOperations() {
        LongIntMap m = new LongIntMap(16);
        Map<Long, Integer> ref = new HashMap<>();
        SplittableRandom rnd = new SplittableRandom(7);
        for (int i = 0; i < 200_000; i++) {
            long key = rnd.nextInt(5000);
            int op = rnd.nextInt(3);
            if (op < 2) {
                int v = rnd.nextInt(1000);
                m.put(key, v);
                ref.put(key, v);
            } else {
                m.remove(key);
                ref.remove(key);
            }
            if (i % 997 == 0) {
                assertEquals(ref.size(), m.size());
                for (long k = 0; k < 5000; k++) {
                    assertEquals(ref.getOrDefault(k, -7), m.get(k, -7), "key " + k);
                }
            }
        }
    }

    @Test
    void removeOfMissingKeyIsNoOp() {
        LongIntMap m = new LongIntMap();
        m.put(5, 1);
        m.remove(6);
        assertEquals(1, m.size());
        assertEquals(1, m.get(5, -1));
        m.clear();
        assertEquals(0, m.size());
        assertEquals(-1, m.get(5, -1));
    }
}
