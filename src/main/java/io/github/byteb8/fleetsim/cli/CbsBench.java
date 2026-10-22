package io.github.byteb8.fleetsim.cli;

import io.github.byteb8.fleetsim.grid.DistanceField;
import io.github.byteb8.fleetsim.grid.GridMap;
import io.github.byteb8.fleetsim.grid.Layouts;
import io.github.byteb8.fleetsim.grid.WarehouseSpec;
import io.github.byteb8.fleetsim.planning.Cbs;
import io.github.byteb8.fleetsim.planning.CooperativePlanner;
import io.github.byteb8.fleetsim.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Compares the prioritized planner (full horizon, priority by id) with CBS-optimal solutions on
 * random small warehouse instances: sum-of-costs ratio, solve rates and planning times.
 */
public final class CbsBench {
    private CbsBench() {}

    public static void main(String[] args) throws IOException {
        Map<String, String> a = Main.parse(args);
        int instances = Integer.parseInt(a.getOrDefault("instances", "100"));
        int[] agentCounts = java.util.Arrays.stream(a.getOrDefault("agents", "2,4,6,8").split(","))
                .mapToInt(Integer::parseInt).toArray();
        long seed = Long.parseLong(a.getOrDefault("seed", "1"));
        long limitMs = Long.parseLong(a.getOrDefault("limit-ms", "5000"));
        GridMap map = Layouts.warehouse(new WarehouseSpec(2, 2, 4, 2, false, 2, 2));
        DistanceField df = new DistanceField(map);
        int[] free = map.cellsOfType(GridMap.FREE);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> summary = new ArrayList<>();
        for (int k : agentCounts) {
            SplittableRandom rnd = new SplittableRandom(seed * 1000 + k);
            int solved = 0, prioFeasible = 0, optimal = 0, both = 0;
            double ratioSum = 0, cbsMs = 0, prioMs = 0;
            double worstRatio = 1;
            for (int inst = 0; inst < instances; inst++) {
                int[] starts = new int[k], goals = new int[k];
                boolean[] usedS = new boolean[map.size()], usedG = new boolean[map.size()];
                for (int i = 0; i < k; i++) {
                    int s, g;
                    do s = free[rnd.nextInt(free.length)]; while (usedS[s]);
                    do g = free[rnd.nextInt(free.length)]; while (usedG[g] || g == s);
                    usedS[s] = true;
                    usedG[g] = true;
                    starts[i] = s;
                    goals[i] = g;
                }
                int[] order = new int[k];
                for (int i = 0; i < k; i++) order[i] = i;
                CooperativePlanner prio = new CooperativePlanner(map, df, 128);
                boolean[] failed = new boolean[k];
                long t0 = System.nanoTime();
                int[][] pp = prio.replanAll(0, starts, goals, order, failed);
                long t1 = System.nanoTime();
                int prioCost = CooperativePlanner.conflictFree(pp) ? Cbs.prioritizedCost(pp, goals) : -1;
                Cbs cbs = new Cbs(map, df);
                long t2 = System.nanoTime();
                int[][] cp = cbs.solve(starts, goals, limitMs * 1_000_000L);
                long t3 = System.nanoTime();
                int cbsCost = cp == null ? -1 : Cbs.sumOfCosts(cp);
                if (cp != null && !CooperativePlanner.conflictFree(padded(cp))) throw new IllegalStateException("CBS solution conflicts");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("agents", k);
                row.put("instance", inst);
                row.put("prioCost", prioCost);
                row.put("cbsCost", cbsCost);
                row.put("prioMs", (t1 - t0) / 1e6);
                row.put("cbsMs", (t3 - t2) / 1e6);
                row.put("cbsHighLevel", cbs.highLevelExpansions());
                rows.add(row);
                if (cbsCost >= 0) {
                    solved++;
                    cbsMs += (t3 - t2) / 1e6;
                }
                if (prioCost >= 0) prioFeasible++;
                prioMs += (t1 - t0) / 1e6;
                if (cbsCost >= 0 && prioCost >= 0) {
                    both++;
                    double r = prioCost / (double) cbsCost;
                    if (prioCost < cbsCost) throw new IllegalStateException("prioritized beat the optimum: instance " + inst);
                    ratioSum += r;
                    worstRatio = Math.max(worstRatio, r);
                    if (prioCost == cbsCost) optimal++;
                }
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("agents", k);
            s.put("instances", instances);
            s.put("cbsSolved", solved);
            s.put("prioFeasible", prioFeasible);
            s.put("comparable", both);
            s.put("prioOptimal", optimal);
            s.put("meanRatio", both == 0 ? Double.NaN : ratioSum / both);
            s.put("worstRatio", worstRatio);
            s.put("cbsMeanMs", solved == 0 ? Double.NaN : cbsMs / solved);
            s.put("prioMeanMs", prioMs / instances);
            summary.add(s);
            System.out.printf("agents=%d cbsSolved=%d/%d prioFeasible=%d prioOptimal=%d/%d meanRatio=%.4f worst=%.3f cbsMs=%.1f prioMs=%.2f%n",
                    k, solved, instances, prioFeasible, optimal, both, both == 0 ? Double.NaN : ratioSum / both, worstRatio,
                    solved == 0 ? Double.NaN : cbsMs / solved, prioMs / instances);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("map", map.toAscii());
        out.put("limitMs", limitMs);
        out.put("summary", summary);
        out.put("rows", rows);
        if (a.containsKey("out")) {
            Path p = Path.of(a.get("out"));
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, Json.write(out) + "\n");
        }
    }

    /** CBS paths end at the goal; pad them to equal length so the pairwise check sees the stays. */
    static int[][] padded(int[][] paths) {
        int max = 0;
        for (int[] p : paths) max = Math.max(max, p.length);
        int[][] out = new int[paths.length][max];
        for (int i = 0; i < paths.length; i++) {
            for (int t = 0; t < max; t++) out[i][t] = paths[i][Math.min(t, paths[i].length - 1)];
        }
        return out;
    }
}
