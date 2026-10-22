package io.github.byteb8.fleetsim.sim;

public enum RobotState {
    IDLE, TO_PICKUP, PICKING, TO_DROP, DROPPING, TO_CHARGER, CHARGING, TO_PARK, PARKED;

    public boolean hasTask() {
        return this == TO_PICKUP || this == PICKING || this == TO_DROP || this == DROPPING;
    }

    public boolean moving() {
        return this == TO_PICKUP || this == TO_DROP || this == TO_CHARGER || this == TO_PARK;
    }
}
