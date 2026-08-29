package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The reason this repository exists.
 *
 * <p>Every fixture in {@code parity/fixtures/} is validated by this Java implementation, and the
 * result is compared byte for byte against {@code parity/expected/}, which is the output of the
 * Python reference implementation, ChelseaKR/ctdl-validate, over the same fixture. Any disagreement
 * — a different finding, a different severity, a different rule citation, a different order, a
 * different exit code — fails this test and therefore the build.
 *
 * <p>The expectation files are generated, never hand-written. {@code
 * tools/generate_expectations.py} produces them from the pinned reference release, and CI reruns
 * that script and fails if the committed files have drifted, so this suite cannot quietly become a
 * comparison of the Java implementation against itself.
 */
class ParityTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));
  private static final Path FIXTURES = ROOT.resolve("parity/fixtures");
  private static final Path EXPECTED = ROOT.resolve("parity/expected");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static Stream<String> fixtureNames() throws IOException {
    try (Stream<Path> files = Files.list(FIXTURES)) {
      return files
          .filter(path -> String.valueOf(path.getFileName()).endsWith(".json"))
          .map(path -> String.valueOf(path.getFileName()))
          .sorted()
          .toList()
          .stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtureNames")
  @DisplayName("Java and Python agree finding-for-finding")
  void agreesWithTheReferenceImplementation(String name) throws IOException {
    String expected = Files.readString(EXPECTED.resolve(name), StandardCharsets.UTF_8);
    String actual = ParityDocument.render(MAPPER.readTree(FIXTURES.resolve(name).toFile()));
    assertEquals(
        expected,
        actual,
        () ->
            "This Java implementation disagrees with the Python reference on "
                + name
                + ". One of the two is wrong about a published CTDL rule.");
  }

  @Test
  @DisplayName("the corpus is non-empty and every fixture has an expectation")
  void corpusIsComplete() throws IOException {
    Set<String> fixtures = new TreeSet<>(fixtureNames().toList());
    Set<String> expectations = new TreeSet<>();
    try (Stream<Path> files = Files.list(EXPECTED)) {
      files
          .map(path -> String.valueOf(path.getFileName()))
          .filter(fileName -> fileName.endsWith(".json"))
          .forEach(expectations::add);
    }
    assertFalse(fixtures.isEmpty(), "the parity corpus is empty");
    assertEquals(
        fixtures,
        expectations,
        "every fixture needs an expectation and every expectation needs a fixture; run"
            + " tools/generate_expectations.py");
  }

  @Test
  @DisplayName("the corpus covers every finding code the checks can emit")
  void corpusCoversEveryCode() throws IOException {
    Set<String> emitted = new TreeSet<>();
    for (String name : fixtureNames().toList()) {
      for (Object finding : findingsOf(name)) {
        emitted.add(String.valueOf(((java.util.Map<?, ?>) finding).get("code")));
      }
    }
    // Codes this port emits ahead of the pinned reference cannot appear in
    // parity/expected/, because the pinned release does not know them. They are
    // covered by parity/ahead/ instead, which AheadOfReferenceTest holds to the
    // dispositions they are allowed to be.
    for (String name : AheadOfReferenceTest.fixtureNames().toList()) {
      for (JsonNode finding : AheadOfReferenceTest.portDocument(name).get("findings")) {
        emitted.add(finding.get("code").asText());
      }
    }
    // Parsed out of src/main/java, not read off FindingCodes.ALL. Deriving the
    // expectation from a list this port maintains would make a rule the port
    // never learned about invisible here; FindingCodeCensusTest is what compares
    // the port's real rule set against the reference's.
    Set<String> declared = FindingCodeCensusTest.portCodes();
    assertEquals(
        declared,
        emitted,
        "a finding code with no fixture is a rule neither implementation is being compared on");
  }

  @Test
  @DisplayName("the comparison fails when the two implementations disagree")
  void theGateItselfHasBeenBroken() throws IOException {
    // A parity suite nobody has watched fail is a parity suite you are trusting
    // on faith. This corrupts one expectation the way a real divergence would --
    // one changed severity -- and asserts the comparison notices.
    String name = "identifier_kind.json";
    String expected = Files.readString(EXPECTED.resolve(name), StandardCharsets.UTF_8);
    String corrupted =
        expected.replaceFirst("\"severity\": \"ERROR\"", "\"severity\": \"WARNING\"");
    assertNotEquals(expected, corrupted, "the corruption did not change the expectation");

    String actual = ParityDocument.render(MAPPER.readTree(FIXTURES.resolve(name).toFile()));
    assertEquals(expected, actual, "sanity: the uncorrupted comparison must pass");
    assertNotEquals(corrupted, actual, "the comparison did not notice a changed severity");
  }

  @Test
  @DisplayName("expectations carry a rule citation, source URL, and retrieval date")
  void everyFindingCitesItsRule() throws IOException {
    int checked = 0;
    for (String name : fixtureNames().toList()) {
      for (Object finding : findingsOf(name)) {
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) finding;
        java.util.Map<?, ?> rule = (java.util.Map<?, ?>) map.get("rule");
        assertTrue(
            !String.valueOf(rule.get("citation")).isBlank(), name + ": finding without a citation");
        assertTrue(!String.valueOf(rule.get("url")).isBlank(), name + ": finding without a source");
        assertTrue(
            !String.valueOf(rule.get("retrieved")).isBlank(),
            name + ": finding without a retrieval date");
        checked++;
      }
    }
    assertTrue(checked > 0, "no findings were checked");
  }

  private static List<?> findingsOf(String name) throws IOException {
    java.util.Map<?, ?> document =
        MAPPER.readValue(EXPECTED.resolve(name).toFile(), java.util.Map.class);
    Object findings = document.get("findings");
    return findings instanceof List<?> list ? list : new ArrayList<>();
  }
}
