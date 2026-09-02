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
 * Check 5: inverse consistency, for the representation the schema-and-range checks already treat as
 * equivalent to a bare reference -- an inline object that carries its own {@code @id} -- but which
 * {@code InversesCheck} used to compare only by string.
 */
class InversesCheckTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  @Test
  @DisplayName("a back-reference written as a nested object with an @id is not an inverse mismatch")
  void aNestedBackReferenceWithAnIdSatisfiesTheInverse() throws IOException {
    // Course A embeds a fuller, inline description of Course B under
    // hasPart (rather than the bare-IRI form) but still gives it B's real
    // @id. Course B separately, and correctly, asserts isPartOf back at A
    // with a bare IRI. Both directions genuinely agree; this must produce no
    // findings for the pair, not a false ERROR.
    List<Finding> findings =
        validate(
            """
            {
              "@graph": [
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "ceterms:hasPart": [
                    {
                      "@id": "https://credentialengineregistry.org/resources/ce-d9a8ddae-ea6e-4f69-9c36-3f51a3104a0e",
                      "@type": "ceterms:Course",
                      "ceterms:name": { "en-US": "Inline but identified copy of Course B" }
                    }
                  ]
                },
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-d9a8ddae-ea6e-4f69-9c36-3f51a3104a0e",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-d9a8ddae-ea6e-4f69-9c36-3f51a3104a0e",
                  "ceterms:isPartOf": [
                    "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344"
                  ]
                }
              ]
            }
            """);
    assertTrue(
        withCode(findings, "INVERSE_MISMATCH").isEmpty(),
        "a nested-but-identified back-reference should not read as a mismatch: " + findings);
  }

  @Test
  @DisplayName("a nested object whose @id points at something else is still a real mismatch")
  void aNestedReferenceToTheWrongIdIsStillAMismatch() throws IOException {
    // Course B's inverse points back at a different course entirely (still
    // written as a nested object with its own @id), so this must still be
    // reported.
    List<Finding> findings =
        validate(
            """
            {
              "@graph": [
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
                  "ceterms:hasPart": [
                    "https://credentialengineregistry.org/resources/ce-d9a8ddae-ea6e-4f69-9c36-3f51a3104a0e"
                  ]
                },
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-d9a8ddae-ea6e-4f69-9c36-3f51a3104a0e",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-d9a8ddae-ea6e-4f69-9c36-3f51a3104a0e",
                  "ceterms:isPartOf": [
                    {
                      "@id": "https://credentialengineregistry.org/resources/ce-79298677-d0e4-4799-853a-a633d9071826",
                      "@type": "ceterms:Course",
                      "ceterms:name": { "en-US": "A different course" }
                    }
                  ]
                },
                {
                  "@id": "https://credentialengineregistry.org/resources/ce-79298677-d0e4-4799-853a-a633d9071826",
                  "@type": "ceterms:Course",
                  "ceterms:ctid": "ce-79298677-d0e4-4799-853a-a633d9071826"
                }
              ]
            }
            """);
    // The finding is raised from Course A's side: A asserts hasPart at B, B
    // asserts isPartOf at a different course, so A's assertion has no match
    // pointing back at it.
    List<Finding> mismatches = withCode(findings, "INVERSE_MISMATCH");
    assertEquals(1, mismatches.size(), findings.toString());
    assertEquals(
        "https://credentialengineregistry.org/resources/ce-59e8d15f-7895-4346-a5a8-7a0739a3d344",
        mismatches.get(0).entity());
  }
}
