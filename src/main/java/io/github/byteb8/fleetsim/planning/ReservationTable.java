package io.github.byteb8.fleetsim.planning;

import io.github.byteb8.fleetsim.util.LongIntMap;
import io.github.byteb8.fleetsim.util.LongList;

import java.util.HashMap;
import java.util.Map;

/**
 * Space-time reservations shared by all agents. A vertex reservation says "agent a is in
 * cell c at tick t"; an edge reservation says "agent a moves from c1 to c2 between t and t+1".
 * Two agents conflict if they share a vertex at the same tick, or traverse the same edge in
 * opposite directions at the same tick (a swap).
 */
public final class ReservationTable {
    public static final int NONE = -1;

    private final LongIntMap vertex = new LongIntMap(1 << 14);
    private final LongIntMap edge = new LongIntMap(1 << 14);
    private final Map<Integer, LongList[]> byAgent = new HashMap<>();

    static long vkey(int cell, int t) {
        return ((long) t << 32) | (cell & 0xFFFFFFFFL);
    }

    static long ekey(int from, int to, int t) {
        return ((long) t << 42) | ((long) from << 21) | to;
    }

    public int ownerAt(int cell, int t) {
        return vertex.get(vkey(cell, t), NONE);
    }

    /** True if no other agent than {@code self} occupies {@code cell} at tick {@code t}. */
    public boolean vertexFree(int cell, int t, int self) {
        int o = vertex.get(vkey(cell, t), NONE);
        return o == NONE || o == self;
    }

    /** True if no other agent moves {@code to -> from} during tick {@code t}, which would be a swap. */
    public boolean edgeFree(int from, int to, int t, int self) {
        int o = edge.get(ekey(to, from, t), NONE);
        return o == NONE || o == self;
    }

    private LongList[] lists(int agent) {
        return byAgent.computeIfAbsent(agent, a -> new LongList[] {new LongList(), new LongList()});
    }

    /** Reserves {@code cells[i]} at tick {@code t0 + i}, and the edges between consecutive distinct cells. */
    public void reservePath(int agent, int[] cells, int t0) {
        LongList[] ls = lists(agent);
        for (int i = 0; i < cells.length; i++) {
            long k = vkey(cells[i], t0 + i);
            vertex.put(k, agent);
            ls[0].add(k);
            if (i + 1 < cells.length && cells[i] != cells[i + 1]) {
                long e = ekey(cells[i], cells[i + 1], t0 + i);
                edge.put(e, agent);
                ls[1].add(e);
            }
        }
    }

    public void reserveStay(int agent, int cell, int t0, int t1) {
        LongList[] ls = lists(agent);
        for (int t = t0; t <= t1; t++) {
            long k = vkey(cell, t);
            vertex.put(k, agent);
            ls[0].add(k);
        }
    }

    /** Drops every reservation held by {@code agent}. */
    public void release(int agent) {
        LongList[] ls = byAgent.get(agent);
        if (ls == null) return;
        for (int i = 0; i < ls[0].size(); i++) {
            long k = ls[0].get(i);
            if (vertex.get(k, NONE) == agent) vertex.remove(k);
        }
        for (int i = 0; i < ls[1].size(); i++) {
            long k = ls[1].get(i);
            if (edge.get(k, NONE) == agent) edge.remove(k);
        }
        ls[0].clear();
        ls[1].clear();
    }

    public void clear() {
        vertex.clear();
        edge.clear();
        for (LongList[] ls : byAgent.values()) {
            ls[0].clear();
            ls[1].clear();
        }
    }

    public int vertexCount() {
        return vertex.size();
    }
}
