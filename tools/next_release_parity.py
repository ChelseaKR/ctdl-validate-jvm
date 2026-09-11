#!/usr/bin/env python3
"""Is this port byte-equal with the reference at a commit no release carries yet?

ADR 0004 keeps a rule the pinned release lacks off `main`: `parity/expected/`
is evidence about one published artifact, and a port that emits a finding
that artifact does not is failing its own gate. So checks 6 to 9, which the
reference carries on its main branch and in no release, are ported on a branch
that cannot merge until the reference cuts a release carrying them (#40,
ChelseaKR/ctdl-validate#52).

This is how that branch shows it is right before then. It renders the parity
document for every fixture in both corpora with the reference *at one pinned
commit*, runs this port over the same fixtures with the same `--resolve`
directories, and compares the two byte for byte -- the comparison `ParityTest`
makes, against a rule set no release has published yet.

Nothing here is a gate, and it must be deleted rather than kept once the pin
moves: the day the reference releases these rules, `parity/expected/` becomes
this comparison, and a second copy of it would be a second copy of the gate.

Usage:
    ./gradlew installDist
    python3 tools/next_release_parity.py --reference ../ctdl-validate
    python3 tools/next_release_parity.py --self-check

`--reference` is a git checkout of ChelseaKR/ctdl-validate, clean, at exactly
the commit `parity/reference-main-commit.txt` names. The reference is imported
from that checkout's `src/`, never from whatever is installed, and the harness
checks that it was.

Exit 0 when every fixture is byte-equal, 1 when any differs, 2 when the
comparison could not be made.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMMIT_FILE = ROOT / "parity" / "reference-main-commit.txt"
PORT = ROOT / "build" / "install" / "ctdl-validate-jvm" / "bin" / "ctdl-validate-jvm"
CORPORA = ("parity/fixtures", "parity/ahead/fixtures")
SHA = re.compile(r"^[0-9a-f]{40}$", re.MULTILINE)
EXIT_CANNOT_MEASURE = 2

#: Run in a separate interpreter, so the reference imported is the checkout's
#: and not an installed copy of some other version. It prints where the module
#: really came from, and the harness refuses a render from anywhere else.
_RENDER = """
import os, sys
from pathlib import Path
root, out = Path(sys.argv[1]), Path(sys.argv[2])
import ctdl_validate
print(ctdl_validate.__file__)
import generate_expectations as g
os.chdir(root)
for corpus in sys.argv[3:]:
    for f in sorted((root / corpus).glob("*.json")):
        t = out / corpus / f.name
        t.parent.mkdir(parents=True, exist_ok=True)
        t.write_text(g.render(g.parity_document(f, g.resolve_for(f))), encoding="utf-8")
