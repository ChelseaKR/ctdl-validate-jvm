#!/usr/bin/env python3
"""Regenerate parity/expected/ from the Python sibling, ctdl-validate.

The expectation files in parity/expected/ are not hand-written. They are the
output of ChelseaKR/ctdl-validate, the reference implementation this repository
ports, run over every fixture in parity/fixtures/. This script is how they are
produced, and CI runs it and fails if the committed files differ, so a golden
file can never drift away from what the reference implementation actually does.

Usage:
    pip install ctdl-validate==<pinned version>
    python3 tools/generate_expectations.py [--check]

--check regenerates into memory and reports differences without writing.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from ctdl_validate import __version__ as reference_version
from ctdl_validate.findings import Finding, Severity
from ctdl_validate.graph import DocumentError
from ctdl_validate.validator import validate_document

ROOT = Path(__file__).resolve().parent.parent
FIXTURES = ROOT / "parity" / "fixtures"
EXPECTED = ROOT / "parity" / "expected"

#: The order the reference implementation counts and prints severities in.
#: Restated here rather than imported: it moved modules between 0.1.0 and the
#: reference's main branch, and this script pins to the released 0.1.0.
SEVERITY_ORDER = (Severity.ERROR, Severity.WARNING, Severity.INFO, Severity.UNVERIFIABLE)


def counts(findings: list[Finding]) -> dict[str, int]:
    return {s.value: sum(1 for f in findings if f.severity is s) for s in SEVERITY_ORDER}


def render_text(findings: list[Finding]) -> str:
    """The plain-text report, exactly as the reference implementation prints it."""
    lines = [f.render_text() + "\n" for f in findings]
    tally = counts(findings)
    summary = ", ".join(f"{tally[s.value]} {s.value}" for s in SEVERITY_ORDER)
    lines.append(f"{len(findings)} finding(s): {summary}")
    return "\n".join(lines)


def parity_document(path: Path) -> dict[str, object]:
    """The comparable result of validating one fixture.

    Deliberately excludes the tool name and version: those differ between the
    two implementations and are the only things allowed to. Everything else --
    exit code, every finding field, every rule citation, the order the findings
    come out in, and the text report a human reads -- must agree.
    """
    data = json.loads(path.read_text(encoding="utf-8"))
    try:
        findings = validate_document(data)
    except DocumentError as exc:
        # The CLI prints the message to stderr and nothing to stdout, so there
        # is no text report to compare in this case.
        return {
            "exit_code": 2,
            "error": str(exc),
            "findings": [],
            "summary": counts([]),
            "text_report": None,
        }
    exit_code = 1 if any(f.severity is Severity.ERROR for f in findings) else 0
    return {
        "exit_code": exit_code,
        "error": None,
        "findings": [f.to_dict() for f in findings],
        "summary": counts(findings),
        "text_report": render_text(findings),
    }


def render(document: dict[str, object]) -> str:
    return json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="report differences instead of writing files",
    )
    args = parser.parse_args(argv)

    fixtures = sorted(FIXTURES.glob("*.json"))
    if not fixtures:
        print(f"no fixtures found in {FIXTURES}", file=sys.stderr)
        return 2

    EXPECTED.mkdir(parents=True, exist_ok=True)
    differences = 0
    for fixture in fixtures:
        target = EXPECTED / fixture.name
        rendered = render(parity_document(fixture))
        if args.check:
            current = target.read_text(encoding="utf-8") if target.exists() else ""
            if current != rendered:
                differences += 1
                print(f"differs: {target.relative_to(ROOT)}", file=sys.stderr)
        else:
            target.write_text(rendered, encoding="utf-8")

    stale = {p.name for p in EXPECTED.glob("*.json")} - {p.name for p in fixtures}
    for name in sorted(stale):
        differences += 1
        print(f"expectation with no fixture: {name}", file=sys.stderr)
        if not args.check:
            (EXPECTED / name).unlink()

    print(
        f"{len(fixtures)} fixture(s) against ctdl-validate {reference_version}"
        + (f"; {differences} difference(s)" if args.check else "")
    )
    return 1 if differences else 0


if __name__ == "__main__":
    raise SystemExit(main())
