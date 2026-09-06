package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The full-history secret scan must stay capable of failing on a leak that has already been
 * revoked.
 *
 * <p>TruffleHog sorts a finding into {@code verified} (it authenticated the credential against the
 * live service), {@code unknown} (verification errored) and {@code unverified} (it asked, and the
 * service said no). A credential that leaked and was later <em>revoked</em> is the normal end state
 * of a real incident, and it is exactly what a scheduled full-history sweep exists to catch. It
 * answers "no", so it lands in {@code unverified}. A scan configured {@code --only-verified},
 * {@code --results=verified} or {@code --results=verified,unknown} therefore cannot fail on it, and
 * goes green either way. Measured 2026-09-06 on a throwaway clone with a real-shaped AWS key
 * planted in one commit and deleted in the next: the first three exited 0 reporting nothing; adding
 * {@code unverified} exited 183 with {@code unverified_secrets: 1}.
 *
 * <p>Three further properties are asserted because each has silently un-armed a scan in this
 * portfolio without turning any build red:
 *
 * <ul>
 *   <li>The action ref and the {@code version:} input name the same release. The input is what
 *       selects the scanning binary ({@code ghcr.io/trufflesecurity/trufflehog:${VERSION}}); the
 *       {@code uses:} SHA pins only the wrapper, and omitting the input entirely means {@code
 *       latest}. Dependabot edits {@code uses:} and never a {@code with:} input, so the two drift.
 *   <li>{@code fetch-depth: 0} survives, or the checkout is one commit deep and a "full-history"
 *       sweep quietly becomes a one-commit scan that still reports success.
 *   <li>{@code path: ./} survives, or the action exits on its own "BASE and HEAD commits are the
 *       same" guard having scanned nothing.
 * </ul>
 *
 * <p>The pin comment is a YAML comment and so is invisible to a YAML parser: this reads the
 * workflow as text on purpose.
 */
class SecretScanTiersTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));

  private static final Path WORKFLOW = Path.of(".github", "workflows", "trufflehog.yml");

  /** The tier a revoked credential lands in. Its absence is the defect. */
  private static final String REQUIRED_RESULT_TIER = "unverified";

  private static final Pattern EXTRA_ARGS =
      Pattern.compile("^\\s*extra_args:\\s*(.+?)\\s*$", Pattern.MULTILINE);
  private static final Pattern RESULTS = Pattern.compile("--results=([\\w,]+)");
  private static final Pattern PINNED_REF =
      Pattern.compile("trufflesecurity/trufflehog@[0-9a-f]{40}\\s*#\\s*v(\\d+(?:\\.\\d+)*)");
  private static final Pattern VERSION_INPUT =
      Pattern.compile("^\\s*version:\\s*\"?(\\d+(?:\\.\\d+)*)\"?\\s*$", Pattern.MULTILINE);
  private static final Pattern FETCH_DEPTH_ZERO =
      Pattern.compile("^\\s*fetch-depth:\\s*0\\s*(?:#.*)?$", Pattern.MULTILINE);
  private static final Pattern SCAN_PATH =
      Pattern.compile("^\\s*path:\\s*\\./\\s*(?:#.*)?$", Pattern.MULTILINE);

  private static String workflow() {
    Path file = ROOT.resolve(WORKFLOW);
    assertTrue(
        Files.isRegularFile(file),
        file
            + " is missing. If the full-history secret scan was deliberately removed or replaced,"
            + " update this test with the replacement rather than deleting it: an absent scan must"
            + " be a decision, not a silence.");
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> lanes() {
    List<String> lanes = new ArrayList<>();
    Matcher m = EXTRA_ARGS.matcher(workflow());
    while (m.find()) {
      lanes.add(m.group(1));
    }
    assertFalse(
        lanes.isEmpty(),
        "no `extra_args:` found in "
            + WORKFLOW
            + "; this guard can no longer see which result tiers the scan reports on");
    return lanes;
  }

  private static List<String> matches(Pattern pattern, String text) {
    List<String> found = new ArrayList<>();
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      found.add(m.group(1));
    }
    return found;
  }

  @Test
  @DisplayName("no lane of the scan uses --only-verified")
  void noLaneUsesOnlyVerified() {
    for (String args : lanes()) {
      assertFalse(
          args.contains("--only-verified"),
          "`--only-verified` cannot fail on a credential the provider has already revoked, which"
              + " is the normal end state of a real leak and the case this scan exists for."
              + " Offending args: "
              + args);
      assertTrue(
          RESULTS.matcher(args).find(),
          "expected an explicit `--results=` tier list, got: " + args);
    }
  }

  @Test
  @DisplayName("some lane of the scan reports the unverified tier")
  void someLaneReportsTheUnverifiedTier() {
    boolean armed = false;
    List<String> seen = new ArrayList<>();
    for (String args : lanes()) {
      Matcher m = RESULTS.matcher(args);
      if (m.find()) {
        String tiers = m.group(1);
        seen.add(tiers);
        for (String tier : tiers.split(",")) {
          if (REQUIRED_RESULT_TIER.equals(tier)) {
            armed = true;
          }
        }
      }
    }
    assertTrue(
        armed,
        "no lane of this scan reports `"
            + REQUIRED_RESULT_TIER
            + "` results (found "
            + seen
            + "), so nothing here can fail on a credential that leaked and was then revoked."
            + " Measured: verified and verified,unknown both exit 0 on a planted-then-deleted AWS"
            + " key; adding unverified exits 183.");
  }

  @Test
  @DisplayName("the action ref and the version input name the same release")
  void actionRefAndVersionInputAgree() {
    String text = workflow();
    List<String> pinned = matches(PINNED_REF, text);
    List<String> selected = matches(VERSION_INPUT, text);

    assertFalse(
        pinned.isEmpty(),
        "could not read a SHA-pinned trufflesecurity/trufflehog ref and its `# vX.Y.Z` comment in "
            + WORKFLOW);
    assertFalse(
        selected.isEmpty(),
        "no `version:` input on the trufflehog step in "
            + WORKFLOW
            + ". Without it the action defaults to \"latest\", so the SHA pin above it pins only"
            + " the wrapper and not the binary that actually scans.");
    assertEquals(
        pinned.size(),
        selected.size(),
        "every pinned trufflehog step needs its own `version:` input; found "
            + pinned.size()
            + " ref(s) and "
            + selected.size()
            + " input(s)");
    for (int i = 0; i < pinned.size(); i++) {
      assertEquals(
          pinned.get(i),
          selected.get(i),
          "the action is pinned to v"
              + pinned.get(i)
              + " but `version: "
              + selected.get(i)
              + "` is what downloads the scanner, so the scan runs "
              + selected.get(i)
              + " and the bump to v"
              + pinned.get(i)
              + " changed nothing");
    }
  }

  @Test
  @DisplayName("the checkout keeps the full history and the scan walks the whole repository")
  void checkoutKeepsFullHistory() {
    String text = workflow();
    assertTrue(text.contains("actions/checkout@"), "the scan no longer checks the repository out");
    assertTrue(
        FETCH_DEPTH_ZERO.matcher(text).find(),
        "`fetch-depth: 0` is missing from the checkout. actions/checkout then fetches a single"
            + " commit and this full-history sweep silently becomes a one-commit scan that still"
            + " reports success.");
    assertTrue(
        SCAN_PATH.matcher(text).find(),
        "`path: ./` is missing; with path, base and head all unset the action exits on its own"
            + " \"BASE and HEAD commits are the same\" guard having scanned nothing.");
  }
}
