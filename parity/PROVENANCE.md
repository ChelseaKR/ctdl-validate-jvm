# Where the parity corpus comes from

## Fixtures

`parity/fixtures/` holds 25 CTDL JSON-LD payloads. They are synthetic. Every
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

Fourteen were written for this repository, because the sibling's fixtures
exercise 11 of the reference's 20 finding codes and a parity corpus that leaves
9 rules untested is not comparing the implementations on those rules:

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
| `resolved_references.json` | `REF_RESOLVED_SUPPLIED`, validated with the two documents in `parity/resolve/resolved_references/`: a supplied target typed in range, one typed outside it (a `RANGE_VIOLATION` naming the file it was read from), one typed only outside CTDL, one declared inline inside a supplied entity, one declared by both documents (the first read wins), a supplied blank node that must not be indexed, a supplied document with a defect of its own that must not be reported, the plural "None of the 2 documents" shortfall, and an `isPartOf` no framework in the payload or the documents supplied matches |
| `resolved_one_document.json` | the singular "None of the 1 document" shortfall, and a `RANGE_DOCS_CONFLICT` whose target was read from a supplied document |
| `resolved_nothing_supplied.json` | `--resolve` given a directory holding no `.json` file: the reference supplies nothing from it and still prompts for `--resolve`, so the port has to as well |

## Supplied documents

`parity/resolve/<fixture>/` holds the documents a fixture is validated *with*,
the way `--resolve` passes them: `tools/generate_expectations.py` hands the
reference that directory as one `--resolve` argument, and `ParityTest` hands the
port the same one. They are synthetic by the same rule as the fixtures, and none
of them is vendored from the sibling.

The reference prints the path of every supplied document inside its findings,
so the path is part of what the two implementations must agree on. It is spelled
relative to the repository root and the generator runs from there, because an
absolute path would put one machine's layout into a committed file. The port
spells a path the way Python's `pathlib` does (`./a.json` is `a.json`), because
`java.nio.file.Path` does not.

`resolved_references/` also holds three files the reference must not read, each
there to fail if it is read: `nested/one_level_too_deep.json` (a directory is
read one level deep), `not_a_json_suffix.jsonld` (only `.json` files are read),
and `.json`, which `pathlib` gives no suffix at all because a leading dot is part
of a name. Each declares the identifier the fixture's `ceterms:approvedBy` names,
so reading any one of them would turn that `REF_OUTSIDE_PAYLOAD` into
`REF_RESOLVED_SUPPLIED`.

`ParityTest` fails on a resolve directory with no fixture, and on any resolve
directory whose documents change nothing about what its fixture reports, except
`resolved_nothing_supplied/`, whose whole point is that they do not.

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

The reference is pinned to `ctdl-validate==0.2.1`, a released PyPI artifact,
by version and by both artifact hashes, in `reference-requirements.txt`.

The pin is to a release rather than to `main` so the expectations sit against
a rule set that cannot move underneath them. It was `0.1.0` until `--resolve`
was ported; the rule core of `0.1.0` was byte identical to the reference's
`main` as of 2026-08-14. That has not been true since. `main` has landed the
three dispositions this port carries in `parity/ahead/` and six rules this port
does not have, none of it in any release. Bumping the pin is a review of a
rule-set change, not a dependency chore; the ahead corpus below bounds the
divergence from the pin, and the next section but one says how far `main` has
moved.

CI reinstalls the pinned reference, regenerates the directory, and fails if
anything differs from what is committed.

### What moving the pin to 0.2.1 cost

Measured by hand on 2026-08-29, and exactly what the move cost when it was made
on 2026-09-11:

- **The ahead corpus did not collapse.** 0.2.1 carries none of the three
  dispositions and neither of the two shared-defect fixes. Regenerating
  `parity/ahead/reference/` against it changed not one byte, so every entry in
  `parity/ahead/` is still genuinely ahead and none of them may be deleted.
- **Three `parity/expected/` documents moved**, on message and citation text
  rather than on any code or severity:
  `bug_class_252_wrong_framework_identifier`, `ctid_uri_mismatch`, and
  `external_reference`. Nothing else in the corpus moved.
- **The reference gained a rule, `REF_RESOLVED_SUPPLIED`**, taking its census
  from 19 codes to 20.

