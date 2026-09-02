# Roadmap

## What this repository is for

One claim, and only one: that a conformance rule set can be specified precisely
enough to be rebuilt in another language and reconciled with the original
finding for finding. Everything below is scoped to that. A row that would make
this a general-purpose JVM project rather than the evidence for that claim does
not belong here, and several of the N/A rows below are N/A for exactly that
reason.

Tier: C. Archetype: `cli-utility`. No hosted route, no HTML surface, no
background service, no model, no network call at all — `OfflineGuaranteeTest`
reads the compiled classes and fails if any of them so much as names a
networking type.

## Metrics ledger

Every gate below is `./gradlew verify`, which is exactly what CI runs on both
Temurin 17 and 21, plus the three workflows beside it. AUTO means the build
fails on it without a human deciding anything.

| Metric | Target | Measured by | Gate | Owner |
|--------|--------|-------------|------|-------|
| Byte equality with the pinned reference over every fixture | 100% of `parity/fixtures/`, no exemptions | `ParityTest` in `:test` | AUTO | maintainer |
| The committed expectations really are the reference's output | regenerating changes nothing | `tools/generate_expectations.py --check`, in its own CI job, against `ctdl-validate==0.1.0` installed with `--require-hashes` | AUTO | maintainer |
| That regeneration check can fail | proven per run | the parity job perturbs one expectation in a copy of the tree and requires `--check` to notice | AUTO | maintainer |
| Finding codes the reference has and this port does not | visible, not silent | `FindingCodeCensusTest` against `parity/reference-codes.json`, parsed out of the reference's own source | AUTO | maintainer |
| Every finding code the checks can emit has a fixture | 100% | `ParityTest` | AUTO | maintainer |
| Divergences from the pinned reference | only the declared dispositions, each gated on a predicate | `AheadOfReferenceTest` over `parity/ahead/` | AUTO | maintainer |
| Branch coverage [CQ-08] | branch coverage >= 85% (measured at 88.51% branch on 2026-09-01) | `jacocoTestCoverageVerification` in `verify` | AUTO | maintainer |
| SpotBugs findings, main and test | 0, at max effort and low threshold | `spotbugsMain`, `spotbugsTest` in `verify` | AUTO | maintainer |
| Formatter findings | 0 (google-java-format via Spotless) | `spotlessCheck` in `verify` | AUTO | maintainer |
| Compiler warnings | 0 (`--release 17`, `-Xlint:all -Werror`) | `compileJava` | AUTO | maintainer |
| The Java 17 floor is a tested claim, not a compiler flag | the suite runs on 17 | the CI matrix runs `verify` on Temurin 17 as well as 21 | AUTO | maintainer |
| Network reachability from the validator | none | `OfflineGuaranteeTest` reads the compiled classes | AUTO | maintainer |
| Same input, same bytes out | always | `DeterminismTest` | AUTO | maintainer |
| The vendored CTDL and CTDL-ASN snapshots are the ones `SOURCES.md` names | hashes match | `VendorIntegrityTest` | AUTO | maintainer |
| Figures the documents publish about this repository | derived, never typed | `PublishedFiguresTest` over README, `CONTRIBUTING.md`, `CITATION.cff`, `parity/PROVENANCE.md`, this file, and one Javadoc paragraph. Rewording a sentence past the pattern that reads it also fails | AUTO | maintainer |
| Taint-style SAST | 0 blocking findings | Semgrep (`--config auto`), own workflow | AUTO | maintainer |
| Verified secrets in the whole history | 0 | TruffleHog full-clone scan, own workflow, weekly and per PR | AUTO | maintainer |
| SHA-pinned `uses:` in workflows [SEC-25] | 100%: all 9 `uses:` steps carry a full commit SHA, and the Semgrep container is pinned by image digest | `PublishedFiguresTest` counts them and fails on an unpinned one; Dependabot maintains the pins | AUTO | maintainer |
| Dependency freshness | Dependabot, Gradle and Actions, seven-day cooldown | Dependabot; the reference pin is excluded on purpose | REVIEW | maintainer |
| Published artifact, tag, or release | none, deliberately | nothing to gate; see the exceptions below | REVIEW | maintainer |

### Why 85% and not the 90% CQ-08 asks of a published library

