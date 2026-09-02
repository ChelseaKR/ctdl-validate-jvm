# 5. A disposition may be gated on the payload, and only in the direction that withdraws

## Status

Accepted

Extends [ADR 0004](0004-the-port-may-lead-the-pinned-reference.md). Nothing in
0004 is reversed.

## Context

ADR 0004 says every entry in `parity/ahead/` is gated on "a predicate evaluated
against the vendored snapshot", and the three entries it was written for are
schema arguments end to end: a property whose declared range is `rdfs:Resource`,
a property CTDL ranges on `skos:Concept` while naming a `meta:targetScheme`, a
version property whose range is a strict subset of its own domain. In each of
those the property name really is the whole of the argument, and grounding the
permission in the snapshot is what keeps the table from being a licence.

The duplicate-`@id` defect (#14) does not have that shape.

`Graph.byId` keeps whichever declaration of an `@id` the walk reached first, and
the walk descends into an earlier entity's inline objects before it reaches the
next top-level entry. A stub embedded under some unrelated entity therefore
becomes "the" node for every later bare-IRI resolution of that identifier, and a
class ruling is decided by where in the document the stub happened to be written.
The repro is a `ceterms:address` reference to an `@id` the same `@graph` declares
at top level as a `ceterms:Place` — squarely in range — reported as a
`RANGE_VIOLATION` because an inline `ceterms:Organization` stub for that `@id`
sits three lines earlier.

Nothing about `ceterms:address` is special. *Every* property whose declared range
names entity classes can produce that finding, so a property predicate for this
disposition would admit essentially the whole schema. Writing one anyway would
satisfy the letter of ADR 0004 and defeat its purpose: a gate that admits
everything is the licence 0004 exists to prevent, and it would be worse than no
gate because it would look like one.

The fact that makes the disagreement legitimate is not about the property. It is
about the payload: the identifier the finding names really is declared more than
once, and those declarations really do disagree about `@type`. That is a fact the
fixture contains and a test can read.

## Decision

- A `Disposition` may carry a **second gate over the fixture and the reference's
  own finding**, alongside the property predicate. Both must hold. The default is
  no document gate, so the three existing rows are unchanged and unaffected.
- The document gate is **read out of the fixture**, never asserted. For the
  shadowed-declaration row it requires three things: the `@id` the finding names
  as its value is declared more than once in the payload; those declarations do
  not agree about `@type`, so there was something to shadow; and the entity the
  finding is about really does carry that property with that value, so the row
  cannot be reached by a finding relabelled onto a property the document never
  used. That third clause is what keeps
  `AheadOfReferenceTest#theComparisonItselfHasBeenBroken` biting: corrupting the
  reference's `property` still has to make the comparison fail, and for this row
  the property predicate alone would wave it through.
- The gate **fails closed**. A fixture written in a shape the reader cannot
  parse — full-IRI property keys, say — reports no shadowing, the disagreement
  goes undeclared, and the build is red. A permission that cannot be established
  is not granted.
- **The fix itself runs one way only.** A range ruling asks every declaration of
  the identifier, and it asks *after* the resolved node has already failed, so it
  can withdraw an ERROR and can never raise one. ADR 0004's "the port may never
  add a finding the reference does not have" is therefore untouched, and it is
  untouched by construction rather than by care.
- **The mirror case is not fixed, and is recorded rather than left implicit.**
  Where the first-walked declaration satisfies a range and a later one does not,
  a genuine violation is suppressed, and this port goes on suppressing it exactly
  as the reference does. Reporting it means raising an ERROR the pinned reference
  does not raise, which `parity/ahead/` is arranged not to permit and which is a
  rule-level ruling that belongs in the sibling. `DuplicateIdShadowingTest`
  asserts the current behaviour so that changing it has to come back through this
  ADR and the README limits together.

## Consequences

- The kind of defect `parity/ahead/` can bound is wider than "the schema says
  something the reference did not read", without the table getting looser: a row
  whose argument is about the payload has to prove that argument against the
  payload.
- Two gates is more machinery than one, and the cost is real: an entry now needs
  a property predicate *and*, where the property predicate is not the argument, a
  document predicate and a reason in ADR terms for why it is the right one. The
  intended steady state is still zero entries.
- Merging same-`@id` declarations outright — the union of `@type` and properties,
  which is what a JSON-LD flattening would do — was considered and rejected here.
  It is the more complete answer and it is not available under ADR 0004: where
  the first declaration carries no `@type` at all, a union gives a target the
  checks currently decline to judge, and judging it can raise an ERROR the
  reference does not raise. That is a rule change, it belongs in the sibling
  first, and this ADR should be revisited if it lands there.
