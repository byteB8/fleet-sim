package io.github.byteb8.fleetsim.assign;

import java.util.Arrays;

/** Tasks in column order (the caller puts the oldest first); each takes the cheapest free robot. */
public final class GreedyAssigner implements Assigner {
    @Override
    public int[] assign(double[][] cost) {
        int n = cost.length;
        int[] out = new int[n];
        Arrays.fill(out, -1);
        if (n == 0) return out;
        int m = cost[0].length;
        boolean[] robotUsed = new boolean[n];
        for (int j = 0; j < m; j++) {
            int best = -1;
            double bestCost = INFEASIBLE;
            for (int i = 0; i < n; i++) {
                if (robotUsed[i]) continue;
                if (cost[i][j] < bestCost) {
                    bestCost = cost[i][j];
                    best = i;
                }
            }
            if (best >= 0) {
                robotUsed[best] = true;
                out[best] = j;
            }
        }
        return out;
    }
}
