# 8. Checks 6 to 9 are ported before the release that carries them

## Status

Accepted

## Context

The reference's main branch carries four checks and six finding codes that no
release has, and changes the rule core underneath them: a repeated `@id` is one
entity, the `@graph` envelope's `@id` is checked, the encodings' concepts and
unstable terms are read, and a document with nothing to check says so. Measured
out of source by `tools/reference_gap.py`, this port implemented 22 of `main`'s
28 validation rules.

ADR 0003 makes parity byte equality against a pinned release, and ADR 0004
forbids the port emitting a finding the pinned release does not. Both are right,
and both mean the port cannot carry these rules on `main` until the reference
releases them. Waiting to write them has two costs: the port's distance from the
reference grows with every upstream commit, and whoever writes them after the
release has no way to tell a porting mistake from a release that differs from
`main`.

## Decision

- **The rules are ported now, on a branch that cannot merge until the release
  exists** (#40, waiting on `ChelseaKR/ctdl-validate#52`). The branch is a
  draft, and it is red against the pin by design.
- **Its evidence is byte equality with the reference at one unreleased
  commit.** `tools/next_release_parity.py` renders every fixture in both corpora
  with the reference imported from a clean checkout of the commit
  `parity/reference-main-commit.txt` names, runs the port over the same
  fixtures, and compares the documents byte for byte. A non-gating workflow runs
  it on every pull request.
- **The red set against the pin is itself asserted, not waved at.** `ParityTest`
  is red on exactly the fixtures whose pinned and `main` documents differ;
  `FindingCodeCensusTest` is red on exactly the six codes no release has;
  `AheadOfReferenceTest` is red where the merge adds a disclosure the pin lacks.
  Anything else red is a defect in the branch.
- **The port's own shadowed-declaration disposition (ADR 0005) is retired.** The
  merge makes it meaningless -- there is one declaration per identifier to ask
  -- and the reference's merge decides the same fixtures the same way.

## Consequences

- The day the release exists, the bump is mechanical: move
  `parity/reference-requirements.txt` to it with both hashes; regenerate; delete
  `parity/ahead/` entries the release now agrees with (the harness measures all
  five agreeing with `main`), and empty `FindingCodeCensusTest.PORT_IS_AHEAD` if
  both dispositions shipped; delete the harness, its commit file and its
  workflow; and restate the figures `PublishedFiguresTest` then asks for.
- If the release differs from the pinned commit, the regeneration shows exactly
  where, against a port already known to equal that commit, so the difference
  is the release's and not the port's.
- The branch has to be kept rebased and re-measured while it waits. That is the
  cost, and it is smaller than porting nine checks' worth of changes blind.
