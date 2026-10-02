package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Python's {@code ==}, for the one place the reference relies on it over decoded JSON values.
 *
 * <p>When two declarations of one {@code @id} are merged, the reference keeps a value from the
 * second only if it is {@code not in} the first's: {@code already + tuple(v for v in values if v
 * not in already)}. That test is Python equality, which is not Jackson's {@code JsonNode.equals}:
 * {@code True == 1 == 1.0} in Python, and a dict equals another with the same keys in a different
 * order. Using Jackson's equality would keep a value the reference drops.
 */
public final class PythonEquality {

  private PythonEquality() {}

  /** Whether {@code candidate} is {@code in} {@code values} by Python's equality. */
  public static boolean in(Value candidate, List<Value> values) {
    for (Value value : values) {
      if (equal(candidate, value)) {
        return true;
      }
    }
    return false;
  }

  /** Python's {@code ==} over two parsed property values. */
  public static boolean equal(Value a, Value b) {
    if (a instanceof Value.Text left && b instanceof Value.Text right) {
      return left.text().equals(right.text());
    }
    if (a instanceof Value.Nested left && b instanceof Value.Nested right) {
      // A frozen dataclass compares field by field.
      return left.targetPath().equals(right.targetPath())
          && java.util.Objects.equals(left.targetId(), right.targetId());
    }
    if (a instanceof Value.Json left && b instanceof Value.Json right) {
      return equal(left.node(), right.node());
    }
    return false;
  }

  /** Python's {@code ==} over two decoded JSON values. */
  public static boolean equal(JsonNode a, JsonNode b) {
    if (isNumeric(a) && isNumeric(b)) {
      return number(a).compareTo(number(b)) == 0;
    }
    if (a.isNull() || b.isNull()) {
      return a.isNull() && b.isNull();
    }
    if (a.isTextual() && b.isTextual()) {
      return a.textValue().equals(b.textValue());
    }
    if (a.isArray() && b.isArray()) {
      if (a.size() != b.size()) {
        return false;
      }
      for (int index = 0; index < a.size(); index++) {
        if (!equal(a.get(index), b.get(index))) {
          return false;
        }
      }
      return true;
    }
    if (a.isObject() && b.isObject()) {
      if (a.size() != b.size()) {
        return false;
      }
      for (Map.Entry<String, JsonNode> field : a.properties()) {
        JsonNode other = b.get(field.getKey());
        if (other == null || !equal(field.getValue(), other)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /** {@code bool} is a subclass of {@code int} in Python, so it compares as 0 or 1. */
  private static boolean isNumeric(JsonNode node) {
    return node.isNumber() || node.isBoolean();
  }

  /** Exact, so that an int and a float compare the way Python compares them. */
  private static BigDecimal number(JsonNode node) {
    if (node.isBoolean()) {
      return node.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
    }
    if (node.isIntegralNumber()) {
      return new BigDecimal(node.bigIntegerValue());
    }
    return new BigDecimal(node.doubleValue());
  }
}
