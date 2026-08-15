package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Check 3: reference resolution within the payload.
 *
 * <p>A blank node reference that its own payload does not define is an ERROR: blank node
 * identifiers have no meaning outside the graph that declares them. An absolute IRI that does not
 * resolve inside the payload is UNVERIFIABLE, not a failure: the entity may exist in the Registry
 * or elsewhere, and this tool does not fetch anything at validation time.
 */
public final class ReferencesCheck implements Check {

  @Override
  public List<Finding> run(Graph graph, SchemaIndex schema) {
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : graph.nodes()) {
      String entity = node.label();
      for (String prop : node.sortedPropertyNames()) {
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        if (propDef == null || !propDef.idCoerced() || !propDef.rangeHasEntities()) {
          continue;
        }
        for (Value value : node.valuesOf(prop)) {
          // Inline containment is trivially resolved, so nested values are skipped.
          if (!(value instanceof Value.Text text) || graph.resolve(value) != null) {
            continue;
          }
          String raw = text.text();
          if (raw.startsWith("_:")) {
            findings.add(
                new Finding(
                    "REF_UNRESOLVED_BNODE",
                    Severity.ERROR,
                    entity,
                    prop,
                    raw,
                    "Blank node reference is not defined anywhere in this payload. A blank node"
                        + " identifier only has meaning inside the graph that declares it, so this"
                        + " reference cannot identify anything.",
                    Rules.BNODE_SCOPE));
          } else if (raw.contains(":")) {
            findings.add(
                new Finding(
                    "REF_OUTSIDE_PAYLOAD",
                    Severity.UNVERIFIABLE,
                    entity,
                    prop,
                    raw,
                    "Reference does not resolve inside this payload. It may exist in the Registry"
                        + " or elsewhere; without fetching it, its existence and class cannot be"
                        + " confirmed or denied.",
                    Rules.NO_NETWORK_POLICY));
          }
          // Non-IRI strings are already reported by the identifier-kind check.
        }
      }
    }
    return findings;
  }
}
