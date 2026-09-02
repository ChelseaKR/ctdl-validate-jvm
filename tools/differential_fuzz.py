#!/usr/bin/env python3
"""Run this port and the pinned reference over generated payloads and diff them.

`parity/fixtures/` is a couple of dozen hand-chosen payloads. It reaches every
finding code, and that is not the same as reaching every document *shape* that
produces one. Both
defects this port has fixed independently of the reference -- an inverse asserted
as a nested object, and a class ruling decided by a shadowed duplicate `@id` --
lived in exactly that gap: byte equality was green because both implementations
shared the mistake and no fixture reached the shape.

This is the mechanical answer to "which shapes does the corpus reach". It builds
CTDL-shaped JSON-LD rather than arbitrary JSON, because the point is to reach the
checks and not to test two JSON parsers, and it draws its class and property names
out of the same vendored snapshot the validator reads, so a generated document
looks like something a publisher could have written.

The value pool is biased at the cases a purposive corpus misses and a generator
finds: supplementary-plane characters, strings that differ only above the BMP,
floats around the boundary where Python switches to scientific notation, integers
that are and are not exactly representable as doubles, empty strings and empty
containers, booleans and nulls where a string belongs, and inline objects nested
several deep. Every one of the three divergences this port actually hit while
being written was of that kind.

Malformed JSON is deliberately out of scope: both sides exit 2, but the message
comes from Jackson or from CPython, and pinning it would compare parsers rather
than rules. `README.md` says the same.

This is not a merge gate and must not become one. It is nondeterministic in what
it reaches, it needs a built CLI and an installed reference, and a gate that
sometimes finds nothing is a gate that teaches people to ignore it. The merge
gate is the deterministic corpus. What this produces is fixtures: every divergence
is minimised and written out so it can be added to `parity/fixtures/` -- or, where
this port is right and the pinned release is not, to `parity/ahead/`.

Usage:
    python3 -m pip install --require-hashes -r parity/reference-requirements.txt
    ./gradlew installDist
    python3 tools/differential_fuzz.py --count 2000 --seed 1
    python3 tools/differential_fuzz.py --self-check

Exit code is 0 when the two implementations agreed on every generated document,
1 when they did not, and 2 when the harness could not run.
"""

from __future__ import annotations

import argparse
import json
import random
import re
import subprocess
import sys
import tempfile
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from generate_expectations import parity_document, render  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
PORT = ROOT / "build" / "install" / "ctdl-validate-jvm" / "bin" / "ctdl-validate-jvm"
VENDOR = ROOT / "src" / "main" / "resources" / "vendor"

REGISTRY = "https://credentialengineregistry.org/resources/"

#: Strings chosen because they are where two languages stop agreeing, not because
#: they are realistic. The last four differ from each other only above the BMP or
#: only in normalisation, which is what separates code-point order from UTF-16
#: order and what `CodePointOrder` exists for.
HARD_STRINGS = [
    "",
    " ",
    "plain",
    "Ünïcödé",
    "日本語のコース",
    "\U0001f600",  # astral: a single supplementary code point
    "\U0010ffff",  # the last code point there is
    "z",  # sorts after the astral characters by code unit, before them by code point
    "￿",
    "a\U0001f600b",
    "a￿b",
    "line\nbreak",
    "tab\there",
    "quote\"and\\backslash",
    "\u0000",  # NUL, which JSON permits escaped
    "é",  # combining acute
    "é",  # the same letter precomposed
]

#: Numbers around the places Python's repr and Java's Double.toString stop
#: agreeing: the switch to scientific notation at either end, the largest exactly
#: representable integer, and values that are integral doubles.
HARD_NUMBERS = [
    0,
    -0.0,
    1,
    -1,
    1.0,
    0.1,
    1e15,
    1e16,
    1e17,
    1.5e300,
    5e-5,
    1e-4,
    1e-5,
    2**53,
    2**53 + 1,
    2**63,
    10**25,
    3.141592653589793,
    1.7976931348623157e308,
    5e-324,
]

