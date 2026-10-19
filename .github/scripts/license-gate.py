#!/usr/bin/env python3
import argparse
import json
import re
import sys
from collections import Counter
from fnmatch import fnmatchcase
from pathlib import Path

STRONG_COPYLEFT_NAME = re.compile(r"\bAGPL\b|Affero|\bGPL\b|General Public License", re.IGNORECASE)
WEAK_OR_EXCEPTED_NAME = re.compile(r"Lesser|Library|\bLGPL\b|Classpath", re.IGNORECASE)


def license_terms(component):
    terms = []
    for entry in component.get("licenses") or []:
        if "expression" in entry:
            terms.append(entry["expression"])
        else:
            lic = entry.get("license") or {}
            terms.append(lic.get("id") or lic.get("name") or "")
    return [t for t in terms if t]


def matches(term, patterns):
    return any(fnmatchcase(term, p) for p in patterns)


def is_denied_term(term, policy):
    if matches(term, policy["allowed"]):
        return False
    if matches(term, policy["denied"]):
        return True
    return bool(STRONG_COPYLEFT_NAME.search(term)) and not WEAK_OR_EXCEPTED_NAME.search(term)


def is_denied_expression(expression, policy):
    alternatives = re.split(r"\s+OR\s+", expression.strip("() "))
    return all(
        any(is_denied_term(part.strip("() "), policy) for part in re.split(r"\s+AND\s+", alt))
        for alt in alternatives
    )


def evaluate(component, policy):
    terms = license_terms(component)
    if not terms:
        return "unknown", terms
    if all(is_denied_expression(t, policy) for t in terms):
        return "denied", terms
    return "allowed", terms


def find_exception(purl, terms, policy):
    for exc in policy["exceptions"]:
        if purl.startswith(exc["package"]) and exc["license"] in terms:
            return exc
    return None


def main():
    parser = argparse.ArgumentParser(description="License policy gate over CycloneDX SBOMs")
    parser.add_argument("sboms", nargs="+")
    parser.add_argument("--policy", default=".github/license-policy.json")
    parser.add_argument("--title", default="License compliance")
    args = parser.parse_args()

    policy = json.loads(Path(args.policy).read_text())
    ecosystems = tuple(f"pkg:{e}/" for e in policy["ecosystems"])

    seen = {}
    for path in args.sboms:
        for component in json.loads(Path(path).read_text()).get("components") or []:
            purl = component.get("purl", "")
            if purl.startswith(ecosystems) and not purl.startswith(tuple(policy["ignored"])):
                seen.setdefault(purl.split("?")[0], component)

    distribution = Counter()
    violations, accepted, unknown = [], [], []
    for purl, component in sorted(seen.items()):
        status, terms = evaluate(component, policy)
        distribution.update(terms or ["(none)"])
        row = (purl, " / ".join(terms))
        if status == "unknown":
            unknown.append(row)
        elif status == "denied":
            exc = find_exception(purl, terms, policy)
            (accepted if exc else violations).append(row + ((exc["reason"],) if exc else ()))

    out = [f"### {args.title}", ""]
    out.append(f"**{len(seen)}** packages checked against `{args.policy}` · **{len(violations)}** violations · "
               f"{len(accepted)} accepted exceptions · {len(unknown)} without license data")
    out.append("")
    if violations:
        out += [f"#### :x: Denied licenses ({len(violations)})", "", "| Package | License |", "|---|---|"]
        out += [f"| `{p}` | {lic} |" for p, lic in violations]
        out.append("")
    else:
        out += ["#### :white_check_mark: No denied licenses", ""]
    if accepted:
        out += ["#### Accepted exceptions", "", "| Package | License | Reason |", "|---|---|---|"]
        out += [f"| `{p}` | {lic} | {reason} |" for p, lic, reason in accepted]
        out.append("")
    if unknown:
        out += ["<details><summary>Packages without license data</summary>", ""]
        out += [f"- `{p}`" for p, _ in unknown]
        out += ["", "</details>", ""]
    out += ["<details><summary>License distribution</summary>", "", "| License | Packages |", "|---|---:|"]
    out += [f"| {lic} | {n} |" for lic, n in distribution.most_common()]
    out += ["", "</details>"]
    print("\n".join(out))

    for purl, lic in violations:
        print(f"::error title=Denied license::{purl} is licensed under {lic}", file=sys.stderr)
    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
