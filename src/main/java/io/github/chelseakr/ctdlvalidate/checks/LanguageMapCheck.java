package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.PythonRepr;
import io.github.chelseakr.ctdlvalidate.Rules;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import io.github.chelseakr.ctdlvalidate.Session;
import io.github.chelseakr.ctdlvalidate.Severity;
import io.github.chelseakr.ctdlvalidate.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Check 8: a language-map property carries a language map.
 *
 * <p>The vendored contexts declare terms with {@code {"@container": "@language"}}. The parser
 * already reads that declaration -- it keeps such a value as the map it is instead of walking it as
 * a node -- and this check reports the same reading rather than only relying on it. A bare literal
 * in that position states no language, which is the one thing the declaration exists to record.
 *
 * <p>The datatype half of the subject ({@code xsd:date} and the rest) is not here, as it is not in
 * the reference: checking a lexical space needs a vendored specification of it, and writing an ISO
 * 8601 grammar from memory is the rule-from-memory neither implementation permits itself.
 */
public final class LanguageMapCheck implements Check {

  @Override
  public List<Finding> run(Session session) {
    SchemaIndex schema = session.schema();
    List<Finding> findings = new ArrayList<>();
    for (Graph.Node node : session.graph().nodes()) {
      for (Map.Entry<String, List<Value>> entry : node.props().entrySet()) {
        String prop = entry.getKey();
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        if (propDef == null || !propDef.languageMap()) {
          continue;
        }
        for (Value value : entry.getValue()) {
          if (value instanceof Value.Json json && json.node().isObject()) {
            continue;
          }
          findings.add(
              new Finding(
                  "LANGUAGE_MAP_EXPECTED",
                  Severity.WARNING,
                  node.label(),
                  prop,
                  asPythonStr(value),
                  "The context declares "
                      + prop
                      + " a language map, and this value is a bare literal, so the text it carries"
                      + " states no language. Writing it as {\"en-US\": ...} records the language"
                      + " the declaration exists to record. Reported as a warning, not an error,"
                      + " because a plain literal is still well-formed JSON-LD and no published"
                      + " Credential Engine document says the Registry rejects one here.",
                  Rules.languageMapShape(prop)));
        }
      }
    }
    return findings;
  }

  /**
   * Python's {@code str(value)}: the text itself for a string, and for every other JSON value the
   * same characters as its {@code repr}. A nested reference cannot reach a language-map property,
   * because the parser keeps an object there as the map it is.
   */
  private static String asPythonStr(Value value) {
    if (value instanceof Value.Text text) {
      return text.text();
    }
    if (value instanceof Value.Json json) {
      return PythonRepr.of(json.node());
    }
    throw new IllegalStateException("a nested object never reaches a language-map property");
  }
}
