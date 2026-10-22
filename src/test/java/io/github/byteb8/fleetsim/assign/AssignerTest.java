package io.github.byteb8.fleetsim.assign;

import static org.junit.jupiter.api.Assertions.*;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AssignerTest {
    static double total(double[][] cost, int[] assign) {
        double s = 0;
        for (int i = 0; i < assign.length; i++) if (assign[i] >= 0) s += cost[i][assign[i]];
        return s;
    }

    static int matched(int[] assign) {
        int k = 0;
        for (int a : assign) if (a >= 0) k++;
        return k;
    }

    /** Best total cost over all matchings that use min(n, m) feasible pairs when possible. */
    static double bruteForce(double[][] cost) {
        int n = cost.length, m = cost[0].length;
        double[] best = {Double.POSITIVE_INFINITY};
        int[] bestSize = {0};
        recurse(cost, 0, new boolean[m], 0, 0.0, best, bestSize);
        return best[0];
    }

    private static void recurse(double[][] c, int i, boolean[] used, int size, double sum, double[] best, int[] bestSize) {
        if (i == c.length) {
            if (size > bestSize[0] || (size == bestSize[0] && sum < best[0])) {
                bestSize[0] = size;
                best[0] = sum;
            }
            return;
        }
        recurse(c, i + 1, used, size, sum, best, bestSize);
        for (int j = 0; j < c[0].length; j++) {
            if (used[j] || c[i][j] == Double.POSITIVE_INFINITY) continue;
            used[j] = true;
            recurse(c, i + 1, used, size + 1, sum + c[i][j], best, bestSize);
            used[j] = false;
        }
    }

    @Test
    void hungarianMatchesBruteForceOnRandomRectangularMatrices() {
        SplittableRandom rnd = new SplittableRandom(11);
        HungarianAssigner h = new HungarianAssigner();
        for (int trial = 0; trial < 400; trial++) {
            int n = 1 + rnd.nextInt(6), m = 1 + rnd.nextInt(6);
            double[][] cost = new double[n][m];
            for (double[] row : cost) {
                for (int j = 0; j < m; j++) row[j] = rnd.nextInt(10) < 2 ? Double.POSITIVE_INFINITY : rnd.nextInt(50);
            }
            int[] a = h.assign(cost);
            boolean[] taskUsed = new boolean[m];
            for (int i = 0; i < n; i++) {
                if (a[i] < 0) continue;
                assertFalse(taskUsed[a[i]], "task given twice");
                taskUsed[a[i]] = true;
                assertNotEquals(Double.POSITIVE_INFINITY, cost[i][a[i]], "infeasible pair used");
            }
            double expected = bruteForce(cost);
            if (expected == Double.POSITIVE_INFINITY) {
                assertEquals(0, matched(a));
            } else {
                assertEquals(expected, total(cost, a), 1e-9, "trial " + trial);
            }
        }
    }

    @Test
    void greedyIsNeverBetterThanHungarianAndCanBeWorse() {
        double[][] cost = {{1, 2}, {2, 100}};
        int[] g = new GreedyAssigner().assign(cost);
        int[] h = new HungarianAssigner().assign(cost);
        assertEquals(101.0, total(cost, g));
        assertEquals(4.0, total(cost, h));
        assertArrayEquals(new int[] {1, 0}, h);
    }

    @Test
    void emptyInputs() {
        assertEquals(0, new HungarianAssigner().assign(new double[0][0]).length);
        assertEquals(0, new GreedyAssigner().assign(new double[0][0]).length);
        int[] a = new HungarianAssigner().assign(new double[][] {{Double.POSITIVE_INFINITY}});
        assertArrayEquals(new int[] {-1}, a);
    }
}
