package io.github.byteb8.fleetsim.sim;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.GridMap;

/** Chargers and parking bays: single-occupancy cells a robot claims before driving there. */
final class Bays {
    private final int[] chargers;
    private final int[] parking;
    private final boolean[] taken;
    private final DistanceField df;
    private int freeChargers;
    private int freeParking;

    Bays(GridMap map, DistanceField df) {
        this.chargers = map.cellsOfType(GridMap.CHARGER);
        this.parking = map.cellsOfType(GridMap.PARKING);
        this.taken = new boolean[map.size()];
        this.df = df;
        this.freeChargers = chargers.length;
        this.freeParking = parking.length;
    }

    int chargerCount() {
        return chargers.length;
    }

    int parkingCount() {
        return parking.length;
    }

    int freeChargers() {
        return freeChargers;
    }

    int freeParking() {
        return freeParking;
    }

    boolean isCharger(int cell) {
        for (int c : chargers) if (c == cell) return true;
        return false;
    }

    /** Claims the free charger nearest to {@code from}, or -1. */
    int claimCharger(int from) {
        int c = nearestFree(chargers, from);
        if (c >= 0) {
            taken[c] = true;
            freeChargers--;
        }
        return c;
    }

    /** Claims the free parking bay nearest to {@code from}, or -1. */
    int claimParking(int from) {
        int c = nearestFree(parking, from);
        if (c >= 0) {
            taken[c] = true;
            freeParking--;
        }
        return c;
    }

    void release(int cell) {
        if (cell < 0 || !taken[cell]) return;
        taken[cell] = false;
        if (isCharger(cell)) freeChargers++;
        else freeParking++;
    }

    private int nearestFree(int[] cells, int from) {
        int best = -1, bestD = Integer.MAX_VALUE;
        for (int c : cells) {
            if (taken[c]) continue;
            int d = df.toGoal(c)[from];
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
