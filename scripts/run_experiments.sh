#!/usr/bin/env bash
# Runs every experiment behind the README numbers. Output: results/raw/<experiment>/<name>.json
# Usage: scripts/run_experiments.sh [jar]   (about 5 minutes on a laptop)
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=${1:-target/fleet-sim-0.1.0.jar}
RAW=results/raw
SEEDS="1 2 3"
TICKS=7200
run() { # name, args...
  local out=$1; shift
  java -jar "$JAR" "$@" --out "$RAW/$out.json" | cut -c1-150
}

echo "== E1 fleet size: unlimited admission vs cap 8"
for n in 10 20 30 40 50 60 80 100 120; do
  for cap in 0 8; do
    for s in $SEEDS; do run "fleet/n${n}_cap${cap}_s${s}" --robots $n --ticks $TICKS --station-cap $cap --seed $s; done
  done
done

echo "== E2 assigner: greedy vs hungarian"
for n in 20 40 60; do
  for a in greedy hungarian; do
    for s in $SEEDS; do run "assigner/${a}_n${n}_s${s}" --robots $n --ticks $TICKS --assigner $a --seed $s; done
  done
done

echo "== E3 layout: aisle width and one-way lanes"
for n in 40 60; do
  for lay in "a2_twoway --aisle 2" "a1_twoway --aisle 1" "a1_oneway --aisle 1 --one-way true" "a2_oneway --aisle 2 --one-way true"; do
    set -- $lay; name=$1; shift
    for s in $SEEDS; do run "layout/${name}_n${n}_s${s}" --robots $n --ticks $TICKS "$@" --seed $s; done
  done
done

echo "== E3b one-way 1-wide aisles with a tighter admission cap"
for n in 40 60; do
  for s in $SEEDS; do run "layout/a1_oneway_cap4_n${n}_s${s}" --robots $n --ticks $TICKS --aisle 1 --one-way true --station-cap 4 --seed $s; done
done

echo "== E4 charging: threshold vs opportunistic over an 8 h shift at about 70% load"
for c in threshold opportunistic; do
  for s in $SEEDS; do
    run "charging/${c}_s${s}" --robots 40 --ticks 28800 --arrival 0.4 --charging $c --seed $s
  done
done

echo "== E5 planning window"
for w in "8 4" "16 8" "32 16" "64 32"; do
  set -- $w
  for s in $SEEDS; do run "window/w$1_n60_s${s}" --robots 60 --ticks $TICKS --window $1 --replan $2 --seed $s; done
done

echo "== E6 CBS versus the prioritized planner"
mkdir -p $RAW/cbs
java -cp "$JAR" io.github.byteb8.fleetsim.cli.CbsBench --instances 100 --agents 2,4,6,8,10 --limit-ms 5000 --out $RAW/cbs/bench.json

echo "done"
