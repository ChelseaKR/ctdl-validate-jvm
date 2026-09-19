package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.CodePointOrder;
import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Session;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Check 7: a controlled-vocabulary value belongs to the vocabulary named for it.
 *
 * <p>Properties in the vendored encodings declare {@code meta:targetScheme}, the concept scheme a
 * value is drawn from, and the same encodings declare for each concept the scheme it belongs to.
 * Both halves are vendored and hash-pinned, which is what makes this checkable with no network.
 *
 * <p>Every value on a scheme-bound property lands in exactly one of four outcomes, and nothing is
 * skipped silently: declared in a scheme the property names (no finding); declared in some other
 * scheme (WARNING); not declared at all (UNVERIFIABLE); an alignment object that names no target
 * (UNVERIFIABLE). The third is the common case in published documents -- CTDL's alignment objects
 * point outside CTDL, at O*NET, CIP and NAICS -- and is why this check never reports an ERROR.
 */
public final class ConceptSchemeCheck implements Check {

  /** The property an alignment object carries its term identifier on. */
  static final String TARGET_NODE = "ceterms:targetNode";

  /** What an alignment object carries when it names its term in words instead. */
  static final String TARGET_NODE_NAME = "ceterms:targetNodeName";

  /**
   * The term identifiers a value states, and whether it named none at all.
   *
   * @param terms the identifiers, written directly or on an alignment object's {@link #TARGET_NODE}
   * @param namedNothing true for an alignment object that carries no identifier: it states its term
   *     by label, which membership cannot be decided from
   */
  record Stated(List<String> terms, boolean namedNothing) {}

  /** What a scheme-bound value states, as the reference's {@code _terms} reads it. */
  static Stated terms(Value value, Graph graph) {
    if (value instanceof Value.Text text) {
      return new Stated(List.of(text.text()), false);
    }
    if (!(value instanceof Value.Nested nested)) {
      return new Stated(List.of(), false);
    }
    Graph.Node target = graph.byPath().get(nested.targetPath());
    if (target == null) {
      return new Stated(List.of(), false); // every nested value is registered by the parser
    }
    List<String> stated = new ArrayList<>();
    for (Value item : target.valuesOf(TARGET_NODE)) {
      if (item instanceof Value.Text text) {
        stated.add(text.text());
      }
    }
    return new Stated(List.copyOf(stated), stated.isEmpty());
  }

  @Override
  public List<Finding> run(Session session) {
    SchemaIndex schema = session.schema();
    Graph graph = session.graph();
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : graph.nodes()) {
      for (Map.Entry<String, List<Value>> entry : node.props().entrySet()) {
        String prop = entry.getKey();
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        if (propDef == null || propDef.targetScheme().isEmpty()) {
          continue;
        }
        for (Value value : entry.getValue()) {
          Stated stated = terms(value, graph);
          if (stated.namedNothing()) {
            findings.add(noTarget(node.label(), prop, propDef));
            continue;
          }
          for (String term : stated.terms()) {
            Set<String> declared = schema.concepts().get(term);
            if (declared == null) {
              findings.add(notInSnapshot(node.label(), prop, term, propDef));
            } else if (Collections.disjoint(declared, propDef.targetScheme())) {
              findings.add(wrongScheme(node.label(), prop, term, declared, propDef));
            }
          }
        }
      }
    }
    return findings;
  }

  private static String sortedJoin(Collection<String> terms) {
    List<String> ordered = new ArrayList<>(terms);
    ordered.sort(CodePointOrder.COMPARATOR);
    return String.join(", ", ordered);
  }

  private static Finding wrongScheme(
      String entity,
      String prop,
      String term,
      Set<String> declared,
      SchemaIndex.PropertyDef propDef) {
    return new Finding(
        "CONCEPT_OUTSIDE_SCHEME",
        Severity.WARNING,
        entity,
        prop,
        term,
        term
            + " is a concept the encoding declares in "
            + sortedJoin(declared)
            + ", and "
            + prop
            + " draws from "
            + sortedJoin(propDef.targetScheme())
            + ". Both declarations are in the vendored snapshot, so this is a term from the wrong"
            + " vocabulary for this property rather than a term the tool does not recognise."
            + " Reported as a warning, not an error, because no published Credential Engine"
            + " document says the Registry enforces meta:targetScheme on ingest.",
        Rules.conceptScheme(prop, propDef.targetScheme()));
  }

  private static Finding notInSnapshot(
      String entity, String prop, String term, SchemaIndex.PropertyDef propDef) {
    return new Finding(
        "CONCEPT_OUTSIDE_SNAPSHOT",
        Severity.UNVERIFIABLE,
        entity,
        prop,
        term,
        prop
            + " draws from "
            + sortedJoin(propDef.targetScheme())
            + ", and the vendored encoding does not declare "
            + term
            + " as a concept in any scheme. That is normal on this property: CTDL's alignment"
            + " objects reference frameworks outside CTDL, and published Registry documents point"
            + " these properties at O*NET, CIP and NAICS. This tool has not vendored those"
            + " frameworks and fetches nothing, so it did not check the value. It is not"
            + " reporting that the value is wrong.",
        Rules.CONCEPT_OUTSIDE_SNAPSHOT);
  }

  private static Finding noTarget(String entity, String prop, SchemaIndex.PropertyDef propDef) {
    return new Finding(
        "CONCEPT_NOT_IDENTIFIED",
        Severity.UNVERIFIABLE,
        entity,
        prop,
        "(alignment object with no " + TARGET_NODE + ")",
        "This value of "
            + prop
            + " names its term in words rather than by identifier, so there is nothing to match"
            + " against "
            + sortedJoin(propDef.targetScheme())
            + ". Adding "
            + TARGET_NODE
            + " with the concept's identifier would make it checkable; "
            + TARGET_NODE_NAME
            + " alone cannot be.",
        Rules.CONCEPT_OUTSIDE_SNAPSHOT);
  }
}
