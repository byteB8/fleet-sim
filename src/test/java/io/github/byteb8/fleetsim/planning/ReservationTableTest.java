package io.github.byteb8.fleetsim.planning;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReservationTableTest {
    @Test
    void vertexAndSwapConflicts() {
        ReservationTable t = new ReservationTable();
        t.reservePath(1, new int[] {10, 11, 11, 12}, 100);
        assertFalse(t.vertexFree(11, 101, 2));
        assertFalse(t.vertexFree(11, 102, 2));
        assertTrue(t.vertexFree(11, 103, 2));
        assertTrue(t.vertexFree(11, 101, 1), "an agent never conflicts with itself");
        // agent 1 moves 10 -> 11 during tick 100; agent 2 moving 11 -> 10 at 100 would swap
        assertFalse(t.edgeFree(11, 10, 100, 2));
        assertTrue(t.edgeFree(10, 11, 100, 2), "following in the same direction is fine");
        assertTrue(t.edgeFree(11, 10, 101, 2), "no edge during a wait");
    }

    @Test
    void releaseDropsOnlyThatAgent() {
        ReservationTable t = new ReservationTable();
        t.reserveStay(1, 5, 0, 3);
        t.reservePath(2, new int[] {6, 7}, 0);
        t.release(1);
        assertTrue(t.vertexFree(5, 2, 9));
        assertFalse(t.vertexFree(7, 1, 9));
        assertEquals(2, t.vertexCount());
        t.clear();
        assertEquals(0, t.vertexCount());
    }
}
