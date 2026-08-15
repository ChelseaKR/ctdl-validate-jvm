package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.chelseakr.ctdlvalidate.checks.Check;
import io.github.chelseakr.ctdlvalidate.checks.CtidFormatCheck;
import io.github.chelseakr.ctdlvalidate.checks.DomainRangeCheck;
import io.github.chelseakr.ctdlvalidate.checks.IdentifierKindCheck;
import io.github.chelseakr.ctdlvalidate.checks.InversesCheck;
import io.github.chelseakr.ctdlvalidate.checks.ReferencesCheck;
import java.util.ArrayList;
import java.util.List;

/** Orchestration: run every check over a parsed document. */
public final class Validator {

  /** The check registry, in the order the README documents the checks in. */
  public static final List<Check> ALL_CHECKS =
      List.of(
          new CtidFormatCheck(),
          new IdentifierKindCheck(),
          new ReferencesCheck(),
          new DomainRangeCheck(),
          new InversesCheck());

  private Validator() {}

  /**
   * Validate a decoded CTDL JSON-LD document.
   *
   * <p>Accepts an object with {@code @graph}, a single entity object, or an array of entities.
   * Returns findings in a deterministic order.
   *
   * @throws Graph.DocumentException for shapes the tool does not read
   */
  public static List<Finding> validate(JsonNode data) {
    SchemaIndex schema = SchemaLoader.load();
    Graph graph = GraphParser.parse(data, schema);
    List<Finding> findings = new ArrayList<>();
    for (Check check : ALL_CHECKS) {
      findings.addAll(check.run(graph, schema));
    }
    return Report.finalizeFindings(findings);
  }

  /** True when at least one finding gates the exit code. */
  public static boolean hasErrors(List<Finding> findings) {
    for (Finding finding : findings) {
      if (finding.severity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }
}
