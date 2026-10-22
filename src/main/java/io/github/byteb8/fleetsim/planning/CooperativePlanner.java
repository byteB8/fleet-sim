package io.github.byteb8.fleetsim.planning;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.GridMap;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

/**
 * Windowed hierarchical cooperative A* (Silver, 2005) over all agents.
 * Agents are planned one at a time in priority order against a shared reservation table.
 * An agent that cannot find any plan, not even waiting in place, is made stationary for the
 * window; any higher-priority agent whose path ran through its cell is then re-planned.
 * The stationary set only grows, so this cascade terminates.
 */
public final class CooperativePlanner {
    private final GridMap map;
    private final DistanceField distances;
    private final ReservationTable table = new ReservationTable();
    private final SpaceTimeAStar astar;
    private final int window;

    private long failures;
    private long cascades;

    public CooperativePlanner(GridMap map, DistanceField distances, int window) {
        if (window < 1) throw new IllegalArgumentException("window < 1");
        this.map = map;
        this.distances = distances;
        this.astar = new SpaceTimeAStar(map);
        this.window = window;
    }

    public int window() {
        return window;
    }

    public ReservationTable table() {
        return table;
    }

    public long failures() {
        return failures;
    }

    public long cascades() {
        return cascades;
    }

    public long expansions() {
        return astar.expansions();
    }

    /**
     * Re-plans every agent from tick {@code t0} for the whole window.
     *
     * @param starts current cell of each agent
     * @param goals  goal cell of each agent, or -1 to stay put; an agent already at its goal stays put
     * @param order  agent ids from highest to lowest priority
     * @param failed output: set true for agents that had a goal but were made stationary
     * @return paths, each of length window + 1, indexed by agent
     */
    public int[][] replanAll(int t0, int[] starts, int[] goals, int[] order, boolean[] failed) {
        int n = starts.length;
        int[][] paths = new int[n][];
        boolean[] stationary = new boolean[n];
        table.clear();
        Arrays.fill(failed, false);
        for (int a = 0; a < n; a++) {
            if (goals[a] < 0 || goals[a] == starts[a]) {
                stationary[a] = true;
                paths[a] = stay(starts[a]);
                table.reserveStay(a, starts[a], t0, t0 + window);
            }
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean[] queued = new boolean[n];
        for (int a : order) {
            if (!stationary[a]) {
                queue.add(a);
                queued[a] = true;
            }
        }
        while (!queue.isEmpty()) {
            int a = queue.poll();
            queued[a] = false;
            if (stationary[a]) continue;
            table.release(a);
            int[] path = astar.plan(table, a, starts[a], goals[a], t0, window, distances.toGoal(goals[a]));
            if (path != null) {
                paths[a] = path;
                table.reservePath(a, path, t0);
                continue;
            }
            failures++;
            failed[a] = true;
            stationary[a] = true;
            paths[a] = stay(starts[a]);
            // Evict whoever planned through this cell and send them back to the queue, in priority order.
            boolean[] hit = new boolean[n];
            for (int t = t0 + 1; t <= t0 + window; t++) {
                int o = table.ownerAt(starts[a], t);
                if (o != ReservationTable.NONE && o != a) hit[o] = true;
            }
            for (int o : order) {
                if (hit[o] && !queued[o] && !stationary[o]) {
                    table.release(o);
                    queue.add(o);
                    queued[o] = true;
                    cascades++;
                }
            }
            table.reserveStay(a, starts[a], t0, t0 + window);
        }
        return paths;
    }

    /**
     * Plans one agent against the current table from tick {@code t} up to {@code horizon},
     * releasing its previous reservations first. If no plan exists, or the agent is at its goal,
     * it is reserved in place; any other agent that had planned through that cell loses its
     * reservations and is returned in {@code evicted}, and the caller must re-plan those agents.
     *
     * @return the path for ticks t..horizon; {@code failed[0]} tells whether it is a forced stay
     */
    public int[] replanOne(int t, int horizon, int agent, int start, int goal, boolean[] failed, List<Integer> evicted) {
        int w = Math.max(0, horizon - t);
        table.release(agent);
        failed[0] = false;
        int[] path = (goal == start || w == 0) ? null : astar.plan(table, agent, start, goal, t, w, distances.toGoal(goal));
        if (path == null) {
            if (goal != start && w > 0) {
                failures++;
                failed[0] = true;
            }
            path = new int[w + 1];
            Arrays.fill(path, start);
            for (int u = t + 1; u <= horizon; u++) {
                int o = table.ownerAt(start, u);
                if (o != ReservationTable.NONE && o != agent) {
                    table.release(o);
                    if (!evicted.contains(o)) evicted.add(o);
                    cascades++;
                }
            }
            table.reserveStay(agent, start, t, horizon);
            return path;
        }
        table.reservePath(agent, path, t);
        return path;
    }

    private int[] stay(int cell) {
        int[] p = new int[window + 1];
        Arrays.fill(p, cell);
        return p;
    }

    /** Pairwise conflict check for tests and the checker: vertex at the same tick or swap. */
    public static boolean conflictFree(int[][] paths) {
        int n = paths.length;
        int len = paths[0].length;
        for (int k = 0; k < len; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    if (paths[i][k] == paths[j][k]) return false;
                    if (k + 1 < len && paths[i][k] == paths[j][k + 1] && paths[i][k + 1] == paths[j][k]) return false;
                }
            }
        }
        return true;
    }

    public GridMap map() {
        return map;
    }
}
