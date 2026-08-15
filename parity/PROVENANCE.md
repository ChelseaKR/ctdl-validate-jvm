# Where the parity corpus comes from

## Fixtures

`parity/fixtures/` holds 21 CTDL JSON-LD payloads. They are synthetic. Every
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

Ten were written for this repository, because the sibling's fixtures exercise
11 of the 19 finding codes and a parity corpus that leaves 8 rules untested
is not comparing the implementations on those rules:

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

`ParityTest` fails if any finding code the checks can emit has no fixture
producing it, so the corpus cannot silently fall behind the rule set.

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
`checks/` — is byte identical to the reference's `main`; the two differ only
in CLI plumbing and in an extraction subcommand this port does not cover.

CI reinstalls the pinned reference, regenerates the directory, and fails if
anything differs from what is committed.

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
