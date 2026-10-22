package io.github.byteb8.fleetsim.util;

import java.util.Arrays;

/**
 * Open-addressing hash map from non-negative long keys to int values: linear probing,
 * Fibonacci hashing, backward-shift deletion, at most half full. No boxing on the hot path.
 */
public final class LongIntMap {
    private static final long EMPTY = -1L;

    private long[] keys;
    private int[] vals;
    private int mask;
    private int size;

    public LongIntMap() {
        this(1024);
    }

    public LongIntMap(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
        keys = new long[cap];
        vals = new int[cap];
        Arrays.fill(keys, EMPTY);
        mask = cap - 1;
    }

    public int size() {
        return size;
    }

    private int home(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - Integer.numberOfTrailingZeros(keys.length))) & mask;
    }

    /** Returns the value for {@code key}, or {@code missing} if absent. */
    public int get(long key, int missing) {
        int i = home(key);
        while (true) {
            long k = keys[i];
            if (k == key) return vals[i];
            if (k == EMPTY) return missing;
            i = (i + 1) & mask;
        }
    }

    public boolean containsKey(long key) {
        return slotOf(key) >= 0;
    }

    private int slotOf(long key) {
        int i = home(key);
        while (true) {
            long k = keys[i];
            if (k == key) return i;
            if (k == EMPTY) return -1;
            i = (i + 1) & mask;
        }
    }

    public void put(long key, int value) {
        if (key < 0) throw new IllegalArgumentException("negative key");
        if ((size + 1) * 2 > keys.length) grow();
        int i = home(key);
        while (true) {
            long k = keys[i];
            if (k == key) {
                vals[i] = value;
                return;
            }
            if (k == EMPTY) {
                keys[i] = key;
                vals[i] = value;
                size++;
                return;
            }
            i = (i + 1) & mask;
        }
    }

    /** Removes {@code key} if present, shifting later entries of the probe run back into place. */
    public void remove(long key) {
        int i = slotOf(key);
        if (i < 0) return;
        int j = i;
        while (true) {
            j = (j + 1) & mask;
            long kj = keys[j];
            if (kj == EMPTY) break;
            int h = home(kj);
            boolean between = (i <= j) ? (i < h && h <= j) : (i < h || h <= j);
            if (between) continue;
            keys[i] = kj;
            vals[i] = vals[j];
            i = j;
        }
        keys[i] = EMPTY;
        size--;
    }

    public void clear() {
        Arrays.fill(keys, EMPTY);
        size = 0;
    }

    private void grow() {
        long[] ok = keys;
        int[] ov = vals;
        keys = new long[ok.length * 2];
        vals = new int[ok.length * 2];
        Arrays.fill(keys, EMPTY);
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) if (ok[i] != EMPTY) put(ok[i], ov[i]);
    }
}
