#!/usr/bin/env python3
"""Independent checker for a fleet-sim run log.

Reads positions.csv and events.csv written with --log DIR and the map written with
--print-map, and verifies, without using any simulator code:

  1. every robot has exactly one position per tick;
  2. every position is a traversable cell;
  3. every move is a wait or a legal one-cell step (bounds, shelves, one-way lanes);
  4. no two robots share a cell at the same tick;
  5. no two robots swap cells between consecutive ticks;
  6. battery never drops below zero and never rises while the robot is not charging;
  7. each task is assigned once, picked up at its pickup cell by its robot, and dropped at a
     station by the same robot, in that order.

Exit status 0 if everything holds; otherwise the violations are printed and the status is 1.
"""
import csv
import sys
from collections import defaultdict

DIRS = {"N": (0, -1), "E": (1, 0), "S": (0, 1), "W": (-1, 0)}
ONE_WAY = {">": "W", "<": "E", "^": "S", "v": "N"}  # the forbidden exit direction


def load_map(path):
    rows = [line.rstrip("\n") for line in open(path) if line.strip()]
    h, w = len(rows), len(rows[0])
    return rows, w, h


def legal_move(rows, w, h, a, b):
    (ax, ay), (bx, by) = a, b
    dx, dy = bx - ax, by - ay
    if (dx, dy) == (0, 0):
        return True
    if abs(dx) + abs(dy) != 1:
        return False
    if not (0 <= bx < w and 0 <= by < h):
        return False
    if rows[by][bx] == "#":
        return False
    src = rows[ay][ax]
    name = {v: k for k, v in DIRS.items()}[(dx, dy)]
    if src in ONE_WAY and ONE_WAY[src] == name:
        return False
    if src in "PCS":  # bays: only the inward direction is allowed
        if ay == 0 and name != "S":
            return False
        if ay == h - 1 and name != "N":
            return False
        if ax == 0 and name != "E":
            return False
        if ax == w - 1 and name != "W":
            return False
    return True


def main(log_dir, map_path):
    rows, w, h = load_map(map_path)
    errors = []
    pos = defaultdict(dict)  # t -> robot -> (x, y, state, battery)
    with open(f"{log_dir}/positions.csv") as f:
        for r in csv.DictReader(f):
            t, rid = int(r["t"]), int(r["robot"])
            if rid in pos[t]:
                errors.append(f"t={t} robot {rid} logged twice")
            pos[t][rid] = (int(r["x"]), int(r["y"]), r["state"], float(r["battery"]))
    ticks = sorted(pos)
    robots = sorted(pos[ticks[0]])
    for t in ticks:
        if sorted(pos[t]) != robots:
            errors.append(f"t={t} robot set differs")
        cells = {}
        for rid, (x, y, st, b) in pos[t].items():
            if not (0 <= x < w and 0 <= y < h) or rows[y][x] == "#":
                errors.append(f"t={t} robot {rid} on untraversable cell ({x},{y})")
            if (x, y) in cells:
                errors.append(f"t={t} robots {cells[(x, y)]} and {rid} both at ({x},{y})")
            cells[(x, y)] = rid
            if b < 0:
                errors.append(f"t={t} robot {rid} battery {b} < 0")
    for i in range(1, len(ticks)):
        t0, t1 = ticks[i - 1], ticks[i]
        if t1 != t0 + 1:
            errors.append(f"gap in ticks between {t0} and {t1}")
        prev, cur = pos[t0], pos[t1]
        for rid in robots:
            a, b = prev[rid][:2], cur[rid][:2]
            if not legal_move(rows, w, h, a, b):
                errors.append(f"t={t0}->{t1} robot {rid} illegal move {a}->{b}")
            if cur[rid][3] > prev[rid][3] + 1e-9 and prev[rid][2] != "CHARGING":
                errors.append(f"t={t1} robot {rid} battery rose while {prev[rid][2]}")
        where = {prev[r][:2]: r for r in robots}
        for rid in robots:
            a, b = prev[rid][:2], cur[rid][:2]
            if a == b:
                continue
            other = where.get(b)
            if other is not None and other != rid and cur[other][:2] == a:
                errors.append(f"t={t0}->{t1} robots {rid} and {other} swap {a}<->{b}")

    tasks = defaultdict(list)
    with open(f"{log_dir}/events.csv") as f:
        for r in csv.DictReader(f):
            tasks[int(r["task"])].append((int(r["t"]), r["event"], int(r["robot"]), int(r["cell"])))
    last_t = ticks[-1]
    checked = 0
    for tid, evs in tasks.items():
        evs.sort()
        names = [e[1] for e in evs]
        expect = ["arrive", "assign", "pickup", "drop"]
        if names != expect[: len(names)]:
            errors.append(f"task {tid} event order {names}")
            continue
        if len(names) < 4:
            continue  # still in flight at the end of the log
        (_, _, _, pickup_cell), (ta, _, ra, _), (tp, _, rp, cp), (td, _, rd, cd) = evs
        if not (ra == rp == rd):
            errors.append(f"task {tid} handled by robots {ra},{rp},{rd}")
        if cp != pickup_cell:
            errors.append(f"task {tid} picked at {cp}, expected {pickup_cell}")
        if rows[cd // w][cd % w] != "S":
            errors.append(f"task {tid} dropped at non-station cell {cd}")
        if tp <= last_t and pos[tp][rp][:2] != (cp % w, cp // w):
            errors.append(f"task {tid} robot {rp} not at pickup cell at t={tp}")
        if td <= last_t and pos[td][rd][:2] != (cd % w, cd // w):
            errors.append(f"task {tid} robot {rd} not at drop cell at t={td}")
        checked += 1

    print(f"ticks={len(ticks)} robots={len(robots)} tasks={len(tasks)} completed_checked={checked} violations={len(errors)}")
    for e in errors[:50]:
        print("  " + e)
    return 1 if errors else 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: check_log.py LOG_DIR MAP_FILE", file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
