## What this changes

<!-- One or two sentences. If this is a rule change, stop: rules belong in
     ChelseaKR/ctdl-validate, and a rule change made here first will fail the
     parity suite. -->

## Parity

- [ ] `./gradlew verify` is green, including `ParityTest`
- [ ] If `parity/expected/` changed, it was regenerated with
      `python3 tools/generate_expectations.py` against the pinned reference,
      not edited by hand
- [ ] If a fixture was added, `parity/PROVENANCE.md` says what it is for
- [ ] If this changes the checks, the differential fuzzer was run either side of
      the change at the same seed and count, and the seed, the count and what
      moved in the shape summary are stated below. "Nothing moved" is an
      answer; saying nothing is not. See
      [ADR 0006](../docs/adr/0006-the-differential-fuzzer-runs-beside-a-change-not-on-a-clock.md)
      — the harness exits non-zero on the pinned pair either way, so the diff
      between the two summaries is the artifact, not the exit code.

## Checks

- [ ] Every new finding carries a rule citation, a source URL, and a
      retrieval date
- [ ] No new network access, and no model anywhere
- [ ] Output is still deterministic: no timestamps, no unordered iteration
      reaching the report
- [ ] A structural decision, if there was one, is recorded in `docs/adr/`
