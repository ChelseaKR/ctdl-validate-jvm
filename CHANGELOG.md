# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once there
is a version to speak of.

Nothing has been released. There is no tag and no published artifact.

## [Unreleased]

### Fixed

- The parity drift check could pass on an incomplete corpus, and healed drift in
  the working tree while it looked. It regenerated every expectation over the
  checkout and then asked `git diff --exit-code` what had moved. `git diff` does
  not report an untracked file, so a fixture committed without its expectation
  regenerated into a brand new file and the step went green on a
  `parity/expected/` that was missing an entry. Measured rather than reasoned
  about: with the fixture committed and the expectation absent, the step exited
  0. Writing into the checkout was the second half of the same problem, and the
  more dangerous half, because the run that detects drift was also the run that
  repairs it locally, leaving the committed bytes stale and every later local run
  green. `--check`, which the script has always had and nothing used, compares
  against the committed bytes in memory: it reports a missing expectation as a
  difference and writes nothing. Both `make parity` and the CI job now use it.

- The 85% branch-coverage floor was published in `README.md` twice and
  `CONTRIBUTING.md` once, copied by hand from `build.gradle.kts`. Raising the
  floor edits one line of a task `:test` does not depend on, so the change that
  invalidates all three sentences was the change least likely to be noticed.
  `parity/PROVENANCE.md`'s "Eleven are vendored" and "Eleven were written for
  this repository" were the same shape: counts of a list in that very file and of
  a directory beside it, with nothing deriving either. All three figures are
  correct today. Nothing was keeping them that way.

- Five published claims that had gone stale, and the reason they could.
  `CITATION.cff` and the GitHub repository description said both implementations
  run over "one shared fixture corpus"; there are two, and the second exists
  precisely because they disagree there. The README said the 22-payload corpus
  covers every finding code, which is true of the union with `parity/ahead/` and
  not of the corpus the same paragraph describes: 19 of the 21 codes appear in
  `parity/expected/`. It said two of the three ahead dispositions withdraw or
  downgrade an ERROR, where its own table three lines later shows
  `RANGE_VIOLATION`/ERROR in all three rows. It sized the vendored snapshot at
  139 classes, which counts `ctdl/schema.json` and not the 11 in
  `ctdlasn/schema.json` that `SchemaLoader` loads beside it; the ruling that
  nothing reaches `rdfs:Resource` holds at 150 and is unchanged. And it put
  `ceterms:isSimilarTo`'s declared range at 80 terms, which is how many of them
  are declared as classes; the range is 83 distinct terms. `CONTRIBUTING.md`
  still described the ahead corpus as one declared substitution, and
  `parity/PROVENANCE.md` left "the 19 finding codes" ambiguous between the two
  rule sets. Every figure was established by reading the artifact it describes.

### Added

- The parity CI job now proves on every run that it can fail. It perturbs one
  committed expectation the way a real divergence would, in a copy of the tree so
  the checkout is never touched, and requires `--check` to notice. It also fails
  if the perturbation changed nothing and if the run left any mark on the working
  tree, so it cannot report a proof it did not obtain.

- Three more figures are derived rather than published: the branch-coverage floor,
  read out of `build.gradle.kts` and held to the three sentences that state it,
  and the two fixture counts in `parity/PROVENANCE.md`, read out of the vendored
  list and the fixture directory.

- `PublishedFiguresTest`, so the above cannot recur silently.
  `VendorIntegrityTest` was the only test that read a Markdown file and asserted
  against it, which is why an earlier pull request corrected four counts in the
  README and left the same counts wrong in three other files. Each figure is now
  derived on every run from the directory listing, the parsed source, the
  generated expectations, the
  vendored snapshot, or `AheadOfReferenceTest`'s disposition table, and every
  live document is held to it: README, `CITATION.cff`, `CONTRIBUTING.md`,
  `parity/PROVENANCE.md`, and the one Javadoc paragraph that states the snapshot
  size. Rewording a sentence past the pattern that reads it also fails, because
  a figure the gate can no longer find is a figure nothing is checking. Every
  fixture in both corpora must be named in `PROVENANCE.md`. `CHANGELOG.md` and
  `docs/plans/` are deliberately out of scope: they are dated records, and
  holding them to today's count would be rewriting the record.
- The parity corpora, `SOURCES.md`, and the published documents are declared as
  inputs to the `test` task. They are on no classpath, so Gradle could not see
  them, and editing a fixture or a README count alone left `:test` UP-TO-DATE
  and the gate green on a change it had never looked at.

### Documented

- What bumping the parity pin to the reference's current release costs, measured
  rather than estimated. `parity/ahead/` was built to expire when the reference
  catches up, so the bump was carried out against `ctdl-validate==0.2.1` to let
  it. It does not expire: 0.2.1 carries none of the three dispositions, so
  regenerating `parity/ahead/reference/` against it changed nothing and every
  entry is still genuinely ahead. What the bump does instead is fail
  `ParityTest` on three amended messages and `FindingCodeCensusTest` on
  `REF_RESOLVED_SUPPLIED`, a rule the reference has and this port does not.
  Both come from one upstream feature, `--resolve`, whose amended
  `REF_OUTSIDE_PAYLOAD` message names the flag, so byte parity is unreachable
  until the flag is ported. The pin stays at 0.1.0 and `parity/PROVENANCE.md`
  now says so and why.

- The two `parity/ahead/` fixtures that had no row in `parity/PROVENANCE.md`,
  `version_range_conflict.json` and `universal_range.json`. The table described
  one of the three divergences the corpus bounds.

### Fixed

- The port reported `RANGE_VIOLATION` / ERROR on every reference through
  `ceterms:hasMember`, `owl:sameAs`, and `ceterms:isSimilarTo`. All three
  declare `rdfs:Resource` in `schema:rangeIncludes`, and RDF Schema 1.1 (W3C
  Recommendation, 25 February 2014) section 3.1 defines that as "the class of
  everything", with "all other classes are subclasses of this class". The
  vendored snapshot declares no such class and none of its 150 classes reaches
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