#: Identifier shapes, valid and not. The CTID grammar is `ce-` plus a lowercase
#: v4 UUID; everything else here is a way to be wrong about it that the checks
#: have a code for.
CTIDS = [
    "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
    "ce-59E8D15F-7895-4346-A5A8-7A0739A3D344",  # uppercase hex
    "ce-59e8d15f-7895-1346-a5a8-7a0739a3d344",  # version 1, not 4
    "59e8d15f-7895-4346-a5a8-7a0739a3d344",  # bare UUID, no prefix
    "ce-not-a-uuid",
    "",
    "ce-59e8d15f-7895-4346-a5a8-7a0739a3d34",  # one character short
]


def load_terms() -> tuple[list[str], list[str], list[str]]:
    """Class names, property names, and the id-coerced subset, from the snapshot.

    Read out of the vendored files the validator itself loads rather than listed
    here, so a refreshed snapshot refreshes what the generator can reach. A
    generator working from a hand-written list of terms would keep exercising the
    schema of the day it was written.
    """
    classes: list[str] = []
    properties: list[str] = []
    id_coerced: list[str] = []
    for vocab in ("ctdl", "ctdlasn"):
        schema = json.loads((VENDOR / vocab / "schema.json").read_text(encoding="utf-8"))
        for term in schema.get("@graph", schema):
            term_id = term.get("@id")
            if not isinstance(term_id, str):
                continue
            if term.get("@type") == "rdfs:Class":
                classes.append(term_id)
            elif term.get("@type") == "rdf:Property":
                properties.append(term_id)
        context = json.loads((VENDOR / vocab / "context.json").read_text(encoding="utf-8"))
        for name, definition in context.get("@context", {}).items():
            if isinstance(definition, dict) and definition.get("@type") == "@id":
                id_coerced.append(name)
    if not classes or not properties:
        raise SystemExit(f"no terms found under {VENDOR}; is the snapshot vendored?")
    return sorted(set(classes)), sorted(set(properties)), sorted(set(id_coerced))


