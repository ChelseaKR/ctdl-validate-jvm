package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A gate that has not been deliberately broken is a gate you are trusting on faith.
 *
 * <p>This suite starts from a fixture the parity corpus proves clean, corrupts one thing at a time,
 * and asserts the corruption is caught. It mirrors the reference implementation's own
 * break-the-gate suite; the point of repeating it here is that the port must fail for the same
 * reasons, not only agree on the corpus.
 */
class BreakTheGateTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String cleanFramework() throws IOException {
    return java.nio.file.Files.readString(ROOT.resolve("parity/fixtures/clean_framework.json"));
  }

  private static List<Finding> validate(String json) throws IOException {
    JsonNode data = MAPPER.readTree(json);
    return Validator.validate(data);
  }

  private static boolean has(List<Finding> findings, String code) {
    return findings.stream().anyMatch(finding -> code.equals(finding.code()));
  }

  private void assertCorruptionIsCaught(String code, UnaryOperator<String> corrupt)
      throws IOException {
    String clean = cleanFramework();
    assertTrue(validate(clean).isEmpty(), "the starting fixture is not clean");
    String broken = corrupt.apply(clean);
    assertFalse(broken.equals(clean), "the corruption changed nothing");
    assertTrue(has(validate(broken), code), () -> "corrupting the payload did not produce " + code);
  }

  @Test
  @DisplayName("stripping the ce- prefix from a CTID is caught")
  void strippedCtidPrefix() throws IOException {
    assertCorruptionIsCaught(
        "CTID_BARE_UUID",
        json -> json.replace("\"ceterms:ctid\": \"ce-177f4c85", "\"ceterms:ctid\": \"177f4c85"));
  }

  @Test
  @DisplayName("pointing isPartOf at the wrong framework identifier is caught")
  void wrongFrameworkIdentifier() throws IOException {
    assertCorruptionIsCaught(
        "ISPARTOF_FRAMEWORK_MISMATCH",
        json ->
            json.replace(
                "\"ceasn:isPartOf\": \"https://credentialengineregistry.org/resources/ce-177f4c85"
                    + "-4efe-401d-acdd-1ea4adeeaf37\"",
                "\"ceasn:isPartOf\": \"https://credentialengineregistry.org/resources/ce-82566cee"
                    + "-17f3-4a6e-8f59-b45273aac457\""));
  }

  @Test
  @DisplayName("breaking one direction of a declared inverse pair is caught")
  void brokenInversePair() throws IOException {
    assertCorruptionIsCaught(
        "INVERSE_MISMATCH",
        json ->
            json.replace(
                "\"ceasn:isChildOf\": \"https://credentialengineregistry.org/resources/ce-5e3de882"
                    + "-3b49-421b-b623-695c63587f4f\"",
                "\"ceasn:isChildOf\": \"https://credentialengineregistry.org/resources/ce-9e492574"
                    + "-07fc-4154-b7f2-898425f4f3a3\""));
  }

  @Test
  @DisplayName("retyping the framework as something else is caught")
  void retypedFramework() throws IOException {
    assertCorruptionIsCaught(
        "RANGE_VIOLATION",
        json ->
            json.replace(
                "\"@type\": \"ceasn:CompetencyFramework\"", "\"@type\": \"ceasn:Competency\""));
  }

  @Test
  @DisplayName("corrupting a Registry URI is caught")
  void corruptedRegistryUri() throws IOException {
    assertCorruptionIsCaught(
        "REGISTRY_URI_MALFORMED",
        json ->
            json.replace(
                "resources/ce-9e492574-07fc-4154-b7f2-898425f4f3a3",
                "resources/9e492574-07fc-4154-b7f2-898425f4f3a3"));
  }

  @Test
  @DisplayName("a blank node reference the payload never defines is caught")
  void danglingBlankNode() throws IOException {
    assertCorruptionIsCaught(
        "REF_UNRESOLVED_BNODE",
        json ->
            json.replace(
                "\"ceasn:isPartOf\": \"https://credentialengineregistry.org/resources/ce-177f4c85"
                    + "-4efe-401d-acdd-1ea4adeeaf37\"",
                "\"ceasn:isPartOf\": \"_:never-declared\""));
  }

  @Test
  @DisplayName("an undeclared term is reported rather than silently accepted")
  void undeclaredTerm() throws IOException {
    assertCorruptionIsCaught(
        "UNKNOWN_PROPERTY",
        json -> json.replace("\"ceasn:competencyText\"", "\"ceasn:competencyTxt\""));
  }
}
