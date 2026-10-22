package io.github.byteb8.fleetsim.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A rectangular grid of cells. Cells are addressed by index {@code y * width + x}.
 * Each cell has a type and a bitmask of directions a robot may leave it in,
 * which is how one-way lanes are expressed.
 */
public final class GridMap {
    public static final byte FREE = 0;
    public static final byte SHELF = 1;
    public static final byte STATION = 2;
    public static final byte CHARGER = 3;
    public static final byte PARKING = 4;

    public final int width;
    public final int height;
    private final byte[] type;
    private final byte[] allowed;

    public GridMap(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("empty map");
        this.width = width;
        this.height = height;
        this.type = new byte[width * height];
        this.allowed = new byte[width * height];
        Arrays.fill(allowed, (byte) Dir.ALL_BITS);
    }

    public int size() {
        return width * height;
    }

    public int index(int x, int y) {
        return y * width + x;
    }

    public int x(int idx) {
        return idx % width;
    }

    public int y(int idx) {
        return idx / width;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public byte type(int idx) {
        return type[idx];
    }

    public void setType(int idx, byte t) {
        type[idx] = t;
    }

    public int allowed(int idx) {
        return allowed[idx];
    }

    public void setAllowed(int idx, int mask) {
        allowed[idx] = (byte) (mask & Dir.ALL_BITS);
    }

    public boolean traversable(int idx) {
        return type[idx] != SHELF;
    }

    /** Target cell of a move from {@code idx} in direction {@code d}, or -1 if the move is not allowed. */
    public int step(int idx, Dir d) {
        int nx = x(idx) + d.dx;
        int ny = y(idx) + d.dy;
        if (!inBounds(nx, ny) || !traversable(idx)) return -1;
        if ((allowed[idx] & d.bit()) == 0) return -1;
        int to = index(nx, ny);
        return traversable(to) ? to : -1;
    }

    public boolean adjacent(int a, int b) {
        return Math.abs(x(a) - x(b)) + Math.abs(y(a) - y(b)) == 1;
    }

    public int[] cellsOfType(byte t) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < type.length; i++) if (type[i] == t) out.add(i);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** FREE cells that are 4-adjacent to a shelf: where a robot stands to pick. */
    public int[] pickupCells() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < type.length; i++) {
            if (type[i] != FREE) continue;
            int x = x(i), y = y(i);
            for (Dir d : Dir.ALL) {
                int nx = x + d.dx, ny = y + d.dy;
                if (inBounds(nx, ny) && type[index(nx, ny)] == SHELF) {
                    out.add(i);
                    break;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * ASCII form. {@code #} shelf, {@code .} free, {@code S} station, {@code C} charger,
     * {@code P} parking, and {@code > < ^ v} for free one-way cells (the reverse direction is forbidden).
     */
    public static GridMap parse(String ascii) {
        String[] lines = ascii.strip().split("\n");
        int h = lines.length;
        int w = lines[0].length();
        GridMap m = new GridMap(w, h);
        for (int y = 0; y < h; y++) {
            String line = lines[y].stripTrailing();
            if (line.length() != w) throw new IllegalArgumentException("ragged map at row " + y);
            for (int x = 0; x < w; x++) {
                int i = m.index(x, y);
                char c = line.charAt(x);
                switch (c) {
                    case '#' -> m.setType(i, SHELF);
                    case '.' -> m.setType(i, FREE);
                    case 'S' -> m.setType(i, STATION);
                    case 'C' -> m.setType(i, CHARGER);
                    case 'P' -> m.setType(i, PARKING);
                    case '>' -> m.setAllowed(i, Dir.ALL_BITS & ~Dir.W.bit());
                    case '<' -> m.setAllowed(i, Dir.ALL_BITS & ~Dir.E.bit());
                    case '^' -> m.setAllowed(i, Dir.ALL_BITS & ~Dir.S.bit());
                    case 'v' -> m.setAllowed(i, Dir.ALL_BITS & ~Dir.N.bit());
                    default -> throw new IllegalArgumentException("bad map char '" + c + "'");
                }
            }
        }
        return m;
    }

    public String toAscii() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = index(x, y);
                char c = switch (type[i]) {
                    case SHELF -> '#';
                    case STATION -> 'S';
                    case CHARGER -> 'C';
                    case PARKING -> 'P';
                    default -> freeChar(allowed[i]);
                };
                sb.append(c);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static char freeChar(int mask) {
        if (mask == Dir.ALL_BITS) return '.';
        if (mask == (Dir.ALL_BITS & ~Dir.W.bit())) return '>';
        if (mask == (Dir.ALL_BITS & ~Dir.E.bit())) return '<';
        if (mask == (Dir.ALL_BITS & ~Dir.S.bit())) return '^';
        if (mask == (Dir.ALL_BITS & ~Dir.N.bit())) return 'v';
        return '.';
    }
}
