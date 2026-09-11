package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Session;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Check 9: terms the published encoding marks unstable, disclosed.
 *
 * <p>Both vendored encodings carry {@code vs:term_status} on the terms they declare, and a payload
 * could be built entirely from terms the vocabulary itself flags as not settled without anything
 * saying so. What this check must not do is say what that means: the vendored files carry no prose
 * defining {@code vs:unstable}, so the finding states the declaration and stops. INFO, for the same
 * reason. Every term a payload names is covered, in whichever role it appears: as a class on {@code
 * @type}, as a property key, or as a concept a scheme-bound property points at.
 */
public final class TermStatusCheck implements Check {

  @Override
  public List<Finding> run(Session session) {
    SchemaIndex schema = session.schema();
    Graph graph = session.graph();
    Set<String> unstable = schema.unstable();
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : graph.nodes()) {
      for (String declared : node.types()) {
        if (unstable.contains(declared)) {
          findings.add(finding(node.label(), "@type", declared, "as a class on @type"));
        }
      }
      for (Map.Entry<String, List<Value>> entry : node.props().entrySet()) {
        String prop = entry.getKey();
        if (unstable.contains(prop)) {
          findings.add(finding(node.label(), prop, prop, "as a property"));
        }
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        if (propDef == null || propDef.targetScheme().isEmpty()) {
          continue;
        }
        for (Value value : entry.getValue()) {
          for (String term : ConceptSchemeCheck.terms(value, graph).terms()) {
            if (unstable.contains(term)) {
              findings.add(finding(node.label(), prop, term, "as a concept value"));
            }
          }
        }
      }
    }
    return findings;
  }

  private static Finding finding(String entity, String prop, String term, String role) {
    return new Finding(
        "TERM_UNSTABLE",
        Severity.INFO,
        entity,
        prop,
        term,
        "The published encoding declares "
            + term
            + " vs:term_status vs:unstable, and this payload uses it "
            + role
            + ". That is a fact about the vocabulary, not a defect in the document: the encoding"
            + " does not say what an unstable term obliges a publisher to do, and this tool does"
            + " not guess. Worth knowing before you build on it.",
        Rules.termStatus(term));
  }
}
