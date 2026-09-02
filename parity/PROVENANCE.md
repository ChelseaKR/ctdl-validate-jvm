# Where the parity corpus comes from

## Fixtures

`parity/fixtures/` holds 22 CTDL JSON-LD payloads. They are synthetic. Every
identifier in them is a generated UUID, every name is invented, and none of
them is a copy of anything published to the Credential Registry or to any
organization's website.

Eleven are vendored from the reference implementation's own test suite,
`ChelseaKR/ctdl-validate`, at `tests/fixtures/`, unmodified. They are that
repository's original test data, Apache-2.0, same author:

    bug_class_250_bare_uuid_for_ctid.json
    bug_class_252_wrong_framework_identifier.json
    clean_empty_graph.json
    clean_framework.json
    clean_optional_only.json
    clean_single_entity.json
    ctid_warnings.json
    domain_violation.json
    external_reference.json
    inverse_mismatch.json
    unresolved_bnode.json

Eleven were written for this repository, because the sibling's fixtures
exercise 11 of the reference's 19 finding codes and a parity corpus that leaves
8 rules untested is not comparing the implementations on those rules:

| Fixture | Why it exists |
|---|---|
| `ctid_malformed.json` | `CTID_MALFORMED` for a string, and for the non-string JSON values whose reported text is Python's `repr` |
| `ctid_uri_mismatch.json` | `CTID_URI_MISMATCH`, and `REGISTRY_URI_MALFORMED` on a property rather than on `@id` |
| `identifier_kind.json` | `REF_BARE_UUID`, `REF_BARE_CTID`, `REF_NOT_IRI` |
| `unknown_terms.json` | `UNKNOWN_CLASS`, `UNKNOWN_PROPERTY`, and a foreign-namespace term that must be left alone |
| `range_docs_conflict.json` | `RANGE_DOCS_CONFLICT`: the documented disagreement between the schema encoding and Credential Engine's usage note |
| `nested_entities.json` | inline objects as property values, where the reported label is a document path rather than an `@id` |
| `mixed_shapes.json` | full-IRI keys, `{"@id": ...}` reference-only objects, a bare top-level array, and a duplicated `@id` |
| `bad_graph_not_array.json` | exit code 2: `@graph` present but not an array |
| `bad_top_level_scalar.json` | exit code 2: a document shape the tool does not read |
| `bad_entity_not_object.json` | exit code 2: an array whose second element is not an entity |
| `concept_range_guards.json` | the edges of the concept-range disposition, all of which both implementations still agree on: a real `skos:Concept` target satisfying the range, a wrong class that is still a `RANGE_VIOLATION`, and `ceterms:classification` — `skos:Concept` with no `meta:targetScheme` — which the disposition must not reach |

`ParityTest` fails if any finding code the checks can emit has no fixture
producing it, so the corpus cannot silently fall behind the rule set.

The corpus had no concept-valued property at all until
`concept_range_guards.json`, which is the direct reason parity never caught
the concept-range false positive that failed 36 of 120 published Registry
documents. A rule the corpus does not touch is a rule the two implementations
are not being compared on, even when every code technically has a fixture.

## Expectations

`parity/expected/` is generated. Each file is what the Python reference
implementation reports for the fixture of the same name, produced by
`tools/generate_expectations.py`, which imports the reference and calls it
directly. Nothing in that directory is hand-written, and editing a file there
to make a test pass would be defeating the only thing this repository is for.

The reference is pinned to `ctdl-validate==0.1.0`, a released PyPI artifact,
by version and by both artifact hashes, in `reference-requirements.txt`.

The pin is to a release rather than to `main` so the expectations sit against
a rule set that cannot move underneath them. As of 2026-08-14 the rule core
of 0.1.0 — `ctid.py`, `graph.py`, `validator.py`, and all five modules under
`checks/` — was byte identical to the reference's `main`; the two differed
only in CLI plumbing and in an extraction subcommand this port does not cover.

That is no longer true. The reference has since released 0.2.0 and 0.2.1 and
landed three rule changes on `main` — the concept-range, version-range, and
universal-range dispositions — which this port carries and no released version
does. Bumping the pin is a review of a rule-set change, not a dependency chore,
so it has not been done reflexively; see the ahead corpus below for how the
resulting divergence is bounded in the meantime.

CI reinstalls the pinned reference, regenerates the directory, and fails if
anything differs from what is committed.

### What bumping the pin to 0.2.1 costs, measured 2026-08-29

The bump was carried out against `ctdl-validate==0.2.1`, the current release,
and the result is recorded here rather than kept, because it does not do what
the ahead corpus was arranged to make it do.

