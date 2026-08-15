package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Serializes a small JSON tree exactly the way the reference implementation does, so that the two
 * implementations' reports can be compared byte for byte rather than field by field.
 *
 * <p>The reference calls Python's {@code json.dumps(payload, indent=2, sort_keys=True,
 * ensure_ascii=False)}. That fixes four things this writer reproduces:
 *
 * <ul>
 *   <li>two-space indentation, with {@code ": "} between key and value and a bare {@code ,} before
 *       each newline;
 *   <li>empty objects and arrays written as {@code {}} and {@code []} with no inner newline;
 *   <li>object keys sorted by Unicode code point;
 *   <li>non-ASCII characters written literally, with only {@code "}, {@code \}, and the C0 control
 *       characters escaped. Notably {@code /} is not escaped, and neither are U+2028 and U+2029.
 * </ul>
 *
 * <p>Accepted node types are {@link Map}, {@link List}, {@link String}, {@link Integer}, {@link
 * Boolean}, and {@code null} — the whole of what a report contains. Anything else is a programming
 * error and is rejected rather than guessed at.
 */
public final class CanonicalJson {

  private static final int INDENT = 2;

  private CanonicalJson() {}

  /** Renders a report tree. The result has no trailing newline. */
  public static String write(Object node) {
    StringBuilder out = new StringBuilder();
    writeNode(node, out, 0);
    return out.toString();
  }

  private static void writeNode(Object node, StringBuilder out, int depth) {
    if (node == null) {
      out.append("null");
    } else if (node instanceof String s) {
      writeString(s, out);
    } else if (node instanceof Integer || node instanceof Long) {
      out.append(node);
    } else if (node instanceof Boolean b) {
      out.append(b ? "true" : "false");
    } else if (node instanceof Map<?, ?> map) {
      writeObject(map, out, depth);
    } else if (node instanceof List<?> list) {
      writeArray(list, out, depth);
    } else {
      throw new IllegalArgumentException("not a report node type: " + node.getClass().getName());
    }
  }

  private static void writeObject(Map<?, ?> map, StringBuilder out, int depth) {
    if (map.isEmpty()) {
      out.append("{}");
      return;
    }
    List<String> keys = new ArrayList<>();
    for (Object key : map.keySet()) {
      if (!(key instanceof String s)) {
        throw new IllegalArgumentException("object keys must be strings");
      }
      keys.add(s);
    }
    Collections.sort(keys, CodePointOrder.COMPARATOR);

    out.append("{\n");
    for (int i = 0; i < keys.size(); i++) {
      indent(out, depth + 1);
      writeString(keys.get(i), out);
      out.append(": ");
      writeNode(map.get(keys.get(i)), out, depth + 1);
      out.append(i == keys.size() - 1 ? "\n" : ",\n");
    }
    indent(out, depth);
    out.append('}');
  }

  private static void writeArray(List<?> list, StringBuilder out, int depth) {
    if (list.isEmpty()) {
      out.append("[]");
      return;
    }
    out.append("[\n");
    for (int i = 0; i < list.size(); i++) {
      indent(out, depth + 1);
      writeNode(list.get(i), out, depth + 1);
      out.append(i == list.size() - 1 ? "\n" : ",\n");
    }
    indent(out, depth);
    out.append(']');
  }

  private static void indent(StringBuilder out, int depth) {
    out.append(" ".repeat(INDENT * depth));
  }

  private static void writeString(String value, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
