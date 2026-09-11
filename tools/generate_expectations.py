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

A fixture can also be validated *with* documents, the way ``--resolve`` passes
them: a directory named for the fixture under ``resolve/`` beside its corpus
(``parity/resolve/<fixture stem>/``) is handed to the reference as one
``--resolve`` argument. The reference prints the path of every supplied
document inside its findings, so the path it is given is spelled relative to
the repository root and every run happens from there; an absolute path would
put this machine's layout into a committed file.

Usage:
    python3 -m pip install --require-hashes -r parity/reference-requirements.txt
    python3 tools/generate_expectations.py [--check]

--check regenerates into memory and reports differences without writing.
"""

from __future__ import annotations

import argparse
import ast
import importlib.metadata
import json
import os
import re
import sys
from pathlib import Path

import ctdl_validate
from ctdl_validate import __version__ as reference_version
from ctdl_validate.findings import SEVERITY_ORDER, Severity, counts, render_findings_text
from ctdl_validate.graph import DocumentError
from ctdl_validate.validator import validate_document

ROOT = Path(__file__).resolve().parent.parent

#: Where the reference implementation's own finding-code census is written.
#: ParityTest reads it to answer "what rules does the other side have?", which
#: this port cannot answer from anything it maintains itself.
REFERENCE_CODES = ROOT / "parity" / "reference-codes.json"

#: The requirements file that pins the reference release. It is the pin: ADR
#: 0003 makes parity byte-equality against one immutable published artifact,
#: and moving it is a review of a rule-set change, never a dependency chore.
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

#: The directory, beside each corpus, holding the documents a fixture is
#: validated with. See the module docstring.
RESOLVE_DIRNAME = "resolve"

# The severity order, the counts and the text report are the reference's own
# (``ctdl_validate.findings``), imported rather than restated. Until the pin
# reached 0.2.1 they could not be: in 0.1.0 they were private to its CLI, so
# this script carried a copy of the renderer and nothing held the copy to the
# thing it copied. A second copy of what a program prints is a second program.


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


def installed_from_somewhere_else() -> str | None:
    """Why the imported reference is not the artifact the pin names, or None if it is.

    The version is not enough. The reference's main branch reports the version
    of its last release -- 0.2.1, measured 2026-09-11 -- so two things that are
    not the pinned artifact pass a version comparison:

    - a build of main installed from a local path, and
    - the pinned wheel with a checkout of main ahead of it on ``PYTHONPATH``,
      where ``importlib.metadata`` reads the installed wheel's version while
      Python imports the checkout's code.

    Both were measured reaching the corpus with the version check in place: ten
    ordinary ``differs:`` lines, stopped only by an unrelated census refusal
    further down. In write mode those ten expectations would already have been
    rewritten.

    Three facts separate the artifact from both. The metadata is an installer's
    and not a source tree's -- an installed distribution carries a ``RECORD``,
    and the ``.egg-info`` a build leaves beside its own source does not, which
    is exactly what the second case above resolves to. The module imported is
    the file that distribution recorded. And the distribution came from an index
    rather than from a direct URL: PEP 610 has an installer record
    ``direct_url.json`` for a URL, local path, VCS or editable install, and
    nothing for an install from an index.
    """
    try:
        distribution = importlib.metadata.distribution("ctdl-validate")
    except importlib.metadata.PackageNotFoundError:
        return "no ctdl-validate distribution is installed; the module came from a source tree"
    if distribution.read_text("RECORD") is None:
        metadata = getattr(distribution, "_path", "an unrecorded location")
        return (
            f"its metadata at {metadata} carries no installer RECORD, so it is a source tree's "
            "own build information rather than an installed distribution"
        )
    installed = Path(str(distribution.locate_file("ctdl_validate/__init__.py"))).resolve()
    imported = Path(ctdl_validate.__file__).resolve()
    if installed != imported:
        return (
            f"ctdl_validate was imported from {imported}, not from the installed distribution "
            f"at {installed}"
        )
    direct = distribution.read_text("direct_url.json")
    if direct is not None:
        try:
            where = str(json.loads(direct).get("url", "an unrecorded location"))
        except ValueError:
            where = "a location whose installer record does not parse"
        return f"the installed distribution came from {where}, not from the package index"
    return None


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
    That was the pin move ROADMAP section 2 forbade until #35 was ported,
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
            "Moving the pin is a deliberate act: see docs/ROADMAP.md and "
            "parity/PROVENANCE.md."
        )
    elsewhere = installed_from_somewhere_else()
    if elsewhere is not None:
        raise SystemExit(
            f"the installed ctdl-validate reports {reference_version}, the pinned version, but "
            f"{elsewhere}. The reference's main branch reports the version of its last release, "
            "so the version cannot tell an unreleased build from the pinned artifact.\n"
            "  python3 -m pip install --force-reinstall --require-hashes -r "
            f"{REFERENCE_REQUIREMENTS.relative_to(ROOT)}"
        )
    return pinned


def resolve_for(fixture: Path) -> list[Path] | None:
    """The ``--resolve`` argument a fixture is validated with, or None for none.

    Relative to the repository root, and read from there: see the module
    docstring for why the spelling is load-bearing.
    """
    directory = fixture.parent.parent / RESOLVE_DIRNAME / fixture.stem
    if not directory.is_dir():
        return None
    return [directory.resolve().relative_to(ROOT)]


def parity_document(path: Path, resolve: list[Path] | None = None) -> dict[str, object]:
    """The comparable result of validating one fixture.

    Deliberately excludes the tool name and version: those differ between the
    two implementations and are the only things allowed to. Everything else --
    exit code, every finding field, every rule citation, the order the findings
    come out in, and the text report a human reads -- must agree.
    """
    data = json.loads(path.read_text(encoding="utf-8"))
    try:
        findings = validate_document(data, resolve)
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
        "text_report": render_findings_text(findings),
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
    # Supplied documents are named in findings by the path the reference was
    # given, which is relative to here. See resolve_for.
    os.chdir(ROOT)

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
            rendered = render(parity_document(fixture, resolve_for(fixture)))
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

        # Documents for a fixture that no longer exists would be read by
        # nothing, and a directory nothing reads is a claim nothing checks.
        # Refused in both modes and never deleted: unlike an expectation, these
        # are written by hand, so removing them is a decision for a person.
        resolve_root = fixture_dir.parent / RESOLVE_DIRNAME
        if resolve_root.is_dir():
            orphans = {d.name for d in resolve_root.iterdir() if d.is_dir()}
            for name in sorted(orphans - {f.stem for f in fixtures}):
                differences += 1
                print(
                    f"resolve documents with no fixture: {(resolve_root / name).relative_to(ROOT)}",
                    file=sys.stderr,
                )

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
