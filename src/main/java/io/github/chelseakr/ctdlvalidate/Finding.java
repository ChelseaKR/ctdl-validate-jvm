package io.github.chelseakr.ctdlvalidate;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One thing the validator found, with the published rule it came from. */
public record Finding(
    String code,
    Severity severity,
    String entity,
    String property,
    String value,
    String message,
    Rule rule) {

  /**
   * The ordering the reference implementation sorts findings by: entity, then property, then code,
   * then value, then severity name, then message.
   *
   * <p>The reference stops there. This port appends the rule's citation, URL, and retrieval date as
   * a final tiebreak. That is a deliberate, recorded divergence, and it can only ever change the
   * output for two findings that agree on all six of the reference's keys and disagree only on
   * their rule — a case where the reference's own ordering is not deterministic, because it sorts a
   * Python {@code set} whose iteration order depends on per-process string hash randomization. No
   * fixture in the parity corpus reaches it. See the limits section of the README.
   */
  public static final Comparator<Finding> ORDER =
      Comparator.comparing(Finding::entity, CodePointOrder.COMPARATOR)
          .thenComparing(Finding::property, CodePointOrder.COMPARATOR)
          .thenComparing(Finding::code, CodePointOrder.COMPARATOR)
          .thenComparing(Finding::value, CodePointOrder.COMPARATOR)
          .thenComparing(f -> f.severity().name(), CodePointOrder.COMPARATOR)
          .thenComparing(Finding::message, CodePointOrder.COMPARATOR)
          .thenComparing(f -> f.rule().citation(), CodePointOrder.COMPARATOR)
          .thenComparing(f -> f.rule().url(), CodePointOrder.COMPARATOR)
          .thenComparing(f -> f.rule().retrieved(), CodePointOrder.COMPARATOR);

  public Finding {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(property, "property");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(rule, "rule");
  }

  /** The JSON shape of a finding, field for field with the reference implementation. */
  public Map<String, Object> toMap() {
    Map<String, Object> ruleMap = new LinkedHashMap<>();
    ruleMap.put("citation", rule.citation());
    ruleMap.put("url", rule.url());
    ruleMap.put("retrieved", rule.retrieved());

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("code", code);
    map.put("severity", severity.name());
    map.put("entity", entity);
    map.put("property", property);
    map.put("value", value);
    map.put("message", message);
    map.put("rule", ruleMap);
    return map;
  }

  /**
   * The human-readable block, line for line with the reference implementation. Separators are
   * literal newlines rather than the platform's, because output is compared byte for byte.
   */
  public String renderText() {
    return leftPad(severity.name(), SEVERITY_COLUMN)
        + " "
        + code
        + "  entity="
        + entity
        + "\n    "
        + property
        + " = "
        + value
        + "\n    "
        + message
        + "\n    rule: "
        + rule.citation()
        + "\n    source: "
        + rule.url()
        + " (retrieved "
        + rule.retrieved()
        + ")";
  }

  /** Width of the severity column, matching the reference implementation's {@code {:12}}. */
  private static final int SEVERITY_COLUMN = 12;

  private static String leftPad(String text, int width) {
    return text.length() >= width ? text : text + " ".repeat(width - text.length());
  }
}
