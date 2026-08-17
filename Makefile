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

# Regenerate the parity expectations from the pinned reference implementation and
# fail if they moved. Needs Python 3.12 and the pinned reference release:
#   python3 -m pip install --require-hashes -r parity/reference-requirements.txt
parity:
	python3 tools/generate_expectations.py
	git --no-pager diff --exit-code -- parity/expected

clean:
	$(GRADLE) clean
