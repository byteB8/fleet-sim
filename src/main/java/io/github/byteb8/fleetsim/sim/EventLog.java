package io.github.byteb8.fleetsim.sim;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** CSV logs of positions and task events, for the independent Python checker and the animation. */
final class EventLog implements AutoCloseable {
    private final BufferedWriter positions;
    private final BufferedWriter events;

    EventLog(Path dir) {
        try {
            Files.createDirectories(dir);
            positions = Files.newBufferedWriter(dir.resolve("positions.csv"));
            positions.write("t,robot,x,y,state,battery\n");
            events = Files.newBufferedWriter(dir.resolve("events.csv"));
            events.write("t,event,task,robot,cell\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void position(int t, Robot r, int x, int y) {
        try {
            positions.write(t + "," + r.id + "," + x + "," + y + "," + r.state + ","
                    + String.format("%.3f", r.battery) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void event(int t, String event, int task, int robot, int cell) {
        try {
            events.write(t + "," + event + "," + task + "," + robot + "," + cell + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            positions.close();
            events.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
