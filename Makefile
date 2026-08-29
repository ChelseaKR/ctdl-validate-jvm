# A thin front door onto the Gradle build, so `make verify` means the same thing
# here as it does in the rest of the portfolio. Gradle remains the build system
# and `build.gradle.kts` remains the definition of the gate; nothing below adds a
# step, reorders one, or can pass when `./gradlew verify` would fail.

GRADLE ?= ./gradlew

.PHONY: verify format lint test parity clean

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

clean:
	$(GRADLE) clean
