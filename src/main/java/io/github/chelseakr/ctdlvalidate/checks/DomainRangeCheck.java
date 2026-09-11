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
 *
 * <p>Two range disagreements are dispositions rather than errors, because the published sources
 * contradict each other rather than the document contradicting a source: {@code
 * RANGE_DOCS_CONFLICT} for {@code ceasn:isChildOf}, and {@code CONCEPT_RANGE_CONFLICT} for the
 * properties CTDL ranges on {@code skos:Concept} while ranging the same kind of value on {@code
 * ceterms:CredentialAlignmentObject} elsewhere. Both are INFO and neither gates the exit code.
 */
public final class DomainRangeCheck implements Check {

  /**
   * Documented conflicts between the schema encoding and Credential Engine's own usage guidance.
   * See {@link Rules#ISCHILDOF_RANGE_CONFLICT}.
   */
  private static final Map<String, String> DOCUMENTED_RANGE_CONFLICTS =
      Map.of("ceasn:isChildOf", "ceasn:CompetencyFramework");

  /**
   * The class that satisfies a declared {@code skos:Concept} range in practice even though the
   * encoding gives it no path to it. See {@link Rules#conceptRangeConflict}.
   */
  private static final Set<String> ALIGNMENT_RANGE = Set.of(SchemaIndex.ALIGNMENT_RANGE_TERM);

  private static final Set<String> COMPETENCY = Set.of("ceasn:Competency");
  private static final Set<String> COMPETENCY_FRAMEWORK = Set.of("ceasn:CompetencyFramework");

  @Override
  public List<Finding> run(Session session) {
    Graph graph = session.graph();
    SchemaIndex schema = session.schema();
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
        findings.addAll(rangeFindings(node, prop, session));
      }
      findings.addAll(isPartOfFrameworkFindings(node, session));
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

