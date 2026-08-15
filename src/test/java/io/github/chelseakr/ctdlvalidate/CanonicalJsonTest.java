package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Expected strings here were produced by {@code json.dumps(value, indent=2, sort_keys=True,
 * ensure_ascii=False)} in CPython 3.12, which is the call the reference implementation makes.
 */
class CanonicalJsonTest {

  @Test
  @DisplayName("keys are sorted and indentation is two spaces")
  void sortsKeys() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("b", 2);
    value.put("a", 1);
    assertEquals("{\n  \"a\": 1,\n  \"b\": 2\n}", CanonicalJson.write(value));
  }

  @Test
  @DisplayName("empty containers stay on one line")
  void emptyContainers() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("list", List.of());
    value.put("map", Map.of());
    assertEquals("{\n  \"list\": [],\n  \"map\": {}\n}", CanonicalJson.write(value));
  }

  @Test
  @DisplayName("nesting indents and null renders as null")
  void nesting() {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("x", null);
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("outer", List.of(inner));
    assertEquals(
        "{\n  \"outer\": [\n    {\n      \"x\": null\n    }\n  ]\n}", CanonicalJson.write(value));
  }

  @Test
  @DisplayName("only the characters CPython escapes are escaped")
  void escaping() {
    // ensure_ascii=False leaves non-ASCII literal, and CPython never escapes '/'.
    assertEquals("\"a/b é ☃\"", CanonicalJson.write("a/b é ☃"));
    assertEquals("\"line\\nbreak\"", CanonicalJson.write("line\nbreak"));
    assertEquals("\"quote\\\" slash\\\\\"", CanonicalJson.write("quote\" slash\\"));
    assertEquals("\"\\u0001\"", CanonicalJson.write(String.valueOf((char) 1)));
    assertEquals("\"\\t\\r\\b\\f\"", CanonicalJson.write("\t\r\b\f"));
  }

  @Test
  @DisplayName("a node type a report cannot contain is rejected rather than guessed at")
  void rejectsUnknownNodeTypes() {
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.write(1.5d));
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.write(Map.of(1, "x")));
  }
}
