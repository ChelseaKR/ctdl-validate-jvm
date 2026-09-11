package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The comparable result of validating one payload: the thing the two implementations must agree on.
 *
 * <p>It deliberately excludes the tool's name and version, which are the only things the two are
 * allowed to differ about. Everything else is in scope: the exit code, the document-level error if
 * there is one, every field of every finding including its rule citation and retrieval date, the
 * order the findings come out in, and the plain-text report a human reads.
 *
 * <p>{@code tools/generate_expectations.py} builds the same document from the Python reference
 * implementation and writes it to {@code parity/expected/}. The Java parity suite builds it here
 * and compares byte for byte.
 */
public final class ParityDocument {

  private ParityDocument() {}

  /** Builds the parity document for a decoded payload, with nothing supplied. */
  public static Map<String, Object> of(JsonNode data) {
    return of(data, List.of(), Path.of(""));
  }

  /**
   * Builds the parity document for a decoded payload and the documents supplied with it.
   *
   * @param resolve paths as they would be given to {@code --resolve}; they are printed in findings
   *     exactly as given, so the reference must be given the same spelling
   * @param base what a relative {@code resolve} path is read against
   */
  public static Map<String, Object> of(JsonNode data, List<String> resolve, Path base) {
    Map<String, Object> document = new LinkedHashMap<>();
    List<Finding> findings;
    try {
      findings = Validator.validate(Validator.session(data, resolve, base));
    } catch (Graph.DocumentException exception) {
      // The CLI prints the message to stderr and nothing to stdout, so there is
      // no text report to compare in this case.
      document.put("exit_code", 2);
      document.put("error", exception.getMessage());
      document.put("findings", List.of());
      document.put("summary", Report.counts(List.of()));
      document.put("text_report", null);
      return document;
    }
    List<Object> encoded = new ArrayList<>(findings.size());
    for (Finding finding : findings) {
      encoded.add(finding.toMap());
    }
    document.put("exit_code", Validator.hasErrors(findings) ? 1 : 0);
    document.put("error", null);
    document.put("findings", encoded);
    document.put("summary", Report.counts(findings));
    document.put("text_report", Report.text(findings));
    return document;
  }

  /** The parity document, rendered the way the reference implementation renders JSON. */
  public static String render(JsonNode data) {
    return render(of(data));
  }

  /** The parity document for a payload and its supplied documents, rendered. */
  public static String render(JsonNode data, List<String> resolve, Path base) {
    return render(of(data, resolve, base));
  }

  /** An already-built parity document, rendered. */
  public static String render(Map<String, Object> document) {
    return CanonicalJson.write(document) + "\n";
  }
}
