# Responsible-Tech Audits: ctdl-validate-jvm

Project-specific findings under the portfolio Responsible-Tech Framework.
Generic thresholds stay in the portfolio standards; what is here is what is
specific to this repository. Last reviewed: 2026-09-01 (first edition).

This is a port. Where a harm surface is inherited unchanged from
[`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate), this
document says so and does not restate the sibling's analysis. Section B is the
one that is genuinely this repository's own, and it is the reason this file
exists rather than pointing at the sibling's.

## Applicability

- **A Ethics:** applies, inherited. One harm surface: false confidence in a
  validator. Section A.
- **B Ethics, specific to a port:** applies, and is this repository's own. A
  parity suite that quietly compares the port against itself would certify a
  shared mistake as agreement. Section B.
- **C Bias:** minimal surface. The tool applies published structural rules
  uniformly to every payload; it does not rank, score, or profile, and it says
  nothing about the organization that published a document.
- **D Privacy / DPIA:** applies in a narrow form, inherited. The validator reads
  the one local file it is pointed at, entirely in process, keeps nothing, and
  makes no network call — `OfflineGuaranteeTest` reads the compiled classes and
  fails if any of them so much as names a networking type. Credential data can
  describe real people and organizations; none of it leaves the machine.
- **E Transparency:** applies and is the design center. Every finding carries the
  rule citation, source URL, and retrieval date it enforces, and the citation
  text is the reference's verbatim because it quotes Credential Engine's own
  sentences.
- **F Accessibility:** N/A. No graphical or web surface. The CLI's output is
  plain text and `--format json`. The sibling publishes a browser playground and
  gates it; this repository publishes nothing of the kind, and this row becomes
  Applies the day it does.
- **G Security:** applies; see [`SECURITY.md`](../SECURITY.md). Input is
  untrusted JSON. One runtime dependency. The supply-chain controls — SHA-pinned
  actions, `--require-hashes` on the reference install, Semgrep, full-history
  TruffleHog, Dependabot — are in CI and listed in [`ROADMAP.md`](ROADMAP.md).
- **H Effect on third parties:** N/A. Nothing here fetches anyone's server. The
  sibling's extractor does; this port does not cover that subcommand.
- **AI Evaluation:** N/A. No model call exists anywhere in this repository and
  there will not be one. AI-assisted authoring is disclosed in the README.

## A. False confidence in a validator

A tool that reports zero findings is read as "this document is fine". It is not
what this tool measures. It measures conformance to the rules it has vendored, at
the retrieval date recorded beside each one, over the subset of CTDL it parses.
A clean run means nothing was found by those rules, which is a much smaller claim.

Controls, all of them in the README rather than in an appendix: the first ten
lines say what the tool is and is not; "What is not ported, and why" is a section
rather than a footnote; every finding carries its citation, URL, and retrieval
date so a reader can check the rule rather than trust the verdict; and "Limits,
recorded" lists the known-wrong verdicts, including two ERRORs that are wrong
today and are not fixed here because fixing them belongs upstream.

The severity design is part of this control. `UNVERIFIABLE` exists precisely so
that "this reference points outside the payload and I cannot judge it" is not
reported as a pass and not reported as a failure. A missing answer is rendered as
a missing answer.

## B. A parity suite that compares the port against itself

This is the harm surface specific to being a port, and it is the one worth being
blunt about, because the failure mode is a green build.

If `parity/expected/` were hand-written, or generated once from this Java
implementation and then frozen, the suite would be comparing the port against
itself. It would pass forever and prove nothing, while presenting itself — in the
README, in `CITATION.cff`, in the repository description — as evidence that two
independent implementations agree. That is a claim about someone else's software
made from a file this repository wrote about itself.

**The control.** A CI job installs the pinned reference release with
`--require-hashes`, regenerates every expectation from it, and fails on any diff.
Nothing in `parity/expected/` or `parity/ahead/reference/` is hand-written, and
`CONTRIBUTING.md` says editing one to make a test pass is never the fix. The job
additionally perturbs one committed expectation, in a copy of the tree, and
requires the regeneration check to notice — so the control itself is proven to be
able to fail, on every run, rather than trusted.

**The residual risk, and it is real.** Byte equality cannot catch a defect both
implementations share. Two have been found and fixed since: an inverse asserted
as a nested object read as a mismatch, and a class ruling decided by whichever
declaration of a duplicated `@id` was walked first. Both were green under parity,
because the reference agreed — it had the same bug. `FindingCodeCensusTest`
answers "what rules does the other side have that this one does not" by parsing
the reference's own source, which closes part of the gap. The rest is the shape
gap: the corpus covers every finding *code* and not every document *shape* that
reaches one, which is what issue #7's differential fuzzing is for. It is item 1
on the roadmap and it is stated in the README's limits as an open weakness, not
as a solved problem.

**Divergence is bounded rather than hidden.** Where this port answers differently
from the pinned release on purpose, the divergence lives in `parity/ahead/` with a
declared disposition, a recorded reference document generated by the same job, and
a test that fails if the set of disagreements widens by one finding. See
[ADR 0004](adr/0004-the-port-may-lead-the-pinned-reference.md) and
[ADR 0005](adr/0005-a-disposition-may-be-gated-on-the-payload.md). The corpus is
arranged to delete itself when the pin catches up, because a divergence record
that outlives its divergence is a lie.

## C. Data governance

Parity fixtures are synthetic by rule: generated identifiers, invented names,
nothing copied from the Credential Registry or from any organization's website.
`parity/PROVENANCE.md` records which fixtures came from the sibling's own test
suite, which were written here, and why each one exists. The vendored CTDL and
CTDL-ASN snapshots keep their origin and retrieval date in
`src/main/resources/vendor/SOURCES.md`, and `VendorIntegrityTest` fails if the
bytes stop matching what that file says.

## Open items

- **No dated audit record before this one.** This file is the first. The README's
  Standards Conformance table previously carried the declaration on its own,
  which is what issue #9 filed.
- **Not registered in the portfolio standards manifest.** This repository has no
  entry in `STANDARDS/applicability.yml`, which the weekly conformance run treats
  as a loud failure rather than a silent skip. The entry lives in the private
  standards repository and its `publication` field is a decision about this
  repository's public status, so it is not written from here. Issue #9.
- **Two known-wrong verdicts, upstream.** The `owl:sameAs` `DOMAIN_VIOLATION` and
  the suppressing half of the duplicate-`@id` defect. Both are in the README
  limits with the reason each is not fixed here.
