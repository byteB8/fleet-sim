#!/usr/bin/env python3
"""Aggregates results/raw/**.json into results/summary.csv and draws the README figures.

Usage: scripts/plots.py            (run from the repo root after scripts/run_experiments.sh)
Every figure is written in a light and a dark variant under docs/figures/.
"""
import csv
import glob
import json
import os
import statistics as st
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

RAW = "results/raw"
FIG = "docs/figures"
os.makedirs(FIG, exist_ok=True)

C = {"blue": "#1f77b4", "red": "#d62728", "orange": "#ff7f0e", "green": "#2ca02c", "grey": "#7f7f7f", "purple": "#9467bd"}


def load(exp):
    out = []
    for p in sorted(glob.glob(f"{RAW}/{exp}/*.json")):
        d = json.load(open(p))
        d["name"] = os.path.basename(p)[:-5]
        out.append(d)
    return out


def agg(rows, key_fn, field):
    g = defaultdict(list)
    for d in rows:
        g[key_fn(d)].append(d["results"][field])
    return {k: (st.mean(v), st.pstdev(v) if len(v) > 1 else 0.0, len(v)) for k, v in g.items()}


def style(dark):
    plt.rcParams.update({
        "figure.facecolor": "#111111" if dark else "white", "axes.facecolor": "#111111" if dark else "white",
        "axes.edgecolor": "#cccccc" if dark else "#333333", "axes.labelcolor": "#eeeeee" if dark else "#111111",
        "xtick.color": "#eeeeee" if dark else "#111111", "ytick.color": "#eeeeee" if dark else "#111111",
        "text.color": "#eeeeee" if dark else "#111111", "legend.facecolor": "#111111" if dark else "white",
        "legend.edgecolor": "#555555" if dark else "#cccccc", "axes.grid": True, "grid.color": "#333333" if dark else "#dddddd",
        "grid.linewidth": 0.6, "font.size": 10, "axes.titlesize": 11, "savefig.dpi": 150,
    })


def save(fig, name, dark):
    fig.tight_layout()
    fig.savefig(f"{FIG}/{name}-{'dark' if dark else 'light'}.png")
    plt.close(fig)


def errbar(ax, xs, stats, color, label, marker="o"):
    m = [stats[x][0] for x in xs]
    s = [stats[x][1] for x in xs]
    ax.errorbar(xs, m, yerr=s, color=color, marker=marker, ms=4, lw=1.6, capsize=3, label=label)


summary = []


def note(exp, key, stats, field):
    for k, (m, s, n) in sorted(stats.items(), key=lambda kv: str(kv[0])):
        summary.append({"experiment": exp, "key": key, "config": k, "metric": field, "mean": round(m, 3), "std": round(s, 3), "n": n})


def fig_fleet(dark):
    rows = load("fleet")
    if not rows:
        return
    sizes = sorted({d["config"]["robots"] for d in rows})
    fig, axes = plt.subplots(1, 2, figsize=(10, 3.6))
    for cap, color, label in [(0, C["red"], "no admission control"), (8, C["blue"], "admission cap 8 per station")]:
        sub = [d for d in rows if d["config"]["stationCap"] == cap]
        thr = agg(sub, lambda d: d["config"]["robots"], "throughputPerHour")
        fail = agg(sub, lambda d: d["config"]["robots"], "planFailures")
        errbar(axes[0], sizes, thr, color, label)
        errbar(axes[1], sizes, fail, color, label)
        note("fleet", f"cap{cap}", thr, "throughputPerHour")
        note("fleet", f"cap{cap}", fail, "planFailures")
    axes[0].set_xlabel("robots")
    axes[0].set_ylabel("tasks per hour")
    axes[0].set_title("Throughput, saturated backlog, 2 h, 6 stations")
    axes[0].legend(frameon=False, fontsize=8)
    axes[1].set_xlabel("robots")
    axes[1].set_ylabel("forced stays in 2 h")
    axes[1].set_yscale("symlog")
    axes[1].set_title("Planner failures (robots boxed in)")
    save(fig, "fleet", dark)
    # scaling
    sub = [d for d in rows if d["config"]["stationCap"] == 8]
    ms = agg(sub, lambda d: d["config"]["robots"], "planningMsPerTick")
    wall = agg(sub, lambda d: d["config"]["robots"], "wallMs")
    note("fleet", "cap8", ms, "planningMsPerTick")
    note("fleet", "cap8", wall, "wallMs")
    fig, ax = plt.subplots(figsize=(5, 3.4))
    errbar(ax, sizes, ms, C["purple"], "planning time per tick")
    ax.set_xlabel("robots")
    ax.set_ylabel("ms per simulated second")
    ax.set_title("Planning cost (cap 8)")
    save(fig, "scaling", dark)


