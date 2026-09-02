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
 *     declaration wins, as it does in the reference implementation. "First" is a function of walk
 *     order — depth-first into an earlier entity's inline objects before the next top-level entry —
 *     so which declaration a bare-IRI reference resolves to depends on where in the document a
 *     same-{@code @id} stub happens to be written. {@link #declarationsOf} is how a check asks the
 *     question the document actually answers: what does this payload declare about this identifier,
 *     all of it, rather than whichever declaration was reached first.
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

  /**
   * Every node in the payload declaring this {@code @id}, in document order.
   *
   * <p>{@link #byId} keeps one of them and drops the rest, which is fine for asking "does this
   * reference resolve at all" and wrong for asking "what class is it". A document that declares the
   * same {@code @id} twice asserts both declarations; keeping only the first makes the answer a
   * function of walk order rather than of the document.
   *
   * @param nodeId the identifier to look up, or null
   * @return the declarations, in document order; empty when the id is null or absent
   */
  public List<Node> declarationsOf(String nodeId) {
    if (nodeId == null) {
      return List.of();
    }
    List<Node> declarations = new ArrayList<>(1);
    for (Node node : nodes) {
      if (nodeId.equals(node.nodeId())) {
        declarations.add(node);
      }
    }
    return List.copyOf(declarations);
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
