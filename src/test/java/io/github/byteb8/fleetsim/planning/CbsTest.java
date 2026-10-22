package io.github.byteb8.fleetsim.planning;

import static org.junit.jupiter.api.Assertions.*;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.GridMap;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CbsTest {
    static int[][] padded(int[][] paths) {
        int max = 0;
        for (int[] p : paths) max = Math.max(max, p.length);
        int[][] out = new int[paths.length][max];
        for (int i = 0; i < paths.length; i++) {
            for (int t = 0; t < max; t++) out[i][t] = paths[i][Math.min(t, paths[i].length - 1)];
        }
        return out;
    }

    @Test
    void headOnInACorridorNeedsTheOtherToYield() {
        GridMap m = GridMap.parse("""
                #######
                .......
                #####.#
                """);
        Cbs cbs = new Cbs(m, new DistanceField(m));
        int[] starts = {m.index(0, 1), m.index(6, 1)};
        int[] goals = {m.index(6, 1), m.index(0, 1)};
        int[][] paths = cbs.solve(starts, goals, 5_000_000_000L);
        assertNotNull(paths);
        assertTrue(CooperativePlanner.conflictFree(padded(paths)));
        assertEquals(goals[0], paths[0][paths[0].length - 1]);
        assertEquals(goals[1], paths[1][paths[1].length - 1]);
        // Optimal: agent 1 ducks into the pocket (2 extra ticks) while agent 0 drives straight: 6 + 6 + 2 + 3 waits
        assertEquals(17, Cbs.sumOfCosts(paths));
    }

    @Test
    void agentMustNotParkOnAGoalAnotherAgentPassesLater() {
        GridMap m = GridMap.parse("""
                #####
                .....
                #####
                """);
        Cbs cbs = new Cbs(m, new DistanceField(m));
        // agent 0 goes 0 -> 4; agent 1 starts at 3 and wants 1: it must retreat into... nowhere: unsolvable corridor
        int[] starts = {m.index(0, 1), m.index(3, 1)};
        int[] goals = {m.index(4, 1), m.index(1, 1)};
        assertNull(cbs.solve(starts, goals, 500_000_000L), "a 1-wide corridor with crossing agents has no solution");
    }

    @Test
    void randomInstancesAreConflictFreeAndNeverWorseThanPrioritized() {
        GridMap m = GridMap.parse("""
                ........
                .##..##.
                ........
                .##..##.
                ........
                """);
        DistanceField df = new DistanceField(m);
        int[] free = m.cellsOfType(GridMap.FREE);
        SplittableRandom rnd = new SplittableRandom(4);
        int solved = 0;
        for (int inst = 0; inst < 40; inst++) {
            int k = 2 + rnd.nextInt(4);
            int[] starts = new int[k], goals = new int[k];
            boolean[] us = new boolean[m.size()], ug = new boolean[m.size()];
            for (int i = 0; i < k; i++) {
                int s, g;
                do s = free[rnd.nextInt(free.length)]; while (us[s]);
                do g = free[rnd.nextInt(free.length)]; while (ug[g]);
                us[s] = true;
                ug[g] = true;
                starts[i] = s;
                goals[i] = g;
            }
            int[][] cp = new Cbs(m, df).solve(starts, goals, 3_000_000_000L);
            if (cp == null) continue;
            solved++;
            assertTrue(CooperativePlanner.conflictFree(padded(cp)), "instance " + inst);
            int lower = 0;
            for (int i = 0; i < k; i++) lower += df.toGoal(goals[i])[starts[i]];
            assertTrue(Cbs.sumOfCosts(cp) >= lower);
            int[] order = new int[k];
            for (int i = 0; i < k; i++) order[i] = i;
            boolean[] failed = new boolean[k];
            int[][] pp = new CooperativePlanner(m, df, 64).replanAll(0, starts, goals, order, failed);
            int prio = CooperativePlanner.conflictFree(pp) ? Cbs.prioritizedCost(pp, goals) : -1;
            if (prio >= 0) assertTrue(prio >= Cbs.sumOfCosts(cp), "prioritized cannot beat the optimum, instance " + inst);
        }
        assertTrue(solved >= 30, "solved " + solved);
    }
}