def bars(ax, groups, series, stats_by_series, colors, ylabel, title):
    import numpy as np
    x = np.arange(len(groups))
    wdt = 0.8 / len(series)
    for i, (s, color) in enumerate(zip(series, colors)):
        m = [stats_by_series[s][g][0] for g in groups]
        e = [stats_by_series[s][g][1] for g in groups]
        ax.bar(x + (i - (len(series) - 1) / 2) * wdt, m, wdt, yerr=e, capsize=3, color=color, label=s)
    ax.set_xticks(x)
    ax.set_xticklabels([str(g) for g in groups])
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.legend(frameon=False, fontsize=8)


def fig_assigner(dark):
    rows = load("assigner")
    if not rows:
        return
    sizes = sorted({d["config"]["robots"] for d in rows})
    fig, axes = plt.subplots(1, 2, figsize=(9, 3.4))
    thr, lat = {}, {}
    for a in ["greedy", "hungarian"]:
        sub = [d for d in rows if d["config"]["assigner"] == a]
        thr[a] = agg(sub, lambda d: d["config"]["robots"], "throughputPerHour")
        lat[a] = agg(sub, lambda d: d["config"]["robots"], "latencyP95")
        note("assigner", a, thr[a], "throughputPerHour")
        note("assigner", a, lat[a], "latencyP95")
        note("assigner", a, agg(sub, lambda d: d["config"]["robots"], "latencyP50"), "latencyP50")
    bars(axes[0], sizes, ["greedy", "hungarian"], thr, [C["grey"], C["blue"]], "tasks per hour", "Throughput by assigner")
    bars(axes[1], sizes, ["greedy", "hungarian"], lat, [C["grey"], C["blue"]], "p95 task latency (s)", "Latency by assigner")
    axes[0].set_xlabel("robots")
    axes[1].set_xlabel("robots")
    save(fig, "assigner", dark)


def fig_layout(dark):
    rows = load("layout")
    if not rows:
        return
    sizes = sorted({d["config"]["robots"] for d in rows})
    names = ["a2_twoway", "a1_twoway", "a1_oneway", "a2_oneway"]
    labels = {"a2_twoway": "2-wide, two-way", "a1_twoway": "1-wide, two-way", "a1_oneway": "1-wide, one-way", "a2_oneway": "2-wide, one-way"}
    thr, fail = {}, {}
    for nme in names:
        sub = [d for d in rows if d["name"].startswith(nme + "_")]
        thr[labels[nme]] = agg(sub, lambda d: d["config"]["robots"], "throughputPerHour")
        fail[labels[nme]] = agg(sub, lambda d: d["config"]["robots"], "planFailures")
        note("layout", nme, thr[labels[nme]], "throughputPerHour")
        note("layout", nme, fail[labels[nme]], "planFailures")
    fig, axes = plt.subplots(1, 2, figsize=(10, 3.6))
    cols = [C["blue"], C["grey"], C["green"], C["orange"]]
    bars(axes[0], sizes, [labels[n] for n in names], thr, cols, "tasks per hour", "Throughput by aisle layout")
    bars(axes[1], sizes, [labels[n] for n in names], fail, cols, "forced stays in 2 h", "Planner failures by layout")
    axes[1].set_yscale("symlog")
    axes[0].set_xlabel("robots")
    axes[1].set_xlabel("robots")
    save(fig, "layout", dark)


