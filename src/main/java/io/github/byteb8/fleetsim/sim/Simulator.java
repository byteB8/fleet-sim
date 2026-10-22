package io.github.byteb8.fleetsim.sim;

import io.github.byteb8.fleetsim.assign.Assigner;
import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.Dir;
import io.github.byteb8.fleetsim.grid.GridMap;
import io.github.byteb8.fleetsim.grid.Layouts;
import io.github.byteb8.fleetsim.planning.CooperativePlanner;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Discrete-time simulation of a robot fleet in a warehouse. Each tick: new tasks arrive, robots
 * that reached their goals change state, idle robots get tasks or go to charge or park, paths are
 * (re)planned, then every robot takes one step. Before the step the next positions are checked
 * for vertex collisions, swaps, illegal moves and untraversable cells; a violation is a bug and
 * stops the run.
 */
public final class Simulator {
    private final SimConfig cfg;
    private final GridMap map;
    private final DistanceField df;
    private final CooperativePlanner planner;
    private final Assigner assigner;
    private final ChargingPolicy charging;
    private final Robot[] robots;
    private final Bays bays;
    private final Metrics metrics = new Metrics();
    private final EventLog log;
    private final SplittableRandom rnd;
    private final int[] pickupCells;
    private final int[] stations;
    private final int[] stationLoad;
    private final ArrayDeque<Task> pending = new ArrayDeque<>();
    private int nextTaskId;
    private int t;
    private int horizon = -1;
    private boolean forceFullReplan;

    public Simulator(SimConfig cfg) {
        cfg.validate();
        this.cfg = cfg;
        this.map = cfg.mapAscii != null ? GridMap.parse(cfg.mapAscii) : Layouts.warehouse(cfg.layout);
        this.df = new DistanceField(map);
        this.planner = new CooperativePlanner(map, df, cfg.window);
        this.assigner = Assigner.of(cfg.assigner);
        this.charging = ChargingPolicy.of(cfg.charging, cfg.batteryLow, cfg.batteryHigh, cfg.batteryLeave);
        this.rnd = new SplittableRandom(cfg.seed);
        this.pickupCells = map.pickupCells();
        this.stations = map.cellsOfType(GridMap.STATION);
        this.stationLoad = new int[map.size()];
        if (pickupCells.length == 0 || stations.length == 0) throw new IllegalArgumentException("map needs shelves and stations");
        this.bays = new Bays(map, df);
        this.robots = new Robot[cfg.robots];
        this.log = cfg.logDir == null ? null : new EventLog(Path.of(cfg.logDir));
        placeRobots();
    }

    public GridMap map() {
        return map;
    }

    public Metrics metrics() {
        return metrics;
    }

    public Robot[] robots() {
        return robots;
    }

    private void placeRobots() {
        boolean[] used = new boolean[map.size()];
        for (int i = 0; i < robots.length; i++) {
            double battery = 60 + 40.0 * i / Math.max(1, robots.length - 1);
            int cell = bays.claimCharger(0);
            RobotState st = RobotState.CHARGING;
            if (cell < 0) {
                cell = bays.claimParking(0);
                st = RobotState.PARKED;
            }
            if (cell < 0) {
                st = RobotState.IDLE;
                do cell = pickupCells[rnd.nextInt(pickupCells.length)]; while (used[cell]);
            }
            used[cell] = true;
            Robot r = new Robot(i, cell, battery);
            r.state = st;
            r.bay = st == RobotState.IDLE ? -1 : cell;
            r.goal = -1;
            robots[i] = r;
            if (log != null) log.position(0, r, map.x(cell), map.y(cell));
        }
    }

    public Metrics run() {
        long start = System.nanoTime();
        try {
            for (t = 0; t < cfg.ticks; t++) step();
        } finally {
            if (log != null) log.close();
        }
        metrics.wallNanos = System.nanoTime() - start;
        metrics.planFailures = planner.failures();
        metrics.cascades = planner.cascades();
        metrics.expansions = planner.expansions();
        return metrics;
    }

    private void step() {
        arrivals();
        transitions();
        assign();
        idleDecisions();
        plan();
        move();
        if (t % 60 == 0) metrics.backlogSample(pending.size());
    }

    // ---- tasks -------------------------------------------------------------------------------

