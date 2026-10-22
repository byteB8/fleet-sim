package io.github.byteb8.fleetsim.planning;

import static org.junit.jupiter.api.Assertions.*;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.GridMap;
import org.junit.jupiter.api.Test;

class SpaceTimeAStarTest {
    static final GridMap OPEN = GridMap.parse("""
            .....
            .....
            .....
            """);

    static void assertValidPath(GridMap m, int[] path, ReservationTable t, int agent, int t0) {
        for (int i = 0; i < path.length; i++) {
            assertTrue(path[i] >= 0, "hole at " + i);
            assertTrue(m.traversable(path[i]));
            if (i > 0) {
                assertTrue(path[i] == path[i - 1] || m.adjacent(path[i], path[i - 1]), "jump at " + i);
                if (path[i] != path[i - 1]) {
                    boolean legal = false;
                    for (var d : io.github.byteb8.fleetsim.grid.Dir.ALL) if (m.step(path[i - 1], d) == path[i]) legal = true;
                    assertTrue(legal, "illegal move at " + i);
                    assertTrue(t.edgeFree(path[i - 1], path[i], t0 + i - 1, agent), "swap at " + i);
                }
                assertTrue(t.vertexFree(path[i], t0 + i, agent), "vertex conflict at " + i);
            }
        }
    }

    @Test
    void unobstructedPathIsShortestThenParks() {
        ReservationTable t = new ReservationTable();
        SpaceTimeAStar a = new SpaceTimeAStar(OPEN);
        int start = OPEN.index(0, 0), goal = OPEN.index(4, 2);
        int[] path = a.plan(t, 0, start, goal, 10, 12, DistanceField.computeToGoal(OPEN, goal));
        assertNotNull(path);
        assertEquals(13, path.length);
        assertEquals(start, path[0]);
        assertEquals(goal, path[6], "Manhattan distance 6");
        for (int i = 6; i <= 12; i++) assertEquals(goal, path[i]);
        assertValidPath(OPEN, path, t, 0, 10);
    }

    @Test
    void waitsForAReservedCell() {
        ReservationTable t = new ReservationTable();
        GridMap m = GridMap.parse("""
                #####
                .....
                #####
                """);
        int start = m.index(0, 1), goal = m.index(4, 1);
        t.reserveStay(7, m.index(2, 1), 0, 3); // someone sits in the corridor until tick 3
        SpaceTimeAStar a = new SpaceTimeAStar(m);
        int[] path = a.plan(t, 0, start, goal, 0, 10, DistanceField.computeToGoal(m, goal));
        assertNotNull(path);
        assertValidPath(m, path, t, 0, 0);
        assertEquals(goal, path[10]);
        assertEquals(goal, path[6], "arrive at tick 6: wait until cell (2,1) frees at tick 4");
        assertNotEquals(goal, path[5]);
    }

    @Test
    void doesNotParkOnAGoalThatIsReservedLater() {
        ReservationTable t = new ReservationTable();
        int start = OPEN.index(0, 0), goal = OPEN.index(2, 0);
        t.reserveStay(7, goal, 5, 5);
        SpaceTimeAStar a = new SpaceTimeAStar(OPEN);
        int[] path = a.plan(t, 0, start, goal, 0, 8, DistanceField.computeToGoal(OPEN, goal));
        assertNotNull(path);
        assertValidPath(OPEN, path, t, 0, 0);
        assertNotEquals(goal, path[5]);
        assertEquals(goal, path[8]);
    }

    @Test
    void partialPathMakesProgressWhenGoalIsBeyondTheWindow() {
        ReservationTable t = new ReservationTable();
        SpaceTimeAStar a = new SpaceTimeAStar(OPEN);
        int start = OPEN.index(0, 0), goal = OPEN.index(4, 2);
        int[] d = DistanceField.computeToGoal(OPEN, goal);
        int[] path = a.plan(t, 0, start, goal, 0, 3, d);
        assertNotNull(path);
        assertEquals(4, path.length);
        assertEquals(3, d[path[3]], "three steps closer");
    }

    @Test
    void baysAreNotPullOverPockets() {
        GridMap m = GridMap.parse("""
                #####
                .....
                ##P##
                """);
        ReservationTable t = new ReservationTable();
        int start = m.index(0, 1), goal = m.index(4, 1);
        t.reserveStay(7, m.index(3, 1), 2, 4); // blocks the corridor for a while
        SpaceTimeAStar a = new SpaceTimeAStar(m);
        int[] path = a.plan(t, 0, start, goal, 0, 10, DistanceField.computeToGoal(m, goal));
        assertNotNull(path);
        for (int c : path) assertNotEquals(m.index(2, 2), c, "waited in the corridor, not in the bay");
        int[] toBay = a.plan(t, 0, start, m.index(2, 2), 0, 10, DistanceField.computeToGoal(m, m.index(2, 2)));
        assertNotNull(toBay);
        assertEquals(m.index(2, 2), toBay[10], "the bay is reachable when it is the goal");
    }

    @Test
    void boxedInAgentCannotPlan() {
        ReservationTable t = new ReservationTable();
        GridMap m = GridMap.parse("""
                ###
                #.#
                ###
                """);
        GridMap open = GridMap.parse("""
                ...
                ...
                """);
        int start = open.index(0, 0);
        t.reserveStay(1, start, 1, 4); // someone will drive through my cell and I cannot leave in time
        t.reserveStay(2, open.index(1, 0), 1, 4);
        t.reserveStay(3, open.index(0, 1), 1, 4);
        SpaceTimeAStar a = new SpaceTimeAStar(open);
        assertNull(a.plan(t, 0, start, open.index(2, 1), 0, 4, DistanceField.computeToGoal(open, open.index(2, 1))));
        int lone = m.index(1, 1);
        int[] stay = new SpaceTimeAStar(m).plan(new ReservationTable(), 0, lone, lone, 0, 2,
                DistanceField.computeToGoal(m, lone));
        assertNotNull(stay, "a lone cell is its own goal");
        for (int c : stay) assertEquals(lone, c);
    }
}
