#!/usr/bin/env python3
"""Regenerate parity/expected/ and parity/ahead/reference/ from ctdl-validate.

The expectation files in parity/expected/ are not hand-written. They are the
output of ChelseaKR/ctdl-validate, the reference implementation this repository
ports, run over every fixture in parity/fixtures/. This script is how they are
produced, and CI runs it and fails if the committed files differ, so a golden
file can never drift away from what the reference implementation actually does.

parity/ahead/ is the same idea for the narrow set of behaviours where this port
deliberately leads the pinned release, and its reference documents are recorded
by the same code path for the same reason: what is written down there has to be
what the pinned reference really says, not a recollection of it. See
docs/adr/0004-the-port-may-lead-the-pinned-reference.md.

Usage:
    pip install ctdl-validate==<pinned version>
    python3 tools/generate_expectations.py [--check]

--check regenerates into memory and reports differences without writing.
"""

from __future__ import annotations

import argparse
import ast
import json
import sys
from pathlib import Path

import ctdl_validate
from ctdl_validate import __version__ as reference_version
from ctdl_validate.findings import Finding, Severity
from ctdl_validate.graph import DocumentError
from ctdl_validate.validator import validate_document

ROOT = Path(__file__).resolve().parent.parent

#: Where the reference implementation's own finding-code census is written.
#: ParityTest reads it to answer "what rules does the other side have?", which
#: this port cannot answer from anything it maintains itself.
REFERENCE_CODES = ROOT / "parity" / "reference-codes.json"

#: (fixtures, output) pairs. The first is the byte-equality corpus; the second
#: records what the pinned reference says about the fixtures this port answers
#: differently, so the divergence is measured against the reference rather than
#: asserted from memory.
CORPORA = (
    (ROOT / "parity" / "fixtures", ROOT / "parity" / "expected"),
    (ROOT / "parity" / "ahead" / "fixtures", ROOT / "parity" / "ahead" / "reference"),
)

#: The order the reference implementation counts and prints severities in.
#: Restated here rather than imported: it moved modules between 0.1.0 and the
#: reference's main branch, and this script pins to the released 0.1.0.
SEVERITY_ORDER = (Severity.ERROR, Severity.WARNING, Severity.INFO, Severity.UNVERIFIABLE)


def reference_finding_codes() -> list[str]:
    """Every finding code the installed reference implementation can construct.

    Parsed out of the reference's own source with ``ast``, not read off a list
    either side maintains. That is the whole point: a rule the reference has and
    this port has never heard of is invisible to a coverage test built from the
    port's own ``FindingCodes.ALL``, because such a rule is missing from the
    port's list, from the port's output, and from parity/expected/ alike.

    A ``Finding(...)`` call whose ``code`` is not a plain string literal raises
    rather than being skipped. A census that silently drops what it cannot read
    would report a smaller rule set than the reference really has, which is the
    same blindness in a new place.
    """
    package = Path(ctdl_validate.__file__).parent
    sources = sorted(package.rglob("*.py"))
    if not sources:
        raise SystemExit(f"no reference source found under {package}")

    codes: set[str] = set()
    for source in sources:
        tree = ast.parse(source.read_text(encoding="utf-8"), filename=str(source))
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            name = node.func.id if isinstance(node.func, ast.Name) else None
            if name != "Finding":
                continue
            keywords = {kw.arg: kw.value for kw in node.keywords}
            code = keywords.get("code")
            if code is None and node.args:
                code = node.args[0]
            if not isinstance(code, ast.Constant) or not isinstance(code.value, str):
                raise SystemExit(
                    f"{source.relative_to(package)}:{node.lineno}: Finding() built with a "
                    "non-literal code; this census cannot see it, so it must not pass silently"
                )
            codes.add(code.value)
    if not codes:
        raise SystemExit(f"parsed {len(sources)} reference source file(s) and found no codes")
    return sorted(codes)


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

    total = 0
    differences = 0
    for fixture_dir, output_dir in CORPORA:
        fixtures = sorted(fixture_dir.glob("*.json"))
        if not fixtures:
            print(f"no fixtures found in {fixture_dir}", file=sys.stderr)
            return 2
        total += len(fixtures)

        output_dir.mkdir(parents=True, exist_ok=True)
        for fixture in fixtures:
            target = output_dir / fixture.name
            rendered = render(parity_document(fixture))
            if args.check:
                current = target.read_text(encoding="utf-8") if target.exists() else ""
                if current != rendered:
                    differences += 1
                    print(f"differs: {target.relative_to(ROOT)}", file=sys.stderr)
            else:
                target.write_text(rendered, encoding="utf-8")

        stale = {p.name for p in output_dir.glob("*.json")} - {p.name for p in fixtures}
        for name in sorted(stale):
            differences += 1
            print(f"expectation with no fixture: {name}", file=sys.stderr)
            if not args.check:
                (output_dir / name).unlink()

    codes = reference_finding_codes()
    rendered_codes = render(
        {
            "reference": "ctdl-validate",
            "version": reference_version,
            "codes": codes,
        }
    )
    if args.check:
        current = REFERENCE_CODES.read_text(encoding="utf-8") if REFERENCE_CODES.exists() else ""
        if current != rendered_codes:
            differences += 1
            print(f"differs: {REFERENCE_CODES.relative_to(ROOT)}", file=sys.stderr)
    else:
        REFERENCE_CODES.write_text(rendered_codes, encoding="utf-8")

    print(
        f"{total} fixture(s) and {len(codes)} reference finding code(s) "
        f"against ctdl-validate {reference_version}"
        + (f"; {differences} difference(s)" if args.check else "")
    )
    return 1 if differences else 0


if __name__ == "__main__":
    raise SystemExit(main())
