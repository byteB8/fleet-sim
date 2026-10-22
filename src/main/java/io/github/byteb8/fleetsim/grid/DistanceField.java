package io.github.byteb8.fleetsim.grid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Shortest-path distances on the static map, by breadth-first search. */
public final class DistanceField {
    public static final int UNREACHABLE = Integer.MAX_VALUE;

    private final GridMap map;
    private final Map<Integer, int[]> toGoalCache = new HashMap<>();

    public DistanceField(GridMap map) {
        this.map = map;
    }

    /** Distance from every cell to {@code goal}, following one-way rules (reverse BFS). Cached. */
    public int[] toGoal(int goal) {
        return toGoalCache.computeIfAbsent(goal, g -> computeToGoal(map, g));
    }

    public static int[] computeToGoal(GridMap m, int goal) {
        int n = m.size();
        int[] dist = new int[n];
        Arrays.fill(dist, UNREACHABLE);
        int[] queue = new int[n];
        int head = 0, tail = 0;
        dist[goal] = 0;
        queue[tail++] = goal;
        while (head < tail) {
            int u = queue[head++];
            int ux = m.x(u), uy = m.y(u);
            for (Dir d : Dir.ALL) {
                int vx = ux - d.dx, vy = uy - d.dy;
                if (!m.inBounds(vx, vy)) continue;
                int v = m.index(vx, vy);
                if (dist[v] != UNREACHABLE || !m.traversable(v)) continue;
                if (m.step(v, d) != u) continue;
                dist[v] = dist[u] + 1;
                queue[tail++] = v;
            }
        }
        return dist;
    }

    /** Distance from {@code start} to every cell (forward BFS). Not cached. */
    public static int[] computeFromStart(GridMap m, int start) {
        int n = m.size();
        int[] dist = new int[n];
        Arrays.fill(dist, UNREACHABLE);
        int[] queue = new int[n];
        int head = 0, tail = 0;
        dist[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int u = queue[head++];
            for (Dir d : Dir.ALL) {
                int v = m.step(u, d);
                if (v < 0 || dist[v] != UNREACHABLE) continue;
                dist[v] = dist[u] + 1;
                queue[tail++] = v;
            }
        }
        return dist;
    }
}
