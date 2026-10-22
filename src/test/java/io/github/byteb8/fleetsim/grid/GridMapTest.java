package io.github.byteb8.fleetsim.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GridMapTest {
    static final String SMALL = """
            C.P
            .#.
            S>.
            """;

    @Test
    void parseRoundTrips() {
        GridMap m = GridMap.parse(SMALL);
        assertEquals(3, m.width);
        assertEquals(3, m.height);
        assertEquals(SMALL, m.toAscii());
        assertEquals(GridMap.CHARGER, m.type(m.index(0, 0)));
        assertEquals(GridMap.SHELF, m.type(m.index(1, 1)));
        assertEquals(GridMap.STATION, m.type(m.index(0, 2)));
    }

    @Test
    void stepRespectsBoundsShelvesAndOneWay() {
        GridMap m = GridMap.parse(SMALL);
        int top = m.index(1, 0);
        assertEquals(-1, m.step(top, Dir.N), "off the map");
        assertEquals(-1, m.step(top, Dir.S), "into a shelf");
        assertEquals(m.index(2, 0), m.step(top, Dir.E));
        int lane = m.index(1, 2);
        assertEquals(-1, m.step(lane, Dir.W), "reverse of a one-way lane");
        assertEquals(m.index(2, 2), m.step(lane, Dir.E));
        assertEquals(-1, m.step(lane, Dir.N), "shelf above the lane");
    }

    @Test
    void pickupCellsTouchAShelf() {
        GridMap m = GridMap.parse(SMALL);
        int[] picks = m.pickupCells();
        assertEquals(4, picks.length, "the one-way lane cell counts too");
        for (int p : picks) {
            assertEquals(GridMap.FREE, m.type(p));
            boolean touches = false;
            for (Dir d : Dir.ALL) {
                int nx = m.x(p) + d.dx, ny = m.y(p) + d.dy;
                if (m.inBounds(nx, ny) && m.type(m.index(nx, ny)) == GridMap.SHELF) touches = true;
            }
            assertTrue(touches);
        }
    }

    @Test
    void distanceFieldFollowsOneWayLanes() {
        GridMap m = GridMap.parse(SMALL);
        int[] toStation = DistanceField.computeToGoal(m, m.index(0, 2));
        assertEquals(0, toStation[m.index(0, 2)]);
        assertEquals(1, toStation[m.index(0, 1)]);
        assertEquals(7, toStation[m.index(1, 2)], "eastbound lane: must loop round the shelf");
        assertEquals(DistanceField.UNREACHABLE, toStation[m.index(1, 1)], "shelf");
        assertEquals(6, toStation[m.index(2, 2)]);
        assertEquals(3, toStation[m.index(1, 0)]);
    }
}
