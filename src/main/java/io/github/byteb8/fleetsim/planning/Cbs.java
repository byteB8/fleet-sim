package io.github.byteb8.fleetsim.planning;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.Dir;
import io.github.byteb8.fleetsim.grid.GridMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Conflict-based search (Sharon et al., 2015): optimal multi-agent path finding by sum of costs.
 * The high level searches a tree of constraints; the low level is space-time A* for one agent
 * under vertex and edge constraints. Agents stay at their goal once they arrive, and a path may
 * only end at the goal after the last vertex constraint on that goal cell.
 *
 * It is exponential in the number of conflicts, so it is used here only as the optimal
 * reference for small instances, to measure how far the prioritized planner is from optimal.
 */
public final class Cbs {
    private final GridMap map;
    private final DistanceField distances;
    private long highLevelExpansions;
    private long lowLevelExpansions;

    public Cbs(GridMap map, DistanceField distances) {
        this.map = map;
        this.distances = distances;
    }

    public long highLevelExpansions() {
        return highLevelExpansions;
    }

    public long lowLevelExpansions() {
        return lowLevelExpansions;
    }

    private static final class Node {
        final Node parent;
        final int agent;
        final long key;
        final boolean edge;
        final int[][] paths;
        final int cost;

        Node(Node parent, int agent, long key, boolean edge, int[][] paths, int cost) {
            this.parent = parent;
            this.agent = agent;
            this.key = key;
            this.edge = edge;
            this.paths = paths;
            this.cost = cost;
        }
    }

    /** Sum of costs of a solution: each agent's arrival time at its goal, after which it stays. */
    public static int sumOfCosts(int[][] paths) {
        int s = 0;
        for (int[] p : paths) s += p.length - 1;
        return s;
    }

    /**
     * @return one path per agent, ending at its goal (the agent stays there afterwards), or null
     *         if no solution was found within {@code timeLimitNanos}
     */
    public int[][] solve(int[] starts, int[] goals, long timeLimitNanos) {
        long deadline = System.nanoTime() + timeLimitNanos;
        int n = starts.length;
        int[][] root = new int[n][];
        for (int a = 0; a < n; a++) {
            root[a] = lowLevel(starts[a], goals[a], Set.of(), Set.of(), -1);
            if (root[a] == null) return null;
        }
        PriorityQueue<Node> open = new PriorityQueue<>((x, y) -> x.cost != y.cost ? Integer.compare(x.cost, y.cost)
                : Integer.compare(conflictCount(x.paths), conflictCount(y.paths)));
        open.add(new Node(null, -1, 0, false, root, sumOfCosts(root)));
        while (!open.isEmpty()) {
            if (System.nanoTime() > deadline) return null;
            Node node = open.poll();
            highLevelExpansions++;
            int[] c = firstConflict(node.paths);
            if (c == null) return node.paths;
            int i = c[0], j = c[1], t = c[2];
            if (c[3] == 0) {
                int cell = c[4];
                branch(open, node, i, ReservationTable.vkey(cell, t), false, starts, goals);
                branch(open, node, j, ReservationTable.vkey(cell, t), false, starts, goals);
            } else {
                int from = c[4], to = c[5];
                branch(open, node, i, ReservationTable.ekey(from, to, t), true, starts, goals);
                branch(open, node, j, ReservationTable.ekey(to, from, t), true, starts, goals);
            }
        }
        return null;
    }

    private void branch(PriorityQueue<Node> open, Node parent, int agent, long key, boolean edge, int[] starts, int[] goals) {
        Set<Long> v = new HashSet<>(), e = new HashSet<>();
        int lastGoal = -1;
        for (Node x = parent; x != null && x.agent >= 0; x = x.parent) {
            if (x.agent != agent) continue;
            if (x.edge) e.add(x.key);
            else v.add(x.key);
        }
        if (edge) e.add(key);
        else v.add(key);
        for (long k : v) {
            if ((int) (k & 0xFFFFFFFFL) == goals[agent]) lastGoal = Math.max(lastGoal, (int) (k >>> 32));
        }
        int[] path = lowLevel(starts[agent], goals[agent], v, e, lastGoal);
        if (path == null) return;
        int[][] paths = parent.paths.clone();
        paths[agent] = path;
        open.add(new Node(parent, agent, key, edge, paths, sumOfCosts(paths)));
    }