- **The ahead corpus does not collapse.** 0.2.1 carries none of the three
  dispositions. `CONCEPT_RANGE_CONFLICT`, `VERSION_RANGE_CONFLICT`, and the
  universal-range withdrawal are still on the reference's `main` and in no
  release, and regenerating `parity/ahead/reference/` against 0.2.1 changed
  not one byte of it. `AheadOfReferenceTest` stayed green on all 11 cases, so
  every entry in `parity/ahead/` is still genuinely ahead and none of them may
  be deleted.
- **Three `parity/expected/` documents move**, on message and citation text
  rather than on any code or severity:
  `bug_class_252_wrong_framework_identifier`, `ctid_uri_mismatch`, and
  `external_reference`. `ParityTest` fails all three.
- **The reference gained a rule this port does not have**,
  `REF_RESOLVED_SUPPLIED`, taking its census from 19 codes to 20.
  `FindingCodeCensusTest` fails on it, which is the census doing its job: a
  port that is behind on a rule is what it exists to make visible.

Those are one change and not two. 0.2.1 adds a `--resolve` flag that indexes
documents the operator already has, and the amended `REF_OUTSIDE_PAYLOAD`
message ends `Pass it with --resolve to settle this.` Byte parity on that
sentence is not reachable by editing a string here: this port has no such
flag, and printing the sentence anyway would direct a reader to an option that
does not exist. The pin therefore cannot move until `--resolve` is ported, and
that is a feature with its own design to review — the reference's `session.py`
describes a side index of supplied `@id`s that is never itself validated, and
a resolution that may turn a non-answer into an answer but never into a
failure — rather than a step inside a version bump.

Until then the pin stays at 0.1.0 and this repository is behind the reference
by one rule, on purpose and in writing.

## The ahead corpus

`parity/ahead/fixtures/` holds payloads where this port answers differently
from the pinned reference on purpose, and `parity/ahead/reference/` records
what the pinned release says about them. Both are subject to everything above:
the payloads are synthetic, and the reference documents are generated by
`tools/generate_expectations.py` and re-checked by the same CI job.

| Fixture | The divergence |
|---|---|
| `concept_range_conflict.json` | `ceterms:creditUnitType` and `ceterms:creditLevelType` carrying `CredentialAlignmentObject` values, the way every published Registry document encodes them. The pinned release calls both `RANGE_VIOLATION`/ERROR; this port calls them `CONCEPT_RANGE_CONFLICT`/INFO. The two properties exercise both halves of the citation: `creditUnitType` has no alignment-ranged sibling over its scheme and argues from the snapshot as a whole, `creditLevelType` has one (`ceterms:audienceLevelType`) and names it |
| `version_range_conflict.json` | A `ceasn:CompetencyFramework` naming another framework through `ceterms:previousVersion`, whose declared range drops six of the 61 classes in its own declared domain. The pinned release calls it `RANGE_VIOLATION`/ERROR; this port calls it `VERSION_RANGE_CONFLICT`/INFO, because the domain and the range contradict each other and the document satisfies one of them |
| `universal_range.json` | `ceterms:hasMember`, `ceterms:isSimilarTo`, and `owl:sameAs`, all of which declare `rdfs:Resource` in `schema:rangeIncludes`. The pinned release raises four `RANGE_VIOLATION`/ERROR findings across them; this port withdraws all four, because a range that admits every entity there is excludes nothing. The fifth finding, `DOMAIN_VIOLATION` on `owl:sameAs`, is a false ERROR both sides still raise and this port has deliberately not disposed of; see the README limits |
| `nested_inverse_back_reference.json` | The same `ceterms:hasPart`/`ceterms:isPartOf` pair as `parity/fixtures/inverse_mismatch.json`, and the same two courses, written the one way that corpus never writes it: Course B asserts the inverse as an inline object carrying Course A's `@id` rather than as a bare IRI. Both directions genuinely agree. The pinned release compares only strings and calls it `INVERSE_MISMATCH`/ERROR; this port withdraws it. The `INVERSE_ONE_DIRECTION`/INFO both sides also raise is restated verbatim and is not disposed of: it is about the inline object's own path, which carries no `ceterms:hasPart`, and it does not gate the exit code. Filed upstream as `ChelseaKR/ctdl-validate#32`; still present in `0.2.1` |

No Java-side expectation is written for these, deliberately. See
`docs/adr/0004-the-port-may-lead-the-pinned-reference.md` for why, and for how
the corpus is arranged to delete itself once the pin catches up.

## What the document contains

```json
{
  "exit_code": 0,
  "error": null,
  "findings": [ ... ],
  "summary": { "ERROR": 0, "WARNING": 0, "INFO": 0, "UNVERIFIABLE": 0 },
  "text_report": "..."
}
```

Everything in it must agree between the two implementations. `text_report` is
the human-readable report verbatim, so both reporters are covered and not
only the machine-readable one; it is `null` for the exit-code-2 cases, where
the reference prints its message to stderr and nothing to stdout.

The tool's name and version are deliberately not in the document: they are
the only things the two implementations are entitled to differ about.
