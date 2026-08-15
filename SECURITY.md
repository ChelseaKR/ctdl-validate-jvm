# Security policy

ctdl-validate-jvm is a deterministic, offline validator: it reads a JSON
file, checks it against vendored schema snapshots on the classpath, and
prints findings. It makes no network calls at all, and it has one runtime
dependency (Jackson, for parsing). Security here is mostly integrity: the
tool must not misreport what a payload contains, and crafted input must not
escape the documented failure modes.

Nothing here is released. There is no tag, and no artifact on Maven Central
or any other registry.

## Supported versions

| Version | Supported |
| ------- | --------- |
| `main` | yes |
| anything else | there is nothing else |

## Reporting a vulnerability

Preferred: GitHub private vulnerability reporting (this repository's
*Security* tab, "Report a vulnerability"). Alternatively, email
ckellyreif@gmail.com with `ctdl-validate-jvm security` in the subject. Expect
an acknowledgement within 72 hours; this is a volunteer project, so please do
not disclose publicly until a fix is available.

Reproduce issues with synthetic payloads like the fixtures under
`parity/fixtures/`; never attach credentials or non-public organizational
data.

If the issue is in the rules themselves rather than in this port, it belongs
in [`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate),
which is where the rule set lives. A finding that affects both is best
reported there; say so and it will be carried across.

## What we consider a vulnerability

In addition to the usual (code execution from input data, secret exposure,
supply-chain compromise), the following are first-class security bugs here:

- **A false clean report.** Any path by which this tool reports no findings
  on a payload that violates a rule it claims to check. This is an integrity
  bug, not a cosmetic one: the tool exists to gate publication.
- **A silent divergence from the reference implementation.** Any way for this
  port to disagree with `ChelseaKR/ctdl-validate` without `ParityTest`
  failing — including anything that makes the parity comparison weaker than
  byte equality, or that lets `parity/expected/` stop being the reference's
  actual output.
- **Crafted JSON** that crashes outside the documented exit-code contract
  (0 = no ERROR findings, 1 = ERROR findings, 2 = unreadable input), hangs,
  recurses without bound, or allocates without bound.
- **Any tampering with the vendored schema snapshots** that
  `VendorIntegrityTest` and the recorded SHA-256 hashes in
  `src/main/resources/vendor/SOURCES.md` would not catch.
- **Any network access at all.** There is no flag to enable one, and there is
  no code path that should reach one; `OfflineGuaranteeTest` fails if a
  compiled class so much as names a networking type. A way around that is a
  vulnerability, not a feature request.

## Our commitments

- Every GitHub Action is pinned to a full commit SHA. Semgrep and a
  full-history TruffleHog sweep run on every change, and Dependabot watches
  the Gradle and Actions ecosystems.
- `./gradlew verify` is the same gate locally and in CI: compile, formatter,
  SpotBugs, tests including parity, and a coverage floor.
- Integrity regressions — false clean reports, and undetected divergence from
  the reference — are fixed with the highest priority.
- We credit reporters who want credit, and respect those who want anonymity.