Those were one change and not two. 0.2.1 adds a `--resolve` flag that indexes
documents the operator already has, and the amended `REF_OUTSIDE_PAYLOAD`
message ends `Pass it with --resolve to settle this.` Byte parity on that
sentence was not reachable by editing a string, because printing it would have
directed a reader to an option this port did not have. So `--resolve` was
ported first, as the reference's `session.py` writes it — a side index of
supplied `@id`s that is never itself validated, and a resolution that may turn
a non-answer into an answer but never into a failure — and the pin moved with
it. [ADR 0007](../docs/adr/0007-resolve-is-ported-as-the-reference-wrote-it.md)
records the design.

### What the port is behind by, measured against source

The section above is one measurement of one release, taken by hand. It goes
stale on the sibling's next commit, and it says nothing about `main`, which is
where the reference now carries three dispositions this port has and six rules
it does not.

`tools/reference_gap.py` reads both rule sets out of source and prints the two
numbers -- how many of the reference's validation rules this port implements,
and out of how many. It needs a checkout of the sibling and nothing else: no
install, no network, no built CLI.

```sh
python3 tools/reference_gap.py --reference ../ctdl-validate --ref v0.1.0
python3 tools/reference_gap.py --reference ../ctdl-validate --ref v0.2.1
python3 tools/reference_gap.py --reference ../ctdl-validate --ref origin/main
```

Measured 2026-09-11, after `--resolve` was ported, with the reference at
`origin/main` = `816591ac6364`:

| reference ref | its validation rules | this port implements | behind by | ahead by |
|---|---:|---|---:|---:|
| `v0.1.0` | 19 | 19 of 19 | 0 | 3 |
| `v0.2.1` (the pin, newest release) | 20 | 20 of 20 | 0 | 2 |
| `origin/main` | 28 | 22 of 28 | **6** | 0 |

Before the port the same three rows read 19 of 19, 19 of 20 and 21 of 28.

Three things in that table are worth reading rather than skimming.

**The pin row agrees with `FindingCodeCensusTest`, from the other direction.**
That suite reads `parity/reference-codes.json`, a record generated by
`tools/generate_expectations.py` from the installed release; this reads the
reference's Python source at a git ref. Two readers, two languages, two data
paths, and they agree that at the pin the port is behind by nothing and ahead by
exactly `CONCEPT_RANGE_CONFLICT` and `VERSION_RANGE_CONFLICT`. That is the
strongest evidence available that either of them is right. Against `0.1.0` the
port is now ahead by three, the third being `REF_RESOLVED_SUPPLIED`: `0.1.0` had
no `--resolve`.

**On `main` the port is ahead by nothing.** Both dispositions are now on the
reference's `main`, so `parity/ahead/` is ahead of the *pin* and no longer ahead
of the *reference*. It is still correct and must not be deleted -- the pin is
what it is measured against -- but the divergence it bounds is now a version
gap rather than a disagreement.

**The six are named, and none of them is in a release.**
`CONCEPT_NOT_IDENTIFIED`, `CONCEPT_OUTSIDE_SCHEME`, `CONCEPT_OUTSIDE_SNAPSHOT`,
`ID_DECLARED_MORE_THAN_ONCE`, `LANGUAGE_MAP_EXPECTED`, `TERM_UNSTABLE`: checks 6
to 9, all landed on `main` after `0.2.1`. They are what the next pin bump costs,
and it cannot happen until the reference cuts a release carrying them.
`REF_RESOLVED_SUPPLIED` was the seventh and is ported.

The reference's other **20** finding codes are extraction notes under
`src/ctdl_validate/extract/`, a subcommand this port does not have and does not
claim to. They are excluded by name, and both totals are printed, so the
exclusion is visible rather than assumed: counting them would report this port
as covering 46% of the reference rather than 79%, and would move whenever the
sibling touched a surface that is out of scope here.

This table is a dated observation at a named commit, not a generated artifact,
and nothing gates on it. Making it self-updating is what #41 asks for.

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
| `shadowed_duplicate_id.json` | A `ceterms:address` reference to an `@id` the same `@graph` declares at top level as a `ceterms:Place`, which is in range — but an unrelated entity embeds an inline stub for that same `@id` typed `ceterms:Organization` three lines earlier, and the walk reaches the stub first. The pinned release rules on the stub and calls it `RANGE_VIOLATION`/ERROR; this port asks every declaration of the identifier and withdraws it. Org Y's second reference, `ceterms:jurisdiction`, is in the fixture on purpose and is not disposed of: nothing in the payload is a `ceterms:JurisdictionProfile`, so both sides raise that ERROR identically. It is what gives `AheadOfReferenceTest` a finding the port really reports, which is the only way the "never invent a finding" half of the comparison can be watched to fail on this fixture. Filed upstream as `ChelseaKR/ctdl-validate#33`; still present in `0.2.1` |

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
