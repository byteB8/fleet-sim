package io.github.byteb8.fleetsim.grid;

/**
 * Parameters of a generated warehouse: a grid of shelf blocks separated by aisles,
 * a two-cell perimeter corridor, chargers and parking bays along the top and side edges,
 * and pick stations along the bottom edge.
 *
 * @param blocksX    shelf blocks per row
 * @param blocksY    shelf block rows
 * @param shelfLen   cells per shelf block (each block is two shelf rows deep)
 * @param aisleWidth width of the aisles between blocks, in cells
 * @param oneWay     make inner aisles one-way, alternating direction, like highways
 * @param stations   number of pick stations on the bottom edge
 * @param chargers   number of chargers on the top edge; remaining edge cells are parking bays
 */
public record WarehouseSpec(int blocksX, int blocksY, int shelfLen, int aisleWidth,
                            boolean oneWay, int stations, int chargers) {
    public WarehouseSpec {
        if (blocksX < 1 || blocksY < 1 || shelfLen < 1 || aisleWidth < 1)
            throw new IllegalArgumentException("bad layout dimensions");
        if (stations < 1 || chargers < 0) throw new IllegalArgumentException("bad station/charger count");
    }

    public static WarehouseSpec defaults() {
        return new WarehouseSpec(6, 4, 8, 2, false, 6, 12);
    }

    public int width() {
        return 6 + blocksX * shelfLen + (blocksX - 1) * aisleWidth;
    }

    public int height() {
        return 6 + blocksY * 2 + (blocksY - 1) * aisleWidth;
    }
}
