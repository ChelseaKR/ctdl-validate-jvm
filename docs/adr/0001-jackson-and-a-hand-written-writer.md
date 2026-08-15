# 1. Parse with Jackson; write JSON by hand

## Status

Accepted

## Context

The reference implementation has zero runtime dependencies, which its
[ADR 0001](https://github.com/ChelseaKR/ctdl-validate/blob/main/docs/adr/0001-vendored-schemas-zero-runtime-deps.md)
records as a supply-chain and reproducibility decision. Python's standard
library includes a JSON parser, so that posture costs nothing there.

Java's does not. Matching the sibling's zero-dependency stance would mean
hand-writing a JSON parser, which is both more code than the validator itself
and a strange thing for a repository whose stated purpose is to work in JVM
idioms. A reader assessing this code would reasonably read a hand-rolled
parser as evidence of not knowing the ecosystem.

There is a second, opposite problem. The parity suite compares reports as
*text*, because that is the strictest available form of "these agree", and
the reference produces its text with `json.dumps(payload, indent=2,
sort_keys=True, ensure_ascii=False)`. Jackson's pretty-printer does not
produce those bytes: it spaces separators differently, escapes a different
set of characters, and renders empty containers differently.

## Decision

Split the two directions.

- **Parsing** uses Jackson (`jackson-databind`), the ecosystem's usual
  choice. It is the only runtime dependency.
- **Writing** does not use Jackson. `CanonicalJson` is a small writer whose
  entire specification is "produce what `json.dumps(..., indent=2,
  sort_keys=True, ensure_ascii=False)` produces", with the four behaviours
  that matters for written down in its Javadoc and covered by tests whose
  expected values came from CPython.

## Consequences

- One runtime dependency, kept current by Dependabot, rather than zero. The
  difference from the sibling's posture is stated in the README's limits
  section rather than left for a reader to notice.
- The output writer is small, dependency-free, and testable against the thing
  it is imitating. It handles only the node types a report can contain and
  rejects anything else rather than guessing, so a future report field of an
  unexpected type fails loudly.
- Byte-for-byte comparison against the reference stays available, which is
  what makes the parity claim as strong as it is. Had the writer been
  Jackson's, parity would have had to weaken to structural equality, and a
  formatting difference could have hidden a content difference.
