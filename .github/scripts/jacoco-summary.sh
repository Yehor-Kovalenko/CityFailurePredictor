#!/usr/bin/env bash
# Prints a Markdown table with per-module line/branch coverage built from JaCoCo CSV reports.
# Usage: jacoco-summary.sh [root-dir]   (typically appended to $GITHUB_STEP_SUMMARY)
set -euo pipefail
export LC_ALL=C

root="${1:-.}"

pct() { awk -v m="$1" -v c="$2" 'BEGIN { t = m + c; if (t == 0) print "n/a"; else printf "%.1f%%", 100 * c / t }'; }

echo "### Backend code coverage (JaCoCo)"
echo
echo "| Module | Line coverage | Branch coverage |"
echo "|---|---:|---:|"

total_lm=0 total_lc=0 total_bm=0 total_bc=0
shopt -s nullglob
for csv in "$root"/services/*/target/site/jacoco/jacoco.csv; do
  module="$(basename "$(dirname "$(dirname "$(dirname "$(dirname "$csv")")")")")"
  # CSV columns: 6=BRANCH_MISSED 7=BRANCH_COVERED 8=LINE_MISSED 9=LINE_COVERED
  read -r bm bc lm lc < <(awk -F, 'NR > 1 { bm += $6; bc += $7; lm += $8; lc += $9 } END { print bm+0, bc+0, lm+0, lc+0 }' "$csv")
  echo "| \`$module\` | $(pct "$lm" "$lc") | $(pct "$bm" "$bc") |"
  total_lm=$((total_lm + lm)); total_lc=$((total_lc + lc))
  total_bm=$((total_bm + bm)); total_bc=$((total_bc + bc))
done

echo "| **Total** | **$(pct "$total_lm" "$total_lc")** | **$(pct "$total_bm" "$total_bc")** |"
