package io.github.byteb8.fleetsim.planning;

import static org.junit.jupiter.api.Assertions.*;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.GridMap;
import io.github.byteb8.fleetsim.grid.Layouts;
import io.github.byteb8.fleetsim.grid.WarehouseSpec;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CooperativePlannerTest {
    @Test
    void headOnInACorridorWithAPocket() {
        GridMap m = GridMap.parse("""
                #######
                .......
                #####.#
                """);
        CooperativePlanner p = new CooperativePlanner(m, new DistanceField(m), 12);
        int[] starts = {m.index(0, 1), m.index(6, 1)};
        int[] goals = {m.index(6, 1), m.index(0, 1)};
        boolean[] failed = new boolean[2];
        int[][] paths = p.replanAll(0, starts, goals, new int[] {0, 1}, failed);
        assertFalse(failed[0]);
        assertFalse(failed[1]);
        assertTrue(CooperativePlanner.conflictFree(paths));
        assertEquals(goals[0], paths[0][12]);
        assertEquals(goals[1], paths[1][12]);
        assertEquals(goals[0], paths[0][6], "the first agent drives straight through");
        boolean usedPocket = false;
        for (int c : paths[1]) if (c == m.index(5, 2)) usedPocket = true;
        assertTrue(usedPocket, "the second ducks into the pocket");
        assertEquals(goals[1], paths[1][11]);
    }

    @Test
    void stationaryAgentEvictsAPathThroughItsCell() {
        GridMap m = GridMap.parse("""
                .....
                .....
                """);
        CooperativePlanner p = new CooperativePlanner(m, new DistanceField(m), 6);
        // agent 1 is wedged: its goal is unreachable? No: it has a goal but all moves are blocked by stays
        int[] starts = {m.index(0, 0), m.index(2, 0), m.index(1, 1), m.index(3, 0), m.index(2, 1)};
        int[] goals = {m.index(4, 0), m.index(2, 0), -1, -1, -1};
        boolean[] failed = new boolean[5];
        int[][] paths = p.replanAll(0, starts, goals, new int[] {0, 1, 2, 3, 4}, failed);
        assertTrue(CooperativePlanner.conflictFree(paths));
        for (int[] path : paths) for (int c : path) assertTrue(c >= 0);
        assertFalse(failed[1], "agent 1 is at its goal, so it just stays");
        assertEquals(m.index(2, 0), paths[1][6]);
    }

    @Test
    void randomAgentsOnAWarehouseAreConflictFreeAndMakeProgress() {
        GridMap m = Layouts.warehouse(new WarehouseSpec(3, 2, 5, 2, false, 3, 6));
        DistanceField df = new DistanceField(m);
        CooperativePlanner p = new CooperativePlanner(m, df, 10);
        SplittableRandom rnd = new SplittableRandom(3);
        int[] free = m.pickupCells();
        for (int trial = 0; trial < 30; trial++) {
            int n = 12;
            int[] starts = new int[n], goals = new int[n], order = new int[n];
            boolean[] used = new boolean[m.size()];
            for (int a = 0; a < n; a++) {
                int c;
                do c = free[rnd.nextInt(free.length)]; while (used[c]);
                used[c] = true;
                starts[a] = c;
                goals[a] = free[rnd.nextInt(free.length)];
                order[a] = a;
            }
            boolean[] failed = new boolean[n];
            int[][] paths = p.replanAll(trial * 100, starts, goals, order, failed);
            assertTrue(CooperativePlanner.conflictFree(paths), "trial " + trial);
            for (int a = 0; a < n; a++) {
                assertEquals(starts[a], paths[a][0]);
                for (int k = 1; k <= 10; k++) {
                    assertTrue(paths[a][k] == paths[a][k - 1] || m.adjacent(paths[a][k], paths[a][k - 1]));
                }
                if (!failed[a]) {
                    int[] d = df.toGoal(goals[a]);
                    assertTrue(d[paths[a][10]] <= d[starts[a]], "no regress for agent " + a);
                }
            }
        }
    }

    @Test
    void forcedStayEvictsWhoeverPlannedThroughTheCell() {
        GridMap m = GridMap.parse("""
                .....
                """);
        CooperativePlanner p = new CooperativePlanner(m, new DistanceField(m), 6);
        boolean[] failed = new boolean[1];
        int[][] paths = p.replanAll(0, new int[] {m.index(0, 0)}, new int[] {m.index(4, 0)}, new int[] {0}, failed);
        assertEquals(m.index(3, 0), paths[0][3]);
        assertEquals(0, p.table().ownerAt(m.index(3, 0), 3));
        // A second agent must now dwell on (3,0) from tick 1: agent 0 planned through it and is evicted.
        java.util.List<Integer> evicted = new java.util.ArrayList<>();
        boolean[] f = new boolean[1];
        int[] stay = p.replanOne(1, 6, 1, m.index(3, 0), m.index(3, 0), f, evicted);
        assertFalse(f[0], "being at the goal is not a failure");
        assertEquals(6, stay.length);
        for (int c : stay) assertEquals(m.index(3, 0), c);
        assertEquals(java.util.List.of(0), evicted);
        assertEquals(ReservationTable.NONE, p.table().ownerAt(m.index(4, 0), 5), "agent 0 lost all its reservations");
        assertEquals(1, p.table().ownerAt(m.index(3, 0), 3));
        // Re-planning agent 0 now makes it wait behind the dwelling agent.
        int[] again = p.replanOne(1, 6, 0, m.index(1, 0), m.index(4, 0), f, evicted);
        assertFalse(f[0]);
        assertTrue(CooperativePlanner.conflictFree(new int[][] {again, stay}));
    }

    @Test
    void replanOneRespectsExistingReservations() {
        GridMap m = GridMap.parse("""
                .....
                .....
                """);
        CooperativePlanner p = new CooperativePlanner(m, new DistanceField(m), 8);
        int[] starts = {m.index(0, 0), m.index(4, 0)};
        int[] goals = {m.index(4, 0), -1};
        boolean[] failed = new boolean[2];
        int[][] paths = p.replanAll(0, starts, goals, new int[] {0, 1}, failed);
        boolean[] f1 = new boolean[1];
        java.util.List<Integer> evicted = new java.util.ArrayList<>();
        int[] p1 = p.replanOne(3, 8, 1, m.index(4, 0), m.index(0, 0), f1, evicted);
        assertTrue(evicted.isEmpty());
        assertFalse(f1[0]);
        assertEquals(6, p1.length);
        int[][] both = new int[2][6];
        for (int k = 0; k < 6; k++) {
            both[0][k] = paths[0][3 + k];
            both[1][k] = p1[k];
        }
        assertTrue(CooperativePlanner.conflictFree(both));
    }
}