class Generator:
    """Builds one CTDL-shaped payload per call, from a seeded random source."""

    def __init__(self, rng: random.Random, classes, properties, id_coerced) -> None:
        self.rng = rng
        self.classes = classes
        self.properties = properties
        self.id_coerced = id_coerced or properties

    def uuid(self) -> str:
        digits = "0123456789abcdef"
        pick = self.rng.choice
        body = (
            "".join(pick(digits) for _ in range(8))
            + "-"
            + "".join(pick(digits) for _ in range(4))
            + "-4"
            + "".join(pick(digits) for _ in range(3))
            + "-"
            + pick("89ab")
            + "".join(pick(digits) for _ in range(3))
            + "-"
            + "".join(pick(digits) for _ in range(12))
        )
        return f"ce-{body}"

    def identifier(self, pool: list[str]) -> str:
        """An `@id`: a Registry URI, a reused one, or one of the ways to be wrong."""
        roll = self.rng.random()
        if pool and roll < 0.35:
            return self.rng.choice(pool)  # reuse: duplicate @id and resolvable references
        if roll < 0.80:
            return REGISTRY + self.uuid()
        return self.rng.choice(
            [
                "_:b" + str(self.rng.randrange(4)),
                self.uuid(),
                self.uuid()[3:],
                "not an iri at all",
                "https://example.org/" + self.rng.choice(["a", "b", "c"]),
                REGISTRY + self.rng.choice(CTIDS),
            ]
        )

    def scalar(self):
        """A property value that is not an entity."""
        roll = self.rng.random()
        if roll < 0.45:
            return self.rng.choice(HARD_STRINGS)
        if roll < 0.70:
            return self.rng.choice(HARD_NUMBERS)
        if roll < 0.80:
            return self.rng.choice([True, False, None])
        if roll < 0.90:
            return {"en-US": self.rng.choice(HARD_STRINGS)}
        return self.rng.choice([[], {}, [[]], {"@value": self.rng.choice(HARD_STRINGS)}])

    def types(self):
        roll = self.rng.random()
        if roll < 0.65:
            return self.rng.choice(self.classes)
        if roll < 0.75:
            return [self.rng.choice(self.classes) for _ in range(self.rng.randrange(1, 3))]
        if roll < 0.85:
            return "ceterms:NoSuchClassHere"
        if roll < 0.92:
            return "schema:Thing"
        return self.rng.choice([[], None, 7, ""])

    def entity(self, pool: list[str], depth: int) -> dict:
        entity: dict[str, object] = {}
        if self.rng.random() < 0.9:
            entity["@id"] = self.identifier(pool)
            if isinstance(entity["@id"], str) and entity["@id"].startswith(REGISTRY):
                pool.append(entity["@id"])
        if self.rng.random() < 0.92:
            entity["@type"] = self.types()
        if self.rng.random() < 0.6:
            entity["ceterms:ctid"] = self.ctid_value(entity.get("@id"))
        for _ in range(self.rng.randrange(0, 5)):
            prop = self.rng.choice(
                self.id_coerced if self.rng.random() < 0.6 else self.properties
            )
            entity[prop] = self.value(pool, depth)
        if self.rng.random() < 0.06:
            entity["https://schema.org/name"] = self.rng.choice(HARD_STRINGS)
        return entity

    def ctid_value(self, node_id):
        """A `ceterms:ctid`, agreeing with the `@id` about a third of the time."""
        if isinstance(node_id, str) and node_id.startswith(REGISTRY) and self.rng.random() < 0.5:
            return node_id[len(REGISTRY) :]
        roll = self.rng.random()
        if roll < 0.75:
            return self.rng.choice(CTIDS)
        return self.rng.choice(HARD_NUMBERS + [None, True, [], {}])

    def value(self, pool: list[str], depth: int):
        roll = self.rng.random()
        if roll < 0.30:
            return self.identifier(pool)  # a bare-IRI reference
        if roll < 0.40 and pool:
            return {"@id": self.rng.choice(pool)}  # reference-only object
        if roll < 0.55 and depth < 3:
            return self.entity(pool, depth + 1)  # inline nested object
        if roll < 0.70:
            count = self.rng.randrange(0, 3)
            return [self.value(pool, depth + 1) for _ in range(count)]
        return self.scalar()

    def document(self) -> object:
        pool: list[str] = []
        entities = [self.entity(pool, 0) for _ in range(self.rng.randrange(1, 5))]
        roll = self.rng.random()
        if roll < 0.70:
            document: object = {
                "@context": "https://credreg.net/ctdl/schema/context/json",
                "@graph": entities,
            }
        elif roll < 0.85:
            document = entities
        else:
            document = entities[0]
        return document


def port_output(document: object, directory: Path, index: int) -> str:
    """What this port prints for a payload, as bytes decoded UTF-8."""
    path = directory / f"case-{index}.json"
    path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
    result = subprocess.run(  # noqa: S603 - fixed argv, no shell
        [str(PORT), "--format=parity", str(path)],
        capture_output=True,
        check=False,
    )
    if result.returncode not in (0, 1, 2):
        raise SystemExit(
            f"the port exited {result.returncode}: {result.stderr.decode('utf-8', 'replace')}"
        )
    return result.stdout.decode("utf-8")


def reference_output(document: object, directory: Path, index: int) -> str:
    path = directory / f"ref-{index}.json"
    path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
    return render(parity_document(path))


def disagrees(document: object, directory: Path, index: int, mutate=None) -> bool:
    """Whether the two implementations print different bytes for one payload."""
    theirs = reference_output(document, directory, index)
    ours = port_output(document, directory, index)
    if mutate is not None:
        ours = mutate(ours)
    return theirs != ours


