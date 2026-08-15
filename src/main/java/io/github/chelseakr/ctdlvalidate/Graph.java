package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A CTDL JSON-LD document flattened into an indexable node set.
 *
 * @param nodes every entity in the payload, in document order, parents before the objects nested
 *     inside them
 * @param byId nodes reachable by {@code @id}; where an identifier is declared twice the first
 *     declaration wins, as it does in the reference implementation
 * @param byPath nodes reachable by their location in the document
 */
public record Graph(List<Node> nodes, Map<String, Node> byId, Map<String, Node> byPath) {

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

  /** Resolve a reference value to an in-payload node, or null when it does not resolve. */
  public Node resolve(Value value) {
    if (value instanceof Value.Nested nested) {
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
