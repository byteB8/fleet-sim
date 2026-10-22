package io.github.byteb8.fleetsim.grid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LayoutsTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void everyPickupReachesEveryStationAndEveryChargerReachesEveryPickup(boolean oneWay) {
        WarehouseSpec s = new WarehouseSpec(4, 3, 6, oneWay ? 1 : 2, oneWay, 4, 8);
        GridMap m = Layouts.warehouse(s);
        assertEquals(s.width(), m.width);
        assertEquals(s.height(), m.height);
        int[] stations = m.cellsOfType(GridMap.STATION);
        int[] chargers = m.cellsOfType(GridMap.CHARGER);
        int[] picks = m.pickupCells();
        assertEquals(4, stations.length);
        assertEquals(8, chargers.length);
        assertTrue(picks.length > 50);

        for (int st : stations) {
            int[] d = DistanceField.computeToGoal(m, st);
            for (int p : picks) assertNotEquals(DistanceField.UNREACHABLE, d[p], "pickup " + p + " to station " + st);
        }
        for (int c : chargers) {
            int[] d = DistanceField.computeFromStart(m, c);
            for (int p : picks) assertNotEquals(DistanceField.UNREACHABLE, d[p], "charger " + c + " to pickup " + p);
            for (int st : stations) assertNotEquals(DistanceField.UNREACHABLE, d[st]);
        }
        // Bays are only left towards the interior, so a robot cannot drive along an edge strip.
        assertEquals(-1, m.step(m.index(5, 0), Dir.E));
        assertEquals(-1, m.step(m.index(0, 5), Dir.S));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stationsAreMutuallyReachable(boolean oneWay) {
        GridMap m = Layouts.warehouse(new WarehouseSpec(3, 2, 5, 1, oneWay, 3, 3));
        int[] stations = m.cellsOfType(GridMap.STATION);
        for (int a : stations) {
            int[] d = DistanceField.computeFromStart(m, a);
            for (int b : stations) assertNotEquals(DistanceField.UNREACHABLE, d[b]);
            for (int c : m.cellsOfType(GridMap.CHARGER)) assertNotEquals(DistanceField.UNREACHABLE, d[c]);
            for (int p : m.cellsOfType(GridMap.PARKING)) assertNotEquals(DistanceField.UNREACHABLE, d[p]);
        }
    }
}