"""


class CannotMeasure(RuntimeError):
    """The comparison could not be made, which is not the same as agreement."""


def pinned_commit(path: Path = COMMIT_FILE) -> str:
    found = SHA.findall(path.read_text(encoding="utf-8")) if path.exists() else []
    if len(found) != 1:
        raise CannotMeasure(f"{path.name} must name exactly one commit; it names {len(found)}")
    return found[0]


def _git(repo: Path, *arguments: str) -> str:
    done = subprocess.run(  # noqa: S603
        ["git", "-C", str(repo), *arguments], capture_output=True, text=True, check=False  # noqa: S607
    )
    if done.returncode != 0:
        raise CannotMeasure(f"git {' '.join(arguments)} failed in {repo}: {done.stderr.strip()}")
    return done.stdout.strip()


def require_checkout_at(reference: Path, commit: str) -> None:
    """A clean checkout at exactly the pinned commit, or a refusal saying which it is not."""
    if not (reference / "src" / "ctdl_validate").is_dir():
        raise CannotMeasure(f"{reference} is not a checkout of ctdl-validate (no src/ctdl_validate)")
    head = _git(reference, "rev-parse", "HEAD")
    if head != commit:
        raise CannotMeasure(
            f"{reference} is at {head[:12]} and {COMMIT_FILE.name} names {commit[:12]}. "
            f"Check the commit out (git -C {reference} checkout {commit}) or move the pin "
            "deliberately; a comparison against another commit is evidence about that one."
        )
    dirty = _git(reference, "status", "--porcelain", "--untracked-files=no")
    if dirty:
        raise CannotMeasure(
            f"{reference} has uncommitted changes, so it is not the commit it claims to be:\n{dirty}"
        )


def require_fresh_port() -> None:
    """The built port, and not one older than the source it would be standing in for."""
    if not PORT.exists():
        raise CannotMeasure(f"{PORT.relative_to(ROOT)} is not built; run ./gradlew installDist")
    built = min(p.stat().st_mtime for p in (ROOT / "build/install/ctdl-validate-jvm/lib").glob("*.jar"))
    newest = max(p.stat().st_mtime for p in (ROOT / "src/main").rglob("*") if p.is_file())
    if newest > built:
        raise CannotMeasure(
            "the built port is older than src/main, so it would measure code that is not in "
            "this checkout any more; run ./gradlew installDist"
        )


def render_reference(reference: Path, out: Path) -> None:
    env = dict(os.environ, PYTHONPATH=os.pathsep.join([str(reference / "src"), str(ROOT / "tools")]))
    done = subprocess.run(  # noqa: S603
        [sys.executable, "-c", _RENDER, str(ROOT), str(out), *CORPORA],
        capture_output=True, text=True, env=env, check=False,
    )
    if done.returncode != 0:
        raise CannotMeasure(f"the reference could not render the corpus:\n{done.stderr.strip()}")
    imported = Path(done.stdout.splitlines()[0]).resolve()
    if not imported.is_relative_to((reference / "src").resolve()):
        raise CannotMeasure(
            f"the reference was imported from {imported}, not from {reference}/src, so this "
            "would compare the port against something other than the pinned commit"
        )


def render_port(out: Path) -> None:
    for corpus in CORPORA:
        for fixture in sorted((ROOT / corpus).glob("*.json")):
            directory = (ROOT / corpus).parent / "resolve" / fixture.stem
            resolve = ["--resolve", str(directory.relative_to(ROOT))] if directory.is_dir() else []
            done = subprocess.run(  # noqa: S603
                [str(PORT), "--format", "parity", *resolve, str(fixture.relative_to(ROOT))],
                capture_output=True, text=True, cwd=ROOT, check=False,
            )
            if done.returncode not in (0, 1, 2) or not done.stdout:
                raise CannotMeasure(f"the port did not render {fixture.name}: {done.stderr.strip()}")
            target = out / corpus / fixture.name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(done.stdout, encoding="utf-8")


def compare(reference: Path, port: Path) -> tuple[int, list[str]]:
    """How many documents are byte-equal, and a line for each that is not."""
    names = sorted(p.relative_to(reference) for p in reference.rglob("*.json"))
    if not names:
        raise CannotMeasure("the reference rendered no documents, so there is nothing to compare")
    missing = sorted(set(names) ^ {p.relative_to(port) for p in port.rglob("*.json")})
    if missing:
        raise CannotMeasure(f"the two renders cover different fixtures: {[str(m) for m in missing]}")
    equal, lines = 0, []
    for name in names:
        a, b = (reference / name).read_text(), (port / name).read_text()
        if a == b:
            equal += 1
            continue
        codes = [Counter((f["code"], f["severity"]) for f in json.loads(t)["findings"]) for t in (a, b)]
        lines.append(
            f"differs: {name}  reference-only {dict(codes[0] - codes[1])}  "
            f"port-only {dict(codes[1] - codes[0])}"
            + ("" if codes[0] != codes[1] else "  (same findings; the text differs)")
        )
    return equal, lines


def _self_check() -> int:
    failures: list[str] = []
    with tempfile.TemporaryDirectory(prefix="next-release-parity-") as directory:
        root = Path(directory)
        one, two = root / "one", root / "two"
        document = {"exit_code": 0, "error": None, "findings": [], "summary": {}, "text_report": ""}
        for side in (one, two):
            (side / "parity/fixtures").mkdir(parents=True)
            (side / "parity/fixtures/a.json").write_text(json.dumps(document), encoding="utf-8")
        if compare(one, two) != (1, []):
            failures.append("two identical renders were not reported equal")
        (two / "parity/fixtures/a.json").write_text(json.dumps(document | {"exit_code": 1}), encoding="utf-8")
        equal, lines = compare(one, two)
        if equal != 0 or len(lines) != 1 or "same findings" not in lines[0]:
            failures.append(f"a planted difference was not reported: {equal}, {lines}")
        (two / "parity/fixtures/b.json").write_text(json.dumps(document), encoding="utf-8")
        try:
            compare(one, two)
            failures.append("renders covering different fixtures were compared")
        except CannotMeasure:
            pass
        empty = root / "empty"
        empty.mkdir()
        try:
            compare(empty, two)
            failures.append("an empty reference render was compared")
        except CannotMeasure:
            pass
        (root / "commit.txt").write_text("# two\n" + "a" * 40 + "\n" + "b" * 40 + "\n", encoding="utf-8")
        try:
            pinned_commit(root / "commit.txt")
            failures.append("a commit file naming two commits was accepted")
        except CannotMeasure:
            pass
        try:
            require_checkout_at(root, "c" * 40)
            failures.append("a directory that is not a checkout was accepted")
        except CannotMeasure:
            pass
    if failures:
        print("self-check FAILED:", *failures, sep="\n  ", file=sys.stderr)
        return 1
    print("self-check passed: equal renders agree, a planted difference and every refusal fire")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--reference", type=Path, help="a clean checkout of ctdl-validate")
    parser.add_argument("--self-check", action="store_true", help="prove the refusals fire")
    args = parser.parse_args(argv)
    if args.self_check:
        return _self_check()
    if args.reference is None:
        parser.error("--reference is required unless --self-check is given")
    try:
        commit = pinned_commit()
        require_checkout_at(args.reference.resolve(), commit)
        require_fresh_port()
        with tempfile.TemporaryDirectory(prefix="next-release-parity-") as directory:
            reference_out, port_out = Path(directory) / "reference", Path(directory) / "port"
            render_reference(args.reference.resolve(), reference_out)
            render_port(port_out)
            equal, lines = compare(reference_out, port_out)
    except CannotMeasure as exc:
        print(f"next_release_parity: {exc}", file=sys.stderr)
        return EXIT_CANNOT_MEASURE
    total = equal + len(lines)
    print(f"{equal} of {total} documents byte-equal with ctdl-validate at {commit[:12]}")
    for line in lines:
        print(f"  {line}")
    return 0 if not lines else 1


if __name__ == "__main__":
    raise SystemExit(main())
