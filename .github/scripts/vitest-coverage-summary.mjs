// Prints a Markdown table with total frontend coverage from Vitest's json-summary report.
// Usage: node vitest-coverage-summary.mjs <path/to/coverage-summary.json>   (typically appended to $GITHUB_STEP_SUMMARY)
import { readFileSync } from "node:fs";

const { total } = JSON.parse(readFileSync(process.argv[2], "utf8"));
const metrics = ["lines", "statements", "functions", "branches"];

console.log("### Frontend code coverage (Vitest)\n");
console.log("| Metric | Coverage |");
console.log("|---|---:|");
for (const metric of metrics) {
  console.log(`| ${metric[0].toUpperCase()}${metric.slice(1)} | ${total[metric].pct}% |`);
}
