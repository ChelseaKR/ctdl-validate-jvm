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

The pinned release is the one named in parity/reference-requirements.txt, and
this script refuses to run against any other. Everything it writes is evidence
about that specific published artifact -- parity/expected/ is what it prints,
and parity/reference-codes.json records its version in its own body -- so the
version that happens to be installed must not be what decides.

Usage:
    python3 -m pip install --require-hashes -r parity/reference-requirements.txt
    python3 tools/generate_expectations.py [--check]

--check regenerates into memory and reports differences without writing.
"""

from __future__ import annotations

import argparse
import ast
import json
import re
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

#: The requirements file that pins the reference release. It is the pin: ADR
#: 0003 makes parity byte-equality against one immutable published artifact,
#: and ROADMAP section 2 says the pin does not move until --resolve is ported.
REFERENCE_REQUIREMENTS = ROOT / "parity" / "reference-requirements.txt"

#: The pin line in that file. Anchored at the start of a line so a version
#: named in the prose above it is never mistaken for the pin.
PIN = re.compile(r"^ctdl-validate\s*==\s*([^\s\\]+)", re.MULTILINE)

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


def pinned_version() -> str:
    """The reference version parity/reference-requirements.txt pins.

    Raises rather than returning ``None`` when the file names no pin or names
    more than one. A parser that answered "I could not tell" would make the
    check below skip exactly when the pin file is the thing that is wrong,
    which is the shape of a guard that cannot fail.
    """
    if not REFERENCE_REQUIREMENTS.exists():
        raise SystemExit(f"{REFERENCE_REQUIREMENTS.relative_to(ROOT)} is missing; there is no pin")
    found = PIN.findall(REFERENCE_REQUIREMENTS.read_text(encoding="utf-8"))
    if len(found) != 1:
        raise SystemExit(
            f"{REFERENCE_REQUIREMENTS.relative_to(ROOT)} names {len(found)} pinned "
            "ctdl-validate version(s); exactly one is required"
        )
    return found[0]


def require_the_pinned_reference() -> str:
    """Refuse to speak for the reference unless it is the pinned one.

    Everything this script writes is evidence about a *specific published
    release*: parity/expected/ is what that release prints, and
    parity/reference-codes.json records its version in its own body. Nothing
    here read the pin, so the version installed in the environment decided
    what the evidence said.

    Measured on 2026-09-06 with 0.2.1 installed instead of the pinned 0.1.0.
    ``--check`` still failed -- but it failed with four ordinary "differs"
    lines, the same message a genuine expectation drift produces, naming no
    cause. And the obvious response to that message, running the script
    without ``--check``, rewrote parity/expected/ and moved
    reference-codes.json from 0.1.0/19 codes to 0.2.1/20 codes, exiting 0.
    That is the pin move ROADMAP section 2 forbids until #35 is ported,
    performed silently and arriving in ``git diff`` looking like an ordinary
    expectation update.

    CI cannot hit it, because the job installs from the hash-pinned
    requirements file immediately before running this. A developer following
    the Makefile's comment on a machine with the sibling repository installed
    can, and the sibling is exactly what such a machine has installed.
    """
    pinned = pinned_version()
    if reference_version != pinned:
        raise SystemExit(
            f"the installed ctdl-validate is {reference_version} and "
            f"{REFERENCE_REQUIREMENTS.relative_to(ROOT)} pins {pinned}. Everything this "
            "script writes is evidence about the pinned release, so it will not run "
            "against another one.\n"
            "  python3 -m pip install --require-hashes -r "
            f"{REFERENCE_REQUIREMENTS.relative_to(ROOT)}\n"
            "Moving the pin is a deliberate act: see docs/ROADMAP.md section 2 and "
            "parity/PROVENANCE.md."
        )
    return pinned


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

    # Before anything is read or written: see require_the_pinned_reference.
    require_the_pinned_reference()

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
