package io.github.byteb8.fleetsim.planning;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.Dir;
import io.github.byteb8.fleetsim.grid.GridMap;

import java.util.Arrays;

/**
 * Windowed space-time A* for one agent against a {@link ReservationTable}.
 * States are (cell, k) with k = t - t0 in [0, window]. Moving and waiting both cost one tick,
 * so g = k and every state is settled the first time it is reached. The heuristic is the true
 * static distance to the goal, so the search is admissible and consistent.
 *
 * The result holds the agent's cell at t0, t0+1, ..., t0+window. If the goal is reached and
 * stays free for the rest of the window, the agent parks there; otherwise the path stops at the
 * horizon at the node with the smallest f, which is the standard WHCA* partial plan.
 */
public final class SpaceTimeAStar {
    private final GridMap map;
    private final int nCells;

    private int[] stamp = new int[0];
    private int[] parent = new int[0];
    private int generation;
    private long[] heap = new long[1024];
    private int heapSize;

    private long expansions;

    public SpaceTimeAStar(GridMap map) {
        this.map = map;
        this.nCells = map.size();
    }

    public long expansions() {
        return expansions;
    }

    /**
     * @param distToGoal static distance field towards {@code goal}
     * @return cells at t0..t0+window, or null if even waiting in place is impossible
     */
    public int[] plan(ReservationTable table, int agent, int start, int goal, int t0, int window, int[] distToGoal) {
        if (distToGoal[start] == DistanceField.UNREACHABLE) return null;
        int states = nCells * (window + 1);
        if (stamp.length < states) {
            stamp = new int[states];
            parent = new int[states];
            generation = 0;
        }
        generation++;
        if (generation == Integer.MAX_VALUE) {
            Arrays.fill(stamp, 0);
            generation = 1;
        }
        heapSize = 0;

        int startKey = start;
        stamp[startKey] = generation;
        parent[startKey] = -1;
        push(distToGoal[start], window, startKey);

        while (heapSize > 0) {
            long e = pop();
            int key = (int) (e & 0xFFFFFFFFL);
            int k = key / nCells;
            int cell = key - k * nCells;
            int t = t0 + k;
            expansions++;

            if (k == window) return reconstruct(key, window);
            if (cell == goal && goalFreeAfter(table, agent, goal, t, t0 + window)) {
                int[] path = reconstruct(key, window);
                for (int i = k + 1; i <= window; i++) path[i] = goal;
                return path;
            }

            int nk = k + 1;
            int nt = t + 1;
            // wait
            if (table.vertexFree(cell, nt, agent)) relax(cell, nk, key, distToGoal[cell], window);
            for (Dir d : Dir.ALL) {
                int to = map.step(cell, d);
                if (to < 0 || distToGoal[to] == DistanceField.UNREACHABLE) continue;
                // Stations and bays are entered only as a destination, never as a pull-over pocket.
                if (to != goal && map.type(to) != GridMap.FREE) continue;
                if (!table.vertexFree(to, nt, agent)) continue;
                if (!table.edgeFree(cell, to, t, agent)) continue;
                relax(to, nk, key, distToGoal[to], window);
            }
        }
        return null;
    }

    private boolean goalFreeAfter(ReservationTable table, int agent, int goal, int t, int tEnd) {
        for (int u = t + 1; u <= tEnd; u++) if (!table.vertexFree(goal, u, agent)) return false;
        return true;
    }

    private void relax(int cell, int k, int parentKey, int h, int window) {
        int key = k * nCells + cell;
        if (stamp[key] == generation) return;
        stamp[key] = generation;
        parent[key] = parentKey;
        push(k + h, window - k, key);
    }

    private int[] reconstruct(int key, int window) {
        int[] path = new int[window + 1];
        Arrays.fill(path, -1);
        int cur = key;
        while (cur >= 0) {
            int k = cur / nCells;
            path[k] = cur - k * nCells;
            cur = parent[cur];
        }
        return path;
    }

    // Heap entries: f in the top bits, then (window - k) so that ties prefer deeper nodes, then the key.
    private void push(int f, int tie, int key) {
        long e = ((long) f << 44) | ((long) (tie & 0xFFF) << 32) | (key & 0xFFFFFFFFL);
        if (heapSize == heap.length) heap = Arrays.copyOf(heap, heapSize * 2);
        int i = heapSize++;
        while (i > 0) {
            int p = (i - 1) >>> 1;
            if (heap[p] <= e) break;
            heap[i] = heap[p];
            i = p;
        }
        heap[i] = e;
    }

    private long pop() {
        long top = heap[0];
        long e = heap[--heapSize];
        int i = 0;
        while (true) {
            int l = 2 * i + 1;
            if (l >= heapSize) break;
            int r = l + 1;
            int c = (r < heapSize && heap[r] < heap[l]) ? r : l;
            if (heap[c] >= e) break;
            heap[i] = heap[c];
            i = c;
        }
        if (heapSize > 0) heap[i] = e;
        return top;
    }
}
