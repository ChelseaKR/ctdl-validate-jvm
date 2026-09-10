#!/usr/bin/env python3
"""How much of the reference's rule set does this port implement, and which part does it not?

`FindingCodeCensusTest` answers that against the **pinned release**, which is the
right question for the parity corpus and the wrong one for planning. The pin is
`0.1.0`; the reference has released twice since and moved on further than that on
`main`. So the census is green, correctly, over a port that is behind by rules
nothing in this repository counts.

`parity/PROVENANCE.md` records one measurement of that gap, taken by hand against
`0.2.1` on 2026-08-29 ("the reference gained a rule this port does not have,
`REF_RESOLVED_SUPPLIED`"). A figure measured by hand once is a figure that goes
stale on the sibling's next commit, and #41 asks for it to be produced rather
than remembered. This is the narrow half of that: no installs, no matrix, no
network -- point it at a checkout of the reference and it reads both rule sets
out of source.

It prints two numbers and refuses to print one. "The port implements 21 rules" is
the number that reads well and means nothing; "21 of the reference's 28
validation rules" is the number that decides what #35 and #40 cost.

**The extraction half is excluded, by name and with a reason.** The reference
constructs 48 finding codes; 20 of them are notes emitted by `extract/`, a
subcommand this port does not have and does not claim to (`README.md`,
`parity/PROVENANCE.md`). Counting them would report a 44% port as a 56% one and
would move whenever the sibling touched a surface out of scope here. Both totals
are printed, so the exclusion is visible rather than assumed.

**Nothing here is a gate and it must not become one.** It needs a checkout of
another repository, which CI does not have, and a check that cannot run in CI is
a check that reports agreement from a machine that never asked -- the failure
this repository's whole parity apparatus exists to avoid. `./gradlew verify` is
the gate; this is a harness a person runs before deciding whether to bump the pin.
It follows `tools/differential_fuzz.py`, which is deliberately outside `verify`
for the same kind of reason (ADR 0006).

Usage:
    python3 tools/reference_gap.py --reference ../ctdl-validate
    python3 tools/reference_gap.py --reference ../ctdl-validate --ref v0.2.1
    python3 tools/reference_gap.py --reference ../ctdl-validate --json
    python3 tools/reference_gap.py --self-check

Exit code is 0 when the two rule sets were measured -- a gap is data, not a
verdict, the same posture `diff` takes in the sibling -- and 2 when they could
not be. There is deliberately no third code for "there is a gap": this port being
behind the reference's `main` is the expected state, declared in ROADMAP section
2, and a harness that went red for it would be red forever.
"""

from __future__ import annotations

import argparse
import ast
import json
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

#: Where the port constructs findings. Parsed, never listed: a list maintained
#: here would go stale at the newest rule, which is the one most likely to be
#: missing, and that is the mistake `FindingCodeCensusTest` was written to stop.
PORT_SOURCE = Path("src/main/java")

#: Where the reference constructs findings, and the two halves of it. `checks/`
#: is the rule set this port is a port of. `extract/` is a subcommand it does
#: not have.
REFERENCE_PACKAGE = Path("src/ctdl_validate")
REFERENCE_IN_SCOPE = REFERENCE_PACKAGE / "checks"
REFERENCE_OUT_OF_SCOPE = REFERENCE_PACKAGE / "extract"

#: Read the working tree instead of a git ref. The only value of `--ref` that
#: does not go through `git show`.
WORKTREE = "WORKTREE"

#: `new Finding(` followed by a string literal, which is how the port writes
#: every code. Kept identical to `FindingCodeCensusTest.FINDING_SITE` on purpose:
#: two readers of one construct that disagree would make this harness argue with
#: the gate rather than with the sibling.
_FINDING_SITE = re.compile(r'new\s+Finding\s*\(\s*"([A-Z0-9_]+)"')
_ANY_FINDING_SITE = re.compile(r"new\s+Finding\s*\(")
#: Block comments, line comments and text blocks: prose that quotes a rule is
#: not a rule. `FindingCodeCensusTest` strips the same thing for the same reason.
_COMMENTARY = re.compile(r"/\*.*?\*/|//[^\n]*", re.DOTALL)

EXIT_CANNOT_MEASURE = 2


class CannotMeasure(RuntimeError):
    """The gap could not be measured, which is not the same as there being none."""


