package io.github.byteb8.fleetsim.grid;

/** The four move directions on the grid. Waiting is not a direction; planners treat it separately. */
public enum Dir {
    N(0, -1), E(1, 0), S(0, 1), W(-1, 0);

    public final int dx;
    public final int dy;

    Dir(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public Dir opposite() {
        return switch (this) {
            case N -> S;
            case S -> N;
            case E -> W;
            case W -> E;
        };
    }

    public static final Dir[] ALL = values();
    public static final int ALL_BITS = 0b1111;
}
