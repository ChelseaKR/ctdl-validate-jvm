package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command line interface.
 *
 * <p>A thin one, on purpose. The point of this repository is the rule core and its reporting, not a
 * second CLI: the reference implementation's flags, its extraction subcommand, and its packaging
 * are not ported. What is here is enough to run the validator over a file and to produce the exact
 * document the parity suite compares.
 *
 * <p>Exit codes match the reference: 0 = no ERROR findings; 1 = at least one ERROR finding; 2 = the
 * input could not be read or parsed at all.
 */
public final class Cli {

  private static final String TOOL_NAME = "ctdl-validate-jvm";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Cli() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    String format = "text";
    String file = null;
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--help".equals(arg) || "-h".equals(arg)) {
        out.println(usage());
        return 0;
      } else if ("--format".equals(arg)) {
        if (i + 1 >= args.length) {
          err.println(TOOL_NAME + ": --format needs a value (text, json, or parity)");
          return 2;
        }
        format = args[++i];
      } else if (arg.startsWith("--format=")) {
        format = arg.substring("--format=".length());
      } else if (arg.startsWith("-")) {
        err.println(TOOL_NAME + ": unknown option " + arg);
        return 2;
      } else if (file == null) {
        file = arg;
      } else {
        err.println(TOOL_NAME + ": expected exactly one file");
        return 2;
      }
    }
    if (!List.of("text", "json", "parity").contains(format)) {
      err.println(TOOL_NAME + ": --format must be text, json, or parity");
      return 2;
    }
    if (file == null) {
      err.println(usage());
      return 2;
    }

    String raw;
    try {
      raw = Files.readString(Path.of(file), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException exception) {
      err.println(TOOL_NAME + ": cannot read " + file + ": " + exception.getMessage());
      return 2;
    }

    JsonNode data;
    try {
      data = MAPPER.readTree(raw);
    } catch (JsonProcessingException exception) {
      err.println(
          TOOL_NAME + ": " + file + " is not valid JSON: " + exception.getOriginalMessage());
      return 2;
    }

    if ("parity".equals(format)) {
      out.print(ParityDocument.render(data));
      Object exitCode = ParityDocument.of(data).get("exit_code");
      return (Integer) exitCode;
    }

    List<Finding> findings;
    try {
      findings = Validator.validate(data);
    } catch (Graph.DocumentException exception) {
      err.println(TOOL_NAME + ": " + file + ": " + exception.getMessage());
      return 2;
    }

    out.println(
        "json".equals(format)
            ? CanonicalJson.write(Report.json(findings, TOOL_NAME, version()))
            : Report.text(findings));
    return Validator.hasErrors(findings) ? 1 : 0;
  }

  /**
   * The implementation's version. Nothing has been released, so there is nothing to report but the
   * fact of that.
   */
  static String version() {
    String declared = Cli.class.getPackage().getImplementationVersion();
    return declared == null ? "unreleased" : declared;
  }

  private static String usage() {
    return """
        usage: ctdl-validate-jvm [--format text|json|parity] <file.json>

        Deterministic structural validation of CTDL JSON-LD payloads. Reads an object
        with @graph, a single entity, or an array of entities. No network calls, no
        model calls.

          --format text     the human-readable report (default)
          --format json     the machine-readable report
          --format parity   the document the cross-language parity suite compares

        Exit codes: 0 = no ERROR findings, 1 = at least one, 2 = unreadable input.
        """
        .stripTrailing();
  }
}
