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
 *   <li>{@code base: ""} survives alongside a <em>non-empty</em> {@code head: HEAD}. These two
 *       inputs, and nothing else, are what make this a history scan. Absent them the action picks
 *       its range from the triggering event: {@code push} scans {@code --since-commit
 *       <event.before> --branch <event.after>}, {@code pull_request} scans {@code --since-commit
 *       <pr.base.sha> --branch <pr.head.sha>}, and only {@code schedule} and {@code
 *       workflow_dispatch} scan {@code --since-commit '' --branch ''}. So until 2026-09-13 the
 *       weekly cron did read the whole history while every push and every pull_request run -- the
 *       runs that gate a merge -- read the event's diff, all of them reporting under the job name
 *       "full-history secret scan (all result tiers)". Measured here on one day with the same
 *       pinned scanner 3.97.1: the scheduled run read {@code chunks: 855, bytes: 2475409}; the
 *       pull_request run read {@code chunks: 2, bytes: 1325}; the push to main read {@code chunks:
 *       5, bytes: 5024}. {@code head} must be non-empty, because the action's guard is {@code if [
 *       -n "$BASE" ] || [ -n "$HEAD" ]} -- {@code base: ""} on its own leaves both empty and falls
 *       straight back to the event logic -- and it is {@code HEAD} rather than a branch name
 *       because a pull_request checkout is a detached merge ref.
 *   <li>{@code fetch-depth: 0} survives. It is a <em>necessary precondition</em> and <em>not</em>
 *       the cause: it decides how much history {@code actions/checkout} puts on DISK, and a history
 *       walk cannot read commits that were never fetched. It does not decide what the scanner
 *       reads; how the scanner is invoked does. This very workflow is the proof -- {@code
 *       fetch-depth: 0} sat on the checkout the entire time the push and pull_request runs were
 *       scanning a two-chunk diff and reporting success. Keep asserting it, but never read it as
 *       evidence that history was scanned. That is the assertion above.
 *   <li>{@code path: ./} survives, or the action exits on its own "BASE and HEAD commits are the
 *       same" guard having scanned nothing.
 * </ul>
 *
 * <p>The pin comment is a YAML comment and so is invisible to a YAML parser: this reads the
 * workflow as text on purpose. The invocation assertions read it with YAML comments
 * <em>stripped</em>, because the comment beside those inputs quotes the very strings they require
 * and forbid, and four conformance checks elsewhere in this portfolio passed by matching a tool
 * name inside a comment. The pin assertion deliberately keeps the comments, since the release it
 * compares against is written in one.
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

  /** {@code base:} set to the empty string, in either YAML quoting style. */
  private static final Pattern BASE_EMPTY =
      Pattern.compile("^[ \\t]*base:[ \\t]*(?:''|\"\")[ \\t]*$", Pattern.MULTILINE);

  /** {@code head:} set to the literal {@code HEAD}, which is what arms the whole-history walk. */
  private static final Pattern HEAD_IS_HEAD =
      Pattern.compile("^[ \\t]*head:[ \\t]*HEAD[ \\t]*$", Pattern.MULTILINE);

  /** {@code head:} left empty, which silently hands the range back to the event logic. */
  private static final Pattern HEAD_EMPTY =
      Pattern.compile("^[ \\t]*head:[ \\t]*(?:''|\"\")?[ \\t]*$", Pattern.MULTILINE);

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

  /**
   * The workflow with YAML comments removed. A {@code #} opens a comment only outside quotes and
   * only at the start of a line or after whitespace, which is exactly the YAML rule.
   */
  private static String withoutComments(String yaml) {
    StringBuilder out = new StringBuilder(yaml.length());
    for (String line : yaml.split("\n", -1)) {
      out.append(stripComment(line)).append('\n');
    }
    return out.toString();
  }

  private static String stripComment(String line) {
    boolean inSingle = false;
    boolean inDouble = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
      } else if (c == '"' && !inSingle) {
        inDouble = !inDouble;
      } else if (c == '#'
          && !inSingle
          && !inDouble
          && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
        return line.substring(0, i);
      }
    }
    return line;
  }

  private static int count(Pattern pattern, String text) {
    int n = 0;
    Matcher m = pattern.matcher(text);
    while (m.find()) {
      n++;
    }
    return n;
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
  @DisplayName("the scan is invoked over the whole history on every event, not just on the cron")
  void scanIsInvokedOverTheWholeHistory() {
    String text = withoutComments(workflow());
    int steps = matches(PINNED_REF, workflow()).size();

    assertTrue(
        BASE_EMPTY.matcher(text).find(),
        "`base: \"\"` is missing from the trufflehog step. Without it the action derives its own"
            + " range from the triggering event, so the push and pull_request runs -- the runs that"
            + " gate a merge -- scan the event's diff while reporting under the job name"
            + " \"full-history secret scan (all result tiers)\". Measured here on 2026-09-13: the"
            + " scheduled run read 855 chunks / 2475409 bytes, the pull_request run on the same day"
            + " read 2 chunks / 1325 bytes.");
    assertTrue(
        HEAD_IS_HEAD.matcher(text).find(),
        "`head: HEAD` is missing from the trufflehog step. `base` alone is not enough: the action's"
            + " guard is `if [ -n \"$BASE\" ] || [ -n \"$HEAD\" ]`, so an empty base with no head"
            + " leaves both empty and falls straight back to the event logic. It is `HEAD` and not a"
            + " branch name because a pull_request checkout is a detached merge ref.");
    assertFalse(
        HEAD_EMPTY.matcher(text).find(),
        "`head:` is present but empty. That is indistinguishable from omitting it -- the action's"
            + " `[ -n \"$HEAD\" ]` guard fails and the scan silently reverts to a diff of the"
            + " triggering event while still reporting as a full-history sweep.");
    assertEquals(
        steps,
        count(BASE_EMPTY, text),
        "every pinned trufflehog step needs its own `base: \"\"`; found " + steps + " step(s)");
    assertEquals(
        steps,
        count(HEAD_IS_HEAD, text),
        "every pinned trufflehog step needs its own `head: HEAD`; found " + steps + " step(s)");
  }

  @Test
  @DisplayName("the checkout keeps the full history and the scan walks the whole repository")
  void checkoutKeepsFullHistory() {
    String text = workflow();
    assertTrue(text.contains("actions/checkout@"), "the scan no longer checks the repository out");
    assertTrue(
        FETCH_DEPTH_ZERO.matcher(text).find(),
        "`fetch-depth: 0` is missing from the checkout. It is a necessary precondition for a"
            + " history scan -- actions/checkout would otherwise fetch a single commit and the walk"
            + " could not read commits that are not on disk -- but it is NOT what makes this a"
            + " history scan, and on its own it never did. It governs disk, not the scanner's"
            + " range; `base` and `head` govern the range, and"
            + " scanIsInvokedOverTheWholeHistory() is the assertion that covers them. This"
            + " workflow is the proof: `fetch-depth: 0` was on this checkout the whole time the"
            + " push and pull_request runs were scanning a two-chunk diff and passing.");
    assertTrue(
        SCAN_PATH.matcher(text).find(),
        "`path: ./` is missing; with path, base and head all unset the action exits on its own"
            + " \"BASE and HEAD commits are the same\" guard having scanned nothing.");
  }
}
