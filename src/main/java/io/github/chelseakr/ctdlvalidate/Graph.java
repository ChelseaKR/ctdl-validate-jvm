package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A CTDL JSON-LD document flattened into an indexable node set.
 *
 * @param nodes every entity in the payload, in document order, parents before the objects nested
 *     inside them. A node object whose {@code @id} another one already declared is not a second
 *     entity: it is merged into the first, as JSON-LD reads it, and check 6 reports that it was.
 * @param byId nodes reachable by {@code @id}, one per identifier
 * @param byPath nodes reachable by their location in the document; every location a merged node was
 *     declared at reaches it
 * @param declarations identifier to every path that declared it, in walk order. An entry with more
 *     than one path is an identifier the document declared more than once.
 * @param envelopeId the {@code @id} of the {@code @graph} envelope itself, or null for the
 *     single-entity and bare-array shapes, which have none. It is not a node -- nothing is asserted
 *     about it -- but it is the one position in which a Registry graph URI appears in a published
 *     Registry document, so check 1 reads it from here.
 * @param envelopePath the JSON path of that identifier, or null
 */
public record Graph(
    List<Node> nodes,
    Map<String, Node> byId,
    Map<String, Node> byPath,
    Map<String, List<String>> declarations,
    String envelopeId,
    String envelopePath) {

  /** A graph with no repeated identifiers and no envelope. */
  public Graph(List<Node> nodes, Map<String, Node> byId, Map<String, Node> byPath) {
    this(nodes, byId, byPath, Map.of(), null, null);
  }

  /** One entity. */
  public record Node(
      String path, String nodeId, List<String> types, Map<String, List<Value>> props) {

    /** What findings about this node call it: its {@code @id}, or its path when it has none. */
    public String label() {
      return nodeId != null ? nodeId : path;
    }

    /**
     * Property names in code-point order. The checks walk properties in sorted order rather than
     * document order because the reference implementation does, and because it makes the order
     * findings are generated in independent of how the payload happened to be written.
     */
    public List<String> sortedPropertyNames() {
      List<String> names = new ArrayList<>(props.keySet());
      names.sort(CodePointOrder.COMPARATOR);
      return names;
    }

    /** The values of one property, or an empty list when the node does not carry it. */
    public List<Value> valuesOf(String property) {
      return props.getOrDefault(property, List.of());
    }
  }

  /** Identifiers declared by more than one node object, with their paths, in walk order. */
  public Map<String, List<String>> repeatedIds() {
    Map<String, List<String>> repeated = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : declarations.entrySet()) {
      if (entry.getValue().size() > 1) {
        repeated.put(entry.getKey(), entry.getValue());
      }
    }
    return Collections.unmodifiableMap(repeated);
  }

  /**
   * Resolve a reference value to an in-payload node, or null when it does not resolve.
   *
   * <p>A nested object that carries its own {@code @id} is, by JSON-LD's identity rule, the same
   * node as anything else in the payload with that {@code @id}. Since the parser merges repeated
   * identifiers, the path already reaches the same node; asking by identity first is the direct
   * statement of the rule, as it is in the reference.
   */
  public Node resolve(Value value) {
    if (value instanceof Value.Nested nested) {
      if (nested.targetId() != null && byId.containsKey(nested.targetId())) {
        return byId.get(nested.targetId());
      }
      return byPath.get(nested.targetPath());
    }
    if (value instanceof Value.Text text) {
      return byId.get(text.text());
    }
    return null;
  }

  /** The input is not a shape this tool knows how to read. */
  public static final class DocumentException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DocumentException(String message) {
      super(message);
    }
  }
}
