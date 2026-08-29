# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once there
is a version to speak of.

Nothing has been released. There is no tag and no published artifact.

## [Unreleased]

### Fixed

- The port reported `RANGE_VIOLATION` / ERROR on every reference through
  `ceterms:hasMember`, `owl:sameAs`, and `ceterms:isSimilarTo`. All three
  declare `rdfs:Resource` in `schema:rangeIncludes`, and RDF Schema 1.1 (W3C
  Recommendation, 25 February 2014) section 3.1 defines that as "the class of
  everything", with "all other classes are subclasses of this class". The
  vendored snapshot declares no such class and none of its 139 classes reaches
  it by `rdfs:subClassOf`, so matching a target against it rejected every
  entity where the declaration accepts every entity. A range that excludes
  nothing now produces no finding. CTDL's own published comments say the same:
  `ceterms:hasMember` is "Resource in a Collection", and `ceterms:isSimilarTo`
  is "generally applicable in describing the similarity between any two
  entities".

- `ceterms:latestVersion`, `ceterms:nextVersion`, and
  `ceterms:previousVersion` each declare a `schema:rangeIncludes` that is a
  strict subset of their own `schema:domainIncludes` — a domain of 61 classes
  and a range of 55 in the vendored snapshot, dropping the identical six from
  all three. A resource of a dropped class versioned by another resource of the
  same class is now `VERSION_RANGE_CONFLICT` / INFO rather than
  `RANGE_VIOLATION` / ERROR: the domain says such a resource may have a
  version, the range says that version may not be one of its own kind, and the
  document satisfies one of the two. Which declaration is wrong is not settled
  by any published wording, so the tool reports the disagreement instead of
  picking a side. Any other out-of-range class on these properties stays an
  ERROR.

- The parity gate could not see a rule the port did not have.
  `ParityTest.corpusCoversEveryCode` built its expected set from
  `FindingCodes.ALL`, a list maintained inside this port, so a rule the
  reference has and the port never learned about was absent from the list, from
  the port's output, and from `parity/expected/` alike, and the coverage test
  passed. Both sides are now derived: the port's codes are parsed out of
  `src/main/java`, and the reference's are AST-parsed from the pinned release's
  own source by `tools/generate_expectations.py` into
  `parity/reference-codes.json`, which the CI parity job regenerates and diffs
  alongside the expectations. Every difference between the two sets must be
  declared with a reason, in either direction, or the build fails.

- Three gates passed having scanned nothing.
  `OfflineGuaranteeTest.noNetworkingTypesAreReferenced` asserted only that its
  class directory existed, `OfflineGuaranteeTest.noModelCalls` walked the
  source tree with no assertion it read anything, and
  `DeterminismTest.repeatedRunsAgree` put every assertion inside a loop over a
  directory listing. Each now counts what it scanned and fails on an empty
  sweep.

### Changed

- `AheadOfReferenceTest` holds the port to a table of declared dispositions
  rather than a single hard-coded substitution, and a disposition may now
  withdraw a finding as well as restate one. Each row's applicability is a
  predicate evaluated against the vendored snapshot, and a declared disposition
  no fixture exercises fails the build. See ADR 0004.

- README: the corpus is 22 payloads, not 21, and there are 21 finding codes,
  not 19 or 20. The counts were checked against the directory listing and the
  parsed source rather than against the prose.

- The validator failed 36 of the 120 published Registry documents in the
  sibling's 2026-08-15 survey, and should not have. CTDL declares a reference
  to a term from one of its own concept schemes two incompatible ways: across
  the vendored snapshot 46 properties declare `schema:rangeIncludes
  ceterms:CredentialAlignmentObject` and 45 declare `skos:Concept`, with
  nothing about the values telling the families apart. Three concept schemes
  are named by properties in both families, and a fourth by one property
  declaring both ranges at once. Every published document encodes both as a
  `CredentialAlignmentObject`, so `RANGE_VIOLATION` was reporting Credential
  Engine's own dominant encoding as a defect.

  A property declaring `skos:Concept` in its range **and** a
  `meta:targetScheme` is now a scheme-bound concept reference, and an
  alignment object there is `CONCEPT_RANGE_CONFLICT` (INFO), citing both
  declarations and, where the snapshot has one, a sibling property over the
  same scheme with the other range. Twenty properties are covered, derived
  from the snapshot rather than listed by hand. A `skos:Concept` range with no
  `meta:targetScheme` — `skos:broader`, `ceterms:classification` — is ordinary
  SKOS and stays an ERROR, as does any other out-of-range class on a covered
  property.

  Measured on the survey's 120 documents, re-validated offline from its cache:
  documents failing went 36/120 → 0 and ERROR findings 38 → 0. Every other
  finding, at every severity, is unchanged.

  This is a port of the same ruling in the reference implementation
  ([`ChelseaKR/ctdl-validate#25`](https://github.com/ChelseaKR/ctdl-validate/pull/25)).

- `SchemaLoader` did not read `meta:targetScheme` at all, which is why the
  above could not be distinguished from an ordinary range violation. It is now
  indexed alongside domain, range, and inverse.

### Added

- `parity/ahead/`, a second corpus for the narrow set of behaviours where this
  port leads the pinned reference release, with `AheadOfReferenceTest` holding
  the divergence to one declared substitution and failing when the pin catches
  up. `parity/fixtures/` remains byte equality with no exemptions. See
  [ADR 0004](docs/adr/0004-the-port-may-lead-the-pinned-reference.md).
- `concept_range_guards.json`, a parity fixture putting concept-valued
  properties into the byte-equality corpus for the first time and pinning the
  edges of the new disposition — a real `skos:Concept` target, a wrong class,
  and a `skos:Concept` range with no `meta:targetScheme` — all three of which
  both implementations still agree on.

- A Java port of the rule core of
  [`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate): all
  five checks and all 19 finding codes, the finding model, the severity
  contract, and both reporters.
- A cross-language parity suite. 21 fixtures are validated by both
  implementations and compared byte for byte — exit code, every finding field
  including its rule citation and retrieval date, and the order — with
  disagreement a build failure. Expectations are generated from the pinned
  reference release, never hand-written, and CI regenerates them and fails on
  any drift.
- Ten fixtures written for this repository, covering the eight finding codes
  the reference's own fixtures did not exercise, the document shapes the
  parser accepts, and the three document-level error cases.
- `PythonRepr`, reimplementing CPython's `repr()` over the JSON value subset,
  because the reference reports non-string `ceterms:ctid` values through it.
- `CanonicalJson`, reproducing `json.dumps(..., indent=2, sort_keys=True,
  ensure_ascii=False)` so reports can be compared as text.
- `CodePointOrder`, because Python sorts strings by code point and Java sorts
  by UTF-16 code unit.
- `OfflineGuaranteeTest`, which reads the compiled classes and fails if any of
  them names a networking or process-launching type.
- `VendorIntegrityTest`, enforcing the reference's recorded SHA-256 hashes on
  the four vendored CTDL and CTDL-ASN files.
- `BreakTheGateTest`, corrupting a clean payload one way at a time and
  asserting each corruption is caught.
- CI: build, formatter, static analysis (SpotBugs and Semgrep), coverage
  floor, full-history secret scan, and a job that reconciles the parity
  expectations against the reference implementation. Every action pinned to a
  full commit SHA.