def shrink(document: object, directory: Path, mutate=None) -> object:
    """Greedily remove parts of a diverging payload while it still diverges.

    Not a general shrinker. It drops whole entities, then whole properties, then
    replaces values with the shortest thing that keeps the disagreement, which is
    enough to turn a generated document into something a person can read and file
    as a fixture.
    """
    index = 0

    def still(candidate: object) -> bool:
        nonlocal index
        index += 1
        try:
            return disagrees(candidate, directory, 10_000 + index, mutate)
        except Exception:  # a shrink step that breaks the payload is not a shrink
            return False

    current = document
    changed = True
    while changed:
        changed = False
        for candidate in candidates(current):
            if still(candidate):
                current = candidate
                changed = True
                break
    return current


def candidates(document: object):
    """Smaller payloads to try, largest reduction first."""
    if isinstance(document, dict) and isinstance(document.get("@graph"), list):
        graph = document["@graph"]
        for i in range(len(graph)):
            yield {**document, "@graph": graph[:i] + graph[i + 1 :]}
        for i, entity in enumerate(graph):
            for smaller in shrink_entity(entity):
                yield {**document, "@graph": graph[:i] + [smaller] + graph[i + 1 :]}
        return
    if isinstance(document, list):
        for i in range(len(document)):
            yield document[:i] + document[i + 1 :]
        for i, entity in enumerate(document):
            for smaller in shrink_entity(entity):
                yield document[:i] + [smaller] + document[i + 1 :]
        return
    if isinstance(document, dict):
        yield from shrink_entity(document)


def shrink_entity(entity):
    if not isinstance(entity, dict):
        return
    for key in list(entity):
        if key in ("@id", "@type"):
            continue
        smaller = dict(entity)
        del smaller[key]
        yield smaller
    for key, value in entity.items():
        if isinstance(value, list) and value:
            for i in range(len(value)):
                yield {**entity, key: value[:i] + value[i + 1 :]}
        if isinstance(value, dict) and "@id" not in value:
            for smaller in shrink_entity(value):
                yield {**entity, key: smaller}


def shape_of(document: object, directory: Path, index: int) -> list[str]:
    """The disagreement, as `<what the reference said> -> <what this port said>`.

    Deliberately not a verdict. Whether a disagreement is one of the declared
    dispositions is `AheadOfReferenceTest`'s question, and answering it here would
    put the disposition table in two languages and let them drift. This says only
    what changed, which is enough to triage a run: a withdrawal or a restatement is
    something to look up in `parity/ahead/`, a finding the port *added* is
    something the port may never do at all, and a pure reordering is neither.

    Matched on the finding's identity -- property, entity, value -- and not on
    position. A restatement changes a finding's severity, and severity is part of
    what the report is ordered by, so a restated finding moves; a positional walk
    reports that as a withdrawal *and* an addition and cries wolf about the one
    line that is supposed to mean something.
    """
    reference = json.loads(reference_output(document, directory, index))["findings"]
    ours = json.loads(port_output(document, directory, index))["findings"]

    shared = list(ours)
    only_reference = []
    for finding in reference:
        if finding in shared:
            shared.remove(finding)
        else:
            only_reference.append(finding)
    only_ours = shared

    if not only_reference and not only_ours:
        return ["the same findings in a different order"]

    def identity(finding):
        return (finding["property"], finding["entity"], finding["value"])

    shapes: list[str] = []
    unmatched = list(only_ours)
    for expected in only_reference:
        restated = next((f for f in unmatched if identity(f) == identity(expected)), None)
        if restated is not None:
            unmatched.remove(restated)
            shapes.append(
                f"{expected['code']}/{expected['severity']}"
                f" -> {restated['code']}/{restated['severity']}"
                f" on {expected['property']}"
            )
        else:
            shapes.append(
                f"{expected['code']}/{expected['severity']} -> withdrawn"
                f" on {expected['property']}"
            )
    for added in unmatched:
        shapes.append(
            f"THE PORT ADDED {added['code']}/{added['severity']}"
            f" on {added['property']} -- the port may never add a finding"
        )
    return shapes


