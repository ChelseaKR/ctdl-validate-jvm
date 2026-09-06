package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The vendored schema and context files are the only source of domain, range, inverse, and
 * identifier-coercion rules in this tool. If they drift, the rules drift, so their hashes are part
 * of the build.
 *
 * <p>The hashes checked here are the ones recorded in the reference implementation's SOURCES.md,
 * unchanged. The two repositories therefore validate against the same bytes, which is a
 * precondition for the parity suite meaning anything at all.
 */
class VendorIntegrityTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));

  private static final List<String> VENDORED =
      List.of(
          "ctdl/schema.json", "ctdl/context.json", "ctdlasn/schema.json", "ctdlasn/context.json");

  /**
   * One row of SOURCES.md's table: the file, its source URL, and the hash recorded for that file.
   */
  private static final Pattern SOURCES_ROW =
      Pattern.compile(
          "^\\|\\s*`([^`]+)`\\s*\\|[^|]*\\|\\s*`([0-9a-f]{64})`\\s*\\|\\s*$",
          Pattern.MULTILINE);

  private static Map<String, String> recordedHashes() throws IOException {
    String sources =
        Files.readString(
            ROOT.resolve("src/main/resources/vendor/SOURCES.md"), StandardCharsets.UTF_8);
    Map<String, String> recorded = new LinkedHashMap<>();
    Matcher row = SOURCES_ROW.matcher(sources);
    while (row.find()) {
      recorded.put(row.group(1), row.group(2));
    }
    return recorded;
  }

  @Test
  @DisplayName("every vendored file matches the hash SOURCES.md records for it")
  void hashesMatchSources() throws IOException, NoSuchAlgorithmException {
    // This used to ask whether each hash appeared *somewhere* in SOURCES.md, and
    // separately whether the file was named *somewhere*, which is not the same
    // question as whether the table pairs them. Transposing two vendored files
    // left both hashes and both names present and the gate green, on a build
    // reading different bytes than the table says it reads. SOURCES.md itself
    // promises this test "recomputes all four hashes off the classpath and
    // checks them against this table"; now it does.
    Map<String, String> recorded = recordedHashes();
    assertEquals(
        Set.copyOf(VENDORED),
        recorded.keySet(),
        "SOURCES.md's table and the vendored file list disagree about which files are carried");
    for (String relative : VENDORED) {
      assertEquals(
          recorded.get(relative),
          sha256(relative),
          () -> "the vendored " + relative + " is not the file SOURCES.md records in its row");
    }
  }

  @Test
  @DisplayName("transposing two vendored files would be caught")
  void transpositionWouldBeCaught() throws IOException, NoSuchAlgorithmException {
    // Pairing a file with a hash only means something if the pairs differ. If
    // two rows ever recorded the same digest, swapping those files would again
    // be invisible, so this holds the property the check above depends on.
    Map<String, String> recorded = recordedHashes();
    for (String held : VENDORED) {
      for (String other : VENDORED) {
        if (!held.equals(other)) {
          assertNotEquals(
              recorded.get(held),
              sha256(other),
              () ->
                  "SOURCES.md records the same hash for "
                      + held
                      + " and "
                      + other
                      + ", so transposing them would not be detected");
        }
      }
    }
  }

  @Test
  @DisplayName("the vendored files load off the classpath and index into rules")
  void schemaLoadsFromTheClasspath() {
    SchemaIndex schema = SchemaLoader.load();
    assertTrue(schema.classes().size() > 100, "too few classes indexed");
    assertTrue(schema.properties().size() > 400, "too few properties indexed");

    // Spot checks against declarations in the vendored files, not against memory.
    SchemaIndex.PropertyDef ownedBy = schema.property("ceterms:ownedBy");
    assertTrue(ownedBy.idCoerced(), "ceterms:ownedBy is declared {\"@type\": \"@id\"}");
    assertTrue(ownedBy.rangeHasEntities(), "ceterms:ownedBy has an entity range");
    assertEquals("ceasn:hasChild", schema.property("ceasn:isChildOf").inverse());
    assertTrue(schema.property("ceterms:name").languageMap(), "ceterms:name is a language map");

    // Full IRIs compact through the prefix table the contexts declare.
    assertEquals("ceterms:name", schema.compactIri("https://purl.org/ctdl/terms/name"));
    assertEquals("ceasn:isPartOf", schema.compactIri("https://purl.org/ctdlasn/terms/isPartOf"));
    assertEquals("ceterms:Course", schema.compactIri("ceterms:Course"));

    // Subclass closure comes from rdfs:subClassOf in the encodings.
    assertTrue(
        schema.ancestorsOf("ceterms:CredentialOrganization").contains("ceterms:Organization"),
        "CredentialOrganization is declared a subclass of Organization");
  }

  private static String sha256(String relative) throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream stream = SchemaLoader.class.getResourceAsStream("/vendor/" + relative)) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = stream.read(buffer)) > 0) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