Nothing here is published. There is no tag, no artifact on Maven Central or any
other registry, and no release workflow, so no one can depend on this and be
broken by it. The floor that matters for this repository is byte equality with
the reference, which is absolute and admits no exemption; branch coverage is a
secondary guard on the code around it. It is set where it is because it is the
level the suite actually holds without tests written to move a number, and
`PublishedFiguresTest` reads the figure out of `build.gradle.kts` so raising it
is a one-line change that updates every sentence stating it.

### Standards recorded as exceptions rather than gates

Each of these is a row in the README's Standards Conformance table with its
reason; they are repeated here because a ledger that silently omits them would
be claiming a smaller surface than the standard set defines.

| Standard | Why there is no gate |
|---|---|
| Accessibility | No human-facing HTML. An offline CLI and a library. |
| Internationalization | English-only operator output, matched byte for byte against the reference. See [`I18N.md`](I18N.md). |
| AI Evaluation | No model component, and there will not be one. |
| Performance | No hosted route and no shipped HTML, so no delivery budget to hold. |
| Observability | A single-shot CLI. The observable output is the exit code and the findings, byte-identical for the same input; no tracing, metrics, or SLO surface exists and none is claimed. |
| Release & Versioning | SemVer and Keep-a-Changelog are declared and the CHANGELOG is kept, but nothing has been released. Inventing a release workflow before the first tag would be a workflow nobody has run. |

## The plan

Ordered, and each item states the figure or the artifact that would show it was
done. Nothing here is a date.

### 1. Close the gap between "every code has a fixture" and "every shape has a fixture"

The corpus covers every finding code. It does not cover every *document shape*
that reaches a code, and both defects the port has fixed independently of the
reference — the nested-object inverse and the shadowed duplicate `@id` — hid in
exactly that gap: byte equality was green because both implementations shared
the mistake and no fixture reached the shape.

**Done when** there is a mechanical answer to "which shapes does the corpus
reach", not a longer list of fixtures.

`tools/differential_fuzz.py` is that answer, and it is now here: it generates
CTDL-shaped payloads from the vendored snapshot's own terms, runs both
implementations, and compares the whole parity document byte for byte. Measured
2026-09-01 over 3,000 payloads on three seeds, every disagreement it found was
one of the declared dispositions and nothing else. What is left of this item is
a decision rather than work: whether to run it on a schedule. A nightly job
spends Actions minutes on a check that will usually find nothing, and the
honest alternative — running it before a change to the checks, and after one —
costs nothing and is what `CONTRIBUTING.md` now asks for. That call is not made
here.

### 2. Port `--resolve`, then bump the pin

`parity/PROVENANCE.md` records what bumping to `0.2.1` costs, measured: three
expectations move on message text, and `FindingCodeCensusTest` fails on
`REF_RESOLVED_SUPPLIED`, a rule this port does not have. Both come from one
upstream feature. The pin cannot move until it is ported, and porting it is a
design review of its own — a side index of supplied `@id`s that is never itself
validated — rather than a step inside a version bump.

**Done when** the pin is at the current release and `parity/ahead/` holds only
the entries that are still genuinely ahead.

### 3. Take the remaining known-wrong verdicts upstream

Two are recorded in the README limits: the `owl:sameAs` `DOMAIN_VIOLATION` both
implementations still raise on a domain of `rdfs:Resource`, and the half of the
duplicate-`@id` defect that suppresses a genuine violation rather than inventing
one. Neither is fixable here: the first would be this repository inventing a
disposition rather than porting one, and the second means raising an ERROR the
pinned reference does not raise, which `parity/ahead/` is arranged not to permit.

**Done when** each has a decision in the sibling — fixed, or refused with a
reason — and this repository either ports it or records the refusal.

### 4. Register in the portfolio standards manifest

`STANDARDS/applicability.yml` has no entry for this repository, which the
conformance check treats as a loud failure rather than a silent skip. The entry
itself lives in the private standards repository and its `publication` field is
a decision about this repository's public status, so it is not written here. See
issue #9.

**Done when** the weekly conformance run passes with this repository in it.

## Decided against

- **A release.** See the exception table above. This is a demonstration port;
  publishing it would create a dependency surface with no one on the other end.
- **A second parity corpus that is not byte-equal.** `parity/ahead/` is bounded,
  self-expiring, and unpleasant to grow on purpose. See
  [ADR 0004](adr/0004-the-port-may-lead-the-pinned-reference.md) and
  [ADR 0005](adr/0005-a-disposition-may-be-gated-on-the-payload.md).
- **Rule changes made here first.** The rules belong to the sibling. A rule
  change made here would fail this repository's own build, and correctly so.
