#!/usr/bin/env python3
"""Traffic heat map from a logged run: scripts/heatmap.py LOG_DIR MAP_FILE OUT_PREFIX

Counts how many robot-ticks each cell saw (moving or waiting) and draws it over the floor,
in a light and a dark variant.
"""
import csv
import sys
from collections import Counter

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.colors import LogNorm
from matplotlib.patches import Rectangle


def main(log_dir, map_path, prefix):
    rows = [l.rstrip("\n") for l in open(map_path) if l.strip()]
    h, w = len(rows), len(rows[0])
    visits = Counter()
    waits = Counter()
    last = {}
    ticks = 0
    for r in csv.DictReader(open(f"{log_dir}/positions.csv")):
        t, rid = int(r["t"]), int(r["robot"])
        cell = (int(r["x"]), int(r["y"]))
        ticks = max(ticks, t)
        if r["state"] in ("TO_PICKUP", "TO_DROP", "TO_CHARGER", "TO_PARK"):
            visits[cell] += 1
            if last.get(rid) == cell:
                waits[cell] += 1
        last[rid] = cell
    grid = np.zeros((h, w))
    for (x, y), c in visits.items():
        grid[y, x] = c
    for dark in (False, True):
        bg, fg = ("#111111", "#eeeeee") if dark else ("white", "#111111")
        fig, ax = plt.subplots(figsize=(w / 6.5, h / 6.5 + 0.6), dpi=130, facecolor=bg)
        ax.set_facecolor(bg)
        ax.set_xlim(-0.5, w - 0.5)
        ax.set_ylim(h - 0.5, -0.5)
        ax.set_aspect("equal")
        ax.axis("off")
        masked = np.ma.masked_where(grid <= 0, grid)
        im = ax.imshow(masked, cmap="magma" if dark else "YlOrRd", norm=LogNorm(vmin=1, vmax=max(2, grid.max())),
                       interpolation="nearest")
        for y, line in enumerate(rows):
            for x, ch in enumerate(line):
                if ch == "#":
                    ax.add_patch(Rectangle((x - 0.5, y - 0.5), 1, 1, color="#bbbbbb" if dark else "#444444", lw=0))
                elif ch == "S":
                    ax.add_patch(Rectangle((x - 0.5, y - 0.5), 1, 1, fill=False, ec="#2ca02c", lw=1.2))
                elif ch == "C":
                    ax.add_patch(Rectangle((x - 0.5, y - 0.5), 1, 1, fill=False, ec="#ff7f0e", lw=1.2))
        cb = fig.colorbar(im, ax=ax, fraction=0.025, pad=0.01)
        cb.set_label("robot-ticks in cell (log scale)", color=fg, fontsize=8)
        cb.ax.yaxis.set_tick_params(color=fg, labelcolor=fg, labelsize=7)
        total_wait = sum(waits.values())
        total = sum(visits.values())
        ax.set_title(f"Traffic over {ticks} s: {total:,} robot-ticks on the move, {100 * total_wait / max(1, total):.0f}% of them waiting."
                     f"  Green: stations, orange: chargers.", fontsize=8, color=fg, loc="left")
        fig.tight_layout()
        fig.savefig(f"{prefix}-{'dark' if dark else 'light'}.png", facecolor=bg)
        plt.close(fig)
    top = waits.most_common(5)
    print(f"wrote {prefix}-light/dark.png; busiest waiting cells: {top}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
