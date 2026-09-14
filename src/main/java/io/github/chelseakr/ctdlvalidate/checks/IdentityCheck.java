package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.CodePointOrder;
import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.Session;
import io.github.chelseakr.ctdlvalidate.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Check 6: one identifier, one entity.
 *
 * <p>A CTDL payload may write the same {@code @id} on more than one node object: an entity is
 * declared at the top of the {@code @graph} and embedded again, inline and partially, where another
 * entity refers to it. The parser reads those as one entity, as a JSON-LD processor does. That
 * merge is invisible in the output unless something says it happened, and a merge nobody was told
 * about is worse than the first-declaration-wins reading it replaced: a reader would see a finding
 * against an entity whose type list no single line of their document contains.
 *
 * <p>So this check says it. It reports no defect, and never an ERROR.
 */
public final class IdentityCheck implements Check {

  @Override
  public List<Finding> run(Session session) {
    Graph graph = session.graph();
    Map<String, List<String>> repeated = graph.repeatedIds();
    List<String> identifiers = new ArrayList<>(repeated.keySet());
    identifiers.sort(CodePointOrder.COMPARATOR);
    List<Finding> findings = new ArrayList<>();
    for (String nodeId : identifiers) {
      // Walk order, not sorted: these are locations in the reader's file, and
      // `$.@graph[10]` sorts before `$.@graph[9]`.
      List<String> paths = repeated.get(nodeId);
      Graph.Node node = graph.byId().get(nodeId);
      String types = node.types().isEmpty() ? "no @type" : String.join(", ", node.types());
      findings.add(
          new Finding(
              "ID_DECLARED_MORE_THAN_ONCE",
              Severity.INFO,
              nodeId,
              "@id",
              nodeId,
              paths.size()
                  + " node objects declare this @id ("
                  + String.join(", ", paths)
                  + "). They were read as one entity, typed ["
                  + types
                  + "], and every check below judged that merged entity. If they were meant to be"
                  + " different resources, they need different identifiers.",
              Rules.REPEATED_ID_POLICY));
    }
    return findings;
  }
}
