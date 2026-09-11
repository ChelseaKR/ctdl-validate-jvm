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
 * Check 4's range ruling, where the payload declares the referenced {@code @id} more than once.
 *
 * <p>The parser used to keep whichever declaration the walk reached first, depth-first into an
 * earlier entity's inline objects, so a stub embedded under some unrelated entity decided the class
 * of every later reference to that identifier, and the verdict was a function of array order. The
 * reference fixed that on its main branch by reading the declarations as one entity -- the union of
 * their types and properties -- and reporting the merge (its ADR 0005, issue #33), and this port
 * reads them the same way.
 *
 * <p>Asserted here: the ruling no longer depends on {@code @graph} order, in either direction; a
 * reference no declaration puts in range is still an ERROR; and the case issue #33 called a
 * suppressed violation -- the first declaration in range, a later one not -- is decided the way the
 * reference decided it, with no violation, because the document asserts the resource is both
 * classes and the property admits one of them. That is a ruling the reference made, recorded in its
 * {@code test_one_declaration_in_range_settles_it_from_either_order}, not one made here.
 */
class DuplicateIdShadowingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String ORG_X =
      "https://credentialengineregistry.org/resources/ce-11111111-1111-4111-8111-111111111111";
  private static final String SHARED =
      "https://credentialengineregistry.org/resources/ce-22222222-2222-4222-8222-222222222222";
  private static final String ORG_Y =
      "https://credentialengineregistry.org/resources/ce-33333333-3333-4333-8333-333333333333";

  private static List<Finding> validate(String json) throws IOException {
    return Validator.validate(MAPPER.readTree(json));
  }

  private static List<Finding> withCode(List<Finding> findings, String code) {
    List<Finding> matching = new ArrayList<>();
    for (Finding finding : findings) {
      if (finding.code().equals(code)) {
        matching.add(finding);
      }
    }
    return matching;
  }

  /**
   * Org X embeds a stub for {@code SHARED} inline, typed {@code ceterms:Organization} in passing;
   * the document's own top-level declaration of {@code SHARED} types it {@code ceterms:Place}. Org
   * Y then points {@code ceterms:address} at it, and {@code ceterms:address} ranges on {@code
   * ceterms:Place} alone.
   *
   * @param sharedTopLevelType the class the top-level declaration of {@code SHARED} carries
   */
  private static String shadowedDocument(String sharedTopLevelType) {
    return DOCUMENT
        .replace("__ORG_X__", ORG_X)
        .replace("__SHARED__", SHARED)
        .replace("__ORG_Y__", ORG_Y)
        .replace("__TOP_LEVEL_TYPE__", sharedTopLevelType);
  }

  /**
   * The shadowing shape, with the identifiers and the shadowed class left as tokens.
   *
   * <p>Substituted by {@code replace} rather than {@code formatted}: a JSON payload has to carry
   * the newlines it was written with, and a format string is read by SpotBugs as one that should be
   * emitting {@code %n} instead.
   */
  private static final String DOCUMENT =
      """
        {
          "@context": "https://credreg.net/ctdl/schema/context/json",
          "@graph": [
            {
              "@id": "__ORG_X__",
              "@type": "ceterms:Organization",
              "ceterms:ctid": "ce-11111111-1111-4111-8111-111111111111",
              "ceterms:name": { "en-US": "Org X" },
              "ceterms:parentOrganization": {
                "@id": "__SHARED__",
                "@type": "ceterms:Organization",
                "ceterms:name": { "en-US": "a stub, carrying an incidental @type" }
              }
            },
            {
              "@id": "__SHARED__",
              "@type": "__TOP_LEVEL_TYPE__",
              "ceterms:name": { "en-US": "the top-level declaration" }
            },
            {
              "@id": "__ORG_Y__",
              "@type": "ceterms:Organization",
              "ceterms:ctid": "ce-33333333-3333-4333-8333-333333333333",
              "ceterms:name": { "en-US": "Org Y" },
              "ceterms:address": "__SHARED__"
            }
          ]
        }
        """;

  @Test
  @DisplayName("a reference is in range when any declaration of its @id is, not only the first")
  void aShadowedDeclarationDoesNotDecideTheRange() throws IOException {
    // ce-2222 is declared ceterms:Place at the top level of the same @graph,
    // which is squarely inside ceterms:address's declared range. The only
    // reason this ever reported RANGE_VIOLATION is that Org X's inline stub
    // for the same @id sits earlier in the document and was walked first.
    List<Finding> findings = validate(shadowedDocument("ceterms:Place"));

    assertEquals(
        List.of(),
        withCode(findings, "RANGE_VIOLATION"),
        "the document declares the referenced @id as a Place; only walk order said otherwise");
  }

  @Test
  @DisplayName("a reference no declaration puts in range is still a RANGE_VIOLATION")
  void anIdentifierNoDeclarationPutsInRangeIsStillReported() throws IOException {
    // The guard on the ruling above. Here neither declaration of ce-2222 is a
    // Place, so nothing in the payload puts the reference in range and the
    // ERROR is real. Asking every declaration must not become "ask until one
    // of them lets this through".
    List<Finding> findings = validate(shadowedDocument("ceterms:Course"));

    List<Finding> violations = withCode(findings, "RANGE_VIOLATION");
    assertEquals(1, violations.size(), findings.toString());
    assertEquals(ORG_Y, violations.get(0).entity());
    assertEquals("ceterms:address", violations.get(0).property());
  }

  @Test
  @DisplayName("two declarations of an @id are one node, typed with the union, reachable by both")
  void twoDeclarationsAreOneNode() throws IOException {
    Graph graph =
        GraphParser.parse(MAPPER.readTree(shadowedDocument("ceterms:Place")), SchemaLoader.load());

    Graph.Node shared = graph.byId().get(SHARED);
    assertEquals(
        List.of("ceterms:Organization", "ceterms:Place"),
        shared.types(),
        "the union of both declarations' types, sorted, as the reference merges them");
    assertEquals(
        List.of("$.@graph[0].ceterms:parentOrganization[0]", "$.@graph[1]"),
        graph.repeatedIds().get(SHARED),
        "every declaration site, in walk order");
    assertEquals(shared, graph.byPath().get("$.@graph[1]"));
    assertEquals(shared, graph.byPath().get("$.@graph[0].ceterms:parentOrganization[0]"));
    assertEquals(
        2, shared.valuesOf("ceterms:name").size(), "both declarations' names survive the merge");
    assertEquals(
        1,
        withCode(validate(shadowedDocument("ceterms:Place")), "ID_DECLARED_MORE_THAN_ONCE").size());
  }

  @Test
  @DisplayName("the ruling is the same from either @graph order")
  void theRulingDoesNotDependOnArrayOrder() throws IOException {
    for (String type : List.of("ceterms:Place", "ceterms:Course")) {
      String forwards = shadowedDocument(type);
      // Swap the stub's parent and the top-level declaration, so the top-level
      // declaration is walked first.
      com.fasterxml.jackson.databind.node.ObjectNode document =
          (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(forwards);
      com.fasterxml.jackson.databind.node.ArrayNode graph =
          (com.fasterxml.jackson.databind.node.ArrayNode) document.get("@graph");
      com.fasterxml.jackson.databind.JsonNode first = graph.get(0);
      graph.set(0, graph.get(1));
      graph.set(1, first);
      assertEquals(
          judgements(Validator.validate(MAPPER.readTree(forwards))),
          judgements(Validator.validate(document)),
          type + ": the verdict moved with the array order");
    }
  }

  /**
   * Every finding that judges the document. The merge disclosure is excluded: its message names the
   * paths the declarations sit at, and rearranging the document moves them on purpose.
   */
  private static List<Finding> judgements(List<Finding> findings) {
    List<Finding> judged = new ArrayList<>();
    for (Finding finding : findings) {
      if (!"ID_DECLARED_MORE_THAN_ONCE".equals(finding.code())) {
        judged.add(finding);
      }
    }
    return judged;
  }

  @Test
  @DisplayName("one declaration in range settles it, as the reference rules, and the merge is said")
  void oneDeclarationInRangeSettlesIt() throws IOException {
    // The case issue #33 called a suppressed violation: the first-walked
    // declaration is in range and the top-level one is not. The reference's
    // main branch decided it: the document asserts the resource is both
    // classes, ceterms:address admits one of them, so there is no violation --
    // and the merge is disclosed, so a reader who did not mean one entity can
    // see it was read as one. Recorded upstream as
    // test_one_declaration_in_range_settles_it_from_either_order.
    String mirrored =
        MIRRORED
            .replace("__ORG_X__", ORG_X)
            .replace("__SHARED__", SHARED)
            .replace("__ORG_Y__", ORG_Y);

    List<Finding> findings = validate(mirrored);
    List<Finding> onTheAddress = new ArrayList<>();
    for (Finding finding : withCode(findings, "RANGE_VIOLATION")) {
      if ("ceterms:address".equals(finding.property())) {
        onTheAddress.add(finding);
      }
    }
    assertTrue(
        onTheAddress.isEmpty(),
        "one declaration of the referenced @id is a Place, which ceterms:address admits: "
            + findings);
    assertEquals(
        1,
        withCode(findings, "ID_DECLARED_MORE_THAN_ONCE").size(),
        "the merge that settled it is disclosed: " + findings);

    // Org X's own parentOrganization does report, and must: its value is the
    // inline Place stub itself, resolved by path rather than by @id, and no
    // declaration of that @id is an Organization. This is the guard that the
    // assertion above is about the suppressed address ruling and not about a
    // document that happens to produce nothing.
    assertEquals(
        1,
        withCode(findings, "RANGE_VIOLATION").size(),
        "the only RANGE_VIOLATION here should be Org X's parentOrganization: " + findings);
  }

  /**
   * The same shadowing in the other direction, tokenised the same way as {@link #DOCUMENT}: the
   * first-walked declaration is in range and the top-level one is not.
   */
  private static final String MIRRORED =
      """
      {
        "@context": "https://credreg.net/ctdl/schema/context/json",
        "@graph": [
          {
            "@id": "__ORG_X__",
            "@type": "ceterms:Organization",
            "ceterms:ctid": "ce-11111111-1111-4111-8111-111111111111",
            "ceterms:name": { "en-US": "Org X" },
            "ceterms:parentOrganization": {
              "@id": "__SHARED__",
              "@type": "ceterms:Place",
              "ceterms:name": { "en-US": "a stub that happens to be in range" }
            }
          },
          {
            "@id": "__SHARED__",
            "@type": "ceterms:Course",
            "ceterms:name": { "en-US": "the top-level declaration, out of range" }
          },
          {
            "@id": "__ORG_Y__",
            "@type": "ceterms:Organization",
            "ceterms:ctid": "ce-33333333-3333-4333-8333-333333333333",
            "ceterms:name": { "en-US": "Org Y" },
            "ceterms:address": "__SHARED__"
          }
        ]
      }
      """;
}
