package io.github.byteb8.fleetsim.sim;

public final class Robot {
    public final int id;
    public int cell;
    public RobotState state = RobotState.IDLE;
    public double battery;
    public Task task;
    public int goal = -1;
    public int bay = -1;
    public int dwellLeft;
    public int stalledTicks;
    public boolean wantsCharger;
    public boolean forcedCharge;

    int[] path;
    int pathT0;
    boolean needsPlan;

    Robot(int id, int cell, double battery) {
        this.id = id;
        this.cell = cell;
        this.battery = battery;
    }

    void setGoal(int goal) {
        this.goal = goal;
        this.needsPlan = true;
    }

    int nextCell(int t) {
        int i = t + 1 - pathT0;
        if (path == null || i < 0 || i >= path.length) return cell;
        return path[i];
    }
}
