# 6. The differential fuzzer runs beside a change, not on a clock

## Status

Accepted

## Context

`tools/differential_fuzz.py` (#33) generates CTDL-shaped payloads from the
vendored snapshot's own terms, runs this port and the pinned reference over each
one, and compares the whole parity document byte for byte. It exists because
`parity/fixtures/` reaches every finding code and that is not the same as
reaching every document *shape*, and because both defects this port has fixed
independently of the reference lived in exactly that gap.

The harness landed. Where it runs did not. #7 proposed "nightly or on demand",
`CONTRIBUTING.md` asks for a run before a change to the checks and a run after
one, and `docs/ROADMAP.md` recorded the remainder of plan item 1 as a decision
rather than work: a nightly job spends Actions minutes on a check that will
usually find nothing, against an alternative that costs nothing.

That framing understated the case, and running the thing is what showed it.

**Measured 2026-09-05, seed 1, 1,000 payloads, against the pinned reference
0.1.0 and this port at `3927140`: 34 diverging payloads.** Every one was a
`RANGE_VIOLATION`/ERROR the pinned release raises and this port withdraws or
restates — the disposition table `parity/ahead/` already declares, and nothing
else. No `THE PORT ADDED` line, which is the only line in that report that is
never allowed. The harness's own `--self-check` passed first: 12 of 12 payloads
caught a corrupted exit code, 0 diverged without the corruption, so this is a
comparison that was watched noticing rather than one trusted to.

**The harness exits 1 whenever anything diverges, and on this pinned pair
something always diverges.** `main()` ends `return 1 if minimised else 0`; it
has no notion of which disagreements are declared. That is correct for a triage
tool — `CONTRIBUTING.md` already says to read its output as triage and not as a
verdict — but it decides this question. The nightly #7 sketched was "a
`schedule:` + `workflow_dispatch:` workflow that installs the pinned reference,
runs `installDist`, and fails on a non-zero exit". That workflow would be red
every single night, deterministically, on findings this repository has already
ruled on and written down.

Making it green would mean teaching the harness which disagreements are
declared: a second parity corpus, evaluated at fuzz time, kept in step with
`parity/ahead/`. `docs/ROADMAP.md` lists "a second parity corpus that is not
byte-equal" under Decided against, and ADR 0004 and ADR 0005 are the reasons.
So the scheduled job is not merely low-value. As proposed it is not
implementable without work this repository has already refused.

Three further facts, none of them decisive alone:

- **Nothing changes between nights.** The reference is pinned by version and by
  artifact hash, and the port changes only when someone changes it. A nightly
  run re-measures a pair that did not move.
- **The only thing a schedule buys is seed breadth, and seed breadth is an
  argument.** `--count` and `--seed` are flags on the run you were already
  doing. Ten thousand payloads beside the change that warranted them are worth
  more than ten thousand spread over ten nights, because a divergence found the
  day the code was written is a divergence found while someone is still holding
  it.
- **It is not cheap.** Each payload is a fresh JVM. The 1,000-payload sweep
  above took 8 minutes 41 seconds of wall time and 19 minutes 43 seconds of CPU
  on a ten-core laptop. A nightly at that size is tens of minutes of a shared
  Actions budget, on a demonstration port with no release and no dependents.

The repository has already written the principle down, in `CONTRIBUTING.md` and
in the harness's own docstring: a gate that sometimes finds nothing is a gate
that teaches people to ignore it. A gate that is always red teaches the same
lesson faster.

## Decision

**The differential fuzzer runs on demand, beside a change to the checks. There
is no scheduled workflow, and no CI job runs it.**

`CONTRIBUTING.md` carries the procedure, and this ADR is the reason it is a
procedure rather than a cron entry. The rule is a run before the change and a
run after it, at the same seed and count, and the artifact is the difference
between the two shape summaries — not the exit code, which is 1 either way.

Two middles were considered and rejected:

- **A `workflow_dispatch`-only job, no `schedule:`.** It would make the harness
  reachable without both toolchains installed, and it would cost nothing until
  someone pressed it. It was rejected because it produces a report against
  nothing: the whole method is comparing a run before a change to a run after
  it, and a button in the Actions tab has only the after. It would also arrive
  permanently non-zero, for the reason above, and a red run whose redness means
  nothing is the failure mode this decision is about.
- **A job on pull requests that touch the checks.** Same objection, plus it
  would put a 9-minute two-toolchain step in front of every such PR to
  re-derive a comparison the author has already made and can paste.

## Consequences

- The before/after rule is only as good as the people following it, and this
  ADR does not pretend otherwise. What it does is make the rule executable: the
  exit-code trap is written down, "before/after" is defined concretely enough
  to follow, and `.github/PULL_REQUEST_TEMPLATE.md` asks for the seed, the
  count and the shape-summary difference, so a reviewer has something to look
  at rather than a claim to believe.
- Seed breadth accumulates by choice rather than by clock. Nobody is stopped
  from running 50,000 payloads across ten seeds; the point is that they do it
  when it can tell them something.
- **This decision is cheap to reverse, and one measurement would reverse it.**
  If the harness ever grows a way to separate declared disagreements from
  undeclared ones — so that a green run means something and a red one means a
  defect — the nightly becomes worth its minutes and is a `schedule:` key in a
  file that would then have a reason to exist. That capability, not the Actions
  budget, is what this ADR is really waiting on.
- `make fuzz` exits non-zero today, and that is expected rather than broken.
  The Makefile and `CONTRIBUTING.md` now say so. The exit code is deliberately
  not swallowed: a wrapper that reported 0 on a run that found something would
  be a worse lie than a non-zero exit that needs a sentence of explanation.
