package io.github.chelseakr.ctdlvalidate;

/**
 * How much of a parsed document this tool has jurisdiction over.
 *
 * <p>A run over a file that is not CTDL at all used to be byte-identical to a run over a clean CTDL
 * payload: no findings, exit 0. The reference now says so in words, in the text report, whenever
 * nothing it read declared a {@code ceterms:} or {@code ceasn:} term, and this port says the same
 * sentence. Without it a wide file pattern in a pre-commit hook is a gate that cannot fail.
 *
 * @param entities every node the parser read, nested ones included
 * @param checkedEntities the nodes carrying at least one type or property in a namespace this tool
 *     judges
 */
public record DocumentScope(int entities, int checkedEntities) {

  /** Measured over a parsed graph, the way the reference's {@code scope_of} measures it. */
  public static DocumentScope of(Graph graph) {
    int checked = 0;
    for (Graph.Node node : graph.nodes()) {
      if (declaresACheckedTerm(node)) {
        checked++;
      }
    }
    return new DocumentScope(graph.nodes().size(), checked);
  }

  private static boolean declaresACheckedTerm(Graph.Node node) {
    for (String type : node.types()) {
      if (SchemaIndex.isCheckedTerm(type)) {
        return true;
      }
    }
    for (String prop : node.props().keySet()) {
      if (SchemaIndex.isCheckedTerm(prop)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The sentence the text report ends with when nothing was checked, or null when something was.
   */
  public String nothingCheckedSentence() {
    if (checkedEntities != 0) {
      return null;
    }
    return "Nothing here was checked: "
        + entities
        + (entities == 1 ? " entity" : " entities")
        + " read, none declaring a ceterms: or ceasn: term. This is not a clean CTDL payload; it"
        + " is a document this tool has nothing to say about.";
  }
}
