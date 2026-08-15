package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
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

  @Test
  @DisplayName("every vendored file matches the hash SOURCES.md records for it")
  void hashesMatchSources() throws IOException, NoSuchAlgorithmException {
    String sources =
        Files.readString(
            ROOT.resolve("src/main/resources/vendor/SOURCES.md"), StandardCharsets.UTF_8);
    for (String relative : VENDORED) {
      String digest = sha256(relative);
      assertTrue(
          sources.contains(digest),
          () -> "SOURCES.md records no entry with the current hash of " + relative + ": " + digest);
      assertTrue(
          sources.contains("`" + relative + "`"), () -> "SOURCES.md does not name " + relative);
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
