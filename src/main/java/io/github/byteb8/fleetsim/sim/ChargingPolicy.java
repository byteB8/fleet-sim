package io.github.byteb8.fleetsim.sim;

/**
 * When robots charge. Batteries are percentages.
 * Threshold: charge only when low, to the high mark, then vacate the charger.
 * Opportunistic: an idle robot takes a free charger whenever it can, and leaves it as soon as
 * a task appears and the battery is above the leave mark.
 */
public interface ChargingPolicy {
    String name();

    /** An idle robot with nothing to do: go and charge? */
    boolean chargeWhenIdle(Robot r, boolean chargerFree);

    /** May a charging robot be assigned a task right now? */
    boolean availableWhileCharging(Robot r);

    /** Battery too low to keep working: must charge before the next task. */
    boolean mustCharge(Robot r);

    /** Charging robot has had enough and should vacate the charger if nobody needs it. */
    boolean doneCharging(Robot r);

    static ChargingPolicy of(String name, double low, double high, double leave) {
        return switch (name) {
            case "threshold" -> new Threshold(low, high);
            case "opportunistic" -> new Opportunistic(low, leave);
            default -> throw new IllegalArgumentException("unknown charging policy: " + name);
        };
    }

    record Threshold(double low, double high) implements ChargingPolicy {
        public String name() {
            return "threshold";
        }

        public boolean chargeWhenIdle(Robot r, boolean chargerFree) {
            return r.battery < low;
        }

        public boolean availableWhileCharging(Robot r) {
            return r.battery >= high;
        }

        public boolean mustCharge(Robot r) {
            return r.battery < low;
        }

        public boolean doneCharging(Robot r) {
            return r.battery >= high;
        }
    }

    record Opportunistic(double low, double leave) implements ChargingPolicy {
        public String name() {
            return "opportunistic";
        }

        public boolean chargeWhenIdle(Robot r, boolean chargerFree) {
            return chargerFree || r.battery < low;
        }

        public boolean availableWhileCharging(Robot r) {
            return r.battery >= leave;
        }

        public boolean mustCharge(Robot r) {
            return r.battery < low;
        }

        public boolean doneCharging(Robot r) {
            return r.battery >= 100.0;
        }
    }
}
