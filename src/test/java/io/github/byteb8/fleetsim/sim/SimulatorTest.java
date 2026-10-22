package io.github.byteb8.fleetsim.sim;

import static org.junit.jupiter.api.Assertions.*;

import io.github.byteb8.fleetsim.grid.WarehouseSpec;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The simulator checks collisions, swaps, illegal moves and batteries every tick and throws on a violation. */
class SimulatorTest {
    static SimConfig small() {
        SimConfig c = new SimConfig();
        c.layout = new WarehouseSpec(3, 2, 5, 2, false, 3, 6);
        c.robots = 10;
        c.ticks = 600;
        return c;
    }

    @ParameterizedTest
    @CsvSource({"greedy,threshold", "greedy,opportunistic", "hungarian,threshold", "hungarian,opportunistic"})
    void completesTasksWithoutViolations(String assigner, String charging) {
        SimConfig c = small();
        c.assigner = assigner;
        c.charging = charging;
        Metrics m = new Simulator(c).run();
        assertTrue(m.tasksCompleted > 20, "completed " + m.tasksCompleted);
        assertEquals(600, m.invariantChecks);
        assertTrue(m.batteryMin > 0);
        Map<String, Object> s = m.summary(c);
        assertEquals(m.tasksCompleted * 6.0, (Double) s.get("throughputPerHour"), 1e-9);
    }

    @Test
    void sameSeedSameResult() {
        Metrics a = new Simulator(small()).run();
        Metrics b = new Simulator(small()).run();
        assertEquals(a.tasksCompleted, b.tasksCompleted);
        assertEquals(a.summary(small()).get("latencyMean"), b.summary(small()).get("latencyMean"));
        assertEquals(a.ticksMoving, b.ticksMoving);
        SimConfig other = small();
        other.seed = 2;
        Metrics c = new Simulator(other).run();
        assertNotEquals(a.ticksMoving, c.ticksMoving, "a different seed gives a different run");
    }

    @Test
    void poissonArrivalsAreServedAndRobotsRest() {
        SimConfig c = small();
        c.arrivalRate = 0.02;
        c.ticks = 1200;
        Metrics m = new Simulator(c).run();
        assertTrue(m.tasksArrived > 10);
        assertTrue(m.tasksCompleted >= m.tasksArrived - 15, "backlog stays small at light load");
        assertTrue(m.ticksIdle + m.ticksCharging > 0);
    }

    @Test
    void chargingKeepsBatteriesUpOverALongShift() {
        SimConfig c = small();
        c.ticks = 8 * 3600;
        c.runtimeTicks = 3600;
        c.chargeTicks = 900;
        c.robots = 8;
        for (String policy : new String[] {"threshold", "opportunistic"}) {
            c.charging = policy;
            Metrics m = new Simulator(c).run();
            assertTrue(m.ticksCharging > 0, policy + " never charged");
            assertTrue(m.batteryMin > 0, policy + " hit zero");
            assertTrue(m.tasksCompleted > 200, policy + " completed " + m.tasksCompleted);
        }
    }

    @Test
    void randomConfigurationsNeverViolateInvariants() {
        SplittableRandom rnd = new SplittableRandom(5);
        for (int trial = 0; trial < 12; trial++) {
            SimConfig c = new SimConfig();
            boolean oneWay = rnd.nextBoolean();
            c.layout = new WarehouseSpec(2 + rnd.nextInt(3), 1 + rnd.nextInt(3), 3 + rnd.nextInt(5),
                    1 + rnd.nextInt(2), oneWay, 1 + rnd.nextInt(4), rnd.nextInt(6));
            c.robots = 1 + rnd.nextInt(25);
            c.ticks = 300;
            c.seed = trial;
            c.window = 4 + rnd.nextInt(16);
            c.replanEvery = 1 + rnd.nextInt(c.window);
            c.assigner = rnd.nextBoolean() ? "greedy" : "hungarian";
            c.charging = rnd.nextBoolean() ? "threshold" : "opportunistic";
            c.arrivalRate = rnd.nextBoolean() ? -1 : rnd.nextDouble() * 0.1;
            Metrics m = new Simulator(c).run();
            assertEquals(300, m.invariantChecks, "trial " + trial);
        }
    }

    @Test
    void crowdedFleetRunsWithoutViolations() {
        // 100 robots on the default 62x27 floor once produced a collision through a forced stay
        // that silently overwrote another robot's reservation (tick 1863 of this run).
        SimConfig c = new SimConfig();
        c.robots = 100;
        c.ticks = 2400;
        c.stationCap = 6;
        Metrics m = new Simulator(c).run();
        assertEquals(2400, m.invariantChecks);
    }

    @Test
    void rejectsBadConfig() {
        SimConfig c = small();
        c.replanEvery = c.window + 1;
        assertThrows(IllegalArgumentException.class, () -> new Simulator(c));
    }
}
