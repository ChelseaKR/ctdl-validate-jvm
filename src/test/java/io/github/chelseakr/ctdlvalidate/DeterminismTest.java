package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Same input, same output, byte for byte. There is nothing to seed here — no sampling, no
 * timestamps, no network — so the only way determinism can be lost is through a collection whose
 * iteration order is not fixed, which is exactly what repeating a run inside one JVM catches.
 */
class DeterminismTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("repeated runs over every fixture produce identical bytes")
  void repeatedRunsAgree() throws IOException {
    int validated = 0;
    try (Stream<Path> files = Files.list(ROOT.resolve("parity/fixtures"))) {
      for (Path fixture : files.sorted().toList()) {
        validated++;
        Set<String> renderings = new LinkedHashSet<>();
        for (int run = 0; run < 5; run++) {
          renderings.add(ParityDocument.render(MAPPER.readTree(fixture.toFile())));
        }
        assertEquals(
            1,
            renderings.size(),
            "validating " + fixture.getFileName() + " produced more than one distinct report");
      }
    }
    // Every assertion above lives inside the loop, so an empty fixture directory
    // would make this test pass having validated nothing at all.
    int seen = validated;
    assertTrue(
        seen > 0, () -> "validated " + seen + " fixture(s); determinism was never exercised");
  }

  @Test
  @DisplayName("findings are deduplicated and ordered, whatever order the checks emit them in")
  void findingsAreOrderedAndDeduplicated() {
    Rule rule = new Rule("citation", "url", "2026-08-06");
    Finding first = new Finding("B_CODE", Severity.ERROR, "a", "p", "v", "m", rule);
    Finding second = new Finding("A_CODE", Severity.INFO, "a", "p", "v", "m", rule);
    Finding third = new Finding("A_CODE", Severity.INFO, "b", "p", "v", "m", rule);

    List<Finding> ordered =
        Report.finalizeFindings(List.of(third, first, second, first, second, third));

    assertEquals(List.of(second, first, third), ordered);
  }
}
