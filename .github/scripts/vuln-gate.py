#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path

SEVERITIES = ["Unknown", "Negligible", "Low", "Medium", "High", "Critical"]


def severity_rank(name):
    return SEVERITIES.index(name) if name in SEVERITIES else 0


def load_matches(paths):
    findings = {}
    ignored = 0
    for path in paths:
        report = json.loads(Path(path).read_text())
        ignored += len(report.get("ignoredMatches") or [])
        for match in report.get("matches") or []:
            vuln = match["vulnerability"]
            artifact = match["artifact"]
            key = (vuln["id"], artifact["name"], artifact["version"])
            if key in findings:
                continue
            cves = [r["id"] for r in match.get("relatedVulnerabilities") or [] if r["id"].startswith("CVE-")]
            if vuln["id"].startswith("CVE-"):
                cves.insert(0, vuln["id"])
            findings[key] = {
                "id": vuln["id"],
                "cve": cves[0] if cves else "",
                "url": vuln.get("dataSource", ""),
                "severity": vuln.get("severity", "Unknown"),
                "epss": max((e["epss"] for e in vuln.get("epss") or []), default=0.0),
                "kev": bool(vuln.get("knownExploited")),
                "risk": vuln.get("risk") or 0.0,
                "fix": ", ".join(vuln.get("fix", {}).get("versions") or []),
                "package": artifact["name"],
                "version": artifact["version"],
                "type": artifact.get("type", ""),
            }
    return list(findings.values()), ignored


def is_blocking(finding, epss_threshold, min_severity):
    if finding["kev"]:
        return True
    return (
        severity_rank(finding["severity"]) >= severity_rank(min_severity)
        and finding["epss"] >= epss_threshold
        and bool(finding["fix"])
    )


def table(rows):
    lines = [
        "| Vulnerability | Package | Installed | Fixed in | Severity | EPSS | KEV |",
        "|---|---|---|---|---|---:|:---:|",
    ]
    for f in rows:
        label = f["id"] if not f["cve"] or f["cve"] == f["id"] else f"{f['id']} ({f['cve']})"
        link = f"[{label}]({f['url']})" if f["url"] else label
        lines.append(
            f"| {link} | `{f['package']}` | {f['version']} | {f['fix'] or '-'} | {f['severity']} "
            f"| {f['epss']:.1%} | {'yes' if f['kev'] else ''} |"
        )
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Risk-based gate over Grype JSON reports")
    parser.add_argument("reports", nargs="+")
    parser.add_argument("--title", default="Vulnerability scan")
    parser.add_argument("--epss-threshold", type=float, default=0.1)
    parser.add_argument("--min-severity", default="High", choices=SEVERITIES)
    parser.add_argument("--top", type=int, default=10)
    args = parser.parse_args()

    findings, ignored = load_matches(args.reports)
    findings.sort(key=lambda f: (f["kev"], f["risk"], f["epss"], severity_rank(f["severity"])), reverse=True)
    blocking = [f for f in findings if is_blocking(f, args.epss_threshold, args.min_severity)]
    others = [f for f in findings if f not in blocking]

    counts = {s: sum(1 for f in findings if f["severity"] == s) for s in reversed(SEVERITIES)}
    breakdown = ", ".join(f"{n} {s}" for s, n in counts.items() if n)
    kev = sum(1 for f in findings if f["kev"])
    likely = sum(1 for f in findings if f["epss"] >= args.epss_threshold)

    out = [f"### {args.title}", ""]
    out.append(f"**{len(findings)}** vulnerabilities ({breakdown or 'none'}) · **{kev}** in CISA KEV · "
               f"**{likely}** with EPSS ≥ {args.epss_threshold:.0%} · {ignored} accepted in `.grype.yaml`")
    out.append("")
    out.append(f"Policy: block on any CISA KEV entry, or on {args.min_severity}+ severity with "
               f"EPSS ≥ {args.epss_threshold:.0%} and a fix available.")
    out.append("")
    if blocking:
        out += [f"#### :x: Blocking ({len(blocking)})", "", table(blocking), ""]
    else:
        out += ["#### :white_check_mark: No blocking vulnerabilities", ""]
    if others:
        out += [f"#### Top {min(args.top, len(others))} non-blocking by risk", "", table(others[: args.top]), ""]
    print("\n".join(out))

    for f in blocking:
        reason = "listed in CISA KEV" if f["kev"] else f"EPSS {f['epss']:.1%}"
        print(f"::error title={f['id']} in {f['package']} {f['version']}::{f['severity']}, {reason}, "
              f"fixed in {f['fix'] or 'n/a'}", file=sys.stderr)

    return 1 if blocking else 0


if __name__ == "__main__":
    sys.exit(main())
