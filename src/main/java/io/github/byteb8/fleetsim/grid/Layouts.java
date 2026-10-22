package io.github.byteb8.fleetsim.grid;

/** Builds {@link GridMap}s from a {@link WarehouseSpec}. */
public final class Layouts {
    private Layouts() {}

    public static GridMap warehouse(WarehouseSpec s) {
        int w = s.width(), h = s.height();
        GridMap m = new GridMap(w, h);

        // Edge strips: bays that a robot may only leave towards the interior.
        for (int x = 1; x < w - 1; x++) {
            m.setType(m.index(x, 0), GridMap.PARKING);
            m.setAllowed(m.index(x, 0), Dir.S.bit());
            m.setType(m.index(x, h - 1), GridMap.PARKING);
            m.setAllowed(m.index(x, h - 1), Dir.N.bit());
        }
        for (int y = 1; y < h - 1; y++) {
            m.setType(m.index(0, y), GridMap.PARKING);
            m.setAllowed(m.index(0, y), Dir.E.bit());
            m.setType(m.index(w - 1, y), GridMap.PARKING);
            m.setAllowed(m.index(w - 1, y), Dir.W.bit());
        }
        for (int cx : new int[] {0, w - 1}) {
            for (int cy : new int[] {0, h - 1}) m.setType(m.index(cx, cy), GridMap.SHELF);
        }
        // Chargers spread along the top edge; stations spread along the bottom edge.
        for (int k = 0; k < s.chargers(); k++) {
            m.setType(m.index(spread(k, s.chargers(), w), 0), GridMap.CHARGER);
        }
        for (int k = 0; k < s.stations(); k++) {
            m.setType(m.index(spread(k, s.stations(), w), h - 1), GridMap.STATION);
        }

        // Shelf blocks.
        for (int by = 0; by < s.blocksY(); by++) {
            int y0 = 3 + by * (2 + s.aisleWidth());
            for (int bx = 0; bx < s.blocksX(); bx++) {
                int x0 = 3 + bx * (s.shelfLen() + s.aisleWidth());
                for (int x = x0; x < x0 + s.shelfLen(); x++) {
                    m.setType(m.index(x, y0), GridMap.SHELF);
                    m.setType(m.index(x, y0 + 1), GridMap.SHELF);
                }
            }
        }

        if (s.oneWay()) {
            // Horizontal inner aisles alternate E/W; only within shelf columns, so crossings stay two-way.
            for (int a = 0; a < s.blocksY() - 1; a++) {
                int yStart = 3 + a * (2 + s.aisleWidth()) + 2;
                int forbid = (a % 2 == 0) ? Dir.W.bit() : Dir.E.bit();
                for (int bx = 0; bx < s.blocksX(); bx++) {
                    int x0 = 3 + bx * (s.shelfLen() + s.aisleWidth());
                    for (int x = x0; x < x0 + s.shelfLen(); x++) {
                        for (int y = yStart; y < yStart + s.aisleWidth(); y++) {
                            m.setAllowed(m.index(x, y), Dir.ALL_BITS & ~forbid);
                        }
                    }
                }
            }
            // Vertical cross aisles alternate S/N; only beside shelf rows.
            for (int a = 0; a < s.blocksX() - 1; a++) {
                int xStart = 3 + a * (s.shelfLen() + s.aisleWidth()) + s.shelfLen();
                int forbid = (a % 2 == 0) ? Dir.N.bit() : Dir.S.bit();
                for (int by = 0; by < s.blocksY(); by++) {
                    int y0 = 3 + by * (2 + s.aisleWidth());
                    for (int y = y0; y < y0 + 2; y++) {
                        for (int x = xStart; x < xStart + s.aisleWidth(); x++) {
                            m.setAllowed(m.index(x, y), Dir.ALL_BITS & ~forbid);
                        }
                    }
                }
            }
        }
        return m;
    }

    /** k-th of n positions spread evenly over the interior columns 1..w-2. */
    static int spread(int k, int n, int w) {
        int inner = w - 2;
        return 1 + (int) ((long) (2 * k + 1) * inner / (2L * n));
    }
}
