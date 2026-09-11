package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Session;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Supplied;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Check 3: reference resolution across this run's effective data model.
 *
 * <p>A blank node reference that its own payload does not define is an ERROR: blank node
 * identifiers have no meaning outside the graph that declares them.
 *
 * <p>An absolute IRI is judged against everything the run has in hand. Resolving inside the payload
 * is silent, and check 4 goes on to judge the target's class. Resolving inside a document supplied
 * with {@code --resolve} is an INFO note naming the file, because every claim check 4 then makes
 * about that target rests on that file having been supplied. Resolving nowhere is UNVERIFIABLE, not
 * a failure: the entity may exist in the Registry or elsewhere, and this tool fetches nothing at
 * validation time.
 */
public final class ReferencesCheck implements Check {

  @Override
  public List<Finding> run(Session session) {
    Graph graph = session.graph();
    SchemaIndex schema = session.schema();
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
            findings.add(absoluteIriFinding(entity, prop, raw, session));
          }
          // Non-IRI strings are already reported by the identifier-kind check.
        }
      }
    }
    return findings;
  }

  /** What to say about an IRI the payload itself does not define. */
  private static Finding absoluteIriFinding(
      String entity, String prop, String value, Session session) {
    Supplied supplied = session.supplied();
    Supplied.Entity external = supplied.entities().get(value);
    if (external != null) {
      List<String> declared = session.schema().knownTypes(external.types());
      String asWhat =
          declared.isEmpty()
              ? "carrying no class this schema snapshot declares"
              : "typed [" + String.join(", ", declared) + "]";
      return new Finding(
          "REF_RESOLVED_SUPPLIED",
          Severity.INFO,
          entity,
          prop,
          value,
          "Reference resolves in "
              + external.source()
              + ", supplied with --resolve, "
              + asWhat
              + ". Anything this report says about the referenced entity rests on that document.",
          Rules.RESOLUTION_POLICY);
    }
    // Supplying a directory that holds no document is still supplying nothing,
    // so the sentence stays the one telling a reader to pass the document, exactly
    // as the reference words it.
    String detail =
        supplied.anyDocuments()
            ? " " + supplied.shortfall()
            : " Pass it with --resolve to settle this.";
    return new Finding(
        "REF_OUTSIDE_PAYLOAD",
        Severity.UNVERIFIABLE,
        entity,
        prop,
        value,
        "Reference does not resolve inside this payload. It may exist in the Registry or"
            + " elsewhere; without fetching it, its existence and class cannot be confirmed or"
            + " denied."
            + detail,
        Rules.NO_NETWORK_POLICY);
  }
}