    private void arrivals() {
        int n;
        if (cfg.saturated()) {
            n = Math.max(0, 2 * robots.length - pending.size());
        } else {
            n = poisson(cfg.arrivalRate);
        }
        for (int i = 0; i < n; i++) {
            Task task = new Task(nextTaskId++, pickupCells[rnd.nextInt(pickupCells.length)],
                    stations[rnd.nextInt(stations.length)], t);
            pending.add(task);
            metrics.tasksArrived++;
            if (log != null && t < cfg.logTicks) log.event(t, "arrive", task.id, -1, task.pickup);
        }
    }

    private int poisson(double lambda) {
        double l = Math.exp(-lambda), p = 1.0;
        int k = 0;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > l);
        return k - 1;
    }

    // ---- state machine -----------------------------------------------------------------------

    private void transitions() {
        for (Robot r : robots) {
            switch (r.state) {
                case TO_PICKUP -> {
                    if (r.cell == r.goal) {
                        r.state = RobotState.PICKING;
                        r.dwellLeft = cfg.pickupDwell;
                        r.task.pickupTick = t;
                        r.setGoal(r.cell);
                        if (log != null && t < cfg.logTicks) log.event(t, "pickup", r.task.id, r.id, r.cell);
                    }
                }
                case PICKING -> {
                    if (--r.dwellLeft <= 0) {
                        r.state = RobotState.TO_DROP;
                        r.setGoal(r.task.drop);
                    }
                }
                case TO_DROP -> {
                    if (r.cell == r.goal) {
                        r.state = RobotState.DROPPING;
                        r.dwellLeft = cfg.dropDwell;
                        r.setGoal(r.cell);
                    }
                }
                case DROPPING -> {
                    if (--r.dwellLeft <= 0) {
                        Task task = r.task;
                        task.dropTick = t;
                        stationLoad[task.drop]--;
                        metrics.completed(task);
                        if (log != null && t < cfg.logTicks) log.event(t, "drop", task.id, r.id, r.cell);
                        r.task = null;
                        r.state = RobotState.IDLE;
                        r.goal = -1;
                    }
                }
                case TO_CHARGER -> {
                    if (r.cell == r.goal) {
                        r.state = RobotState.CHARGING;
                        r.setGoal(r.cell);
                    }
                }
                case TO_PARK -> {
                    if (r.cell == r.goal) {
                        r.state = RobotState.PARKED;
                        r.setGoal(r.cell);
                    }
                }
                case CHARGING -> {
                    r.battery = Math.min(100.0, r.battery + cfg.chargePerTick());
                    r.forcedCharge = false;
                }
                default -> { }
            }
        }
    }

    // ---- assignment --------------------------------------------------------------------------

    private boolean canTakeTask(Robot r) {
        return switch (r.state) {
            case IDLE, PARKED -> !charging.mustCharge(r);
            case CHARGING -> charging.availableWhileCharging(r) && !charging.mustCharge(r);
            case TO_PARK -> !charging.mustCharge(r);
            default -> false;
        };
    }

    private void assign() {
        if (pending.isEmpty()) return;
        List<Robot> cands = new ArrayList<>();
        for (Robot r : robots) if (canTakeTask(r)) cands.add(r);
        if (cands.isEmpty()) return;
        // Admission control: a station with stationCap tasks already assigned takes no more for now,
        // so surplus robots wait in bays instead of crowding the corridor in front of it.
        int want = Math.max(1, cands.size() * cfg.assignBatch);
        List<Task> open = new ArrayList<>();
        for (Task task : pending) {
            if (open.size() == want) break;
            if (cfg.stationCap > 0 && stationLoad[task.drop] >= cfg.stationCap) continue;
            open.add(task);
        }
        if (open.isEmpty()) return;
        int cols = open.size();
        Task[] tasks = open.toArray(new Task[0]);
        int j;
        double[][] cost = new double[cands.size()][cols];
        for (int i = 0; i < cands.size(); i++) {
            Robot r = cands.get(i);
            for (j = 0; j < cols; j++) cost[i][j] = taskCost(r, tasks[j]);
        }
        int[] match = assigner.assign(cost);
        for (int i = 0; i < cands.size(); i++) {
            if (match[i] < 0) continue;
            Robot r = cands.get(i);
            Task task = tasks[match[i]];
            pending.remove(task);
            task.assignTick = t;
            task.robot = r.id;
            r.task = task;
            r.state = RobotState.TO_PICKUP;
            r.setGoal(task.pickup);
            bays.release(r.bay);
            r.bay = -1;
            r.wantsCharger = false;
            stationLoad[task.drop]++;
            if (log != null && t < cfg.logTicks) log.event(t, "assign", task.id, r.id, r.cell);
        }
    }

    private double taskCost(Robot r, Task task) {
        int d1 = df.toGoal(task.pickup)[r.cell];
        int d2 = df.toGoal(task.drop)[task.pickup];
        if (d1 == DistanceField.UNREACHABLE || d2 == DistanceField.UNREACHABLE) return Assigner.INFEASIBLE;
        double need = (d1 + d2) * cfg.drainPerMove();
        if (r.battery - need < cfg.batteryLow) return Assigner.INFEASIBLE;
        return d1 + cfg.weightQueue * stationLoad[task.drop] - cfg.weightAge * (t - task.arrivalTick);
    }

    // ---- charging and parking ----------------------------------------------------------------

    private void idleDecisions() {
        for (Robot r : robots) {
            switch (r.state) {
                case IDLE -> decideIdle(r);
                case PARKED -> {
                    if (charging.mustCharge(r)) {
                        bays.release(r.bay);
                        r.bay = -1;
                        r.state = RobotState.IDLE;
                        r.goal = -1;
                        decideIdle(r);
                    }
                }
                case CHARGING -> {
                    if (charging.doneCharging(r) && (charging instanceof ChargingPolicy.Threshold || anyoneWantsCharger())) {
                        sendToPark(r);
                        if (!(charging instanceof ChargingPolicy.Threshold)) metrics.evictions++;
                    }
                }
                default -> { }
            }
        }
    }

    private boolean anyoneWantsCharger() {
        for (Robot r : robots) if (r.wantsCharger) return true;
        return false;
    }

    private void decideIdle(Robot r) {
        boolean must = charging.mustCharge(r);
        if (must || charging.chargeWhenIdle(r, bays.freeChargers() > 0)) {
            int c = bays.claimCharger(r.cell);
            if (c >= 0) {
                r.bay = c;
                r.state = RobotState.TO_CHARGER;
                r.setGoal(c);
                r.wantsCharger = false;
                if (must) {
                    r.forcedCharge = true;
                    metrics.forcedCharges++;
                }
                return;
            }
            if (must) {
                r.wantsCharger = true;
                metrics.ticksWaitingForBay++;
                if (r.goal != -1) r.setGoal(-1);
                return;
            }
        }
        int p = bays.claimParking(r.cell);
        if (p >= 0) {
            r.bay = p;
            r.state = RobotState.TO_PARK;
            r.setGoal(p);
        } else {
            metrics.ticksWaitingForBay++;
            if (r.goal != -1) r.setGoal(-1);
        }
    }

    private void sendToPark(Robot r) {
        int p = bays.claimParking(r.cell);
        if (p < 0) return;
        bays.release(r.bay);
        r.bay = p;
        r.state = RobotState.TO_PARK;
        r.setGoal(p);
    }

    // ---- planning ----------------------------------------------------------------------------

    private void plan() {
        long t0 = System.nanoTime();
        if (t % cfg.replanEvery == 0 || forceFullReplan) {
            forceFullReplan = false;
            fullReplan();
        } else {
            ArrayDeque<Robot> work = new ArrayDeque<>();
            for (Robot r : robots) if (r.needsPlan) work.add(r);
            List<Integer> evicted = new ArrayList<>();
            while (!work.isEmpty()) {
                Robot r = work.poll();
                r.needsPlan = false;
                boolean[] failed = new boolean[1];
                int goal = r.goal < 0 ? r.cell : r.goal;
                evicted.clear();
                r.path = planner.replanOne(t, horizon, r.id, r.cell, goal, failed, evicted);
                r.pathT0 = t;
                metrics.eventReplans++;
                // Boxed in by reservations made before it had a goal: let the next tick re-rank everyone.
                if (failed[0]) forceFullReplan = true;
                // Whoever lost reservations to this forced stay must be re-planned right now.
                for (int id : evicted) {
                    Robot e = robots[id];
                    if (!e.needsPlan && !work.contains(e)) work.add(e);
                }
            }
        }
        metrics.planningNanos += System.nanoTime() - t0;
    }

    private void fullReplan() {
        int n = robots.length;
        int[] starts = new int[n], goals = new int[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            starts[i] = robots[i].cell;
            goals[i] = robots[i].goal;
            order[i] = i;
            robots[i].needsPlan = false;
        }
        // A robot leaving a single-exit bay (station, charger, parking) goes first: whoever queues at
        // its exit must step aside, otherwise the two wait for each other forever.
        Arrays.sort(order, Comparator.<Integer>comparingInt(i -> leavingBay(robots[i]) ? 0 : 1)
                .thenComparingInt(i -> -robots[i].stalledTicks)
                .thenComparingInt(i -> -stateRank(robots[i].state))
                .thenComparingInt(i -> robots[i].task == null ? 0 : robots[i].task.assignTick)
                .thenComparingInt(i -> i));
        int[] ord = new int[n];
        for (int i = 0; i < n; i++) ord[i] = order[i];
        boolean[] failed = new boolean[n];
        int[][] paths = planner.replanAll(t, starts, goals, ord, failed);
        for (int i = 0; i < n; i++) {
            robots[i].path = paths[i];
            robots[i].pathT0 = t;
        }
        horizon = t + cfg.window;
        metrics.fullReplans++;
    }

    private boolean leavingBay(Robot r) {
        return r.goal >= 0 && r.goal != r.cell && map.type(r.cell) != GridMap.FREE;
    }

    private static int stateRank(RobotState s) {
        return switch (s) {
            case TO_DROP -> 4;
            case TO_PICKUP -> 3;
            case TO_CHARGER -> 2;
            case TO_PARK -> 1;
            default -> 0;
        };
    }

    // ---- motion and invariants ---------------------------------------------------------------

    private void move() {
        int n = robots.length;
        int[] next = new int[n];
        for (int i = 0; i < n; i++) next[i] = robots[i].nextCell(t);
        checkInvariants(next);
        for (int i = 0; i < n; i++) {
            Robot r = robots[i];
            boolean moved = next[i] != r.cell;
            boolean unavailable = r.state == RobotState.TO_CHARGER
                    || (r.state == RobotState.CHARGING && !charging.availableWhileCharging(r));
            if (unavailable) metrics.ticksUnavailable++;
            if (moved) {
                r.battery -= cfg.drainPerMove();
                r.stalledTicks = 0;
                metrics.ticksMoving++;
            } else {
                if (r.state != RobotState.CHARGING) r.battery -= cfg.drainPerMove() * cfg.idleDrainFactor;
                if (r.state.moving() && r.goal != r.cell) r.stalledTicks++;
                if (r.state.hasTask()) {
                    if (r.state.moving()) metrics.ticksWaiting++;
                    else metrics.ticksDwell++;
                }
            }
            switch (r.state) {
                case CHARGING -> metrics.ticksCharging++;
                case TO_CHARGER -> metrics.ticksToCharger++;
                case IDLE, PARKED, TO_PARK -> metrics.ticksIdle++;
                default -> { }
            }
            if (r.battery < 0) throw new IllegalStateException("robot " + r.id + " ran out of battery at tick " + t);
            if (r.battery < metrics.batteryMin) metrics.batteryMin = r.battery;
            r.cell = next[i];
            if (log != null && t + 1 <= cfg.logTicks) log.position(t + 1, r, map.x(r.cell), map.y(r.cell));
        }
    }

    private void checkInvariants(int[] next) {
        int n = robots.length;
        boolean[] occupied = new boolean[map.size()];
        int[] occupant = new int[map.size()];
        for (int i = 0; i < n; i++) {
            int from = robots[i].cell, to = next[i];
            if (!map.traversable(to)) throw new IllegalStateException("robot " + i + " enters a shelf at tick " + t);
            if (from != to) {
                boolean legal = false;
                for (Dir d : Dir.ALL) if (map.step(from, d) == to) legal = true;
                if (!legal) throw new IllegalStateException("robot " + i + " illegal move " + from + "->" + to + " at tick " + t);
            }
            if (occupied[to]) throw new IllegalStateException("collision in cell " + to + " between robots " + occupant[to] + " and " + i + " at tick " + (t + 1));
            occupied[to] = true;
            occupant[to] = i;
        }
        // Swap: i moves a->b while j moves b->a. Look up who is currently at my target cell.
        int[] current = new int[map.size()];
        Arrays.fill(current, -1);
        for (int i = 0; i < n; i++) current[robots[i].cell] = i;
        for (int i = 0; i < n; i++) {
            int from = robots[i].cell, to = next[i];
            if (from == to) continue;
            int j = current[to];
            if (j >= 0 && next[j] == from) throw new IllegalStateException("swap between robots " + i + " and " + j + " at tick " + t);
        }
        metrics.invariantChecks++;
    }
}
