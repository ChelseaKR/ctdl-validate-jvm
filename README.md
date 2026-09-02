# ctdl-validate-jvm

A Java port of the rule core of
[`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate),
a validator for CTDL (Credential Transparency Description Language) JSON-LD
payloads, kept honest by a test that runs both implementations over the same
fixtures and fails the build if they disagree about anything.

No network calls at validation time. No model calls, ever. Same input, same
output, byte for byte. Every finding cites the published rule it came from,
with the spec text and the date it was retrieved.

## What this is, and what it is not

This is a deliberate reimplementation of an existing, already-specified rule
set, undertaken to work in JVM idioms. It is a demonstration and a reference
port. It is not a product, it has no users, and it is not a claim of
production JVM experience: the author's daily stack is Python and TypeScript,
and this repository is the ramp, not the résumé line. The interesting content
is the parity property below, not the Java.

The rules are not new here. They were researched, cited, tested, and shipped
in the Python sibling; that repository is where the specification work lives
and where a rule change belongs. What this repository adds is evidence for a
narrower claim: that a conformance rule set can be specified precisely enough
to be rebuilt in another language and reconciled finding for finding.

## The property that makes it worth having

Both implementations are tested against the same corpus, and disagreement is
a build failure.

```
parity/fixtures/     22 CTDL JSON-LD payloads, one per rule and per document shape
parity/expected/     what ChelseaKR/ctdl-validate reports for each of them
```

`ParityTest` validates every fixture with this Java implementation, renders
the result the way the reference renders it, and compares the two strings.
Not "the same number of findings", not "the same codes": the same exit code,
the same findings in the same order, each with the same severity, entity,
property, value, message, rule citation, source URL, and retrieval date --
and, on top of that, the same plain-text report, character for character,
down to the column the severity is padded to.

Two things keep that from being circular:

- **The expectations are generated, never written.**
  [`tools/generate_expectations.py`](tools/generate_expectations.py) produces
  them by importing the reference implementation and running it. Nothing in
  `parity/expected/` is typed by hand.
- **CI regenerates them against the pinned reference release and fails on any
  diff.** Without that job, `parity/expected/` would only record what this
  Java code did on the day the files were written, and the suite would be
  comparing the port against itself. The pin is a released PyPI artifact with
  its hash recorded in
  [`parity/reference-requirements.txt`](parity/reference-requirements.txt).

To watch it fail, change a severity in `src/main/java/.../checks/` and run
`./gradlew test`. `ParityTest` also carries its own break-the-gate case,
which corrupts an expectation and asserts the comparison notices, so the
suite does not depend on anyone remembering to try that by hand.

The corpus is held to the rule set as well: `ParityTest` fails if any finding
code the checks can emit has no fixture exercising it, and the set of codes it
holds them to is parsed out of `src/main/java` rather than read off a list this
port maintains. All 21 have a fixture: 19 of them here, and the 2 this port
emits ahead of the pinned release in `parity/ahead/` below, where byte equality
is not available to be had.

### Where the port leads the reference

Four dispositions in this port are ahead of the pinned release. Three are
readings of a rule: `CONCEPT_RANGE_CONFLICT`, `VERSION_RANGE_CONFLICT`, and the
universal-range reading of `rdfs:Resource` (all below). Each of those fixes
exists on the reference's `main` and in no release, so the pin cannot reach it,
and byte equality on a fixture exercising one is not available to be had.

The fourth is not a reading of a rule. It is an implementation defect both
sides shared and only this one has fixed: an inverse asserted as a nested
object carrying the referenced `@id` was compared as a string, so a
back-reference that really is present read as a mismatch. The rule is
unchanged — `hasPart`/`isPartOf` still have to agree — and only the
recognition of one of the two shapes a reference can take was wrong. It is
filed upstream as `ChelseaKR/ctdl-validate#32` and is present in `0.2.1`, the
current release, as well as in the pinned `0.1.0`, so this entry does not
collapse on a pin bump either.

All four withdraw or downgrade an ERROR the pinned release raises, which is the
direction that matters: a false ERROR tells a publisher to fix something
correct.

That divergence is recorded rather than hidden, in a second corpus whose only
job is to bound it:

```
parity/ahead/fixtures/    4 payloads this port answers differently, on purpose
parity/ahead/reference/   what the pinned release says about them, generated
```

There is deliberately no Java-side golden file. `AheadOfReferenceTest` asserts
the *shape* of the disagreement instead — same findings, same order, each one
either identical to the reference's, or one declared restatement, or one
declared withdrawal. The declared dispositions are:

| The pinned release says | This port says | Only where the snapshot says |
|---|---|---|
| `RANGE_VIOLATION` / ERROR | `CONCEPT_RANGE_CONFLICT` / INFO | the property declares `skos:Concept` and a `meta:targetScheme` |
| `RANGE_VIOLATION` / ERROR | `VERSION_RANGE_CONFLICT` / INFO | the property is a version property whose range is a strict subset of its own domain |
| `RANGE_VIOLATION` / ERROR | nothing at all | the property's declared range includes `rdfs:Resource` |
| `INVERSE_MISMATCH` / ERROR | nothing at all | the property declares an `owl:inverseOf`, which 16 properties in the snapshot do |

Every "only where" column is a predicate evaluated against the vendored
snapshot on each run, not a list of property names, so a disagreement can never
be justified by anything but the schema. A restatement must keep the same
entity, property, and value. The port may never *add* a finding the reference
does not have — which is also the bound on the fourth row: the nested-object
reading can withdraw a mismatch that was never real, and it cannot raise one
the reference does not already raise. Anything wider is a red build. When the
pin is bumped to a release carrying the fix, the reference stops disagreeing
and the suite fails, which is the instruction to fold the fixture back into
`parity/fixtures/` and delete the entry. See
[ADR 0004](docs/adr/0004-the-port-may-lead-the-pinned-reference.md).

## What is ported

The five checks, the finding model, the severity contract, and both
reporters. From
[the sibling's rule table](https://github.com/ChelseaKR/ctdl-validate#what-it-checks-v0):

| # | Check | Codes |
|---|---|---|
| 1 | CTID grammar on `ceterms:ctid`, on `@id`, and on the tail of every Registry resource/graph URI; `ctid` must match the `@id` tail | `CTID_BARE_UUID`, `CTID_MALFORMED`, `CTID_UPPERCASE`, `CTID_NOT_UUIDV4`, `REGISTRY_URI_MALFORMED`, `CTID_URI_MISMATCH` |
| 2 | Identifier kind: properties the CTDL context declares as `{"@type": "@id"}` with entity ranges must carry IRIs or blank node ids | `REF_BARE_UUID`, `REF_BARE_CTID`, `REF_NOT_IRI` |
| 3 | Reference resolution inside the payload; undefined blank nodes are errors, external IRIs are UNVERIFIABLE | `REF_UNRESOLVED_BNODE`, `REF_OUTSIDE_PAYLOAD` |
| 4 | Domain and range per `schema:domainIncludes` / `schema:rangeIncludes`, with `rdfs:subClassOf` closure, plus the wrong-framework `isPartOf` pattern | `DOMAIN_VIOLATION`, `RANGE_VIOLATION`, `ISPARTOF_FRAMEWORK_MISMATCH`, `UNKNOWN_CLASS`, `UNKNOWN_PROPERTY`, `RANGE_DOCS_CONFLICT`, `CONCEPT_RANGE_CONFLICT`, `VERSION_RANGE_CONFLICT` |
| 5 | Inverse consistency for pairs the schema declares with `owl:inverseOf` | `INVERSE_MISMATCH`, `INVERSE_ONE_DIRECTION` |

Severities mean what they mean in the sibling, because the parity test would
not tolerate anything else: **ERROR** gates the exit code, **WARNING** is a
cited signal where the rule is not absolute, **INFO** is worth a human look,
and **UNVERIFIABLE** is the tool declining to guess about something it cannot
see. Never a pass, never a fail.

## What is not ported, and why

- **The CLI surface.** The reference has flags, packaging, a published
  distribution, and a browser build. Here there is one thin entry point,
  enough to run the validator over a file and to emit the document the parity
  suite compares. Porting a CLI would demonstrate nothing the rules do not.
- **The `extract` subcommand.** It fetches a web page. This repository makes
  no network calls at all, which is a property worth keeping whole
  (`OfflineGuaranteeTest`), and extraction is a much larger surface with none
  of the parity payoff.
- **Nothing from the rule set itself.** All five checks and all 21 codes are
  here. No rule was dropped as unportable.

## Running it

Java 17 or newer, to build and to run. The CI gate compiles with `--release 17`
and runs the whole suite twice, on Temurin 17 and on Temurin 21, so the floor is
a runtime that gets exercised rather than only a compiler flag.

```
./gradlew verify        # compile, format, static analysis, tests, coverage
./gradlew installDist   # build/install/ctdl-validate-jvm/bin/ctdl-validate-jvm
```

```
$ ctdl-validate-jvm parity/fixtures/bug_class_250_bare_uuid_for_ctid.json
ERROR        REGISTRY_URI_MALFORMED  entity=https://credentialengineregistry.org/resources/b55f88e3-dfd4-430b-ab47-3e5f9986e1e4
    @id = https://credentialengineregistry.org/resources/b55f88e3-dfd4-430b-ab47-3e5f9986e1e4
    Registry URI whose CTID portion is a bare UUID: the ce- prefix is missing. Expected: ce- followed by a UUID v4 in 8-4-4-4-12 form, 39 characters, lower case hexadecimal, e.g. ce-e8a41a52-6ff6-48f0-9872-889c87b093b7.
    rule: About the CTID, section "CTID-Based URI Structure": ...
    source: https://credreg.net/ctdl/ctid (retrieved 2026-08-06)
```

Exit code 0 when there are no ERROR findings, 1 when there are, 2 when the
input cannot be read at all. `--format json` produces the machine-readable
report; `--format parity` produces exactly the document in
`parity/expected/`, so a divergence can be diffed by hand.

(`./gradlew run --args="<file>"` works too, but a payload with an ERROR
finding makes the validator exit 1, which Gradle then reports as a failed
build. `installDist` gives you a script that just exits 1.)

Nothing here is released. There is no tag, no artifact on Maven Central, and
`CITATION.cff` advertises no version, because there is none.

## Where the rules come from

The same place as the sibling's, byte for byte. The CTDL and CTDL-ASN schema
encodings and JSON-LD contexts are vendored unmodified in
`src/main/resources/vendor/`, with the source URLs, retrieval dates, and
SHA-256 hashes the reference recorded, unchanged, in
[`SOURCES.md`](src/main/resources/vendor/SOURCES.md). `VendorIntegrityTest`
enforces the hashes. All four were retrieved 2026-08-06 by the author of the
sibling.

Prose rules — the CTID grammar, blank node scope, framework publication — are
quoted in [`Rules.java`](src/main/java/io/github/chelseakr/ctdlvalidate/Rules.java)
with their page URLs, and the wording is the sibling's rather than a
paraphrase. That is deliberate: a citation is a quotation of Credential
Engine's published text, and rewording it would make the two implementations
disagree about what a rule *says* while agreeing about what it *does*. The
citation text is the author's own from the sibling repository; the quoted
spec sentences inside it are Credential Engine's, attributed at every use.

No rule is encoded from memory in either implementation.

### When the sources contradict each other

Two rules report a disagreement between published sources rather than a defect
in the document, and neither gates the exit code:

- **`RANGE_DOCS_CONFLICT`** — `ceasn:isChildOf` pointed at a
  `ceasn:CompetencyFramework`, which the schema's declared range excludes and
  Credential Engine's own usage note and examples require.
- **`CONCEPT_RANGE_CONFLICT`** — CTDL declares a reference to a term from one
  of its own concept schemes two incompatible ways. Across the vendored
  snapshot 46 properties declare `schema:rangeIncludes
  ceterms:CredentialAlignmentObject` and 45 declare `skos:Concept`, with
  nothing about the values telling the families apart. Three concept schemes
  (`AudienceLevel`, `CostType`, `ScheduleFrequency`) are named by properties
  in *both* families, and `ceterms:instructionalProgramType` declares both
  ranges at once. Every published document encodes both as a
  `CredentialAlignmentObject`, which the encoding gives no path to
  `skos:Concept` — so an ERROR here reports Credential Engine's own dominant
  encoding as a defect.

  A property declaring `skos:Concept` **and** a `meta:targetScheme` is a
  scheme-bound concept reference; an alignment object there is INFO. The
  covered set — 20 properties — is derived from the snapshot on every run, not
  written down, so refreshing the vendored schema refreshes the ruling. A
  `skos:Concept` range with *no* `meta:targetScheme` (`skos:broader`,
  `ceterms:classification`) is ordinary SKOS and stays an ERROR, as does any
  other out-of-range class on a covered property.

  Measured over the 120 published Registry documents of the sibling's
  2026-08-15 survey, validated offline from its cache: documents failing went
  **36/120 → 0** and ERROR findings **38 → 0**, with every other finding, at
  every severity, unchanged. `ConceptRangeConflictTest` fails if Credential
  Engine ever resolves the conflict this disposition rests on, so it cannot
  outlive its own premise.

- **`VERSION_RANGE_CONFLICT`** — CTDL's three version properties,
  `ceterms:latestVersion`, `ceterms:nextVersion` and `ceterms:previousVersion`,
  each declare a `schema:rangeIncludes` that is a strict subset of their own
  `schema:domainIncludes`. In the vendored snapshot each declares a domain of
  61 classes and a range of 55, and all three drop the identical six:
  `ceasn:Competency`, `ceasn:CompetencyFramework`, `ceasn:Rubric`,
  `ceterms:Collection`, `ceterms:Pathway`, `ceterms:TransferValueProfile`.

  For a dropped class the two declarations cannot both hold: the domain says an
  instance may have a version, the range says that version may not be an
  instance of the same class. Published wording does not settle which of the
  two is wrong, and this tool does not pick. What it rules out is the ERROR,
  because the document satisfies a published declaration and there is nothing
  to tell a publisher to change. Narrowed to a link between two entities of the
  same dropped class; any other out-of-range class on these properties is still
  a `RANGE_VIOLATION`.

### When a range constrains nothing

Not a conflict between sources, but a declaration that admits everything.

`ceterms:hasMember` and `owl:sameAs` declare `schema:rangeIncludes` as exactly
`rdfs:Resource`, and `ceterms:isSimilarTo` declares it among 83 terms. RDF
Schema 1.1 (W3C Recommendation, 25 February 2014) section 3.1 defines that
class: "All things described by RDF are called resources, and are instances of
the class rdfs:Resource. This is the class of everything. All other classes are
subclasses of this class." CTDL's own published comments agree —
`ceterms:hasMember` is "Resource in a Collection", and `ceterms:isSimilarTo` is
"generally applicable in describing the similarity between any two entities".

The vendored snapshot does not declare `rdfs:Resource` as a class, and none of
its 150 classes reaches it by `rdfs:subClassOf`. So a range check that matches a
target's declared classes against it rejects **every** entity where the
declaration accepts every entity. This port previously reported an ERROR on
every reference through those three properties. It now reports nothing, which
is what a range that excludes nothing means.

## What porting it actually took

Most of the work was not the rules. The checks are short and the schema
drives them. The work was in the places where "the same behaviour" turns out
to mean something specific about a language:

- **String ordering.** Findings, property keys, and class lists are sorted.
  Python compares strings by code point; Java's `String.compareTo` compares
  UTF-16 code units, and the two disagree for supplementary characters. CTDL
  payloads are usually ASCII, so the orderings almost always agree — which is
  exactly the kind of "almost" a parity test should not rest on.
  [`CodePointOrder`](src/main/java/io/github/chelseakr/ctdlvalidate/CodePointOrder.java)
  is used everywhere the reference sorts.
- **`repr()` is not `toString()`.** When `ceterms:ctid` carries something
  that is not a string, the reference reports Python's `repr` of it: `None`,
  `True`, `['a', 'b']`, `{'en': 'x'}`. A port that emitted `null`, `true`,
  and `["a","b"]` would produce the right finding with the wrong text.
  [`PythonRepr`](src/main/java/io/github/chelseakr/ctdlvalidate/PythonRepr.java)
  reimplements `repr` for the JSON value subset, including the float case,
  where Python renders the shortest round-tripping decimal and picks
  positional or scientific notation by a rule Java's `Double.toString` does
  not share.
- **JSON serialization.** The reports are compared as text, so the writer had
  to reproduce `json.dumps(..., indent=2, sort_keys=True, ensure_ascii=False)`
  exactly: empty containers on one line, `/` unescaped, non-ASCII literal.
- **Python truthiness**, in the one line of the parser that relies on it.

None of these are deep. All of them were found by the parity test rather than
by reading, which is the argument for having it.

## Limits, recorded

- **A false ERROR this port knowingly still raises.** `owl:sameAs` declares
  `schema:domainIncludes` as exactly `rdfs:Resource`, the same class of
  everything the range section above quotes, so no subject can fall outside its
  domain. Both implementations nonetheless report `DOMAIN_VIOLATION`/ERROR for
  any subject using it, and `parity/ahead/fixtures/universal_range.json` records
  that they still agree on it. It is not fixed here on purpose: the reference
  has not fixed it either, and this README is explicit that the specification
  work lives in the sibling and a rule change belongs there. Fixing it in the
  port alone would be this repository inventing a disposition rather than
  porting one. Reported upstream instead.

- **The parity corpus is 22 payloads, not a proof.** It covers 19 of the 21
  finding codes and every document shape the parser accepts, and it was
  extended beyond the sibling's own fixtures for that reason; the other 2 codes
  are the ones this port emits ahead of the pinned release, covered by
  `parity/ahead/` where byte equality is not available. It is still a corpus.
  Agreement on it is evidence, not a theorem, and no property-based or
  differential fuzzing has been run across the two implementations.
- **Malformed JSON is out of scope for parity.** The two implementations both
  exit 2, but the message comes from the language's JSON parser — Jackson's
  wording is not `json.JSONDecodeError`'s — and pinning that would be
  comparing Jackson to CPython rather than comparing the rules. Documents
  that parse as JSON but are not shapes the tool reads *are* in the corpus:
  their errors are the tool's own strings and must agree exactly.
- **One deliberate divergence, in ordering.** The reference sorts a Python
  `set` by a six-part key. Where two findings agree on all six and differ
  only in their rule, the reference's order depends on per-process string
  hash randomization; this port breaks that tie on the rule fields instead,
  so it is deterministic where the reference is not. No fixture reaches the
  case. It is recorded here rather than left as a surprise, and it is a
  divergence in favour of determinism, not against it.
- **Jackson is a runtime dependency.** The sibling has none; the Python
  standard library ships a JSON parser and Java's does not. Using the
  ecosystem's usual parser is the idiomatic choice and was the point of the
  exercise, but it is a difference from the sibling's stated posture and
  belongs in the open. See
  [ADR 0001](docs/adr/0001-jackson-and-a-hand-written-writer.md).
- **This port will drift if the sibling changes.** The pin in
  `parity/reference-requirements.txt` is a released version. A rule change
  upstream will not fail this build; it will simply mean the port is behind,
  until someone bumps the pin and reads the diff. That is the honest
  arrangement for a demonstration repository, and it is why the pin is
  excluded from Dependabot.

## Development

```
./gradlew verify   # the whole gate; the same target CI runs
make verify        # the same thing, through the portfolio's front door
```

| Gate | Task | What it checks |
| --- | --- | --- |
| Format | `spotlessCheck` | google-java-format |
| Static analysis | `spotbugsMain`, `spotbugsTest` | SpotBugs at max effort, low threshold |
| Tests | `test` | JUnit 5, including the parity suite |
| Coverage | `jacocoTestCoverageVerification` | branch coverage >= 85% |

Semgrep and a full-history TruffleHog sweep run in their own workflows. Every
GitHub Action is pinned to a full commit SHA.

Regenerating the parity expectations needs the reference implementation:

```
python3 -m pip install --require-hashes -r parity/reference-requirements.txt
python3 tools/generate_expectations.py          # write
python3 tools/generate_expectations.py --check  # report differences only
```

## Standards Conformance

Declared against the portfolio standards set. Every standard gets a row, an N/A
gives its reason, and a row that records a gap says so rather than being left
out.

| Standard | State |
|---|---|
| Responsible-Tech Framework | Applies: what this is and is not, the limits, the one deliberate divergence, and the AI-assistance disclosure are all above and are the point of the README rather than an appendix to it. Every finding carries its rule citation, source URL, and retrieval date. No dated audit record is committed; this row and the recorded limits are the declaration |
| Code Quality | Applies: `./gradlew verify` is the gate and CI runs that exact target. Compilation is `--release 17` with `-Xlint:all -Werror`, formatting is google-java-format through Spotless, static analysis is SpotBugs at max effort and low threshold, tests are JUnit 5 including the parity suite, and JaCoCo enforces a branch-coverage floor of 85%. `.pre-commit-config.yaml` runs the cheap parts locally and the full gate at pre-push, and `make verify` is a thin front door onto the same Gradle target |
| Security & Supply-Chain | Applies: every GitHub Action pinned to a full commit SHA, least-privilege workflow permissions, Semgrep and a full-history TruffleHog sweep in their own workflows, and Dependabot for Gradle and Actions with a seven-day cooldown before a newly published version is proposed. The reference implementation is installed with `--require-hashes`. One runtime dependency, and `OfflineGuaranteeTest` reads the compiled classes and fails if any of them so much as names a networking type. Reporting is in [`SECURITY.md`](SECURITY.md) |
| CI/CD | Applies: `ci.yml` runs the same target a contributor runs, and a second job installs the pinned reference implementation, regenerates `parity/expected/` from it, and fails on any diff, so the committed expectations cannot decay into a record of what this port did on the day they were written |
| Release & Versioning | Applies: SemVer and Keep-a-Changelog are declared and the CHANGELOG is kept. Nothing has been released. There is no tag, no artifact on Maven Central or any other registry, and no release workflow. A demonstration port that nobody installs does not need one yet, and inventing one before the first tag would be a workflow nobody has run |
| Observability | Applies (scoped): an offline single-run CLI and library, not a service. The observable output is the exit code and the findings, byte-identical for the same input. No tracing, metrics, or SLO surface exists, and none is claimed |
| Performance | N/A: a pure library and CLI with no hosted route and no shipped HTML, so there is no delivery surface to budget |
| Accessibility | N/A: an offline CLI and library with no human-facing HTML |
| Internationalization | N/A: a developer-facing JVM validator whose operator output is English only, matching the reference implementation it is compared against byte for byte. Translating a message here would make the two implementations disagree, so it is a change to the sibling first, if ever |
| AI Evaluation | N/A: a deterministic validator with no model component. There is no model anywhere in this repository and there will not be one, and the README says so in its first ten lines |
| Documentation | Applies: README, [`CONTRIBUTING.md`](CONTRIBUTING.md), [`SECURITY.md`](SECURITY.md), [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md), CHANGELOG, CITATION.cff, the ADR log under [`docs/adr/`](docs/adr/), the fixture provenance table in `parity/PROVENANCE.md`, and the vendored-source record in `src/main/resources/vendor/SOURCES.md`. The counts those documents publish about this repository are derived from it and gated by `PublishedFiguresTest`, on the pattern `VendorIntegrityTest` set, so a figure corrected in one file cannot be left stale in another |
| Quality & Metrics | Applies: the merge-blocking floors are byte-equality against the reference over every fixture, 85% branch coverage, zero SpotBugs findings, a clean formatter, and the offline guarantee. The parity corpus is 22 payloads and is recorded above as evidence, not a proof |
| AI Development Measurement | Applies: no tool-usage counter is collected and none gates a merge. The Disclosure section below records that the port was written with AI assistance and reviewed by a human; the gate is what a change clears regardless of how it was authored |
| Incident Response | Applies: no incident to date, and nothing is released for one to reach. Vulnerabilities go through the path in [`SECURITY.md`](SECURITY.md), and a postmortem will be committed under `docs/incidents/` when there is one to write |
| Data Governance | Applies: the validator reads a file you give it, keeps nothing, and makes no network call. Parity fixtures are synthetic by rule, with generated identifiers and invented names and nothing copied from a real organization or from the Credential Registry. The vendored CTDL and CTDL-ASN snapshots retain their origin and retrieval date in `src/main/resources/vendor/SOURCES.md` |

## Disclosure

This port was built quickly with AI assistance (Claude), then reviewed and
tested by a human. The rule research it rests on was done first and
elsewhere: the CTID grammar, the context coercions, and every domain, range,
and inverse declaration were pulled from credreg.net on 2026-08-06 and
vendored in the sibling repository before any check was written. Read the
findings' citations critically; if a cited source has changed since
retrieval, the vendored snapshot, not this tool's opinion, is what to update.

## License

Apache-2.0. The ported logic is a reimplementation of
[`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate), also
Apache-2.0, by the same author. CTDL and CTDL-ASN are Credential Engine's,
published under Creative Commons Attribution 4.0; the vendored files retain
their origin in
[`SOURCES.md`](src/main/resources/vendor/SOURCES.md). This project is not
affiliated with or endorsed by Credential Engine, and nothing here has been
published to the Credential Registry.
