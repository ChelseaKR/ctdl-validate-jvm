package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The concept-range disposition, pinned to exactly the properties the evidence covers.
 *
 * <p>CTDL declares a reference to a term from one of its own concept schemes two incompatible ways:
 * across the vendored snapshot 46 properties range on {@code ceterms:CredentialAlignmentObject} and
 * 45 range on {@code skos:Concept}, with nothing about the value distinguishing the families. Three
 * concept schemes are named by different properties in each family, and a fourth by a single
 * property declaring both ranges at once. The published Registry corpus encodes both families as
 * {@code CredentialAlignmentObject}, so erroring on that reports Credential Engine's own dominant
 * encoding as a defect. These tests hold the INFO disposition to that evidence, in both directions.
 */
class ConceptRangeConflictTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String ALIGNMENT_VALUE =
      """
      {
        "@type": "ceterms:CredentialAlignmentObject",
        "ceterms:framework": "https://credreg.net/ctdl/terms/CreditUnit",
        "ceterms:targetNode": "creditUnit:SemesterHour"
      }
      """;

  /** A course whose credit value names a term from the CreditUnit scheme. */
  private static List<Finding> validateCreditUnitType(String unitType) throws IOException {
    return validate(
        """
        {
          "@graph": [
            {
              "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
              "@type": "ceterms:Course",
              "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
              "ceterms:creditValue": [
                {
                  "@type": "ceterms:ValueProfile",
                  "ceterms:creditUnitType": [VALUE],
                  "schema:value": 3.0
                }
              ]
            }
          ]
        }
        """
            .replace("VALUE", unitType));
  }

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

  private static List<Finding> withSeverity(List<Finding> findings, Severity severity) {
    List<Finding> matching = new ArrayList<>();
    for (Finding finding : findings) {
      if (finding.severity() == severity) {
        matching.add(finding);
      }
    }
    return matching;
  }

  private static Finding only(List<Finding> findings, String code) {
    List<Finding> matching = withCode(findings, code);
    assertEquals(1, matching.size(), "expected exactly one " + code + ", got " + matching);
    return matching.get(0);
  }

  @Test
  @DisplayName("an alignment object on a scheme-bound concept property is INFO, not an error")
  void alignmentObjectOnASchemeBoundConceptPropertyIsInfoNotError() throws IOException {
    List<Finding> findings = validateCreditUnitType(ALIGNMENT_VALUE);
    Finding finding = only(findings, "CONCEPT_RANGE_CONFLICT");
    assertEquals(Severity.INFO, finding.severity());
    assertEquals("ceterms:creditUnitType", finding.property());
    assertTrue(withSeverity(findings, Severity.ERROR).isEmpty(), "the disposition still errored");
  }

  @Test
  @DisplayName("the message names the property, the scheme, and both declarations")
  void theMessageNamesThePropertyTheSchemeAndTheDeclarations() throws IOException {
    Finding finding = only(validateCreditUnitType(ALIGNMENT_VALUE), "CONCEPT_RANGE_CONFLICT");
    assertTrue(finding.message().contains("ceterms:creditUnitType"), finding.message());
    assertTrue(finding.message().contains("ceterms:CreditUnit"), finding.message());
    assertTrue(finding.message().contains("ceterms:CredentialAlignmentObject"), finding.message());
    assertTrue(finding.rule().citation().contains("skos:Concept"), finding.rule().citation());
    assertTrue(finding.rule().citation().contains("meta:targetScheme"), finding.rule().citation());
    assertEquals("https://credreg.net/ctdl/schema/encoding/json", finding.rule().url());
    assertEquals(Rules.RETRIEVED, finding.rule().retrieved());
  }

  @Test
  @DisplayName("a sibling property over the same scheme is cited where one exists")
  void aSiblingOverTheSameSchemeIsCitedWhereOneExists() throws IOException {
    // ceterms:creditLevelType (skos:Concept) and ceterms:audienceLevelType
    // (CredentialAlignmentObject) both declare meta:targetScheme
    // ceterms:AudienceLevel. That pair is the sharpest evidence in the snapshot,
    // so the citation names it rather than arguing in the abstract.
    Finding finding =
        only(
            validate(
                """
                {
                  "@graph": [
                    {
                      "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                      "@type": "ceterms:Course",
                      "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                      "ceterms:creditValue": [
                        {
                          "@type": "ceterms:ValueProfile",
                          "ceterms:creditLevelType": [
                            {
                              "@type": "ceterms:CredentialAlignmentObject",
                              "ceterms:framework": "https://credreg.net/ctdl/terms/AudienceLevel",
                              "ceterms:targetNode": "audLevel:BeginnerLevel"
                            }
                          ],
                          "schema:value": 3.0
                        }
                      ]
                    }
                  ]
                }
                """),
            "CONCEPT_RANGE_CONFLICT");
    assertTrue(
        finding.rule().citation().contains("ceterms:audienceLevelType"), finding.rule().citation());
  }

  @Test
  @DisplayName("a property with no sibling argues from the snapshot as a whole")
  void aPropertyWithNoSiblingArguesFromTheSnapshotAsAWhole() throws IOException {
    // ceterms:CreditUnit is named by no alignment-ranged property, so there is
    // no pair to point at and the citation falls back to the corpus-wide claim.
    Finding finding = only(validateCreditUnitType(ALIGNMENT_VALUE), "CONCEPT_RANGE_CONFLICT");
    assertTrue(
        finding.rule().citation().contains("three concept schemes are named by properties in both"),
        finding.rule().citation());
  }

  @Test
  @DisplayName("an actual skos:Concept satisfies the declared range with no finding")
  void anActualSkosConceptSatisfiesTheDeclaredRange() throws IOException {
    List<Finding> findings =
        validate(
            """
            {
              "@graph": [
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "ceterms:creditValue": [
                    {
                      "@type": "ceterms:ValueProfile",
                      "ceterms:creditUnitType": [
                        "https://credentialengineregistry.org/resources/ce-1f0b7ff2-9e4c-4a19-9d02-6c1b0b3f3a11"
                      ],
                      "schema:value": 3.0
                    }
                  ]
                },
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-1f0b7ff2-9e4c-4a19-9d02-6c1b0b3f3a11",
                  "@type": "skos:Concept",
                  "ceterms:ctid": "ce-1f0b7ff2-9e4c-4a19-9d02-6c1b0b3f3a11"
                }
              ]
            }
            """);
    assertTrue(withCode(findings, "CONCEPT_RANGE_CONFLICT").isEmpty(), findings.toString());
    assertTrue(withCode(findings, "RANGE_VIOLATION").isEmpty(), findings.toString());
  }

  @Test
  @DisplayName("a wrong class on a scheme-bound property is still an error")
  void aWrongClassOnASchemeBoundPropertyIsStillAnError() throws IOException {
    // The disposition covers CredentialAlignmentObject and nothing else. An
    // Organization standing where a concept belongs is still a range error.
    List<Finding> findings =
        validate(
            """
            {
              "@graph": [
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "ceterms:creditValue": [
                    {
                      "@type": "ceterms:ValueProfile",
                      "ceterms:creditUnitType": [
                        "https://credentialengineregistry.org/resources/ce-79298677-d0e4-4799-853a-a633d9071826"
                      ],
                      "schema:value": 3.0
                    }
                  ]
                },
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-79298677-d0e4-4799-853a-a633d9071826",
                  "@type": "ceterms:CredentialOrganization",
                  "ceterms:ctid": "ce-79298677-d0e4-4799-853a-a633d9071826"
                }
              ]
            }
            """);
    Finding finding = only(findings, "RANGE_VIOLATION");
    assertEquals(Severity.ERROR, finding.severity());
    assertEquals("ceterms:creditUnitType", finding.property());
    assertTrue(withCode(findings, "CONCEPT_RANGE_CONFLICT").isEmpty(), findings.toString());
  }

  @Test
  @DisplayName("a skos-ranged property with no targetScheme is still an error")
  void aSkosRangedPropertyWithNoTargetSchemeIsStillAnError() throws IOException {
    // ceterms:classification ranges on skos:Concept but declares no
    // meta:targetScheme, so it is not one of the properties the evidence covers
    // and an alignment object there stays an ERROR. This is the guard against
    // the disposition quietly widening to every skos:Concept range.
    List<Finding> findings =
        validate(
            """
            {
              "@graph": [
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "ceterms:classification": [VALUE]
                }
              ]
            }
            """
                .replace("VALUE", ALIGNMENT_VALUE));
    Finding finding = only(findings, "RANGE_VIOLATION");
    assertEquals(Severity.ERROR, finding.severity());
    assertEquals("ceterms:classification", finding.property());
    assertTrue(withCode(findings, "CONCEPT_RANGE_CONFLICT").isEmpty(), findings.toString());
  }

  @Test
  @DisplayName("the conflict the disposition rests on is still in the snapshot")
  void theConflictTheDispositionRestsOnIsStillInTheSnapshot() {
    // If Credential Engine fixes the encoding, this test says so. The INFO
    // disposition is only defensible while CTDL really does declare one kind of
    // value two incompatible ways. Refreshing the vendored snapshot could settle
    // that, at which point a CONCEPT_RANGE_CONFLICT would be hiding a real error
    // and the disposition should be removed rather than left running on a
    // premise that has expired.
    SchemaIndex schema = SchemaLoader.load();
    Map<String, Set<String>> skosOnly = new TreeMap<>();
    Map<String, Set<String>> alignmentOnly = new TreeMap<>();
    Map<String, Set<String>> bothAtOnce = new TreeMap<>();
    for (SchemaIndex.PropertyDef prop : schema.properties().values()) {
      boolean rangesConcept = prop.range().contains(SchemaIndex.CONCEPT_RANGE_TERM);
      boolean rangesAlignment = prop.range().contains(SchemaIndex.ALIGNMENT_RANGE_TERM);
      for (String scheme : prop.targetScheme()) {
        Map<String, Set<String>> bucket =
            rangesConcept && rangesAlignment
                ? bothAtOnce
                : rangesConcept ? skosOnly : rangesAlignment ? alignmentOnly : null;
        if (bucket != null) {
          bucket.computeIfAbsent(scheme, key -> new TreeSet<>()).add(prop.term());
        }
      }
    }

    // Evidence 1: schemes where two *different* properties disagree on range.
    Set<String> contested = new TreeSet<>(skosOnly.keySet());
    contested.retainAll(alignmentOnly.keySet());
    assertEquals(
        Set.of("ceterms:AudienceLevel", "ceterms:CostType", "ceterms:ScheduleFrequency"),
        contested,
        "the two concept ranges no longer disagree: revisit CONCEPT_RANGE_CONFLICT");
    assertEquals(
        Set.of("ceasn:educationLevelType", "ceterms:creditLevelType"),
        skosOnly.get("ceterms:AudienceLevel"));
    assertEquals(Set.of("ceterms:audienceLevelType"), alignmentOnly.get("ceterms:AudienceLevel"));

    // Evidence 2: CTDL declares one property both ways at once, which is the
    // encoding itself saying the two ranges describe one kind of value.
    assertEquals(
        Map.of(
            "ceterms:InstructionalProgramClassification",
            Set.of("ceterms:instructionalProgramType")),
        bothAtOnce);

    // And the disposition stays narrow: it reaches scheme-bound properties only.
    List<String> covered = schema.schemeBoundConceptProperties();
    assertEquals(20, covered.size(), covered.toString());
    assertTrue(covered.contains("ceterms:creditUnitType"));
    assertFalse(covered.contains("ceterms:classification"), "no meta:targetScheme");
    assertFalse(covered.contains("skos:broader"), "no meta:targetScheme");
  }

  @Test
  @DisplayName("the two range families are both large enough for this to be a class, not a typo")
  void bothRangeFamiliesAreSubstantial() {
    SchemaIndex schema = SchemaLoader.load();
    int concept = 0;
    int alignment = 0;
    for (SchemaIndex.PropertyDef prop : schema.properties().values()) {
      if (prop.range().contains(SchemaIndex.CONCEPT_RANGE_TERM)) {
        concept++;
      }
      if (prop.range().contains(SchemaIndex.ALIGNMENT_RANGE_TERM)) {
        alignment++;
      }
    }
    assertEquals(45, concept, "properties ranging on skos:Concept");
    assertEquals(46, alignment, "properties ranging on ceterms:CredentialAlignmentObject");
  }

  @Test
  @DisplayName("the sibling citation truncates and says how many it left out")
  void theSiblingCitationTruncatesAndSaysHowManyItLeftOut() {
    // No property in the current snapshot has more than two siblings, so the
    // truncation is exercised directly rather than left as an untested branch
    // waiting on a schema release to reach it.
    Rule rule =
        Rules.conceptRangeConflict(
            "ceterms:creditUnitType",
            Set.of("ceterms:CreditUnit"),
            List.of("ceterms:a", "ceterms:b", "ceterms:c", "ceterms:d"));
    assertTrue(
        rule.citation().contains("ceterms:a, ceterms:b, ceterms:c, ... (4 properties total)"),
        rule.citation());
    assertFalse(rule.citation().contains("ceterms:d"), rule.citation());
  }

  @Test
  @DisplayName("siblings are derived from the snapshot, and are empty when there are none")
  void siblingsAreDerivedFromTheSnapshot() {
    SchemaIndex schema = SchemaLoader.load();
    assertEquals(List.of(), schema.alignmentRangedSiblings("ceterms:classification"));
    assertEquals(List.of(), schema.alignmentRangedSiblings("ceterms:notARealProperty"));
    assertEquals(
        List.of("ceterms:audienceLevelType"),
        schema.alignmentRangedSiblings("ceterms:creditLevelType"));
    assertEquals(
        List.of("ceterms:offerFrequencyType", "ceterms:scheduleFrequencyType"),
        schema.alignmentRangedSiblings("ceterms:paymentPatternType"));
  }

  @Test
  @DisplayName("meta:targetScheme is read off the snapshot, not written down here")
  void targetSchemeIsReadOffTheSnapshot() {
    SchemaIndex schema = SchemaLoader.load();
    int declared = 0;
    for (JsonNode entry : SchemaLoader.vendorGraph("ctdl/schema.json")) {
      if (entry.has("meta:targetScheme")) {
        declared++;
        String term = entry.get("@id").asText();
        assertFalse(
            schema.property(term).targetScheme().isEmpty(),
            term + " declares meta:targetScheme in the file but the index dropped it");
      }
    }
    assertTrue(declared > 0, "the vendored CTDL snapshot declares no meta:targetScheme at all");
  }
}
