package io.github.byteb8.fleetsim.assign;

import java.util.Arrays;

/**
 * Minimum-cost assignment by the Hungarian algorithm (Kuhn-Munkres, potentials form), O(n^2 m).
 * Rectangular matrices are handled by transposing so that rows <= columns; infeasible pairs
 * get a large finite cost and are dropped from the answer if the optimum still uses them.
 */
public final class HungarianAssigner implements Assigner {
    static final double BIG = 1e12;

    @Override
    public int[] assign(double[][] cost) {
        int n = cost.length;
        int[] out = new int[n];
        Arrays.fill(out, -1);
        if (n == 0 || cost[0].length == 0) return out;
        int m = cost[0].length;
        boolean transposed = n > m;
        double[][] a = transposed ? transpose(cost) : cost;
        int[] rowToCol = solve(a);
        for (int r = 0; r < rowToCol.length; r++) {
            int c = rowToCol[r];
            if (c < 0) continue;
            int robot = transposed ? c : r;
            int task = transposed ? r : c;
            if (cost[robot][task] >= BIG / 2) continue;
            out[robot] = task;
        }
        return out;
    }

    private static double[][] transpose(double[][] a) {
        double[][] t = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) t[j][i] = a[i][j];
        return t;
    }

    /** rows <= cols. Returns the column matched to each row. */
    static int[] solve(double[][] a) {
        int n = a.length, m = a[0].length;
        double[] u = new double[n + 1], v = new double[m + 1];
        int[] p = new int[m + 1], way = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[m + 1];
            boolean[] used = new boolean[m + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            do {
                used[j0] = true;
                int i0 = p[j0], j1 = 0;
                double delta = Double.POSITIVE_INFINITY;
                for (int j = 1; j <= m; j++) {
                    if (used[j]) continue;
                    double c = a[i0 - 1][j - 1];
                    if (c == Double.POSITIVE_INFINITY) c = BIG;
                    double cur = c - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] rowToCol = new int[n];
        Arrays.fill(rowToCol, -1);
        for (int j = 1; j <= m; j++) if (p[j] != 0) rowToCol[p[j] - 1] = j - 1;
        return rowToCol;
    }
}