@dataclass(frozen=True)
class Gap:
    """Two rule sets, and what each has that the other does not."""

    port: frozenset[str]
    reference_in_scope: frozenset[str]
    reference_out_of_scope: frozenset[str]
    reference_ref: str

    @property
    def shared(self) -> frozenset[str]:
        return self.port & self.reference_in_scope

    @property
    def behind(self) -> frozenset[str]:
        """Rules the reference has and this port does not. The number that matters."""
        return self.reference_in_scope - self.port

    @property
    def ahead(self) -> frozenset[str]:
        """Rules this port has that the reference does not, anywhere in the package.

        Measured against the *whole* reference package rather than against
        `checks/` alone, so a rule the sibling moved to another module does not
        read as this port inventing one.
        """
        return self.port - (self.reference_in_scope | self.reference_out_of_scope)

    def as_dict(self) -> dict[str, object]:
        return {
            "reference_ref": self.reference_ref,
            "reference_codes_in_scope": sorted(self.reference_in_scope),
            "reference_codes_out_of_scope": sorted(self.reference_out_of_scope),
            "port_codes": sorted(self.port),
            "shared": sorted(self.shared),
            "port_is_behind": sorted(self.behind),
            "port_is_ahead": sorted(self.ahead),
            "examined": len(self.shared),
            "examinable": len(self.reference_in_scope),
        }