    static int at(int[] path, int t) {
        return path[Math.min(t, path.length - 1)];
    }

    /** {i, j, t, 0, cell} for a vertex conflict, {i, j, t, 1, from, to} for an edge conflict, or null. */
    static int[] firstConflict(int[][] paths) {
        int n = paths.length, maxLen = 0;
        for (int[] p : paths) maxLen = Math.max(maxLen, p.length);
        for (int t = 0; t < maxLen; t++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (at(paths[i], t) == at(paths[j], t)) return new int[] {i, j, t, 0, at(paths[i], t)};
                    if (at(paths[i], t) == at(paths[j], t + 1) && at(paths[i], t + 1) == at(paths[j], t)
                            && at(paths[i], t) != at(paths[i], t + 1)) {
                        return new int[] {i, j, t, 1, at(paths[i], t), at(paths[i], t + 1)};
                    }
                }
            }
        }
        return null;
    }

    static int conflictCount(int[][] paths) {
        int n = paths.length, maxLen = 0, c = 0;
        for (int[] p : paths) maxLen = Math.max(maxLen, p.length);
        for (int t = 0; t < maxLen; t++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (at(paths[i], t) == at(paths[j], t)) c++;
                    else if (at(paths[i], t) == at(paths[j], t + 1) && at(paths[i], t + 1) == at(paths[j], t)) c++;
                }
            }
        }
        return c;
    }

    /** Space-time A* under constraints. The path ends at the goal; its length - 1 is the cost. */
    int[] lowLevel(int start, int goal, Set<Long> vcons, Set<Long> econs, int lastGoalConstraint) {
        int[] h = distances.toGoal(goal);
        if (h[start] == DistanceField.UNREACHABLE) return null;
        int maxT = h[start] + 2 * (map.width + map.height) + Math.max(0, lastGoalConstraint) + 8;
        int n = map.size();
        PriorityQueue<long[]> open = new PriorityQueue<>((x, y) -> Long.compare(x[0], y[0]));
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        long startKey = key(start, 0);
        open.add(new long[] {(long) h[start] << 32, startKey});
        parent.put(startKey, -1L);
        while (!open.isEmpty()) {
            long[] e = open.poll();
            long k = e[1];
            if (!closed.add(k)) continue;
            lowLevelExpansions++;
            int cell = (int) (k & 0xFFFFFFFFL), t = (int) (k >>> 32);
            if (cell == goal && t > lastGoalConstraint) {
                int[] path = new int[t + 1];
                long cur = k;
                while (cur >= 0) {
                    int ct = (int) (cur >>> 32);
                    path[ct] = (int) (cur & 0xFFFFFFFFL);
                    cur = parent.get(cur);
                }
                return path;
            }
            if (t >= maxT) continue;
            // wait, then the four moves (into FREE cells, or into the goal)
            for (int d = -1; d < 4; d++) {
                int to = d < 0 ? cell : map.step(cell, Dir.ALL[d]);
                if (to < 0 || h[to] == DistanceField.UNREACHABLE) continue;
                if (to != goal && to != cell && map.type(to) != GridMap.FREE) continue;
                if (vcons.contains(ReservationTable.vkey(to, t + 1))) continue;
                if (to != cell && econs.contains(ReservationTable.ekey(cell, to, t))) continue;
                long nk = key(to, t + 1);
                if (closed.contains(nk) || parent.containsKey(nk)) continue;
                parent.put(nk, k);
                long f = (long) (t + 1 + h[to]) << 32 | (0xFFFFFFFFL - (t + 1));
                open.add(new long[] {f, nk});
            }
        }
        return null;
    }

    private static long key(int cell, int t) {
        return ((long) t << 32) | cell;
    }

    /** Sum of costs of windowed prioritized paths: each agent's first tick from which it stays at its goal, or -1. */
    public static int prioritizedCost(int[][] paths, int[] goals) {
        int s = 0;
        for (int a = 0; a < paths.length; a++) {
            int[] p = paths[a];
            if (p[p.length - 1] != goals[a]) return -1;
            int k = p.length - 1;
            while (k > 0 && p[k - 1] == goals[a]) k--;
            s += k;
        }
        return s;
    }

    public static List<int[]> asList(int[][] paths) {
        return new ArrayList<>(List.of(paths));
    }
}
