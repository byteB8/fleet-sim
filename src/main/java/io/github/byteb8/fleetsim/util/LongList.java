package io.github.byteb8.fleetsim.util;

import java.util.Arrays;

/** Growable primitive long list. */
public final class LongList {
    private long[] a = new long[32];
    private int n;

    public void add(long v) {
        if (n == a.length) a = Arrays.copyOf(a, n * 2);
        a[n++] = v;
    }

    public long get(int i) {
        return a[i];
    }

    public int size() {
        return n;
    }

    public void clear() {
        n = 0;
    }
}
