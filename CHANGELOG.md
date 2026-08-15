# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html) once there
is a version to speak of.

Nothing has been released. There is no tag and no published artifact.

## [Unreleased]

### Added

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