  private static List<Finding> rangeFindings(Graph.Node node, String prop, Session session) {
    Graph graph = session.graph();
    SchemaIndex schema = session.schema();
    SchemaIndex.PropertyDef propDef = schema.property(prop);
    if (!propDef.rangeHasEntities()) {
      return List.of();
    }
    // A range of rdfs:Resource admits every entity there is, so nothing can fall
    // outside it. Checking a target's classes against it would invert the
    // declaration and reject everything, because no CTDL class reaches
    // rdfs:Resource by rdfs:subClassOf. See SchemaIndex.UNIVERSAL_RANGE_TERMS.
    if (propDef.rangeIsUniversal()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (Value value : node.valuesOf(prop)) {
      Target hit = resolveTarget(value, session);
      if (hit == null || schema.classMatches(hit.types(), propDef.range())) {
        continue;
      }
      // Only an in-payload target has other declarations to ask. A supplied
      // entity is indexed once, first document wins, as the reference indexes it.
      if (hit.node() != null
          && anotherDeclarationSatisfies(graph, schema, hit.node(), propDef.range())) {
        continue;
      }
      List<String> targetTypes = hit.types();
      String valueText = hit.text();
      // CTDL ranges a reference to a term from one of its own concept schemes on
      // skos:Concept for some properties and on CredentialAlignmentObject for
      // others, with nothing about the value to tell the families apart, and its
      // published documents use CredentialAlignmentObject for both. An ERROR here
      // would report Credential Engine's dominant encoding as a defect, so this
      // is reported and not gated on.
      if (propDef.schemeBoundConcept() && schema.classMatches(targetTypes, ALIGNMENT_RANGE)) {
        findings.add(conceptRangeConflict(node, prop, propDef, valueText, hit.origin(), schema));
        continue;
      }
      Finding versionConflict =
          versionRangeConflict(node, prop, targetTypes, valueText, hit.origin(), schema);
      if (versionConflict != null) {
        findings.add(versionConflict);
        continue;
      }
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
                    + " exactly this pattern."
                    + hit.origin(),
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
                    + hit.label()
                    + " is typed ["
                    + String.join(", ", targetTypes)
                    + "], which is outside the declared range of "
                    + prop
                    + "."
                    + hit.origin(),
                Rules.range(prop, propDef.range())));
      }
    }
    return findings;
  }

  /**
   * The entity a reference names, once the run has found it and typed it.
   *
   * @param label what findings call the target
   * @param types its classes, filtered to the ones the snapshot declares; never empty
   * @param origin empty for an in-payload target, otherwise a sentence naming the supplied document
   *     it was read from, so every judgement about it says which document it rests on
   * @param text what to print as the finding's value: the reference as written where there is one,
   *     the target's own label for a nested node
   * @param node the in-payload node, or null for a supplied entity
   */
  private record Target(
      String label, List<String> types, String origin, String text, Graph.Node node) {}

  /**
   * The reference's target and declared classes, or null if it cannot be judged.
   *
   * <p>Null for a value that is not a reference, for one the run cannot see (check 3 reports that
   * as UNVERIFIABLE, and it is not this check's job), and for one whose target declares no class
   * the snapshot knows -- an undeclared or untyped target is not evidence of anything. A reference
   * the payload does not contain is judged against the documents supplied with {@code --resolve},
   * and only against those.
   */
  private static Target resolveTarget(Value value, Session session) {
    if (value instanceof Value.Json) {
      return null;
    }
    Graph.Node target = session.graph().resolve(value);
    String label;
    List<String> declared;
    String origin;
    if (target != null) {
      label = target.label();
      declared = target.types();
      origin = "";
    } else {
      Supplied.Entity external = session.supplied().get(value);
      if (external == null) {
        return null; // resolution is check 3's job
      }
      label = external.nodeId();
      declared = external.types();
      origin = " That entity was read from " + external.source() + ", supplied with --resolve.";
    }
    List<String> types = session.schema().knownTypes(declared);
    if (types.isEmpty()) {
      return null; // cannot judge an undeclared or untyped target
    }
    String text = value instanceof Value.Text written ? written.text() : label;
    return new Target(label, types, origin, text, target);
  }

  /**
   * Whether some other declaration of the resolved target's {@code @id} satisfies the range.
   *
   * <p>A payload may declare the same {@code @id} more than once — a stub embedded inline beside a
   * fuller top-level entity is the ordinary way it happens — and {@link Graph#byId} keeps only
   * whichever the walk reached first. That is depth-first into an earlier entity's inline objects
   * before the next top-level entry, so a reference is judged against the declaration that happens
   * to sit earliest in the document rather than against the declaration the document means. Where
   * the winner's classes fall outside the range and another declaration of the same identifier is
   * squarely inside it, the ERROR is an artefact of walk order and not a fact about the payload.
   *
   * <p>Asked only after {@link SchemaIndex#classMatches} has already failed, so it can withdraw a
   * finding and can never raise one. That direction is deliberate and is the limit of what is fixed
   * here: the mirror case, where the first-walked declaration satisfies a range and a later one
   * does not, still passes, exactly as the reference does. Reporting it would mean raising an ERROR
   * the pinned reference does not raise, which {@code parity/ahead/} is arranged not to permit, and
   * it is a rule-level ruling that belongs in the sibling. See the README limits and ADR 0005.
   */
  private static boolean anotherDeclarationSatisfies(
      Graph graph, SchemaIndex schema, Graph.Node target, Set<String> range) {
    for (Graph.Node declaration : graph.declarationsOf(target.nodeId())) {
      if (declaration == target) {
        continue;
      }
      List<String> declared = schema.knownTypes(declaration.types());
      if (!declared.isEmpty() && schema.classMatches(declared, range)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The version-range disposition: a version link the encoding admits as a subject and refuses as
   * an object.
   *
   * <p>CTDL's version properties declare a range that is a strict subset of their own domain. Where
   * a document versions an entity with another entity of the same class, and that class is one the
   * encoding dropped from the range, the two declarations contradict each other and the document
   * satisfies the one that says this class may be versioned at all.
   *
   * <p>Narrowed to a link between two entities of the same class, because that is the only reading
   * under which the range omission is certainly the mistake: a version of a thing is a thing of the
   * same kind. Any other out-of-range class on these properties stays a {@code RANGE_VIOLATION}.
   *
   * @return the finding, or {@code null} when this disposition does not apply
   */
  private static Finding versionRangeConflict(
      Graph.Node node,
      String prop,
      List<String> targetTypes,
      String valueText,
      String origin,
      SchemaIndex schema) {
    if (!SchemaIndex.VERSION_PROPERTIES.contains(prop)) {
      return null;
    }
    Set<String> dropped = schema.domainOnlyClasses(prop);
    List<String> asymmetric = new ArrayList<>();
    for (String type : schema.knownTypes(node.types())) {
      if (dropped.contains(type) && targetTypes.contains(type)) {
        asymmetric.add(type);
      }
    }
    if (asymmetric.isEmpty()) {
      return null;
    }
    asymmetric.sort(io.github.chelseakr.ctdlvalidate.CodePointOrder.COMPARATOR);
    String cls = asymmetric.get(0);
    return new Finding(
        "VERSION_RANGE_CONFLICT",
        Severity.INFO,
        node.label(),
        prop,
        valueText,
        prop
            + " declares "
            + cls
            + " in its domain and omits it from its range, so CTDL says a "
            + cls
            + " may have a version while saying that version may not itself be a "
            + cls
            + ". One of those two declarations is wrong, and this tool cannot tell you which. It"
            + " does not gate on it, because every class the range does admit would make a "
            + cls
            + "'s version something other than a "
            + cls
            + ", and there is no third option to point you at."
            + origin,
        Rules.versionRangeConflict(prop, cls, dropped));
  }

  /**
   * The concept-range disposition: an alignment object where CTDL declared {@code skos:Concept} but
   * also named the concept scheme the value is drawn from.
   */
  private static Finding conceptRangeConflict(
      Graph.Node node,
      String prop,
      SchemaIndex.PropertyDef propDef,
      String valueText,
      String origin,
      SchemaIndex schema) {
    List<String> schemes = new ArrayList<>(propDef.targetScheme());
    schemes.sort(io.github.chelseakr.ctdlvalidate.CodePointOrder.COMPARATOR);
    return new Finding(
        "CONCEPT_RANGE_CONFLICT",
        Severity.INFO,
        node.label(),
        prop,
        valueText,
        prop
            + " declares its range as skos:Concept, and this value is a "
            + SchemaIndex.ALIGNMENT_RANGE_TERM
            + ". That is how the Registry's published documents encode it, and how CTDL declares"
            + " the range of other properties drawing on the same concept scheme ("
            + String.join(", ", schemes)
            + "), so this is very likely correct as written. Nothing to fix unless you meant to"
            + " reference a skos:Concept directly."
            + origin,
        Rules.conceptRangeConflict(
            prop, propDef.targetScheme(), schema.alignmentRangedSiblings(prop)));
  }

  /** The generic wrong-framework-identifier bug, as it shows up in competency extracts. */
  private static List<Finding> isPartOfFrameworkFindings(Graph.Node node, Session session) {
    Graph graph = session.graph();
    SchemaIndex schema = session.schema();
    Supplied supplied = session.supplied();
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
    // A framework handed to the run with --resolve is as much a candidate as one
    // in the payload: the question is whether the identifier names a framework
    // the run can see, not where it came from.
    boolean fromSupplied = false;
    for (Supplied.Entity entity : supplied.entities().values()) {
      if (schema.classMatches(schema.knownTypes(entity.types()), COMPETENCY_FRAMEWORK)) {
        frameworkIds.add(entity.nodeId());
        fromSupplied = true;
      }
    }
    if (frameworkIds.isEmpty()) {
      return List.of(); // no framework anywhere in reach: nothing to compare against
    }
    String where = fromSupplied ? "this payload or the documents supplied" : "this payload";
    List<Finding> findings = new ArrayList<>();
    for (Value value : values) {
      if (!(value instanceof Value.Text text)) {
        continue;
      }
      String raw = text.text();
      if (frameworkIds.contains(raw)
          || graph.resolve(value) != null
          || supplied.get(value) != null) {
        // A value that resolves to a non-framework is RANGE_VIOLATION's business.
        continue;
      }
      findings.add(
          new Finding(
              "ISPARTOF_FRAMEWORK_MISMATCH",
              Severity.WARNING,
              node.label(),
              "ceasn:isPartOf",
              raw,
              "This competency's isPartOf identifier matches no CompetencyFramework in "
                  + where
                  + ", although this run can see one ("
                  + String.join(", ", frameworkIds)
                  + "). If this competency belongs to that framework, this identifier is the wrong"
                  + " one.",
              Rules.SAME_GRAPH_FRAMEWORK));
    }
    return findings;
  }
}
