package io.github.byteb8.fleetsim.assign;

/** Matches robots (rows) to tasks (columns) given a cost matrix. */
public interface Assigner {
    double INFEASIBLE = Double.POSITIVE_INFINITY;

    /**
     * @param cost cost[i][j] of giving task j to robot i; {@link #INFEASIBLE} forbids the pair
     * @return for each robot the task index, or -1 if it gets none
     */
    int[] assign(double[][] cost);

    static Assigner of(String name) {
        return switch (name) {
            case "greedy" -> new GreedyAssigner();
            case "hungarian" -> new HungarianAssigner();
            default -> throw new IllegalArgumentException("unknown assigner: " + name);
        };
    }
}
