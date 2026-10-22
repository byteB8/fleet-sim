package io.github.byteb8.fleetsim.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Counters and samples collected during a run. */
public final class Metrics {
    public long tasksArrived, tasksCompleted;
    public long ticksMoving, ticksWaiting, ticksDwell, ticksIdle, ticksCharging, ticksToCharger, ticksUnavailable;
    public long ticksWaitingForBay;
    public long planFailures, cascades, fullReplans, eventReplans, expansions, planningNanos;
    public long invariantChecks, forcedCharges, evictions;
    public int backlogMax;
    public double batteryMin = 100;
    public long wallNanos;
    private final List<Integer> latency = new ArrayList<>();
    private final List<Integer> cycle = new ArrayList<>();
    private final List<Integer> backlogSeries = new ArrayList<>();

    void completed(Task task) {
        tasksCompleted++;
        latency.add(task.dropTick - task.arrivalTick);
        cycle.add(task.dropTick - task.assignTick);
    }

    void backlogSample(int backlog) {
        backlogSeries.add(backlog);
        if (backlog > backlogMax) backlogMax = backlog;
    }

    static double percentile(List<Integer> xs, double p) {
        if (xs.isEmpty()) return Double.NaN;
        int[] a = xs.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(a);
        int i = (int) Math.ceil(p / 100.0 * a.length) - 1;
        return a[Math.max(0, Math.min(a.length - 1, i))];
    }

    static double mean(List<Integer> xs) {
        return xs.isEmpty() ? Double.NaN : xs.stream().mapToLong(Integer::longValue).sum() / (double) xs.size();
    }

    public Map<String, Object> summary(SimConfig cfg) {
        long robotTicks = (long) cfg.robots * cfg.ticks;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tasksArrived", tasksArrived);
        m.put("tasksCompleted", tasksCompleted);
        m.put("throughputPerHour", tasksCompleted * 3600.0 / cfg.ticks);
        m.put("throughputPerRobotHour", tasksCompleted * 3600.0 / cfg.ticks / cfg.robots);
        m.put("latencyMean", mean(latency));
        m.put("latencyP50", percentile(latency, 50));
        m.put("latencyP90", percentile(latency, 90));
        m.put("latencyP95", percentile(latency, 95));
        m.put("cycleMean", mean(cycle));
        m.put("cycleP50", percentile(cycle, 50));
        m.put("cycleP95", percentile(cycle, 95));
        m.put("backlogMax", backlogMax);
        m.put("backlogMeanPerMinute", mean(backlogSeries));
        m.put("fracMoving", ticksMoving / (double) robotTicks);
        m.put("fracWaiting", ticksWaiting / (double) robotTicks);
        m.put("fracDwell", ticksDwell / (double) robotTicks);
        m.put("fracIdle", ticksIdle / (double) robotTicks);
        m.put("fracCharging", ticksCharging / (double) robotTicks);
        m.put("fracToCharger", ticksToCharger / (double) robotTicks);
        m.put("availability", 1.0 - ticksUnavailable / (double) robotTicks);
        m.put("waitingForBayTicks", ticksWaitingForBay);
        m.put("planFailures", planFailures);
        m.put("cascades", cascades);
        m.put("fullReplans", fullReplans);
        m.put("eventReplans", eventReplans);
        m.put("expansions", expansions);
        m.put("planningMs", planningNanos / 1e6);
        m.put("planningMsPerTick", planningNanos / 1e6 / cfg.ticks);
        m.put("invariantChecks", invariantChecks);
        m.put("forcedCharges", forcedCharges);
        m.put("evictions", evictions);
        m.put("batteryMin", batteryMin);
        m.put("wallMs", wallNanos / 1e6);
        return m;
    }
}
