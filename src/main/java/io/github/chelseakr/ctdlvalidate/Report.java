package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Deduplication, ordering, counting, and rendering of findings. */
public final class Report {

  private Report() {}

  /**
   * Deduplicate and order findings deterministically. Findings are values, so two identical ones
   * collapse into one, exactly as the reference implementation's {@code sorted(set(findings))}
   * does.
   */
  public static List<Finding> finalizeFindings(List<Finding> findings) {
    List<Finding> ordered = new ArrayList<>(new LinkedHashSet<>(findings));
    ordered.sort(Finding.ORDER);
    return List.copyOf(ordered);
  }

  /** Counts per severity, in report order. */
  public static Map<String, Object> counts(List<Finding> findings) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Severity severity : Severity.REPORT_ORDER) {
      int count = 0;
      for (Finding finding : findings) {
        if (finding.severity() == severity) {
          count++;
        }
      }
      result.put(severity.name(), count);
    }
    return result;
  }

  /** The JSON report tree. {@code toolName} names the implementation that produced it. */
  public static Map<String, Object> json(List<Finding> findings, String toolName, String version) {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", toolName);
    tool.put("version", version);

    List<Object> encoded = new ArrayList<>(findings.size());
    for (Finding finding : findings) {
      encoded.add(finding.toMap());
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tool", tool);
    payload.put("findings", encoded);
    payload.put("summary", counts(findings));
    return payload;
  }

  /** The plain-text report, block for block with the reference implementation. */
  public static String text(List<Finding> findings) {
    Map<String, Object> counts = counts(findings);
    StringJoiner summary = new StringJoiner(", ");
    for (Severity severity : Severity.REPORT_ORDER) {
      summary.add(counts.get(severity.name()) + " " + severity.name());
    }
    StringBuilder out = new StringBuilder();
    for (Finding finding : findings) {
      out.append(finding.renderText()).append("\n\n");
    }
    out.append(findings.size()).append(" finding(s): ").append(summary);
    return out.toString();
  }
}
