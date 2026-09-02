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
 * <p>{@code Graph.byId} keeps whichever declaration the walk reached first, and the walk goes
 * depth-first into an earlier entity's inline objects before it reaches the next top-level entry.
 * So a stub embedded under some unrelated entity can become "the" node for every later resolution
 * of that identifier, and a reference is judged against a class the document never meant. The
 * direction of the resulting mistake is a function of where the stub was written, not of the
 * payload.
 *
 * <p>What is asserted here is the half that can be fixed inside a port: a range ruling now asks
 * every declaration of the identifier, so a target the document really does declare in range is not
 * an ERROR because a stub was walked first. The mirror case is asserted too, as the limit it is —
 * see {@link #theMirrorCaseIsNotFixedAndTheDocumentsSaySo}.
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
  @DisplayName("every declaration of an @id is reachable, in document order")
  void everyDeclarationIsReachable() throws IOException {
    Graph graph =
        GraphParser.parse(MAPPER.readTree(shadowedDocument("ceterms:Place")), SchemaLoader.load());

    List<Graph.Node> declarations = graph.declarationsOf(SHARED);
    assertEquals(2, declarations.size(), "both declarations of the shared @id");
    assertEquals(
        List.of("ceterms:Organization"),
        declarations.get(0).types(),
        "the inline stub is walked first, which is the whole problem");
    assertEquals(List.of("ceterms:Place"), declarations.get(1).types());
    assertEquals(
        graph.byId().get(SHARED),
        declarations.get(0),
        "byId still keeps the first declaration; declarationsOf is what sees past it");

    assertEquals(List.of(), graph.declarationsOf(null), "a null @id declares nothing");
    assertEquals(
        List.of(),
        graph.declarationsOf("https://credentialengineregistry.org/resources/ce-nope"),
        "an @id the payload does not declare");
  }

  @Test
  @DisplayName("the mirror case is not fixed, and the documents say so")
  void theMirrorCaseIsNotFixedAndTheDocumentsSaySo() throws IOException {
    // The same shadowing in the other direction: the first-walked declaration
    // satisfies the range and the top-level one does not. A genuine violation
    // is suppressed, and this port still suppresses it, exactly as the pinned
    // reference does. Fixing it means raising an ERROR the reference does not
    // raise, which parity/ahead/ is arranged not to permit and which is a
    // rule-level ruling for the sibling. Asserted rather than left implicit so
    // that whoever changes it has to come back through this test, the README
    // limits, and ADR 0005 together.
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
        "this port still suppresses the mirror case. If that has changed, the README limits and"
            + " ADR 0005 both describe behaviour this repository no longer has, and parity/ahead/"
            + " now carries a finding the pinned reference does not. All findings: "
            + findings);

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
