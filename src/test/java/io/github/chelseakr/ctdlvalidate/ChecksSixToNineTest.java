package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks 6 to 9, the rules the reference's main branch carries and no release does.
 *
 * <p>Their byte equality with the reference is shown by {@code tools/next_release_parity.py} over
 * the parity corpus, and cannot be shown by {@code ParityTest} until a release carries them. What
 * is pinned here is what each check promises regardless of wording: which values it reaches, which
 * it leaves alone, and that none of the four can ever gate an exit code.
 */
class ChecksSixToNineTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String CERT =
      "https://credentialengineregistry.org/resources/ce-11111111-1111-4111-8111-111111111111";
  private static final List<String> NEW_CODES =
      List.of(
          "ID_DECLARED_MORE_THAN_ONCE",
          "CONCEPT_OUTSIDE_SCHEME",
          "CONCEPT_OUTSIDE_SNAPSHOT",
          "CONCEPT_NOT_IDENTIFIED",
          "LANGUAGE_MAP_EXPECTED",
          "TERM_UNSTABLE");

  private static List<Finding> validate(String entityBody) throws IOException {
    return Validator.validate(
        MAPPER.readTree(
            "{\"@graph\": [{\"@id\": \""
                + CERT
                + "\", \"@type\": \"ceterms:Certificate\", "
                + entityBody
                + "}]}"));
  }

  private static List<Finding> code(List<Finding> findings, String code) {
    List<Finding> matching = new ArrayList<>();
    findings.forEach(
        f -> {
          if (f.code().equals(code)) {
            matching.add(f);
          }
        });
    return matching;
  }

  private static String aligned(String term) {
    return "{\"@type\": \"ceterms:CredentialAlignmentObject\", \"ceterms:targetNode\": \""
        + term
        + "\"}";
  }

  @Test
  @DisplayName("a concept in the named scheme is silent; one from another scheme is a WARNING")
  void conceptScheme() throws IOException {
    assertEquals(
        List.of(),
        code(
            validate("\"ceterms:audienceLevelType\": [" + aligned("audLevel:AdvancedLevel") + "]"),
            "CONCEPT_OUTSIDE_SCHEME"));
    List<Finding> wrong =
        code(
            validate("\"ceterms:audienceLevelType\": [" + aligned("credentialStat:Active") + "]"),
            "CONCEPT_OUTSIDE_SCHEME");
    assertEquals(1, wrong.size());
    assertEquals(Severity.WARNING, wrong.get(0).severity());
    assertEquals("credentialStat:Active", wrong.get(0).value());
  }

  @Test
  @DisplayName(
      "a term the snapshot does not declare, or none at all, is UNVERIFIABLE and not wrong")
  void conceptUnverifiable() throws IOException {
    List<Finding> outside =
        validate(
            "\"ceterms:audienceLevelType\": ["
                + aligned("https://example.org/onet/15-1252.00")
                + "]");
    assertEquals(
        Severity.UNVERIFIABLE, code(outside, "CONCEPT_OUTSIDE_SNAPSHOT").get(0).severity());
    List<Finding> named =
        validate(
            "\"ceterms:audienceLevelType\": [{\"@type\": \"ceterms:CredentialAlignmentObject\","
                + " \"ceterms:targetNodeName\": {\"en-US\": \"words\"}}]");
    Finding unidentified = code(named, "CONCEPT_NOT_IDENTIFIED").get(0);
    assertEquals("(alignment object with no ceterms:targetNode)", unidentified.value());
    assertEquals(
        List.of(),
        code(
            validate("\"ceterms:audienceType\": \"audience:Citizen\""), "CONCEPT_OUTSIDE_SNAPSHOT"),
        "a term written directly is read directly");
  }

  @Test
  @DisplayName("a bare literal on a language map is a WARNING, rendered as Python's str()")
  void languageMap() throws IOException {
    assertEquals(
        List.of(), code(validate("\"ceterms:name\": {\"en-US\": \"x\"}"), "LANGUAGE_MAP_EXPECTED"));
    List<Finding> bare =
        code(
            validate("\"ceterms:name\": [\"x\", 2, true, {\"@value\": 1.5}]"),
            "LANGUAGE_MAP_EXPECTED");
    List<String> values = new ArrayList<>();
    bare.forEach(f -> values.add(f.value()));
    assertEquals(List.of("1.5", "2", "True", "x"), values);
    assertEquals(
        List.of(),
        code(
            validate("\"ceterms:subjectWebpage\": \"https://example.org\""),
            "LANGUAGE_MAP_EXPECTED"),
        "a property the context does not declare a language map is not this check's business");
  }

  @Test
  @DisplayName("an unstable term is disclosed as a class, a property, and a concept value")
  void termStatus() throws IOException {
    List<Finding> findings =
        Validator.validate(
            MAPPER.readTree(
                "{\"@type\": \"ceasn:Rubric\", \"ceasn:evaluatorType\": [\"x\"],"
                    + " \"ceterms:credentialStatusType\": "
                    + aligned("credentialStat:TeachOut")
                    + "}"));
    List<String> values = new ArrayList<>();
    code(findings, "TERM_UNSTABLE").forEach(f -> values.add(f.property() + "=" + f.value()));
    assertTrue(values.contains("@type=ceasn:Rubric"), values.toString());
    assertTrue(values.contains("ceasn:evaluatorType=ceasn:evaluatorType"), values.toString());
    assertTrue(
        values.contains("ceterms:credentialStatusType=credentialStat:TeachOut"), values.toString());
    assertEquals(
        List.of(),
        code(validate("\"ceterms:name\": {\"en-US\": \"stable\"}"), "TERM_UNSTABLE"),
        "a payload built from stable terms says nothing");
  }

  @Test
  @DisplayName("a merge is disclosed with every site in walk order, including a tenth")
  void identityPathsInWalkOrder() throws IOException {
    StringBuilder graph = new StringBuilder("{\"@graph\": [");
    for (int index = 0; index < 11; index++) {
      graph.append(index == 0 ? "" : ", ").append("{\"@id\": \"").append(CERT).append("\"}");
    }
    graph.append("]}");
    List<Finding> merged =
        code(Validator.validate(MAPPER.readTree(graph.toString())), "ID_DECLARED_MORE_THAN_ONCE");
    assertEquals(0, merged.size(), "a reference-only object declares nothing");
    List<Finding> declared =
        code(
            Validator.validate(
                MAPPER.readTree(
                    graph.toString().replace("\"}", "\", \"@type\": \"ceterms:Certificate\"}"))),
            "ID_DECLARED_MORE_THAN_ONCE");
    assertEquals(1, declared.size());
    assertTrue(
        declared.get(0).message().contains("$.@graph[9], $.@graph[10])"),
        declared.get(0).message());
  }

  @Test
  @DisplayName("none of checks 6 to 9 can ever gate an exit code")
  void noneOfTheFourIsAnError() throws IOException {
    List<Finding> findings = new ArrayList<>();
    for (String name :
        List.of(
            "concept_scheme_membership.json",
            "repeated_identifier_merge.json",
            "mixed_shapes.json")) {
      findings.addAll(
          Validator.validate(
              MAPPER.readTree(
                  java.nio.file.Path.of(
                          System.getProperty(
                              "ctdlvalidate.repoRoot", System.getProperty("user.dir")))
                      .resolve("parity/fixtures")
                      .resolve(name)
                      .toFile())));
    }
    int reached = 0;
    for (Finding finding : findings) {
      if (NEW_CODES.contains(finding.code())) {
        reached++;
        assertTrue(finding.severity() != Severity.ERROR, finding.toString());
      }
    }
    assertTrue(reached >= NEW_CODES.size(), "the sample must reach the new codes to say anything");
  }
}
