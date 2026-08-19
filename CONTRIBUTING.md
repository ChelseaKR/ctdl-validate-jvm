# Contributing to ctdl-validate-jvm

Thank you for considering a contribution. This repository has an unusual
shape, so it is worth being direct about what a contribution can and cannot
be here.

**This is a port.** The rules belong to
[`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate). If
you have found a rule that is wrong, a spec citation that has gone stale, or
a check that should exist, that repository is where it belongs. A rule change
made here first would fail this repository's own build, and correctly so.

What belongs here is anything about the port: a place where the two
implementations disagree, a fixture that would exercise a rule the corpus
does not, a Java idiom used badly, a gap in the build.

If you have not yet, read [`README.md`](README.md) for what this is and
[`SECURITY.md`](SECURITY.md) for how to report a vulnerability.

## Getting set up

Java 17 or newer. The Gradle wrapper is committed; no other install is
needed.

```sh
./gradlew verify
```

To work on the parity corpus you also need Python 3.12 and the pinned
reference implementation:

```sh
python3 -m pip install --require-hashes -r parity/reference-requirements.txt
```

## The merge gate

A change merges when `./gradlew verify` is green. That is the same target CI
runs, so green locally means green in CI. `make verify` is a thin front door
onto the same Gradle target, for consistency with the rest of the portfolio;
Gradle is still the build system and `build.gradle.kts` is still where the gate
is defined.

| Gate | Task | What it checks |
| --- | --- | --- |
| Compile | `compileJava` | `--release 17`, `-Xlint:all -Werror` |
| Format | `spotlessCheck` | google-java-format; `./gradlew spotlessApply` fixes it |
| Static analysis | `spotbugsMain`, `spotbugsTest` | SpotBugs, max effort, low threshold |
| Tests | `test` | JUnit 5, including `ParityTest` |
| Coverage | `jacocoTestCoverageVerification` | branch coverage >= 85% |

Semgrep and a full-history TruffleHog sweep run in their own workflows, and
every GitHub Action is pinned to a full commit SHA.

## Three invariants

**Parity is the point.** `ParityTest` compares this implementation's output
against the reference's, byte for byte, over every fixture. If it goes red,
one of the two implementations is wrong about a published CTDL rule; find out
which before touching anything. Editing a file in `parity/expected/` or
`parity/ahead/reference/` to make a test pass is never the fix — those files
are generated, CI regenerates them from the pinned reference, and a hand-edit
will simply fail there instead. `parity/ahead/` is the only place this port is
allowed to differ from the reference, it is bounded to one declared
substitution by `AheadOfReferenceTest`, and adding an entry to it needs an ADR
0004-shaped argument, not a convenient exemption.

**Cited rules only.** Every finding carries a rule citation, a source URL, and
a retrieval date. Citation text is the reference implementation's wording,
because it quotes Credential Engine's published sentences; rewording it would
make the two implementations disagree about what a rule says. Do not write a
new citation here. Get it into the sibling first.

**Determinism, and no network.** Same input, same output, byte for byte. No
timestamps, no sampling, no iteration over an unordered collection that
reaches the output. `OfflineGuaranteeTest` reads the compiled classes and
fails if any of them so much as names a networking type, so a change that
makes validation touch the network is a change to what this tool claims to
be, and needs an ADR before it needs a review. There is no model anywhere in
this repository and there will not be one.

## Adding a fixture

A new fixture is a good contribution, especially one that reaches a case the
corpus misses.

1. Write it into `parity/fixtures/`. Synthetic data only: generated
   identifiers, invented names, nothing copied from a real organization or
   from the Credential Registry.
2. Generate its expectation from the reference implementation:
   `python3 tools/generate_expectations.py`.
3. Read the generated expectation. If it is not what you expected, that is
   the interesting part of the contribution — say so in the pull request.
4. Run `./gradlew verify`. If the parity test now fails, you have found a
   divergence, which is worth more than the fixture.
5. Add a row to `parity/PROVENANCE.md` saying what the fixture is for.

## Architecture decisions

Structural choices are recorded in [`docs/adr/`](docs/adr/). An ADR here
exists only where this port had a choice the reference implementation did
not; decisions inherited from the sibling are not restated. If you are
changing something a current ADR settled, write a new ADR rather than editing
the old one.

## Code of conduct

Participation is governed by [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).
