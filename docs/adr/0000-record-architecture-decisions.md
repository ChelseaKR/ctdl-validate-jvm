# 0. Record architecture decisions

## Status

Accepted

## Context

This repository is a port, which means most of its decisions were made
elsewhere and should not be re-litigated here: what the checks do, how
severities are defined, why the schemas are vendored. Those live in
`ChelseaKR/ctdl-validate`.

The decisions that *are* this repository's own are few and consequential:
what "parity" is allowed to mean, which JVM dependencies exist, where the
language floor sits, and what happens where the two languages cannot be made
to agree. Each of those is easy to erode by accident later, and a commit
message is not where a load-bearing choice should live.

## Decision

We will record architecture decisions in Architecture Decision Records
(ADRs) using the format described by Michael Nygard.

- Each ADR is a short Markdown file in `docs/adr/`, numbered sequentially and
  named `NNNN-title-in-kebab-case.md`.
- Each ADR has the sections **Title**, **Status**, **Context**, **Decision**,
  and **Consequences**.
- **Status** is one of *Proposed*, *Accepted*, *Deprecated*, or *Superseded*.
  A superseded ADR is not deleted; it is marked superseded and points to the
  ADR that replaces it, and the replacement points back.
- ADRs are immutable once accepted, except to change their status. A new
  decision is a new ADR, not an edit to an old one.
- Decisions inherited from the reference implementation are not restated
  here. An ADR in this repository exists only where this port had a choice
  the reference did not.

## Consequences

- The reasoning behind structural decisions is preserved and versioned
  alongside the code it explains.
- The boundary between "the sibling decided this" and "the port decided this"
  stays visible, which matters because the whole value of the repository
  rests on the port not quietly inventing behaviour.
- ADRs capture decisions, not the full design: the README remains the
  narrative.
