#!/usr/bin/env python3
"""Render a GIF of a logged run: scripts/animate.py LOG_DIR MAP_FILE OUT.gif [max_ticks]"""
import csv
import sys
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
from matplotlib.patches import Rectangle

COLORS = {
    "IDLE": "#9e9e9e", "TO_PICKUP": "#1f77b4", "PICKING": "#1f77b4", "TO_DROP": "#d62728",
    "DROPPING": "#d62728", "TO_CHARGER": "#ff7f0e", "CHARGING": "#ff7f0e", "TO_PARK": "#9e9e9e", "PARKED": "#9e9e9e",
}
CELL = {"#": "#444444", "S": "#8fd19e", "C": "#ffd27f", "P": "#e0e0e0"}


def main(log_dir, map_path, out, max_ticks):
    rows = [l.rstrip("\n") for l in open(map_path) if l.strip()]
    h, w = len(rows), len(rows[0])
    pos = defaultdict(dict)
    for r in csv.DictReader(open(f"{log_dir}/positions.csv")):
        t = int(r["t"])
        if t >= max_ticks:
            continue
        pos[t][int(r["robot"])] = (int(r["x"]), int(r["y"]), r["state"])
    ticks = sorted(pos)
    fig, ax = plt.subplots(figsize=(w / 6.5, h / 6.5 + 0.4), dpi=90)
    ax.set_xlim(-0.5, w - 0.5)
    ax.set_ylim(h - 0.5, -0.5)
    ax.set_aspect("equal")
    ax.axis("off")
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            if ch in CELL:
                ax.add_patch(Rectangle((x - 0.5, y - 0.5), 1, 1, color=CELL[ch], lw=0))
            elif ch in "<>^v":
                ax.add_patch(Rectangle((x - 0.5, y - 0.5), 1, 1, color="#f4f4f4", lw=0))
                ax.text(x, y, ch, ha="center", va="center", fontsize=4, color="#999999")
    robots = sorted(pos[ticks[0]])
    scat = ax.scatter([pos[ticks[0]][r][0] for r in robots], [pos[ticks[0]][r][1] for r in robots],
                      s=28, c=[COLORS[pos[ticks[0]][r][2]] for r in robots], zorder=3, edgecolors="black", linewidths=0.3)
    title = ax.set_title("", fontsize=9, loc="left")
    legend = "blue: to pickup   red: to station   orange: charging   grey: idle"

    def update(i):
        t = ticks[i]
        scat.set_offsets([[pos[t][r][0], pos[t][r][1]] for r in robots])
        scat.set_color([COLORS[pos[t][r][2]] for r in robots])
        title.set_text(f"fleet-sim  t = {t:4d} s   {len(robots)} robots      {legend}")
        return scat, title

    anim = FuncAnimation(fig, update, frames=len(ticks), interval=80, blit=False)
    anim.save(out, writer=PillowWriter(fps=12))
    print(f"wrote {out}: {len(ticks)} frames")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 10**9)