def fig_charging(dark):
    rows = load("charging")
    if not rows:
        return
    pols = ["threshold", "opportunistic"]
    fields = [("latencyP95", "p95 task latency (s)"), ("availability", "availability"), ("forcedCharges", "forced charges"), ("backlogMax", "max backlog")]
    fig, axes = plt.subplots(1, 4, figsize=(12, 3.2))
    for ax, (f, lab) in zip(axes, fields):
        stats = {}
        for p in pols:
            sub = [d for d in rows if d["config"]["charging"] == p]
            stats[p] = agg(sub, lambda d: "8 h shift", f)
            note("charging", p, stats[p], f)
        bars(ax, ["8 h shift"], pols, stats, [C["grey"], C["orange"]], lab, lab)
        ax.set_xticklabels([""])
    for p in pols:
        sub = [d for d in rows if d["config"]["charging"] == p]
        for f in ["latencyP50", "latencyMean", "throughputPerHour", "fracCharging", "tasksArrived", "tasksCompleted"]:
            note("charging", p, agg(sub, lambda d: "8 h shift", f), f)
    save(fig, "charging", dark)


def fig_window(dark):
    rows = load("window")
    if not rows:
        return
    ws = sorted({d["config"]["window"] for d in rows})
    thr = agg(rows, lambda d: d["config"]["window"], "throughputPerHour")
    ms = agg(rows, lambda d: d["config"]["window"], "planningMsPerTick")
    fail = agg(rows, lambda d: d["config"]["window"], "planFailures")
    note("window", "n60", thr, "throughputPerHour")
    note("window", "n60", ms, "planningMsPerTick")
    note("window", "n60", fail, "planFailures")
    fig, axes = plt.subplots(1, 2, figsize=(9, 3.4))
    errbar(axes[0], ws, thr, C["blue"], "throughput")
    axes[0].set_xscale("log", base=2)
    axes[0].set_xlabel("planning window (ticks)")
    axes[0].set_ylabel("tasks per hour")
    axes[0].set_title("Throughput vs window, 60 robots")
    errbar(axes[1], ws, ms, C["purple"], "planning ms per tick")
    axes[1].set_xscale("log", base=2)
    axes[1].set_xlabel("planning window (ticks)")
    axes[1].set_ylabel("ms per simulated second")
    axes[1].set_title("Planning cost vs window")
    save(fig, "window", dark)


def fig_cbs(dark):
    p = f"{RAW}/cbs/bench.json"
    if not os.path.exists(p):
        return
    d = json.load(open(p))
    for s in d["summary"]:
        for f in ["cbsSolved", "prioFeasible", "comparable", "prioOptimal", "meanRatio", "worstRatio", "cbsMeanMs", "prioMeanMs"]:
            summary.append({"experiment": "cbs", "key": f"agents{s['agents']}", "config": s["instances"], "metric": f, "mean": s[f], "std": 0, "n": s["instances"]})
    ks = [s["agents"] for s in d["summary"]]
    fig, axes = plt.subplots(1, 2, figsize=(9, 3.4))
    axes[0].plot(ks, [s["meanRatio"] for s in d["summary"]], marker="o", color=C["blue"], label="mean")
    axes[0].plot(ks, [s["worstRatio"] for s in d["summary"]], marker="s", color=C["red"], label="worst")
    axes[0].set_xlabel("agents")
    axes[0].set_ylabel("prioritized / CBS-optimal sum of costs")
    axes[0].set_title("Suboptimality of the prioritized planner")
    axes[0].legend(frameon=False, fontsize=8)
    axes[1].plot(ks, [s["cbsMeanMs"] for s in d["summary"]], marker="o", color=C["red"], label="CBS")
    axes[1].plot(ks, [s["prioMeanMs"] for s in d["summary"]], marker="o", color=C["blue"], label="prioritized")
    axes[1].set_yscale("log")
    axes[1].set_xlabel("agents")
    axes[1].set_ylabel("ms per instance")
    axes[1].set_title("Planning time")
    axes[1].legend(frameon=False, fontsize=8)
    save(fig, "cbs", dark)


for dark in (False, True):
    style(dark)
    summary.clear()
    fig_fleet(dark)
    fig_assigner(dark)
    fig_layout(dark)
    fig_charging(dark)
    fig_window(dark)
    fig_cbs(dark)

with open("results/summary.csv", "w", newline="") as f:
    w = csv.DictWriter(f, fieldnames=["experiment", "key", "config", "metric", "mean", "std", "n"])
    w.writeheader()
    w.writerows(summary)
print(f"wrote results/summary.csv with {len(summary)} rows and figures in {FIG}/")
