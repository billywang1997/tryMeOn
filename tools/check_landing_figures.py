#!/usr/bin/env python3
"""Fails if docs/index.html quotes a figure the cost engine does not produce.

The ledgers on the landing page are shown to affiliate reviewers, so they have
to come from the engine rather than from someone reading a screenshot. Run
`./gradlew :app:testDebugUnitTest --tests '*LandingPageFiguresTest*'` first: it
writes the expected rows to app/build/landing-ledger-*.html.
"""
import html
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def rows(markup):
    """(label, amount) for each ledger row, tags and entities stripped."""
    out = []
    for cell_a, cell_b in re.findall(r"<tr><td>(.*?)</td><td>(.*?)</td></tr>", markup, re.S):
        label = html.unescape(re.sub(r"<[^>]+>", "", cell_a)).strip()
        # A row may annotate its label ("Freight — billed on 1.5 kg..."); the
        # engine's label is the part before the dash.
        label = re.split(r"\s+[—-]\s+", label)[0].strip()
        out.append((label, html.unescape(cell_b).strip()))
    return out


def main():
    page = rows((ROOT / "docs/index.html").read_text())
    missing = []
    for ledger in sorted((ROOT / "app/build").glob("landing-ledger-*.html")):
        for row in rows(ledger.read_text()):
            if row not in page:
                missing.append(f"{ledger.name}: {row[0]} = {row[1]}")
    if missing:
        print("Landing page does not match the engine:")
        for m in missing:
            print("  " + m)
        return 1
    print(f"docs/index.html: every engine figure accounted for ({len(page)} rows checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
