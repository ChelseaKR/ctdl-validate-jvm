package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Python's {@code ==} over decoded JSON, which decides what a merge of two declarations keeps.
 *
 * <p>{@code parity/fixtures/repeated_identifier_merge.json} is the byte-equality evidence that this
 * matches the reference on a real merge. These pin the individual rules it rests on, several of
 * which Jackson's own {@code equals} answers the other way.
 */
class PythonEqualityTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static JsonNode json(String text) throws IOException {
    return MAPPER.readTree(text);
  }

  private static boolean equal(String a, String b) throws IOException {
    return PythonEquality.equal(json(a), json(b));
  }

  @Test
  @DisplayName("bool is an int: True == 1 and False == 0, where Jackson says no")
  void boolIsAnInt() throws IOException {
    assertTrue(equal("true", "1"));
    assertTrue(equal("false", "0"));
    assertTrue(equal("true", "1.0"));
    assertFalse(equal("true", "2"));
    assertFalse(json("true").equals(json("1")), "Jackson disagrees, which is why this exists");
  }

  @Test
  @DisplayName("an int and a float compare exactly, as Python compares them")
  void numbersCompareExactly() throws IOException {
    assertTrue(equal("2", "2.0"));
    assertTrue(equal("9007199254740992", "9007199254740992.0"));
    assertFalse(equal("9007199254740993", "9007199254740992.0"), "2**53+1 is not a double");
    assertFalse(equal("1", "1.5"));
  }

  @Test
  @DisplayName("null equals only null, and a string never equals a number")
  void nullAndStrings() throws IOException {
    assertTrue(equal("null", "null"));
    assertFalse(equal("null", "false"));
    assertFalse(equal("null", "0"));
    assertFalse(equal("\"1\"", "1"));
    assertTrue(equal("\"a\"", "\"a\""));
  }

  @Test
  @DisplayName("lists compare element-wise and dicts key-wise, in any order")
  void containers() throws IOException {
    assertTrue(equal("[1, [true]]", "[1.0, [1]]"));
    assertFalse(equal("[1]", "[1, 1]"));
    assertTrue(equal("{\"a\": 1, \"b\": 2}", "{\"b\": 2.0, \"a\": true}"));
    assertFalse(equal("{\"a\": 1}", "{\"a\": 1, \"b\": 2}"));
    assertFalse(equal("{\"a\": 1}", "{\"b\": 1}"));
    assertFalse(equal("[]", "{}"));
  }

  @Test
  @DisplayName("parsed values: text by text, a nested reference by both fields, kinds never mix")
  void parsedValues() throws IOException {
    Value text = new Value.Text("x");
    Value nested = new Value.Nested("$.a", "x");
    assertTrue(PythonEquality.equal(text, new Value.Text("x")));
    assertTrue(PythonEquality.equal(nested, new Value.Nested("$.a", "x")));
    assertFalse(PythonEquality.equal(nested, new Value.Nested("$.b", "x")));
    assertFalse(PythonEquality.equal(nested, new Value.Nested("$.a", null)));
    assertTrue(PythonEquality.equal(new Value.Nested("$.a", null), new Value.Nested("$.a", null)));
    assertFalse(PythonEquality.equal(text, nested));
    assertFalse(PythonEquality.equal(text, new Value.Json(json("1"))));
    assertTrue(
        PythonEquality.in(new Value.Json(json("1")), List.of(text, new Value.Json(json("true")))));
    assertFalse(PythonEquality.in(new Value.Json(json("2")), List.of(new Value.Json(json("1")))));
  }
}
