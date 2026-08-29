package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What rules does each side actually have?
 *
 * <p>{@code ParityTest.corpusCoversEveryCode} used to answer that from {@link FindingCodes#ALL}, a
 * list maintained inside this port. A rule the reference has and this port never learned about was
 * therefore invisible three times over: absent from the port's list, absent from the port's output,
 * and absent from {@code parity/expected/} unless a fixture happened to reach it. The coverage test
 * passed and CI stayed green on a port that was missing a rule.
 *
 * <p>This suite derives the answer from both sides instead of trusting either list.
 *
 * <ul>
 *   <li>The port's codes are parsed out of {@code src/main/java}, one per {@code new
 *       Finding("...")} site, so {@link FindingCodes} is documentation rather than authority and
 *       cannot drift.
 *   <li>The reference's codes are read from {@code parity/reference-codes.json}, which {@code
 *       tools/generate_expectations.py} produces by AST-parsing the pinned release's own source,
 *       and which the CI parity job regenerates and diffs alongside {@code parity/expected/}. It
 *       cannot be hand-maintained here.
 * </ul>
 *
 * <p>Every difference between the two sets must then be declared below with a reason. An undeclared
 * difference, in either direction, fails the build.
 */
class FindingCodeCensusTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));
  private static final Path MAIN_SOURCE = ROOT.resolve("src/main/java");
  private static final Path REFERENCE_CODES = ROOT.resolve("parity/reference-codes.json");

  /** {@code new Finding(} followed by a string literal, which is how every code is constructed. */
  private static final Pattern FINDING_SITE =
      Pattern.compile("new\\s+Finding\\s*\\(\\s*\"([A-Z0-9_]+)\"");

  /** Any {@code new Finding(} at all, so a site with a computed code cannot slip past unseen. */
  private static final Pattern ANY_FINDING_SITE = Pattern.compile("new\\s+Finding\\s*\\(");

  /** Block comments, line comments, and text blocks: prose that describes code is not code. */
  private static final Pattern COMMENTARY =
      Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

  /**
   * Codes this port emits that the pinned reference does not, each with why.
   *
   * <p>These are the dispositions {@code parity/ahead/} bounds. Being listed here is not permission
   * to differ: {@code AheadOfReferenceTest} still holds each one to a substitution or withdrawal
   * gated on the vendored snapshot.
   */
  private static final Map<String, String> PORT_IS_AHEAD =
      Map.of(
          "CONCEPT_RANGE_CONFLICT",
              "CTDL declares a reference to a term from one of its own concept schemes two"
                  + " incompatible ways; an ERROR would report Credential Engine's own dominant"
                  + " encoding as a defect. Fixed on the reference's main branch, in no release,"
                  + " so the pin cannot reach it. Bounded by parity/ahead/.",
          "VERSION_RANGE_CONFLICT",
              "CTDL's three version properties declare a range that is a strict subset of their"
                  + " own domain, so for a dropped class the domain and the range contradict each"
                  + " other and the document satisfies one of them. Fixed on the reference's main"
                  + " branch, in no release, so the pin cannot reach it. Bounded by parity/ahead/.");

  /**
   * Codes the pinned reference emits that this port does not, each with why.
   *
   * <p>Empty, and it should stay that way. A rule the reference has and the port lacks is the port
   * being behind, which is the failure direction this whole census exists to make visible. Adding
   * an entry here is a deliberate statement that the port will not port a rule, and needs a reason
   * a reader can check.
   *
   * <p>Empty is a statement about the pinned release and not about the reference. Measured on
   * 2026-08-29, the current release 0.2.1 emits {@code REF_RESOLVED_SUPPLIED} and this port does
   * not, so this map would not be empty if the pin moved. What that bump costs is written down in
   * {@code parity/PROVENANCE.md}; it is not recorded here, because everything in this class is
   * measured against the pin and the pin is 0.1.0.
   */
  private static final Map<String, String> PORT_IS_BEHIND = Map.of();

  @Test
  @DisplayName("the port's codes are what the source constructs, not what a list claims")
  void findingCodesMatchesTheSource() throws IOException {
    Set<String> parsed = portCodes();
    assertEquals(
        new TreeSet<>(FindingCodes.ALL),
        parsed,
        "FindingCodes.ALL and src/main/java disagree about which codes exist. The source is"
            + " right: add the code to FindingCodes, or delete the rule that constructs it.");
  }

  @Test
  @DisplayName("every difference from the reference's rule set is declared, in both directions")
  void everyDivergenceFromTheReferenceIsDeclared() throws IOException {
    Set<String> port = portCodes();
    Set<String> reference = referenceCodes();

    Set<String> ahead = new TreeSet<>(port);
    ahead.removeAll(reference);
    Set<String> behind = new TreeSet<>(reference);
    behind.removeAll(port);

    assertEquals(
        new TreeSet<>(PORT_IS_AHEAD.keySet()),
        ahead,
        "this port emits a code the pinned reference does not, and PORT_IS_AHEAD does not say"
            + " why. A rule only one side has must be declared, never silently carried.");
    assertEquals(
        new TreeSet<>(PORT_IS_BEHIND.keySet()),
        behind,
        "the pinned reference emits a code this port does not. That is the port being behind on"
            + " a rule, which no parity fixture can catch, because a code the port never emits"
            + " cannot appear in a comparison the port takes part in. Port the rule, or declare"
            + " it in PORT_IS_BEHIND with a reason.");
  }

  @Test
  @DisplayName("the recorded reference rule set is a real, generated record")
  void theReferenceRecordIsUsable() throws IOException {
    JsonNode record = MAPPER.readTree(Files.readString(REFERENCE_CODES, StandardCharsets.UTF_8));
    assertEquals(
        "ctdl-validate", record.get("reference").asText(), "the record names another reference");

    String pinned = pinnedVersion();
    assertEquals(
        pinned,
        record.get("version").asText(),
        "parity/reference-codes.json was generated against a different release than"
            + " parity/reference-requirements.txt pins. Reinstall the pin and rerun"
            + " tools/generate_expectations.py.");
    assertTrue(
        referenceCodes().size() > 1,
        "the reference rule set came back with nothing in it, so this census compares against"
            + " silence rather than against the reference");
  }

  /** The version {@code parity/reference-requirements.txt} pins, so the record cannot be stale. */
  private static String pinnedVersion() throws IOException {
    Path requirements = ROOT.resolve("parity/reference-requirements.txt");
    Matcher matcher =
        Pattern.compile("^ctdl-validate==([^\\s\\\\]+)", Pattern.MULTILINE)
            .matcher(Files.readString(requirements, StandardCharsets.UTF_8));
    assertTrue(matcher.find(), "no ctdl-validate== pin found in " + requirements);
    return matcher.group(1);
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Set<String> referenceCodes() throws IOException {
    JsonNode record = MAPPER.readTree(Files.readString(REFERENCE_CODES, StandardCharsets.UTF_8));
    Set<String> codes = new TreeSet<>();
    for (JsonNode code : record.get("codes")) {
      codes.add(code.asText());
    }
    return codes;
  }

  /**
   * Every finding code constructed anywhere in the shipped source.
   *
   * <p>Parsed rather than listed, and strict about it: a {@code new Finding(} whose first argument
   * is not a string literal fails, because a census that silently skipped what it could not read
   * would under-report this port's rule set, which is the same blindness in a new place.
   */
  static Set<String> portCodes() throws IOException {
    Map<String, List<String>> sites = new TreeMap<>();
    Map<String, Integer> unreadable = new LinkedHashMap<>();
    int scanned = 0;
    try (Stream<Path> files = Files.walk(MAIN_SOURCE)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        scanned++;
        // Comments are stripped first: a javadoc that quotes `new Finding("X")`
        // to explain this very census would otherwise be counted as a rule.
        String source =
            COMMENTARY.matcher(Files.readString(file, StandardCharsets.UTF_8)).replaceAll("");
        int literals = 0;
        Matcher literal = FINDING_SITE.matcher(source);
        while (literal.find()) {
          literals++;
          sites
              .computeIfAbsent(literal.group(1), key -> new java.util.ArrayList<>())
              .add(String.valueOf(file.getFileName()));
        }
        int all = 0;
        Matcher any = ANY_FINDING_SITE.matcher(source);
        while (any.find()) {
          all++;
        }
        if (all != literals) {
          unreadable.put(String.valueOf(file.getFileName()), all - literals);
        }
      }
    }
    assertTrue(scanned > 0, "scanned no source files under " + MAIN_SOURCE);
    assertTrue(
        unreadable.isEmpty(),
        () ->
            "a Finding is constructed with a code this census cannot read as a literal, so it"
                + " cannot be compared against the reference's rule set: "
                + unreadable);
    assertTrue(
        !sites.isEmpty(), "parsed " + scanned + " source file(s) and found no finding codes");
    return new TreeSet<>(sites.keySet());
  }
}
