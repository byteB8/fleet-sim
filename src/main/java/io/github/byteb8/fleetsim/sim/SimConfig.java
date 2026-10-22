package io.github.byteb8.fleetsim.sim;

import io.github.byteb8.fleetsim.grid.WarehouseSpec;

/** All knobs of one run. One tick is one second; one cell is one metre; robots move one cell per tick. */
public final class SimConfig {
    public WarehouseSpec layout = WarehouseSpec.defaults();
    public String mapAscii = null;
    public int robots = 20;
    public int ticks = 3600;
    public long seed = 1;
    public String assigner = "hungarian";
    public String charging = "opportunistic";
    /** Mean task arrivals per tick; negative means a saturated backlog (always work waiting). */
    public double arrivalRate = -1;
    public int window = 16;
    public int replanEvery = 8;
    public int pickupDwell = 3;
    public int dropDwell = 3;
    /** Ticks of continuous driving on a full battery. */
    public double runtimeTicks = 4 * 3600;
    /** Ticks to charge from empty to full. */
    public double chargeTicks = 3600;
    public double idleDrainFactor = 0.1;
    public double batteryLow = 20;
    public double batteryHigh = 90;
    public double batteryLeave = 30;
    /** Tasks assigned per station at one time; surplus robots wait in bays. 0 = no limit. */
    public int stationCap = 8;
    /** Tasks considered per idle robot when building the cost matrix. */
    public int assignBatch = 4;
    public double weightQueue = 4.0;
    public double weightAge = 0.1;
    public String logDir = null;
    public int logTicks = Integer.MAX_VALUE;

    public double drainPerMove() {
        return 100.0 / runtimeTicks;
    }

    public double chargePerTick() {
        return 100.0 / chargeTicks;
    }

    public boolean saturated() {
        return arrivalRate < 0;
    }

    public void validate() {
        if (robots < 1) throw new IllegalArgumentException("robots < 1");
        if (ticks < 1) throw new IllegalArgumentException("ticks < 1");
        if (window < 1 || replanEvery < 1 || replanEvery > window)
            throw new IllegalArgumentException("need 1 <= replanEvery <= window");
        if (batteryLow >= batteryHigh) throw new IllegalArgumentException("batteryLow must be below batteryHigh");
    }
}
