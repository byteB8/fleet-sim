#!/usr/bin/env python3
"""Draws ASCII floor maps as a figure: scripts/draw_map.py OUT_PREFIX "title=map.txt" ["title=map.txt" ...]

Writes OUT_PREFIX-light.png and OUT_PREFIX-dark.png with one panel per map.
"""
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Patch, Rectangle

LIGHT = {"#": "#444444", "S": "#8fd19e", "C": "#ffd27f", "P": "#e0e0e0", ".": "#ffffff", "lane": "#f4f4f4", "bg": "white", "fg": "#111111"}
DARK = {"#": "#bbbbbb", "S": "#3fa65b", "C": "#e0a030", "P": "#3a3a3a", ".": "#111111", "lane": "#1e1e1e", "bg": "#111111", "fg": "#eeeeee"}
ARROW = {">": (1, 0), "<": (-1, 0), "^": (0, -1), "v": (0, 1)}


def draw(ax, rows, pal, title):
    h, w = len(rows), len(rows[0])
    ax.set_xlim(-0.5, w - 0.5)
    ax.set_ylim(h - 0.5, -0.5)
    ax.set_aspect("equal")
    ax.axis("off")
    ax.set_title(title, fontsize=9, color=pal["fg"], loc="left")
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            color = pal.get(ch, pal["lane"])
            ax.add_patch(Rectangle((x - 0.5, y - 0.5), 1, 1, color=color, lw=0))
            if ch in ARROW:
                dx, dy = ARROW[ch]
                ax.arrow(x - 0.3 * dx, y - 0.3 * dy, 0.4 * dx, 0.4 * dy, head_width=0.3, head_length=0.2,
                         color="#888888", lw=0.4, length_includes_head=True)


def main(prefix, specs):
    maps = []
    for spec in specs:
        title, path = spec.split("=", 1)
        rows = [l.rstrip("\n") for l in open(path) if l.strip()]
        maps.append((title, rows))
    for dark, pal in ((False, LIGHT), (True, DARK)):
        n = len(maps)
        cols = 2 if n > 1 else 1
        rows_n = (n + cols - 1) // cols
        w = max(len(r[1][0]) for r in maps)
        h = max(len(r[1]) for r in maps)
        fig, axes = plt.subplots(rows_n, cols, figsize=(cols * w / 7.5, rows_n * (h / 7.5 + 0.5)), dpi=130,
                                 facecolor=pal["bg"], squeeze=False)
        for ax in axes.flat:
            ax.axis("off")
        for ax, (title, rows) in zip(axes.flat, maps):
            draw(ax, rows, pal, title)
        handles = [Patch(color=pal["#"], label="shelf"), Patch(color=pal["S"], label="station"),
                   Patch(color=pal["C"], label="charger"), Patch(color=pal["P"], label="parking bay"),
                   Patch(color=pal["lane"], label="one-way lane (arrow)")]
        fig.legend(handles=handles, loc="lower center", ncol=5, frameon=False, fontsize=8, labelcolor=pal["fg"])
        fig.tight_layout(rect=(0, 0.06, 1, 1))
        fig.savefig(f"{prefix}-{'dark' if dark else 'light'}.png", facecolor=pal["bg"])
        plt.close(fig)
    print(f"wrote {prefix}-light.png and {prefix}-dark.png")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
