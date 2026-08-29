package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "No network calls at validation time" and "no model calls, ever" are claims this repository makes
 * on its front page, so they are checked mechanically rather than asserted in prose.
 *
 * <p>The reference implementation proves the first by deleting Python's {@code socket} module and
 * running anyway. The JVM has no comparable move that works across versions — the security manager
 * is disabled by default from Java 18 — so this suite does something structural instead: it reads
 * every compiled class of the main source set and fails if any of them so much as mentions a
 * networking or process-launching type. A class that never names {@code java.net} cannot open a
 * socket, and the check cannot be satisfied by a code path that simply was not exercised.
 */
class OfflineGuaranteeTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));

  /**
   * Type and package names whose presence in a class constant pool would mean this code can reach
   * the network, spawn a process, or load something at runtime that the build did not see. Names
   * are matched at a type boundary, so {@code java/lang/Runtime} does not also flag {@code
   * java/lang/RuntimeException}.
   */
  private static final List<String> FORBIDDEN =
      List.of(
          "java/net/",
          "javax/net/",
          "java/nio/channels/SocketChannel",
          "java/nio/channels/ServerSocketChannel",
          "java/nio/channels/DatagramChannel",
          "java/rmi/",
          "java/lang/ProcessBuilder",
          "java/lang/Runtime",
          "jdk/internal/net/");

  @Test
  @DisplayName("no compiled class of this validator names a networking type")
  void noNetworkingTypesAreReferenced() throws IOException {
    Path classes = ROOT.resolve("build/classes/java/main");
    assertTrue(
        Files.isDirectory(classes),
        "compiled classes not found at " + classes + "; run the tests through Gradle");

    List<Pattern> patterns = new ArrayList<>();
    for (String forbidden : FORBIDDEN) {
      String quoted = Pattern.quote(forbidden);
      patterns.add(
          Pattern.compile(forbidden.endsWith("/") ? quoted : quoted + "(?![A-Za-z0-9_$])"));
    }

    List<String> offenders = new ArrayList<>();
    int scanned = 0;
    try (Stream<Path> files = Files.walk(classes)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
        scanned++;
        // ISO-8859-1 maps every byte to a character, so this is a byte scan of
        // the whole class file, constant pool included.
        String contents = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        for (int i = 0; i < patterns.size(); i++) {
          if (patterns.get(i).matcher(contents).find()) {
            offenders.add(classes.relativize(file) + " references " + FORBIDDEN.get(i));
          }
        }
      }
    }
    if (!offenders.isEmpty()) {
      fail("validation code must not reach the network:\n  " + String.join("\n  ", offenders));
    }
    // A clean scan of nothing is not a clean scan. Without this the whole
    // guarantee passes on an empty or missing output directory.
    int seen = scanned;
    assertTrue(
        seen >= MAIN_CLASS_FLOOR,
        () ->
            "scanned only "
                + seen
                + " compiled class(es) under "
                + classes
                + "; the offline guarantee cannot be evidenced by a walk that found nothing");
  }

  /**
   * The fewest compiled classes a real build of this source set produces. Deliberately far below
   * the true count, which is a moving number; the point is that zero, or a handful left over from
   * some other build, cannot be mistaken for a clean scan.
   */
  private static final int MAIN_CLASS_FLOOR = 15;

  @Test
  @DisplayName("no shipped source file mentions a model, a prompt, or an inference call")
  void noModelCalls() throws IOException {
    // Scoped to src/main/java: the shipped implementation. This test file names
    // the forbidden strings in order to look for them, and cannot exempt itself
    // from a scan that included it.
    List<String> forbidden =
        List.of("openai", "anthropic", "bedrock", "llm", "completion(", "embedding", "prompt");
    List<String> offenders = new ArrayList<>();
    int scanned = 0;
    try (Stream<Path> files = Files.walk(ROOT.resolve("src/main/java"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
        scanned++;
        String lowered = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String needle : forbidden) {
          if (lowered.contains(needle)) {
            offenders.add(file.getFileName() + " mentions " + needle);
          }
        }
      }
    }
    assertTrue(offenders.isEmpty(), () -> "there is no model in this repository: " + offenders);
    // Same reasoning as above: a scan that read no files proves nothing.
    int seen = scanned;
    assertTrue(
        seen >= MAIN_SOURCE_FLOOR,
        () -> "scanned only " + seen + " source file(s); this claim needs a scan that found them");
  }

  /** The fewest source files a checkout of this repository has. See {@link #MAIN_CLASS_FLOOR}. */
  private static final int MAIN_SOURCE_FLOOR = 15;
}
