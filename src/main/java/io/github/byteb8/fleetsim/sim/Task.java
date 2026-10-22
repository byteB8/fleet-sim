package io.github.byteb8.fleetsim.sim;

/** One transport job: go to {@code pickup} (a cell beside a shelf), then deliver to {@code drop} (a station). */
public final class Task {
    public final int id;
    public final int pickup;
    public final int drop;
    public final int arrivalTick;
    public int assignTick = -1;
    public int pickupTick = -1;
    public int dropTick = -1;
    public int robot = -1;

    Task(int id, int pickup, int drop, int arrivalTick) {
        this.id = id;
        this.pickup = pickup;
        this.drop = drop;
        this.arrivalTick = arrivalTick;
    }
}