def _run_git(repo: Path, *arguments: str) -> str:
    completed = subprocess.run(  # noqa: S603
        ["git", "-C", str(repo), *arguments],  # noqa: S607
        capture_output=True,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise CannotMeasure(
            f"`git {' '.join(arguments)}` failed in {repo}: "
            f"{completed.stderr.strip() or 'no message'}"
        )
    return completed.stdout


def _package_exists(reference: Path, ref: str, under: Path) -> bool:
    """Whether the reference has ``under`` at all, at this ref.

    The distinction this function exists for is one a positive control found in
    this harness rather than in anything it measures. Run against the pinned
    ``v0.1.0``, the first version refused: ``src/ctdl_validate/extract`` holds no
    modules there, and the refusal said the package layout had moved and a scan
    of nothing reports no rules. Both sentences were false. The reference had no
    ``extract`` subcommand in ``0.1.0``; zero extraction notes is the *right*
    answer for that release, and refusing it would have made every measurement
    against an early release impossible while reading like a broken reader.

    So an **absent** optional package is a real state with a real answer, and a
    package that is **present and empty** stays a refusal, because that is the
    one a reader gone wrong produces.
    """
    if ref == WORKTREE:
        return (reference / under).is_dir()
    completed = subprocess.run(  # noqa: S603
        ["git", "-C", str(reference), "rev-parse", "--verify", "--quiet", f"{ref}:{under}"],  # noqa: S607
        capture_output=True,
        text=True,
        check=False,
    )
    return completed.returncode == 0


def _reference_sources(reference: Path, ref: str, under: Path) -> dict[str, str]:
    """Every module of the reference under ``under``, as {path: source}.

    Reads a git ref by default rather than the working tree, because a sibling
    checkout is very likely to be mid-edit and a gap measured against somebody's
    uncommitted state is not a fact about the reference.
    """
    if not reference.is_dir():
        raise CannotMeasure(f"--reference {reference} is not a directory")
    if ref == WORKTREE:
        paths = sorted(p for p in (reference / under).rglob("*.py") if p.is_file())
        if not paths:
            raise CannotMeasure(
                f"{reference / under} holds no Python modules, so this scan can only "
                "report that the reference declares no rules, which is not a measurement"
            )
        return {
            str(path.relative_to(reference)): path.read_text(encoding="utf-8") for path in paths
        }
    if not (reference / ".git").exists():
        raise CannotMeasure(
            f"--reference {reference} is not a git checkout, so --ref {ref} cannot be "
            f"read there. Pass --ref {WORKTREE} to read the files on disk instead."
        )
    listing = _run_git(reference, "ls-tree", "-r", "--name-only", ref, str(under))
    names = [line for line in listing.split() if line.endswith(".py")]
    if not names:
        raise CannotMeasure(
            f"{under} holds no Python modules at {ref} in {reference}. The reference's "
            "package layout has moved, and a scan of nothing reports no rules."
        )
    return {name: _run_git(reference, "show", f"{ref}:{name}") for name in names}


def reference_codes(sources: dict[str, str]) -> frozenset[str]:
    """Finding codes the reference constructs, by AST.

    By AST rather than by regex because the reference's own gate says why: a
    ``[A-Z_]+`` scan silently omits ``CTID_NOT_UUIDV4``, whose code carries a
    digit, and a rule missing from a list of rules is the whole failure mode
    here.
    """
    found: set[str] = set()
    unreadable: list[str] = []
    for name, source in sources.items():
        try:
            tree = ast.parse(source)
        except SyntaxError as exc:  # pragma: no cover - exercised by --self-check
            raise CannotMeasure(f"{name} does not parse as Python: {exc}") from exc
        for node in ast.walk(tree):
            if not isinstance(node, ast.keyword) or node.arg != "code":
                continue
            if isinstance(node.value, ast.Constant) and isinstance(node.value.value, str):
                found.add(node.value.value)
            else:
                unreadable.append(name)
    if unreadable:
        raise CannotMeasure(
            "the reference constructs a finding with a code this scan cannot read as a "
            f"literal, so its rule set cannot be compared: {sorted(set(unreadable))}"
        )
    if not found:
        raise CannotMeasure(
            f"scanned {len(sources)} reference module(s) and found no finding codes. "
            "A reader that has stopped matching reports the same empty set as a "
            "package with no rules in it."
        )
    return frozenset(found)


def port_codes(port: Path) -> frozenset[str]:
    """Finding codes this port constructs, parsed out of the shipped Java."""
    source_root = port / PORT_SOURCE
    if not source_root.is_dir():
        raise CannotMeasure(f"{source_root} is not a directory, so the port cannot be read")
    files = sorted(p for p in source_root.rglob("*.java") if p.is_file())
    if not files:
        raise CannotMeasure(f"{source_root} holds no Java sources")
    found: set[str] = set()
    unreadable: dict[str, int] = {}
    for path in files:
        source = _COMMENTARY.sub("", path.read_text(encoding="utf-8"))
        literals = _FINDING_SITE.findall(source)
        every = _ANY_FINDING_SITE.findall(source)
        if len(every) != len(literals):
            unreadable[path.name] = len(every) - len(literals)
        found.update(literals)
    if unreadable:
        raise CannotMeasure(
            "this port constructs a Finding with a code this scan cannot read as a "
            f"literal, so its rule set is understated: {unreadable}"
        )
    if not found:
        raise CannotMeasure(
            f"scanned {len(files)} Java source(s) under {source_root} and found no finding "
            "codes, which is what a reader that stopped matching reports"
        )
    return frozenset(found)


def measure(port: Path, reference: Path, ref: str) -> Gap:
    """The two rule sets at ``ref``.

    ``checks/`` is required: a reference with no validation rules is a reader
    that has stopped working, and it is what every number here is out of.
    ``extract/`` is optional, because a release older than the subcommand really
    has none -- see :func:`_package_exists`.
    """
    out_of_scope: frozenset[str] = frozenset()
    if _package_exists(reference, ref, REFERENCE_OUT_OF_SCOPE):
        out_of_scope = reference_codes(_reference_sources(reference, ref, REFERENCE_OUT_OF_SCOPE))
    return Gap(
        port=port_codes(port),
        reference_in_scope=reference_codes(_reference_sources(reference, ref, REFERENCE_IN_SCOPE)),
        reference_out_of_scope=out_of_scope,
        reference_ref=ref,
    )


def render(gap: Gap) -> str:
    extraction = (
        f"{len(gap.reference_out_of_scope)} extraction note(s) under {REFERENCE_OUT_OF_SCOPE} "
        "(out of scope for this port, which has no extract subcommand)"
        if gap.reference_out_of_scope
        else f"no {REFERENCE_OUT_OF_SCOPE} at this ref, so no extraction notes to exclude"
    )
    lines = [
        f"reference at {gap.reference_ref}: "
        f"{len(gap.reference_in_scope)} validation rule(s) under {REFERENCE_IN_SCOPE}, "
        f"{extraction}",
        f"this port: {len(gap.port)} rule(s)",
        "",
        f"implemented: {len(gap.shared)} of {len(gap.reference_in_scope)} "
        f"reference validation rule(s)",
    ]
    lines.append(f"behind by {len(gap.behind)}:")
    lines += [f"  - {code}" for code in sorted(gap.behind)] or ["  (none)"]
    lines.append(f"ahead by {len(gap.ahead)}:")
    lines += [f"  + {code}" for code in sorted(gap.ahead)] or ["  (none)"]
    lines.append("")
    lines.append(
        "A gap is expected and is not a failure: ROADMAP section 2 holds the pin still "
        "until --resolve is ported. This says what moving it would cost."
    )
    return "\n".join(lines)


def _self_check() -> int:
    """Prove every refusal fires, and that a planted gap is reported.

    A harness whose whole output is "the port is behind by N" will print a
    plausible N from a scan that read nothing, and the number will be believed
    because it is specific. So each way this can fail to measure is planted here
    and required to raise, and the one positive case is required to report the
    rule it was given rather than a count it could have produced from silence.
    """
    failures: list[str] = []

    def expect_refusal(what: str, thunk: object) -> None:
        try:
            thunk()  # type: ignore[operator]
        except CannotMeasure:
            return
        except Exception as exc:  # noqa: BLE001 - a wrong exception is still a failure
            failures.append(f"{what}: raised {type(exc).__name__} rather than refusing")
            return
        failures.append(f"{what}: returned a measurement instead of refusing")

    with tempfile.TemporaryDirectory(prefix="reference-gap-") as directory:
        root = Path(directory)

        # One well-formed port, so the reference-side cases below fail for the
        # reason they are about and not because the port half was unreadable.
        good_port = root / "good-port"
        (good_port / PORT_SOURCE).mkdir(parents=True)
        (good_port / PORT_SOURCE / "Good.java").write_text(
            'class G { Object f() { return new Finding("A_RULE"); } }\n', encoding="utf-8"
        )

        # A reference whose package is not where it should be.
        empty = root / "empty-reference"
        (empty / REFERENCE_IN_SCOPE).mkdir(parents=True)
        expect_refusal(
            "a reference package with no modules",
            lambda: _reference_sources(empty, WORKTREE, REFERENCE_IN_SCOPE),
        )
        expect_refusal(
            "a reference that is not a directory",
            lambda: _reference_sources(root / "absent", WORKTREE, REFERENCE_IN_SCOPE),
        )
        expect_refusal(
            "a git ref against a directory that is not a checkout",
            lambda: _reference_sources(empty, "origin/main", REFERENCE_IN_SCOPE),
        )

        # A module whose code is computed rather than written.
        # One readable code beside the unreadable one, on purpose. With only the
        # unreadable one the module declares nothing, the "no rules" refusal
        # fires instead, and this case passes whether or not the literal check
        # exists -- two refusals reachable from one fixture make both
        # unfalsifiable. Measured: with the literal check deleted, the
        # single-code version of this fixture still refused and the self-check
        # still passed.
        expect_refusal(
            "a reference code that is not a literal",
            lambda: reference_codes(
                {"m.py": 'Finding(code="A_READABLE_RULE")\nCODE = "X"\nFinding(code=CODE)\n'}
            ),
        )
        expect_refusal(
            "a reference module that does not parse",
            lambda: reference_codes({"m.py": "def broken(:\n"}),
        )
        expect_refusal(
            "a reference package that declares no rules",
            lambda: reference_codes({"m.py": "x = 1\n"}),
        )

        # The port side.
        bare = root / "bare-port"
        (bare / PORT_SOURCE).mkdir(parents=True)
        expect_refusal("a port with no Java sources", lambda: port_codes(bare))
        expect_refusal("a port that is not laid out as one", lambda: port_codes(root / "absent"))

        quiet = root / "quiet-port"
        (quiet / PORT_SOURCE).mkdir(parents=True)
        (quiet / PORT_SOURCE / "Quiet.java").write_text("class Quiet {}\n", encoding="utf-8")
        expect_refusal("a port that constructs no findings", lambda: port_codes(quiet))

        computed = root / "computed-port"
        (computed / PORT_SOURCE).mkdir(parents=True)
        (computed / PORT_SOURCE / "Computed.java").write_text(
            # A readable code beside the computed one, for the reason above: with
            # only the computed one the file declares nothing and the "no codes"
            # refusal answers instead.
            'class C { Object f() { return new Finding("A_READABLE_RULE"); }\n'
            "  Object g() { return new Finding(code, S, e, p, v, m, r); } }\n",
            encoding="utf-8",
        )
        expect_refusal("a port code that is not a literal", lambda: port_codes(computed))

        # An absent optional package is a real answer; a present empty one is not.
        # Found by a positive control against v0.1.0, which has no extract/ at all
        # and which the first version of this harness refused, with a message
        # saying the layout had moved.
        no_extract = root / "reference-without-extract"
        (no_extract / REFERENCE_IN_SCOPE).mkdir(parents=True)
        (no_extract / REFERENCE_IN_SCOPE / "rule.py").write_text(
            'Finding(code="A_RULE")\n', encoding="utf-8"
        )
        if _package_exists(no_extract, WORKTREE, REFERENCE_OUT_OF_SCOPE):
            failures.append("an absent extract/ was reported as present")
        try:
            absent = measure(good_port, no_extract, WORKTREE)
        except CannotMeasure as exc:
            failures.append(f"an absent extract/ was refused rather than measured: {exc}")
            absent = Gap(frozenset(), frozenset(), frozenset(), "unmeasured")
        if absent.reference_out_of_scope:
            failures.append(
                f"an absent extract/ produced notes: {sorted(absent.reference_out_of_scope)}"
            )
        if absent.reference_in_scope != {"A_RULE"}:
            failures.append(f"the in-scope scan read {sorted(absent.reference_in_scope)}")
        (no_extract / REFERENCE_OUT_OF_SCOPE).mkdir(parents=True)
        if not _package_exists(no_extract, WORKTREE, REFERENCE_OUT_OF_SCOPE):
            failures.append("a present extract/ was reported as absent")
        expect_refusal(
            "a present but empty extract/",
            lambda: measure(good_port, no_extract, WORKTREE),
        )

        # A comment that quotes a rule is not a rule.
        commented = root / "commented-port"
        (commented / PORT_SOURCE).mkdir(parents=True)
        (commented / PORT_SOURCE / "Commented.java").write_text(
            '// new Finding("ONLY_IN_A_COMMENT", ...)\n'
            'class C { Object f() { return new Finding("REAL_RULE"); } }\n',
            encoding="utf-8",
        )
        parsed = port_codes(commented)
        if parsed != {"REAL_RULE"}:
            failures.append(f"a commented-out rule was counted: {sorted(parsed)}")

        # The positive case: a planted gap is reported, both ways.
        planted = Gap(
            port=frozenset({"SHARED", "ONLY_IN_THE_PORT"}),
            reference_in_scope=frozenset({"SHARED", "ONLY_IN_THE_REFERENCE"}),
            reference_out_of_scope=frozenset({"AN_EXTRACTION_NOTE"}),
            reference_ref="planted",
        )
        if sorted(planted.behind) != ["ONLY_IN_THE_REFERENCE"]:
            failures.append(f"behind: {sorted(planted.behind)}")
        if sorted(planted.ahead) != ["ONLY_IN_THE_PORT"]:
            failures.append(f"ahead: {sorted(planted.ahead)}")
        if (planted.as_dict()["examined"], planted.as_dict()["examinable"]) != (1, 2):
            failures.append(f"examined/examinable: {planted.as_dict()}")
        out_of_scope_only = Gap(
            port=frozenset({"AN_EXTRACTION_NOTE"}),
            reference_in_scope=frozenset({"SHARED"}),
            reference_out_of_scope=frozenset({"AN_EXTRACTION_NOTE"}),
            reference_ref="planted",
        )
        if out_of_scope_only.ahead:
            failures.append(
                "a code the reference declares under extract/ was reported as this port "
                "being ahead, which would read as the port inventing a rule"
            )

    if failures:
        print("self-check FAILED:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1
    print("self-check passed: every refusal fires and a planted gap is reported both ways")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="reference_gap.py",
        description=(
            "Measure this port's rule set against the reference's, out of source. Prints "
            "how many of the reference's validation rules this port implements and names "
            "the ones it does not. Not a gate: it needs a checkout of the sibling."
        ),
    )
    parser.add_argument(
        "--reference",
        type=Path,
        help="path to a checkout of ChelseaKR/ctdl-validate",
    )
    parser.add_argument(
        "--ref",
        default="origin/main",
        help=(
            "the git ref to read the reference at (default: origin/main). "
            f"{WORKTREE} reads the files on disk instead, which is a measurement of "
            "somebody's working tree rather than of the reference"
        ),
    )
    parser.add_argument("--port", type=Path, default=ROOT, help=argparse.SUPPRESS)
    parser.add_argument("--json", action="store_true", help="machine-readable output")
    parser.add_argument(
        "--self-check",
        action="store_true",
        help="prove every refusal fires, and exit; needs no reference checkout",
    )
    args = parser.parse_args(argv)

    if args.self_check:
        return _self_check()
    if args.reference is None:
        parser.error("--reference is required unless --self-check is given")

    try:
        gap = measure(args.port, args.reference, args.ref)
    except CannotMeasure as exc:
        print(f"reference_gap: {exc}", file=sys.stderr)
        return EXIT_CANNOT_MEASURE
    print(json.dumps(gap.as_dict(), indent=2, sort_keys=True) if args.json else render(gap))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
