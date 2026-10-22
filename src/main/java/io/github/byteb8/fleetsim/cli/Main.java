package io.github.byteb8.fleetsim.cli;

import io.github.byteb8.fleetsim.grid.Layouts;
import io.github.byteb8.fleetsim.grid.WarehouseSpec;
import io.github.byteb8.fleetsim.sim.Metrics;
import io.github.byteb8.fleetsim.sim.SimConfig;
import io.github.byteb8.fleetsim.sim.Simulator;
import io.github.byteb8.fleetsim.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Command line: {@code java -jar fleet-sim.jar --robots 50 --ticks 3600 --out run.json}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        Map<String, String> a = parse(args);
        if (a.containsKey("help")) {
            System.out.println(usage());
            return;
        }
        SimConfig cfg = configure(a);
        if (a.containsKey("print-map")) {
            System.out.print(cfg.mapAscii != null ? cfg.mapAscii : Layouts.warehouse(cfg.layout).toAscii());
            return;
        }
        Simulator sim = new Simulator(cfg);
        Metrics m = sim.run();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("config", describe(cfg));
        out.put("results", m.summary(cfg));
        String json = Json.write(out);
        if (a.containsKey("out")) {
            Path p = Path.of(a.get("out"));
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, json + "\n");
        }
        Map<String, Object> r = m.summary(cfg);
        System.out.printf("robots=%d ticks=%d assigner=%s charging=%s arrival=%s completed=%d throughput/h=%.0f "
                        + "latencyP50=%.0f p95=%.0f availability=%.3f planFailures=%d planMs/tick=%.2f wall=%.0fms%n",
                cfg.robots, cfg.ticks, cfg.assigner, cfg.charging, cfg.saturated() ? "saturated" : String.valueOf(cfg.arrivalRate),
                m.tasksCompleted, (Double) r.get("throughputPerHour"), (Double) r.get("latencyP50"),
                (Double) r.get("latencyP95"), (Double) r.get("availability"), m.planFailures,
                (Double) r.get("planningMsPerTick"), (Double) r.get("wallMs"));
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("unexpected argument " + args[i]);
            String key = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) m.put(key, args[++i]);
            else m.put(key, "true");
        }
        return m;
    }

    static SimConfig configure(Map<String, String> a) throws IOException {
        SimConfig c = new SimConfig();
        WarehouseSpec d = WarehouseSpec.defaults();
        c.layout = new WarehouseSpec(
                Integer.parseInt(a.getOrDefault("blocks-x", String.valueOf(d.blocksX()))),
                Integer.parseInt(a.getOrDefault("blocks-y", String.valueOf(d.blocksY()))),
                Integer.parseInt(a.getOrDefault("shelf-len", String.valueOf(d.shelfLen()))),
                Integer.parseInt(a.getOrDefault("aisle", String.valueOf(d.aisleWidth()))),
                Boolean.parseBoolean(a.getOrDefault("one-way", "false")),
                Integer.parseInt(a.getOrDefault("stations", String.valueOf(d.stations()))),
                Integer.parseInt(a.getOrDefault("chargers", String.valueOf(d.chargers()))));
        if (a.containsKey("map")) c.mapAscii = Files.readString(Path.of(a.get("map")));
        c.robots = Integer.parseInt(a.getOrDefault("robots", "20"));
        c.ticks = Integer.parseInt(a.getOrDefault("ticks", "3600"));
        c.seed = Long.parseLong(a.getOrDefault("seed", "1"));
        c.assigner = a.getOrDefault("assigner", "hungarian");
        c.charging = a.getOrDefault("charging", "opportunistic");
        c.arrivalRate = Double.parseDouble(a.getOrDefault("arrival", "-1"));
        c.window = Integer.parseInt(a.getOrDefault("window", "16"));
        c.replanEvery = Integer.parseInt(a.getOrDefault("replan", "8"));
        c.pickupDwell = Integer.parseInt(a.getOrDefault("pickup-dwell", "3"));
        c.dropDwell = Integer.parseInt(a.getOrDefault("drop-dwell", "3"));
        c.runtimeTicks = Double.parseDouble(a.getOrDefault("runtime", "14400"));
        c.chargeTicks = Double.parseDouble(a.getOrDefault("charge-time", "3600"));
        c.batteryLow = Double.parseDouble(a.getOrDefault("battery-low", "20"));
        c.batteryHigh = Double.parseDouble(a.getOrDefault("battery-high", "90"));
        c.batteryLeave = Double.parseDouble(a.getOrDefault("battery-leave", "30"));
        c.stationCap = Integer.parseInt(a.getOrDefault("station-cap", "8"));
        c.assignBatch = Integer.parseInt(a.getOrDefault("assign-batch", "4"));
        c.weightQueue = Double.parseDouble(a.getOrDefault("w-queue", "4"));
        c.weightAge = Double.parseDouble(a.getOrDefault("w-age", "0.1"));
        c.logDir = a.get("log");
        c.logTicks = Integer.parseInt(a.getOrDefault("log-ticks", String.valueOf(Integer.MAX_VALUE)));
        return c;
    }

    static Map<String, Object> describe(SimConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("layout", c.mapAscii != null ? "file" : c.layout.toString());
        m.put("robots", c.robots);
        m.put("ticks", c.ticks);
        m.put("seed", c.seed);
        m.put("assigner", c.assigner);
        m.put("charging", c.charging);
        m.put("arrivalRate", c.arrivalRate);
        m.put("window", c.window);
        m.put("replanEvery", c.replanEvery);
        m.put("pickupDwell", c.pickupDwell);
        m.put("dropDwell", c.dropDwell);
        m.put("runtimeTicks", c.runtimeTicks);
        m.put("chargeTicks", c.chargeTicks);
        m.put("batteryLow", c.batteryLow);
        m.put("batteryHigh", c.batteryHigh);
        m.put("batteryLeave", c.batteryLeave);
        m.put("stationCap", c.stationCap);
        m.put("assignBatch", c.assignBatch);
        m.put("weightQueue", c.weightQueue);
        m.put("weightAge", c.weightAge);
        return m;
    }

    static String usage() {
        return """
                fleet-sim: warehouse robot fleet simulator
                  --robots N --ticks T --seed S
                  --assigner greedy|hungarian   --charging threshold|opportunistic
                  --arrival R   tasks per tick (default -1 = saturated backlog)
                  --window W --replan K         planning window and full-replan period (K <= W)
                  --station-cap N               tasks assigned per station at once (0 = unlimited)
                  --blocks-x --blocks-y --shelf-len --aisle --one-way --stations --chargers   layout
                  --map FILE                    ASCII map instead of the generated layout
                  --log DIR [--log-ticks N]     write positions.csv and events.csv
                  --out FILE                    write the JSON summary
                  --print-map                   print the map and exit
                """;
    }
}