def run(
    count: int, seed: int, workers: int, out: Path | None, mutate=None, minimise: bool = True
) -> list[object]:
    """Generate, compare, and shrink. Returns the diverging payloads."""
    classes, properties, id_coerced = load_terms()
    rng = random.Random(seed)
    documents = [Generator(rng, classes, properties, id_coerced).document() for _ in range(count)]

    diverging: list[object] = []
    with tempfile.TemporaryDirectory() as tmp:
        directory = Path(tmp)

        def check(item):
            index, document = item
            return document if disagrees(document, directory, index, mutate) else None

        with ThreadPoolExecutor(max_workers=workers) as pool:
            for found in pool.map(check, enumerate(documents)):
                if found is not None:
                    diverging.append(found)

        minimised = (
            [shrink(document, directory, mutate) for document in diverging]
            if minimise
            else diverging
        )

    if out is not None and minimised:
        out.mkdir(parents=True, exist_ok=True)
        for i, document in enumerate(minimised):
            target = out / f"divergence-{seed}-{i}.json"
            target.write_text(
                json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            print(f"wrote {target}", file=sys.stderr)
    return minimised


def self_check(workers: int) -> int:
    """Prove the harness can see a divergence, by planting one.

    A fuzzer that reports "no divergences" is indistinguishable from a fuzzer that
    is not comparing anything, and this one will report exactly that on a good day.
    So it is run once against a deliberately corrupted view of the port's output
    and required to notice, the same way `ParityTest` and the parity CI job are
    watched to fail.
    """
    cases = 12
    corrupted = run(
        count=cases,
        seed=0,
        workers=workers,
        out=None,
        mutate=lambda text: re.sub(r'"exit_code": (\d)', r'"exit_code": 9', text, count=1),
    )
    if len(corrupted) != cases:
        print(
            f"self-check FAILED: {len(corrupted)} of {cases} payloads noticed a corrupted"
            " exit code. Every one of them should have, so a green run from this harness"
            " would mean less than it claims",
            file=sys.stderr,
        )
        return 1
    clean = run(count=cases, seed=0, workers=workers, out=None)
    if clean:
        print(
            "self-check FAILED: the same payloads diverge without any corruption, so the"
            " corrupted run proved nothing",
            file=sys.stderr,
        )
        return 1
    print(
        f"self-check passed: {len(corrupted)} of {cases} payloads caught with a corrupted"
        " exit code, 0 without"
    )
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--count", type=int, default=500, help="payloads to generate")
    parser.add_argument("--seed", type=int, default=0, help="seed, so a run is reproducible")
    parser.add_argument("--workers", type=int, default=8, help="parallel port invocations")
    parser.add_argument(
        "--out",
        type=Path,
        default=None,
        help="directory to write minimised diverging payloads into",
    )
    parser.add_argument(
        "--no-shrink",
        action="store_true",
        help="skip minimisation; a fast sweep for whether anything diverges at all",
    )
    parser.add_argument(
        "--self-check",
        action="store_true",
        help="prove the comparison can fail, then exit",
    )
    args = parser.parse_args(argv)

    if not PORT.exists():
        print(f"{PORT} is not built; run ./gradlew installDist", file=sys.stderr)
        return 2

    if args.self_check:
        return self_check(args.workers)

    minimised = run(
        args.count, args.seed, args.workers, args.out, minimise=not args.no_shrink
    )
    print(
        f"{args.count} generated payload(s), seed {args.seed}: "
        + (
            "no divergence"
            if not minimised
            else f"{len(minimised)} diverging payload(s), minimised"
        )
    )
    if minimised:
        shapes: Counter[str] = Counter()
        with tempfile.TemporaryDirectory() as tmp:
            for i, document in enumerate(minimised):
                shapes.update(shape_of(document, Path(tmp), i))
        print("\nwhat changed, by shape:")
        for shape, times in shapes.most_common():
            print(f"  {times:4d}  {shape}")
        print(
            "\nA withdrawal or a restatement may be one of the dispositions"
            " parity/ahead/ declares; add the payload there and let"
            " AheadOfReferenceTest rule on it. A line beginning THE PORT ADDED is"
            " never allowed and is a defect in this port."
        )
    return 1 if minimised else 0


if __name__ == "__main__":
    raise SystemExit(main())
