package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Ctid;
import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Check 2: identifier kind.
 *
 * <p>For properties the CTDL context declares as identifier-valued ({@code {"@type": "@id"}}) whose
 * schema range includes entity classes, each string value must be an identifier of the right kind:
 * an IRI or a blank node identifier. A bare UUID (the ce- prefix missing entirely) is the exact bug
 * class of writing a generated UUID where a CTID-based identifier belongs; a bare CTID is the right
 * kind but not yet an IRI.
 */
public final class IdentifierKindCheck implements Check {

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
          if (!(value instanceof Value.Text text)) {
            continue;
          }
          findings.addAll(valueFindings(entity, prop, text.text()));
        }
      }
    }
    return findings;
  }

  private static List<Finding> valueFindings(String entity, String prop, String value) {
    if (Ctid.isBareUuidAnyCase(value)) {
      return List.of(
          new Finding(
              "REF_BARE_UUID",
              Severity.ERROR,
              entity,
              prop,
              value,
              "Bare UUID where an entity identifier belongs. This property takes an IRI; for"
                  + " Registry resources that is "
                  + Ctid.REGISTRY_RESOURCE_PREFIX
                  + "<CTID>.",
              Rules.idCoercion(prop)));
    }
    if (Ctid.isCtidAnyCase(value)) {
      return List.of(
          new Finding(
              "REF_BARE_CTID",
              Severity.WARNING,
              entity,
              prop,
              value,
              "Bare CTID where an IRI belongs: right identifier kind, wrong form. Registry"
                  + " references use the CTID-based URI "
                  + Ctid.REGISTRY_RESOURCE_PREFIX
                  + "<CTID>.",
              Rules.CTID_URI_STRUCTURE));
    }
    if (!looksLikeIri(value)) {
      return List.of(
          new Finding(
              "REF_NOT_IRI",
              Severity.WARNING,
              entity,
              prop,
              value,
              "Value of an identifier-valued property is neither an IRI nor a blank node"
                  + " identifier.",
              Rules.idCoercion(prop)));
    }
    return List.of();
  }

  /**
   * An IRI here means: has a scheme, or is a blank node identifier. This is deliberately loose; the
   * point is to catch values that are plainly not identifiers, not to fully validate IRIs.
   */
  private static boolean looksLikeIri(String value) {
    return value.startsWith("_:") || value.contains(":");
  }
}
