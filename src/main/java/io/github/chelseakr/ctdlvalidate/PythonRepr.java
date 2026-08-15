package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Python's {@code repr()} for the JSON value subset.
 *
 * <p>This class exists because of one line in the reference implementation. When {@code
 * ceterms:ctid} carries something that is not a string, the reference reports {@code
 * value=repr(value)} — and {@code repr} is Python's, not JSON's. {@code null} comes out as {@code
 * None}, {@code true} as {@code True}, a nested array as {@code ['a', 'b']}, an object as {@code
 * {'en': 'x'}}. A port that wrote {@code null}, {@code true}, and {@code ["a","b"]} would produce
 * the right finding with the wrong text, and the parity test would catch it.
 *
 * <p>So the port reimplements {@code repr} for exactly the types a decoded JSON document can
 * contain: null, booleans, integers of any size, floats, strings, arrays, and objects. It is not a
 * general Python {@code repr}, and it is not meant to be.
 *
 * <p>The float case is the fiddly one. Python renders a float as the shortest decimal string that
 * round-trips, in positional notation when the decimal point falls in {@code (-4, 16]} and
 * scientific notation with a signed two-or-more-digit exponent otherwise. Java's {@link
 * Double#toString} agrees on neither the digit selection (before Java 19) nor the notation, so the
 * shortest round-tripping form is searched for here and then formatted Python's way.
 */
public final class PythonRepr {

  private PythonRepr() {}

  /** Python's {@code repr()} of a decoded JSON value. */
  public static String of(JsonNode node) {
    if (node == null || node.isNull()) {
      return "None";
    }
    if (node.isBoolean()) {
      return node.booleanValue() ? "True" : "False";
    }
    if (node.isIntegralNumber()) {
      return node.bigIntegerValue().toString();
    }
    if (node.isNumber()) {
      return ofDouble(node.doubleValue());
    }
    if (node.isTextual()) {
      return ofString(node.textValue());
    }
    if (node.isArray()) {
      StringJoiner joiner = new StringJoiner(", ", "[", "]");
      for (JsonNode element : node) {
        joiner.add(of(element));
      }
      return joiner.toString();
    }
    if (node.isObject()) {
      StringJoiner joiner = new StringJoiner(", ", "{", "}");
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        joiner.add(ofString(field.getKey()) + ": " + of(field.getValue()));
      }
      return joiner.toString();
    }
    throw new IllegalArgumentException("not a decoded JSON value: " + node.getNodeType());
  }

  /**
   * Python's {@code repr()} of a string: single quotes unless the string contains a single quote
   * and no double quote, backslash and the quote character escaped, and anything Unicode calls
   * unprintable escaped as {@code \\xNN}, {@code \\uNNNN}, or {@code \\UNNNNNNNN}.
   */
  public static String ofString(String value) {
    char quote = value.indexOf('\'') >= 0 && value.indexOf('"') < 0 ? '"' : '\'';
    StringBuilder out = new StringBuilder().append(quote);
    int i = 0;
    while (i < value.length()) {
      int cp = value.codePointAt(i);
      i += Character.charCount(cp);
      if (cp == quote || cp == '\\') {
        out.append('\\').append((char) cp);
      } else if (cp == '\n') {
        out.append("\\n");
      } else if (cp == '\r') {
        out.append("\\r");
      } else if (cp == '\t') {
        out.append("\\t");
      } else if (isPrintable(cp)) {
        out.appendCodePoint(cp);
      } else if (cp < 0x100) {
        out.append(String.format(Locale.ROOT, "\\x%02x", cp));
      } else if (cp < 0x10000) {
        out.append(String.format(Locale.ROOT, "\\u%04x", cp));
      } else {
        out.append(String.format(Locale.ROOT, "\\U%08x", cp));
      }
    }
    return out.append(quote).toString();
  }

  /**
   * Python's {@code str.isprintable()}: everything except the Unicode categories Cc, Cf, Cs, Co,
   * Cn, Zl, Zp, and Zs — with the single space U+0020 printable anyway.
   */
  private static boolean isPrintable(int codePoint) {
    if (codePoint == ' ') {
      return true;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL,
          Character.FORMAT,
          Character.SURROGATE,
          Character.PRIVATE_USE,
          Character.UNASSIGNED,
          Character.LINE_SEPARATOR,
          Character.PARAGRAPH_SEPARATOR,
          Character.SPACE_SEPARATOR ->
          false;
      default -> true;
    };
  }

  /** Python's {@code repr()} of a float. */
  public static String ofDouble(double value) {
    if (Double.isNaN(value)) {
      return "nan";
    }
    if (Double.isInfinite(value)) {
      return value > 0 ? "inf" : "-inf";
    }
    String sign = (value < 0 || 1 / value < 0) ? "-" : "";
    double magnitude = Math.abs(value);
    if (Double.compare(magnitude, 0.0) == 0) {
      return sign + "0.0";
    }

    // Shortest round-tripping digits, found the way Python's 'r' format code
    // does: try increasing precision until the string parses back to the same
    // double. %.Ne gives N+1 significant digits.
    String scientific = null;
    for (int precision = 0; precision <= 16; precision++) {
      String candidate = String.format(Locale.ROOT, "%." + precision + "e", magnitude);
      if (Double.compare(Double.parseDouble(candidate), magnitude) == 0) {
        scientific = candidate;
        break;
      }
    }
    if (scientific == null) {
      scientific = String.format(Locale.ROOT, "%.17e", magnitude);
    }

    int eIndex = scientific.indexOf('e');
    String digits = scientific.substring(0, eIndex).replace(".", "");
    while (digits.length() > 1 && digits.endsWith("0")) {
      digits = digits.substring(0, digits.length() - 1);
    }
    // decpt is where the decimal point sits: value == 0.<digits> * 10^decpt.
    int decpt = Integer.parseInt(scientific.substring(eIndex + 1)) + 1;

    if (decpt <= -4 || decpt > 16) {
      StringBuilder out = new StringBuilder(sign).append(digits.charAt(0));
      if (digits.length() > 1) {
        out.append('.').append(digits, 1, digits.length());
      }
      int exponent = decpt - 1;
      out.append('e').append(exponent < 0 ? '-' : '+');
      String exponentDigits = Integer.toString(Math.abs(exponent));
      if (exponentDigits.length() < 2) {
        out.append('0');
      }
      return out.append(exponentDigits).toString();
    }
    if (decpt <= 0) {
      return sign + "0." + "0".repeat(-decpt) + digits;
    }
    if (decpt >= digits.length()) {
      return sign + digits + "0".repeat(decpt - digits.length()) + ".0";
    }
    return sign + digits.substring(0, decpt) + "." + digits.substring(decpt);
  }
}
