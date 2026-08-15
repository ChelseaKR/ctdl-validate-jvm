package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Check 4: domain and range, from the schema's own declarations.
 *
 * <p>A property used on a class outside its {@code schema:domainIncludes} (including subclasses) is
 * an ERROR, as is a reference resolving to an in-payload entity whose class is outside {@code
 * schema:rangeIncludes}. Terms in the ceterms/ceasn namespaces that the vendored schema snapshot
 * does not declare are WARNINGs: they may be typos or newer than the snapshot. This check also
 * carries the generic form of the wrong-framework-identifier bug — a Competency whose isPartOf
 * matches no CompetencyFramework in its own payload even though the payload contains one.
 */
public final class DomainRangeCheck implements Check {

  /**
   * Documented conflicts between the schema encoding and Credential Engine's own usage guidance.
   * See {@link Rules#ISCHILDOF_RANGE_CONFLICT}.
   */
  private static final Map<String, String> DOCUMENTED_RANGE_CONFLICTS =
      Map.of("ceasn:isChildOf", "ceasn:CompetencyFramework");

  private static final Set<String> COMPETENCY = Set.of("ceasn:Competency");
  private static final Set<String> COMPETENCY_FRAMEWORK = Set.of("ceasn:CompetencyFramework");

  @Override
  public List<Finding> run(Graph graph, SchemaIndex schema) {
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : graph.nodes()) {
      findings.addAll(unknownTypeFindings(node, schema));
      List<String> nodeTypes = schema.knownTypes(node.types());
      for (String prop : node.sortedPropertyNames()) {
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        if (propDef == null) {
          if (SchemaIndex.isCheckedTerm(prop)) {
            findings.add(
                new Finding(
                    "UNKNOWN_PROPERTY",
                    Severity.WARNING,
                    node.label(),
                    prop,
                    "-",
                    "Property is not declared in the vendored schema snapshot.",
                    Rules.unknownTerm("property", SchemaIndex.vocabPrefix(prop))));
          }
          continue;
        }
        if (!nodeTypes.isEmpty()
            && !propDef.domain().isEmpty()
            && !schema.classMatches(nodeTypes, propDef.domain())) {
          findings.add(
              new Finding(
                  "DOMAIN_VIOLATION",
                  Severity.ERROR,
                  node.label(),
                  prop,
                  "@type=[" + String.join(", ", nodeTypes) + "]",
                  prop + " is not declared for class(es) [" + String.join(", ", nodeTypes) + "].",
                  Rules.domain(prop, propDef.domain())));
        }
        findings.addAll(rangeFindings(node, prop, graph, schema));
      }
      findings.addAll(isPartOfFrameworkFindings(node, graph, schema));
    }
    return findings;
  }

  private static List<Finding> unknownTypeFindings(Graph.Node node, SchemaIndex schema) {
    List<Finding> findings = new ArrayList<>();
    for (String nodeType : node.types()) {
      if (SchemaIndex.isCheckedTerm(nodeType) && !schema.classes().containsKey(nodeType)) {
        findings.add(
            new Finding(
                "UNKNOWN_CLASS",
                Severity.WARNING,
                node.label(),
                "@type",
                nodeType,
                "Class is not declared in the vendored schema snapshot.",
                Rules.unknownTerm("class", SchemaIndex.vocabPrefix(nodeType))));
      }
    }
    return findings;
  }

  private static List<Finding> rangeFindings(
      Graph.Node node, String prop, Graph graph, SchemaIndex schema) {
    SchemaIndex.PropertyDef propDef = schema.property(prop);
    if (!propDef.rangeHasEntities()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (Value value : node.valuesOf(prop)) {
      if (value instanceof Value.Json) {
        continue;
      }
      Graph.Node target = graph.resolve(value);
      if (target == null) {
        continue; // resolution is check 3's job
      }
      List<String> targetTypes = schema.knownTypes(target.types());
      if (targetTypes.isEmpty()) {
        continue; // cannot judge an undeclared or untyped target
      }
      if (schema.classMatches(targetTypes, propDef.range())) {
        continue;
      }
      String valueText = value instanceof Value.Text text ? text.text() : target.label();
      String conflictClass = DOCUMENTED_RANGE_CONFLICTS.get(prop);
      String conflict =
          conflictClass != null && targetTypes.contains(conflictClass) ? conflictClass : null;
      if (conflict != null) {
        findings.add(
            new Finding(
                "RANGE_DOCS_CONFLICT",
                Severity.INFO,
                node.label(),
                prop,
                valueText,
                "Referenced entity is a "
                    + conflict
                    + ", which the declared range of "
                    + prop
                    + " does not include, but Credential Engine's own guidance and examples use"
                    + " exactly this pattern.",
                Rules.ISCHILDOF_RANGE_CONFLICT));
      } else {
        findings.add(
            new Finding(
                "RANGE_VIOLATION",
                Severity.ERROR,
                node.label(),
                prop,
                valueText,
                "Referenced entity "
                    + target.label()
                    + " is typed ["
                    + String.join(", ", targetTypes)
                    + "], which is outside the declared range of "
                    + prop
                    + ".",
                Rules.range(prop, propDef.range())));
      }
    }
    return findings;
  }

  /** The generic wrong-framework-identifier bug, as it shows up in competency extracts. */
  private static List<Finding> isPartOfFrameworkFindings(
      Graph.Node node, Graph graph, SchemaIndex schema) {
    if (!schema.classMatches(schema.knownTypes(node.types()), COMPETENCY)) {
      return List.of();
    }
    List<Value> values = node.valuesOf("ceasn:isPartOf");
    if (values.isEmpty()) {
      return List.of();
    }
    Set<String> frameworkIds =
        new TreeSet<>(io.github.chelseakr.ctdlvalidate.CodePointOrder.COMPARATOR);
    for (Graph.Node other : graph.nodes()) {
      if (other.nodeId() != null
          && schema.classMatches(schema.knownTypes(other.types()), COMPETENCY_FRAMEWORK)) {
        frameworkIds.add(other.nodeId());
      }
    }
    if (frameworkIds.isEmpty()) {
      return List.of(); // no framework in the payload: nothing to compare against
    }
    List<Finding> findings = new ArrayList<>();
    for (Value value : values) {
      if (!(value instanceof Value.Text text)) {
        continue;
      }
      String raw = text.text();
      if (frameworkIds.contains(raw) || graph.resolve(value) != null) {
        // A value that resolves in-payload to a non-framework is RANGE_VIOLATION's business.
        continue;
      }
      findings.add(
          new Finding(
              "ISPARTOF_FRAMEWORK_MISMATCH",
              Severity.WARNING,
              node.label(),
              "ceasn:isPartOf",
              raw,
              "This competency's isPartOf identifier matches no CompetencyFramework in this"
                  + " payload, although the payload contains one ("
                  + String.join(", ", frameworkIds)
                  + "). If this competency belongs to that framework, this identifier is the wrong"
                  + " one.",
              Rules.SAME_GRAPH_FRAMEWORK));
    }
    return findings;
  }
}
