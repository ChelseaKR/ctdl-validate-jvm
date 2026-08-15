package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The exit-code contract and the two reporters. */
class CliTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));
  private static final Path FIXTURES = ROOT.resolve("parity/fixtures");

  private record Result(int code, String out, String err) {}

  private static Result run(String... args) throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code;
    try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
        PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
      code = Cli.run(args, outStream, errStream);
    }
    return new Result(
        code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("a clean payload exits 0")
  void cleanPayloadExitsZero() throws Exception {
    Result result = run(FIXTURES.resolve("clean_framework.json").toString());
    assertEquals(0, result.code());
    assertTrue(result.out().contains("0 finding(s)"), result.out());
  }

  @Test
  @DisplayName("an ERROR finding exits 1 and the text report cites its rule")
  void errorFindingExitsOne() throws Exception {
    Result result = run(FIXTURES.resolve("bug_class_250_bare_uuid_for_ctid.json").toString());
    assertEquals(1, result.code());
    assertTrue(result.out().contains("ERROR        CTID_BARE_UUID"), result.out());
    assertTrue(result.out().contains("rule: About the CTID"), result.out());
    assertTrue(result.out().contains("(retrieved 2026-08-06)"), result.out());
  }

  @Test
  @DisplayName("a WARNING alone does not gate the exit code")
  void warningsDoNotGate() throws Exception {
    Result result = run(FIXTURES.resolve("ctid_warnings.json").toString());
    assertEquals(0, result.code());
    assertTrue(result.out().contains("WARNING      CTID_UPPERCASE"), result.out());
  }

  @Test
  @DisplayName("an UNVERIFIABLE finding is neither a pass nor a fail")
  void unverifiableDoesNotGate() throws Exception {
    Result result = run(FIXTURES.resolve("external_reference.json").toString());
    assertEquals(0, result.code());
    assertTrue(result.out().contains("UNVERIFIABLE REF_OUTSIDE_PAYLOAD"), result.out());
  }

  @Test
  @DisplayName("an unreadable document exits 2 and says why on stderr")
  void unreadableDocumentExitsTwo() throws Exception {
    Result result = run(FIXTURES.resolve("bad_top_level_scalar.json").toString());
    assertEquals(2, result.code());
    assertTrue(result.err().contains("expected a JSON-LD object with @graph"), result.err());
  }

  @Test
  @DisplayName("a missing file exits 2")
  void missingFileExitsTwo() throws Exception {
    Result result = run(ROOT.resolve("parity/fixtures/nope.json").toString());
    assertEquals(2, result.code());
    assertTrue(result.err().contains("cannot read"), result.err());
  }

  @Test
  @DisplayName("invalid JSON exits 2")
  void invalidJsonExitsTwo(@TempDir Path tempDir) throws Exception {
    Path broken = tempDir.resolve("broken.json");
    Files.writeString(broken, "{ not json");
    Result result = run(broken.toString());
    assertEquals(2, result.code());
    assertTrue(result.err().contains("is not valid JSON"), result.err());
  }

  @Test
  @DisplayName("--format json produces the machine-readable report")
  void jsonFormat() throws Exception {
    Result result = run("--format", "json", FIXTURES.resolve("unresolved_bnode.json").toString());
    assertEquals(1, result.code());
    assertTrue(result.out().contains("\"code\": \"REF_UNRESOLVED_BNODE\""), result.out());
    assertTrue(result.out().contains("\"name\": \"ctdl-validate-jvm\""), result.out());
    assertTrue(result.out().contains("\"ERROR\": 1"), result.out());
  }

  @Test
  @DisplayName("--format parity produces exactly the committed expectation")
  void parityFormatMatchesTheCommittedExpectation() throws Exception {
    String name = "inverse_mismatch.json";
    Result result = run("--format=parity", FIXTURES.resolve(name).toString());
    String expected =
        Files.readString(ROOT.resolve("parity/expected").resolve(name), StandardCharsets.UTF_8);
    assertEquals(expected, result.out());
    assertEquals(1, result.code());
  }

  @Test
  @DisplayName("bad usage is refused rather than guessed at")
  void badUsage() throws IOException {
    assertEquals(2, run().code());
    assertEquals(2, run("--format", "yaml", "x.json").code());
    assertEquals(2, run("--nope").code());
    assertEquals(2, run("a.json", "b.json").code());
    assertEquals(2, run("--format").code());
    assertEquals(0, run("--help").code());
  }
}
