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
 * Check 5: inverse consistency.
 *
 * <p>Only pairs the schema itself declares with {@code owl:inverseOf} are checked. Where both
 * directions are present between two in-payload entities, they must agree (ERROR when they do not).
 * Where only one direction is present, that is INFO, not an error: publishing one direction is
 * normal.
 */
public final class InversesCheck implements Check {

  @Override
  public List<Finding> run(Graph graph, SchemaIndex schema) {
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : graph.nodes()) {
      if (node.nodeId() == null) {
        continue; // an anonymous node cannot be referenced back
      }
      for (String prop : node.sortedPropertyNames()) {
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        if (propDef == null || propDef.inverse() == null) {
          continue;
        }
        String inverse = propDef.inverse();
        for (Value value : node.valuesOf(prop)) {
          if (value instanceof Value.Json) {
            continue;
          }
          Graph.Node target = graph.resolve(value);
          if (target == null) {
            continue; // outside the payload: check 3 reports it
          }
          String valueText = value instanceof Value.Text text ? text.text() : target.label();
          if (!target.props().containsKey(inverse)) {
            findings.add(
                new Finding(
                    "INVERSE_ONE_DIRECTION",
                    Severity.INFO,
                    node.label(),
                    prop,
                    valueText,
                    node.label()
                        + " asserts "
                        + prop
                        + " but "
                        + target.label()
                        + " does not assert the declared inverse "
                        + inverse
                        + ". One direction alone is not an error.",
                    Rules.inverse(prop, inverse)));
          } else if (!pointsBackAt(target, inverse, node.nodeId())) {
            findings.add(
                new Finding(
                    "INVERSE_MISMATCH",
                    Severity.ERROR,
                    node.label(),
                    prop,
                    valueText,
                    node.label()
                        + " asserts "
                        + prop
                        + " "
                        + valueText
                        + ", but "
                        + target.label()
                        + " asserts "
                        + inverse
                        + " without pointing back at "
                        + node.nodeId()
                        + ". Both directions are present and they disagree.",
                    Rules.inverse(prop, inverse)));
          }
        }
      }
    }
    return findings;
  }

  private static boolean pointsBackAt(Graph.Node target, String inverse, String nodeId) {
    for (Value value : target.valuesOf(inverse)) {
      if (value instanceof Value.Text text && text.text().equals(nodeId)) {
        return true;
      }
      // An inline (nested) object still points back when its own @id is the
      // node being checked -- it is a fuller description of the same
      // reference, not the absence of one. Only a bare-string assertion was
      // recognised before, so a real back-reference written as a nested
      // object with an @id read as a mismatch instead of agreement.
      if (value instanceof Value.Nested nested && nodeId.equals(nested.targetId())) {
        return true;
      }
    }
    return false;
  }
}
