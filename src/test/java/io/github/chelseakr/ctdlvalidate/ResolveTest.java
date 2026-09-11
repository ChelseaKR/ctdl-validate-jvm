package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code --resolve}: the side index of supplied documents, held to the properties the reference's
 * {@code session.py} states and its {@code tests/test_resolve.py} asserts.
 *
 * <p>Byte equality with the reference over the three resolve fixtures is {@code ParityTest}'s job,
 * and it is the stronger evidence. What is here are the properties a fixture cannot show on its
 * own: that a supplied document's defects never reach the report, that a path is spelled the way
 * Python spells it, and that a document which cannot be read stops the run.
 */
class ResolveTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String OWNER =
      "https://credentialengineregistry.org/resources/ce-79298677-d0e4-4799-853a-a633d9071826";

  private static JsonNode fixture(String name) throws IOException {
    return MAPPER.readTree(ROOT.resolve("parity/fixtures").resolve(name).toFile());
  }

  private static Set<String> codes(List<Finding> findings) {
    Set<String> codes = new TreeSet<>();
    findings.forEach(finding -> codes.add(finding.code()));
    return codes;
  }

  private static String owner(String type) {
    return "{\"@graph\": [{\"@id\": \"" + OWNER + "\", \"@type\": \"" + type + "\"}]}";
  }

  /** A certificate whose only reference is to {@link #OWNER}. */
  private static JsonNode certificateOwnedBy() throws IOException {
    return MAPPER.readTree(
        "{\"@graph\": [{\"@id\": \"https://credentialengineregistry.org/resources/"
            + "ce-5e3de882-3b49-421b-b623-695c63587f4f\", \"@type\": \"ceterms:Certificate\","
            + " \"ceterms:ownedBy\": [\""
            + OWNER
            + "\"]}]}");
  }

  @Test
  @DisplayName("a supplied document's own defects never reach the report")
  void suppliedDocumentsAreNeverValidated() throws IOException {
    // The positive control first: the supplied document really is defective, so
    // its absence from the report below is the index declining to validate it
    // rather than there being nothing to find.
    Path supplied = ROOT.resolve("parity/resolve/resolved_references/a_organizations.json");
    Set<String> alone = codes(Validator.validate(MAPPER.readTree(supplied.toFile())));
    assertTrue(alone.contains("CTID_BARE_UUID"), "the supplied fixture must itself be defective");

    Session session =
        Validator.session(
            fixture("resolved_references.json"),
            List.of("parity/resolve/resolved_references"),
            ROOT);
    Set<String> resolved = codes(Validator.validate(session));
    assertFalse(resolved.contains("CTID_BARE_UUID"), resolved.toString());
    assertTrue(resolved.contains("REF_RESOLVED_SUPPLIED"), resolved.toString());
    assertEquals(
        session.graph().nodes().size(),
        Validator.session(fixture("resolved_references.json"), List.of(), ROOT)
            .graph()
            .nodes()
            .size(),
        "supplying documents changed how many entities the report is about");
  }

  @Test
  @DisplayName("an unresolved reference stays UNVERIFIABLE when documents were supplied")
  void resolutionNeverTurnsANonAnswerIntoAFailure(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("unrelated.json"), "{\"@graph\": []}");
    List<Finding> findings =
        Validator.validate(Validator.session(certificateOwnedBy(), List.of("."), directory));
    assertEquals(Set.of("REF_OUTSIDE_PAYLOAD"), codes(findings));
    assertEquals(Severity.UNVERIFIABLE, findings.get(0).severity());
    assertTrue(
        findings
            .get(0)
            .message()
            .endsWith(
                " None of the 1 document supplied with --resolve"
                    + " (unrelated.json) declares it."),
        findings.get(0).message());
  }

  @Test
  @DisplayName("the same reference is an ERROR once its supplied target is the wrong class")
  void aSuppliedTargetIsJudgedAgainstTheRange(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("owner.json"), owner("ceterms:Certification"));
    List<Finding> findings =
        Validator.validate(
            Validator.session(certificateOwnedBy(), List.of("owner.json"), directory));
    assertEquals(Set.of("RANGE_VIOLATION", "REF_RESOLVED_SUPPLIED"), codes(findings));
    Finding violation =
        findings.stream().filter(f -> "RANGE_VIOLATION".equals(f.code())).findFirst().orElseThrow();
    assertTrue(
        violation
            .message()
            .endsWith(" That entity was read from owner.json, supplied with --resolve."),
        violation.message());
  }

  @Test
  @DisplayName("the first document in the order read wins, and blank nodes are not indexed")
  void theIndexIsDeterministicAndSkipsBlankNodes(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("b.json"), owner("ceterms:Certification"));
    Files.writeString(directory.resolve("a.json"), owner("ceterms:CredentialOrganization"));
    Files.writeString(
        directory.resolve("c.json"),
        "{\"@graph\": [{\"@id\": \"_:b0\", \"@type\": \"ceterms:CredentialOrganization\"}]}");
    Supplied supplied = Supplied.build(List.of("."), directory, SchemaLoader.load());
    assertEquals(List.of("a.json", "b.json", "c.json"), supplied.documents());
    assertEquals(Set.of(OWNER), supplied.entities().keySet());
    assertEquals("a.json", supplied.entities().get(OWNER).source());
    assertEquals(List.of("ceterms:CredentialOrganization"), supplied.entities().get(OWNER).types());
  }

  @Test
  @DisplayName("a directory is read one level deep, .json files only, in code-point order")
  void aDirectoryIsReadOneLevelDeep(@TempDir Path directory) throws IOException {
    Path documents = Files.createDirectory(directory.resolve("docs"));
    for (String name : List.of("b.json", "a.json", ".json", "c.jsonld", "d.JSON")) {
      Files.writeString(documents.resolve(name), "{\"@graph\": []}");
    }
    Files.createDirectory(documents.resolve("nested"));
    Files.writeString(documents.resolve("nested/e.json"), "{\"@graph\": []}");
    assertEquals(
        List.of("docs/a.json", "docs/b.json"),
        Supplied.collectPaths(List.of("docs"), directory),
        "a dotfile named .json has no suffix in pathlib, and a directory is not descended into");
    assertEquals(
        List.of("docs/c.jsonld"),
        Supplied.collectPaths(List.of("./docs//c.jsonld"), directory),
        "a file named directly is taken whatever it is called, spelled as pathlib spells it");
  }

  @Test
  @DisplayName("paths are spelled the way pathlib spells them, because findings print them")
  void pathsAreSpelledTheWayPythonSpellsThem() {
    assertEquals(".", Supplied.pythonPath(""));
    assertEquals(".", Supplied.pythonPath("."));
    assertEquals(".", Supplied.pythonPath("./"));
    assertEquals("a.json", Supplied.pythonPath("./a.json"));
    assertEquals("a/b", Supplied.pythonPath("a//b/"));
    assertEquals("a/b", Supplied.pythonPath("a/./b"));
    assertEquals("a/../b", Supplied.pythonPath("a/../b"));
    assertEquals("/a", Supplied.pythonPath("/a"));
    assertEquals("//a", Supplied.pythonPath("//a"));
    assertEquals("/a", Supplied.pythonPath("///a"));
    assertEquals("/", Supplied.pythonPath("/"));

    assertEquals(".json", Supplied.suffix("a.json"));
    assertEquals(".json", Supplied.suffix("a.b.json"));
    assertEquals("", Supplied.suffix(".json"));
    assertEquals("", Supplied.suffix("a."));
    assertEquals("", Supplied.suffix("a"));
  }

  @Test
  @DisplayName("a supplied document that cannot be read is a hard stop, not a skipped file")
  void anUnreadableSuppliedDocumentStopsTheRun(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("broken.json"), "{ not json");
    Files.writeString(directory.resolve("scalar.json"), "3");
    JsonNode payload = certificateOwnedBy();
    Graph.DocumentException missing =
        assertThrows(
            Graph.DocumentException.class,
            () -> Validator.session(payload, List.of("absent.json"), directory));
    assertTrue(missing.getMessage().startsWith("cannot read absent.json: "), missing.getMessage());
    Graph.DocumentException broken =
        assertThrows(
            Graph.DocumentException.class,
            () -> Validator.session(payload, List.of("broken.json"), directory));
    assertTrue(
        broken.getMessage().startsWith("broken.json is not valid JSON: "), broken.getMessage());
    Graph.DocumentException shape =
        assertThrows(
            Graph.DocumentException.class,
            () -> Validator.session(payload, List.of("scalar.json"), directory));
    assertTrue(shape.getMessage().startsWith("expected a JSON-LD object"), shape.getMessage());
  }

  @Test
  @DisplayName("the payload is read before anything supplied, so its own shape error comes first")
  void thePayloadIsParsedFirst() throws IOException {
    JsonNode scalar = MAPPER.readTree("3");
    Graph.DocumentException error =
        assertThrows(
            Graph.DocumentException.class,
            () -> Validator.session(scalar, List.of("absent.json"), ROOT));
    assertTrue(error.getMessage().startsWith("expected a JSON-LD object"), error.getMessage());
  }

  @Test
  @DisplayName("only a string can name a supplied entity, and nothing supplied is nothing")
  void onlyAStringNamesASuppliedEntity(@TempDir Path directory) throws IOException {
    Files.writeString(directory.resolve("owner.json"), owner("ceterms:CredentialOrganization"));
    Supplied supplied = Supplied.build(List.of("owner.json"), directory, SchemaLoader.load());
    assertNotNull(supplied.get(new Value.Text(OWNER)));
    assertNull(supplied.get(new Value.Nested("$.x", OWNER)));
    assertNull(supplied.get(new Value.Json(MAPPER.readTree("3"))));
    assertFalse(Supplied.none().anyDocuments());
    assertTrue(supplied.anyDocuments());
    assertEquals(
        "None of the 1 document supplied with --resolve (owner.json) declares it.",
        supplied.shortfall());
  }

  @Test
  @DisplayName("resolution is deterministic")
  void resolutionIsDeterministic() throws IOException {
    JsonNode payload = fixture("resolved_references.json");
    List<String> resolve = List.of("parity/resolve/resolved_references");
    assertEquals(
        ParityDocument.render(payload, resolve, ROOT),
        ParityDocument.render(payload, resolve, ROOT));
    assertTrue(
        Files.readString(
                ROOT.resolve("parity/resolve/resolved_nothing_supplied/holds_no_json.txt"),
                StandardCharsets.UTF_8)
            .contains("--resolve"),
        "the note explaining the empty directory has gone missing");
  }
}
