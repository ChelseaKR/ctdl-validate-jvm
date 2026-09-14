package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The parser's identity merge and envelope, and the document scope the text report ends with.
 *
 * <p>Byte equality with the reference's main branch over the parity corpus is the stronger evidence
 * for all of this; see {@code tools/next_release_parity.py}. These pin the rules a fixture shows
 * only in combination.
 */
class MergeAndScopeTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String A =
      "https://credentialengineregistry.org/resources/ce-11111111-1111-4111-8111-111111111111";

  private static Graph parse(String json) throws IOException {
    return GraphParser.parse(MAPPER.readTree(json), SchemaLoader.load());
  }

  @Test
  @DisplayName("a single declaration keeps its own type order; a merge sorts the union")
  void typeOrder() throws IOException {
    Graph single =
        parse(
            "{\"@graph\": [{\"@id\": \""
                + A
                + "\", \"@type\": [\"ceterms:QualityAssuranceCredential\", \"ceterms:Credential\"]}]}");
    assertEquals(
        List.of("ceterms:QualityAssuranceCredential", "ceterms:Credential"),
        single.byId().get(A).types());

    Graph merged =
        parse(
            "{\"@graph\": [{\"@id\": \""
                + A
                + "\", \"@type\": \"ceterms:Place\"}, {\"@id\": \""
                + A
                + "\", \"@type\": \"ceterms:Organization\"}]}");
    assertEquals(List.of("ceterms:Organization", "ceterms:Place"), merged.byId().get(A).types());
    assertEquals(1, merged.nodes().size(), "one identifier, one node");
    assertEquals(List.of("$.@graph[0]", "$.@graph[1]"), merged.repeatedIds().get(A));

    assertEquals(
        List.of("b", "a"),
        GraphParser.mergedTypes(List.of("b", "a"), List.of("a")),
        "a second declaration adding nothing leaves the first's order alone");
    assertEquals(List.of("b"), GraphParser.mergedTypes(List.of("b"), List.of()));
  }

  @Test
  @DisplayName("a nested declaration points at the merged node by the first declaration's path")
  void nestedPointsAtTheFirstPath() throws IOException {
    Graph graph =
        parse(
            "{\"@graph\": [{\"@id\": \""
                + A
                + "\", \"@type\": \"ceterms:Place\"}, {\"@type\": \"ceterms:Organization\","
                + " \"ceterms:address\": {\"@id\": \""
                + A
                + "\", \"@type\": \"ceterms:Place\"}}]}");
    Graph.Node organization = graph.byPath().get("$.@graph[1]");
    Value.Nested address = (Value.Nested) organization.valuesOf("ceterms:address").get(0);
    assertEquals("$.@graph[0]", address.targetPath());
    assertSame(graph.byId().get(A), graph.byPath().get("$.@graph[1].ceterms:address[0]"));
    assertSame(graph.byId().get(A), graph.resolve(address));
  }

  @Test
  @DisplayName(
      "a merge keeps a value only if Python's == finds it absent; one declaration keeps repeats")
  void mergedValues() throws IOException {
    Graph graph =
        parse(
            "{\"@graph\": [{\"@id\": \""
                + A
                + "\", \"ceterms:keyword\": [1, 1, \"x\"]}, {\"@id\": \""
                + A
                + "\", \"ceterms:keyword\": [true, 1.0, \"y\"],"
                + " \"https://purl.org/ctdl/terms/keyword\": [\"x\", \"z\"]}]}");
    List<Value> values = graph.byId().get(A).valuesOf("ceterms:keyword");
    assertEquals(5, values.size(), values.toString());
    assertEquals(new Value.Text("y"), values.get(3));
    assertEquals(new Value.Text("z"), values.get(4));
  }

  @Test
  @DisplayName("the envelope's @id is kept for the @graph shape only, and only when it is a string")
  void envelope() throws IOException {
    Graph graph =
        parse("{\"@id\": \"https://credentialengineregistry.org/graph/x\", \"@graph\": []}");
    assertEquals("https://credentialengineregistry.org/graph/x", graph.envelopeId());
    assertEquals("$.@id", graph.envelopePath());
    assertNull(parse("{\"@id\": 3, \"@graph\": []}").envelopeId());
    assertNull(parse("{\"@id\": \"" + A + "\"}").envelopeId(), "a single entity has no envelope");
    assertNull(parse("[{\"@id\": \"" + A + "\"}]").envelopeId());
  }

  @Test
  @DisplayName("a document with nothing CTDL in it says so, in the singular and the plural")
  void nothingChecked() throws IOException {
    DocumentScope empty = DocumentScope.of(parse("{\"@graph\": []}"));
    assertEquals(new DocumentScope(0, 0), empty);
    assertTrue(
        empty.nothingCheckedSentence().startsWith("Nothing here was checked: 0 entities read"));

    DocumentScope one = DocumentScope.of(parse("{\"@type\": \"Person\", \"name\": \"x\"}"));
    assertEquals(new DocumentScope(1, 0), one);
    assertTrue(one.nothingCheckedSentence().startsWith("Nothing here was checked: 1 entity read,"));
    assertTrue(Report.text(List.of(), one).endsWith(one.nothingCheckedSentence()));
    assertEquals(
        "0 finding(s): 0 ERROR, 0 WARNING, 0 INFO, 0 UNVERIFIABLE",
        Report.text(List.of()),
        "no document measured, no sentence: null is not zero");
  }

  @Test
  @DisplayName("a checked type, a checked property, or a nested entity puts a node in scope")
  void whatCountsAsChecked() throws IOException {
    assertEquals(
        new DocumentScope(1, 1), DocumentScope.of(parse("{\"@type\": \"ceterms:Certificate\"}")));
    assertEquals(
        new DocumentScope(1, 1), DocumentScope.of(parse("{\"ceasn:competencyText\": \"x\"}")));
    DocumentScope nested =
        DocumentScope.of(
            parse("{\"@type\": \"Thing\", \"ceterms:address\": {\"@type\": \"ceterms:Place\"}}"));
    assertEquals(new DocumentScope(2, 2), nested);
    assertNull(nested.nothingCheckedSentence());
  }
}
