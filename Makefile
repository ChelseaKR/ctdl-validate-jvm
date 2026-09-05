# A thin front door onto the Gradle build, so `make verify` means the same thing
# here as it does in the rest of the portfolio. Gradle remains the build system
# and `build.gradle.kts` remains the definition of the gate; nothing below adds a
# step, reorders one, or can pass when `./gradlew verify` would fail.

GRADLE ?= ./gradlew

.PHONY: verify format lint test parity fuzz clean

# Compile with -Werror, spotlessCheck, spotbugsMain, spotbugsTest, test
# (including ParityTest), and the branch-coverage gate. The same target CI runs.
verify:
	$(GRADLE) verify

format:
	$(GRADLE) spotlessApply

lint:
	$(GRADLE) spotlessCheck spotbugsMain spotbugsTest

test:
	$(GRADLE) test

# Check the committed parity expectations against the pinned reference implementation.
# --check regenerates them in memory and reports every difference without writing
# anything, which is the whole point: the old form wrote the regenerated files into
# your working tree and then asked git what had changed, so a failing run left you
# holding modified files, and `git diff` could not see the one case that matters most.
# git diff does not report an untracked file, so a fixture whose expectation was never
# committed regenerated into a new file and the check passed green on it. --check
# compares against the committed bytes directly and reports a missing expectation as a
# difference. To write the files, run the script without --check.
#
# Needs Python 3.12 and the pinned reference release:
#   python3 -m pip install --require-hashes -r parity/reference-requirements.txt
parity:
	python3 tools/generate_expectations.py --check

# Differential fuzzing against the pinned reference. Deliberately not part of
# `verify`: it is nondeterministic in what it reaches, it needs both toolchains
# and a built CLI, and a gate that sometimes finds nothing teaches people to
# ignore it. The merge gate is the deterministic corpus. Needs the same pinned
# reference `make parity` does, plus `./gradlew installDist`.
#
# THIS TARGET EXITS NON-ZERO, and that is the expected outcome rather than a
# failure. The harness returns 1 whenever anything diverged and has no notion of
# which disagreements are declared; on the pinned pair the RANGE_VIOLATION
# withdrawals in parity/ahead/ are reached constantly, so a clean tree still
# ends here with an error. Measured 2026-09-05, seed 1, 1000 payloads: 34
# diverging payloads, every one of them a declared disposition.
#
# The exit code is deliberately not swallowed. Reporting 0 on a run that found
# something would be a worse lie than a non-zero exit that needs this comment.
# The artifact is the shape summary, and the method is a run before a change to
# the checks and a run after it at the same seed -- CONTRIBUTING.md has the
# procedure, docs/adr/0006-the-differential-fuzzer-runs-beside-a-change-not-on-a-clock.md
# has the reason there is no nightly job doing it instead.
fuzz:
	$(GRADLE) installDist
	python3 tools/differential_fuzz.py --self-check
	python3 tools/differential_fuzz.py --count 1000 --seed 1

clean:
	$(GRADLE) clean
