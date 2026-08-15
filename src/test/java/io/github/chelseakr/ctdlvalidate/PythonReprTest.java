package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The expected values in this suite are not the author's opinion of what Python prints. They were
 * produced by running {@code repr()} in CPython 3.12 and pasted in, which is the discipline the
 * whole repository follows: the reference implementation's behaviour is recorded, not guessed.
 */
class PythonReprTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @ParameterizedTest(name = "repr({0}) == {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "0.0|0.0",
        "-0.0|-0.0",
        "1.5|1.5",
        "1.0|1.0",
        "-2.25|-2.25",
        "0.1|0.1",
        "100.0|100.0",
        "1e16|1e+16",
        "1e17|1e+17",
        "1e-4|0.0001",
        "1e-5|1e-05",
        "1.7976931348623157e308|1.7976931348623157e+308",
        "4.9e-324|5e-324",
        "123456789012345.6|123456789012345.6",
        "3.141592653589793|3.141592653589793",
        "2.5e-10|2.5e-10",
        "1e30|1e+30",
        "-1e30|-1e+30",
        "1234567890123456789.0|1.2345678901234568e+18",
      })
  @DisplayName("floats match CPython's repr, digits and notation both")
  void floats(String literal, String expected) {
    assertEquals(expected, PythonRepr.ofDouble(Double.parseDouble(literal)));
  }

  @Test
  @DisplayName("strings match CPython's repr: quote choice, escaping, and printability")
  void strings() {
    assertEquals("'abc'", PythonRepr.ofString("abc"));
    // A single quote inside and no double quote: CPython switches the outer quote.
    assertEquals("\"a'b\"", PythonRepr.ofString("a'b"));
    assertEquals("'a\"b'", PythonRepr.ofString("a\"b"));
    // Both quote characters present: CPython keeps single quotes and escapes.
    assertEquals("'a\\'b\"c'", PythonRepr.ofString("a'b\"c"));
    assertEquals("'x\\ny'", PythonRepr.ofString("x\ny"));
    assertEquals("'tab\\tx'", PythonRepr.ofString("tab\tx"));
    assertEquals("'a\\rb'", PythonRepr.ofString("a\rb"));
    assertEquals("'a\\\\b'", PythonRepr.ofString("a\\b"));
    assertEquals("'\\x00'", PythonRepr.ofString(String.valueOf((char) 0x00)));
    assertEquals("'\\x7f'", PythonRepr.ofString(String.valueOf((char) 0x7f)));
    // Printable non-ASCII stays literal; format and separator characters do not.
    assertEquals("'café'", PythonRepr.ofString("café"));
    assertEquals("'☃'", PythonRepr.ofString("☃"));
    assertEquals("'\\u200b'", PythonRepr.ofString(String.valueOf((char) 0x200b)));
    assertEquals("'\\u2028'", PythonRepr.ofString(String.valueOf((char) 0x2028)));
    assertEquals("'😀'", PythonRepr.ofString("😀"));
  }

  @Test
  @DisplayName("decoded JSON values match CPython's repr")
  void jsonValues() throws JsonProcessingException {
    assertRepr("null", "None");
    assertRepr("true", "True");
    assertRepr("false", "False");
    assertRepr("12345", "12345");
    assertRepr("-7", "-7");
    assertRepr("1.5", "1.5");
    assertRepr("\"abc\"", "'abc'");
    assertRepr("[]", "[]");
    assertRepr("{}", "{}");
    assertRepr("[1, \"a\", null, true, 2.5]", "[1, 'a', None, True, 2.5]");
    assertRepr("{\"en\": \"x\", \"k\": [1, {\"z\": null}]}", "{'en': 'x', 'k': [1, {'z': None}]}");
    // Python integers are unbounded, so a big one must not be rendered as a float.
    assertRepr("123456789012345678901234567890", "123456789012345678901234567890");
  }

  private static void assertRepr(String json, String expected) throws JsonProcessingException {
    assertEquals(expected, PythonRepr.of(MAPPER.readTree(json)));
  }
}
