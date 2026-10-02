package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.chelseakr.ctdlvalidate.checks.Check;
import io.github.chelseakr.ctdlvalidate.checks.ConceptSchemeCheck;
import io.github.chelseakr.ctdlvalidate.checks.CtidFormatCheck;
import io.github.chelseakr.ctdlvalidate.checks.DomainRangeCheck;
import io.github.chelseakr.ctdlvalidate.checks.IdentifierKindCheck;
import io.github.chelseakr.ctdlvalidate.checks.IdentityCheck;
import io.github.chelseakr.ctdlvalidate.checks.InversesCheck;
import io.github.chelseakr.ctdlvalidate.checks.LanguageMapCheck;
import io.github.chelseakr.ctdlvalidate.checks.ReferencesCheck;
import io.github.chelseakr.ctdlvalidate.checks.TermStatusCheck;
import java.nio.file.Path;
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
          new InversesCheck(),
          new IdentityCheck(),
          new ConceptSchemeCheck(),
          new LanguageMapCheck(),
          new TermStatusCheck());

  private Validator() {}

  /**
   * Assemble the primary payload, the schema, and any supplied documents.
   *
   * <p>The payload is parsed before anything supplied is read, as the reference does, so a document
   * the tool cannot read at all is reported as that before any supplied document is opened.
   *
   * @param resolve further CTDL documents, or directories of them, whose entities become resolvable
   *     for this run. Read from the local filesystem; never fetched, never themselves validated.
   * @param base what a relative {@code resolve} path is read against
   * @throws Graph.DocumentException for shapes the tool does not read, in the payload or in a
   *     supplied document
   */
  public static Session session(JsonNode data, List<String> resolve, Path base) {
    SchemaIndex schema = SchemaLoader.load();
    Graph graph = GraphParser.parse(data, schema);
    Supplied supplied = resolve.isEmpty() ? Supplied.none() : Supplied.build(resolve, base, schema);
    return new Session(graph, schema, supplied);
  }

  /**
   * Validate a decoded CTDL JSON-LD document with nothing supplied.
   *
   * <p>Accepts an object with {@code @graph}, a single entity object, or an array of entities.
   * Returns findings in a deterministic order.
   *
   * @throws Graph.DocumentException for shapes the tool does not read
   */
  public static List<Finding> validate(JsonNode data) {
    return validate(session(data, List.of(), Path.of("")));
  }

  /** Run every check over an assembled session. */
  public static List<Finding> validate(Session session) {
    List<Finding> findings = new ArrayList<>();
    for (Check check : ALL_CHECKS) {
      findings.addAll(check.run(session));
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
