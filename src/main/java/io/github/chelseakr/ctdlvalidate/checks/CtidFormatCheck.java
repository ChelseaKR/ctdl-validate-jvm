package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Ctid;
import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.PythonRepr;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Check 1: CTID format.
 *
 * <p>Values of {@code ceterms:ctid}, plus the CTID portion of any Registry resource or graph URI
 * appearing anywhere in the payload (including {@code @id}), must match the published grammar. See
 * {@link Ctid} for the grammar and its source.
 */
public final class CtidFormatCheck implements Check {

  private static final String CTID_PROP = "ceterms:ctid";

  @Override
  public List<Finding> run(Graph graph, SchemaIndex schema) {
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : graph.nodes()) {
      String entity = node.label();
      for (Value value : node.valuesOf(CTID_PROP)) {
        findings.addAll(ctidValueFindings(entity, value));
      }

      if (node.nodeId() != null) {
        findings.addAll(registryUriFindings(entity, "@id", node.nodeId()));
        findings.addAll(ctidUriMismatchFindings(node, entity));
      }

      for (String prop : node.sortedPropertyNames()) {
        for (Value value : node.valuesOf(prop)) {
          if (value instanceof Value.Text text) {
            findings.addAll(registryUriFindings(entity, prop, text.text()));
          }
        }
      }
    }
    return findings;
  }

  private static List<Finding> ctidValueFindings(String entity, Value value) {
    if (!(value instanceof Value.Text text)) {
      // Anything that is not a string is reported with Python's repr of it, so
      // that the two implementations name the offending value identically. An
      // inline object under ceterms:ctid becomes a nested reference, and the
      // reference implementation reports that dataclass's own repr.
      String rendered;
      if (value instanceof Value.Json json) {
        rendered = PythonRepr.of(json.node());
      } else if (value instanceof Value.Nested nested) {
        rendered =
            "NestedRef(target_path="
                + PythonRepr.ofString(nested.targetPath())
                + ", target_id="
                + (nested.targetId() == null ? "None" : PythonRepr.ofString(nested.targetId()))
                + ")";
      } else {
        throw new IllegalStateException("unreachable value kind: " + value);
      }
      return List.of(
          new Finding(
              "CTID_MALFORMED",
              Severity.ERROR,
              entity,
              CTID_PROP,
              rendered,
              "ceterms:ctid must be a string matching: " + Ctid.EXPECTED_GRAMMAR + ".",
              Rules.CTID_STRUCTURE));
    }

    String raw = text.text();
    Ctid.Shape shape = Ctid.classify(raw);
    List<Finding> findings = new ArrayList<>();
    if (shape.bareUuid()) {
      findings.add(
          new Finding(
              "CTID_BARE_UUID",
              Severity.ERROR,
              entity,
              CTID_PROP,
              raw,
              "Bare UUID where a CTID belongs: the ce- prefix is missing. Expected grammar: "
                  + Ctid.EXPECTED_GRAMMAR
                  + ".",
              Rules.CTID_STRUCTURE));
    } else if (!shape.matchesShape()) {
      findings.add(
          new Finding(
              "CTID_MALFORMED",
              Severity.ERROR,
              entity,
              CTID_PROP,
              raw,
              "Value does not match the CTID grammar: " + Ctid.EXPECTED_GRAMMAR + ".",
              Rules.CTID_STRUCTURE));
    } else {
      if (!shape.lowercase()) {
        findings.add(
            new Finding(
                "CTID_UPPERCASE",
                Severity.WARNING,
                entity,
                CTID_PROP,
                raw,
                "CTID contains upper case hexadecimal digits. UUID text form is lower case on"
                    + " output; Registry case handling is not documented, so this is a WARNING"
                    + " rather than an ERROR.",
                Rules.CTID_LOWERCASE));
      }
      if (!shape.uuidV4()) {
        findings.add(
            new Finding(
                "CTID_NOT_UUIDV4",
                Severity.WARNING,
                entity,
                CTID_PROP,
                raw,
                "CTID matches the 39-character shape but its UUID version/variant bits are not"
                    + " version 4. The published grammar says \"a standard UUID v4\"; Registry"
                    + " enforcement of the version bits is not documented, so this is a WARNING"
                    + " rather than an ERROR.",
                Rules.CTID_STRUCTURE));
      }
    }
    return findings;
  }

  private static List<Finding> registryUriFindings(String entity, String prop, String value) {
    String tail = Ctid.registryUriTail(value);
    if (tail == null) {
      return List.of();
    }
    Ctid.Shape shape = Ctid.classify(tail);
    if (shape.matchesShape()) {
      return List.of();
    }
    String message =
        shape.bareUuid()
            ? "Registry URI whose CTID portion is a bare UUID: the ce- prefix is missing. Expected: "
                + Ctid.EXPECTED_GRAMMAR
                + "."
            : "Registry URI whose tail is not a CTID. Registry resource and graph URIs end in the"
                + " resource's CTID: "
                + Ctid.EXPECTED_GRAMMAR
                + ".";
    return List.of(
        new Finding(
            "REGISTRY_URI_MALFORMED",
            Severity.ERROR,
            entity,
            prop,
            value,
            message,
            Rules.CTID_URI_STRUCTURE));
  }

  /**
   * About the CTID: the ctid property value exactly matches the CTID portion of the resource's URI.
   */
  private static List<Finding> ctidUriMismatchFindings(Graph.Node node, String entity) {
    String tail = Ctid.registryUriTail(node.nodeId());
    if (tail == null || !Ctid.classify(tail).matchesShape()) {
      return List.of();
    }
    List<Finding> findings = new ArrayList<>();
    for (Value value : node.valuesOf(CTID_PROP)) {
      if (!(value instanceof Value.Text text)) {
        continue;
      }
      String ctid = text.text();
      if (Ctid.classify(ctid).matchesShape() && !ctid.equals(tail)) {
        findings.add(
            new Finding(
                "CTID_URI_MISMATCH",
                Severity.ERROR,
                entity,
                CTID_PROP,
                ctid,
                "ceterms:ctid ("
                    + ctid
                    + ") does not match the CTID portion of the entity's @id ("
                    + tail
                    + ").",
                Rules.CTID_URI_STRUCTURE));
      }
    }
    return findings;
  }
}
