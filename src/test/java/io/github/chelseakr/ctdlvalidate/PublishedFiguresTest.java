package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every number this repository publishes about itself, checked against the repository.
 *
 * <p>{@code VendorIntegrityTest} was the only test here that read a Markdown file and asserted
 * something about it: the vendored files must hash to what {@code SOURCES.md} says. Nothing did the
 * same for the counts in the README, and the cost showed. A pull request corrected four counts in
 * the README and left the identical counts wrong in three other files, because prose is the one
 * artifact in this build that nothing derives and nothing checks.
 *
 * <p>So the figures are derived here and the documents are held to them. A count in the README is
 * read out of the directory listing, the parsed source, the generated expectations, the vendored
 * snapshot, or the disposition table — never off another sentence. Change the repository and the
 * sentence goes red; reword the sentence past the pattern that reads it and it also goes red,
 * because a figure this gate can no longer find is a figure nothing is checking again.
 *
 * <p>{@code CHANGELOG.md} and {@code docs/plans/} are deliberately not scanned. Both are dated
 * records of what was true at a moment: a Keep-a-Changelog entry and an audit are supposed to keep
 * the figure they were written with, and holding them to today's count would be rewriting the
 * record rather than maintaining it. A figure in them that was never true is a defect and gets
 * fixed by hand; a figure that has simply moved since is the point of them.
 */
class PublishedFiguresTest {

  private static final Path ROOT =
      Path.of(System.getProperty("ctdlvalidate.repoRoot", System.getProperty("user.dir")));

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * The documents that describe this repository as it is now, and are therefore answerable for
   * every figure in them. The Javadoc on {@link SchemaIndex} is in the list because it states the
   * size of the vendored snapshot in the same sentence the README does, and was wrong in both.
   */
  private static final List<String> LIVE_DOCUMENTS =
      List.of(
          "README.md",
          "CITATION.cff",
          "CONTRIBUTING.md",
          "docs/ROADMAP.md",
          "parity/PROVENANCE.md",
          "src/main/java/io/github/chelseakr/ctdlvalidate/SchemaIndex.java");

  /**
   * One published figure, the value the repository derives for it, and the sentences that state it.
   *
   * @param what the quantity, for a failure message that says what went stale
   * @param derived what the repository says it is, computed on every run
   * @param sentences regular expressions whose first group captures the published figure; each one
   *     must match somewhere in {@link #LIVE_DOCUMENTS}, and every match anywhere must agree
   */
  private record Claim(String what, int derived, List<String> sentences) {}

  /** Written-out numbers, because English prose does not spell every count in digits. */
  private static final List<String> NUMBER_WORDS =
      List.of(
          "zero",
          "one",
          "two",
          "three",
          "four",
          "five",
          "six",
          "seven",
          "eight",
          "nine",
          "ten",
          "eleven",
          "twelve",
          "thirteen",
          "fourteen",
          "fifteen",
          "sixteen",
          "seventeen",
          "eighteen",
          "nineteen",
          "twenty");

  @Test
  @DisplayName("every figure the live documents publish is the one the repository derives")
  void everyPublishedFigureIsDerived() throws IOException {
    Map<String, String> documents = liveDocuments();
    List<String> problems = new ArrayList<>();

    for (Claim claim : claims()) {
      for (String sentence : claim.sentences()) {
        Pattern pattern = Pattern.compile(sentence);
        int found = 0;
        for (Map.Entry<String, String> document : documents.entrySet()) {
          Matcher matcher = pattern.matcher(document.getValue());
          while (matcher.find()) {
            found++;
            Integer published = number(matcher.group(1));
            if (published == null || published != claim.derived()) {
              problems.add(
                  document.getKey()
                      + " publishes \""
                      + matcher.group(1)
                      + "\" for "
                      + claim.what()
                      + ", which this repository derives as "
                      + claim.derived()
                      + " (sentence: "
                      + sentence
                      + ")");
            }
          }
        }
        if (found == 0) {
          problems.add(
              "no live document states "
                  + claim.what()
                  + " any more: the sentence this gate reads has been reworded or deleted, so the"
                  + " figure is published somewhere nothing checks. Re-derive it and update the"
                  + " pattern (sentence: "
                  + sentence
                  + ")");
        }
      }
    }

    assertEquals(
        List.of(),
        problems,
        "a published figure no longer matches the repository it describes. Fix the document, or"
            + " fix the pattern if the sentence moved; do not adjust the derivation to agree.");
  }

  /** The claims, with every figure computed rather than written down. */
  private static List<Claim> claims() throws IOException {
    SchemaIndex schema = SchemaLoader.load();

    int fixtures = jsonFilesIn("parity/fixtures");
    int aheadFixtures = jsonFilesIn("parity/ahead/fixtures");
    int portCodes = FindingCodeCensusTest.portCodes().size();
    int referenceCodes = referenceCodes().size();
    int byteEqualityCodes = codesExercisedBy(expectationNames()).size();
    Set<String> vendoredFixtures = fixturesProvenanceCallsVendored();
    Set<String> siblingCodes = codesExercisedBy(vendoredFixtures);

    SchemaIndex.PropertyDef version = schema.property("ceterms:previousVersion");

    return List.of(
        new Claim(
            "the byte-equality corpus, in payloads",
            fixtures,
            List.of(
                "parity/fixtures/ ([^ ]+) CTDL JSON-LD payloads",
                "holds ([^ ]+) CTDL JSON-LD payloads",
                "parity corpus is ([^ ]+) payloads")),
        new Claim(
            "the ahead-of-reference corpus, in payloads",
            aheadFixtures,
            List.of("parity/ahead/fixtures/ ([^ ]+) payloads")),
        new Claim(
            "the fixtures vendored from the sibling's own test suite",
            vendoredFixtures.size(),
            List.of("([^ ]+) are vendored from the reference implementation")),
        new Claim(
            "the fixtures written for this repository",
            fixtures - vendoredFixtures.size(),
            List.of("([^ ]+) were written for this repository")),
        new Claim(
            "the branch-coverage floor the build enforces, as a percentage",
            branchCoverageFloorPercent(),
            List.of(
                "branch coverage >= ([^ ]+)",
                "branch-coverage floor of ([^ ]+)",
                "([0-9]+)% branch coverage")),
        new Claim(
            "the finding codes this port emits",
            portCodes,
            List.of(
                "All ([^ ]+) have a fixture",
                "(?i)all five checks and all ([^ ]+) codes are here",
                "covers [^ ]+ of the ([^ ]+) finding codes")),
        new Claim(
            "the finding codes the byte-equality corpus covers",
            byteEqualityCodes,
            List.of(
                "All [^ ]+ have a fixture: ([^ ]+) of them here",
                "covers ([^ ]+) of the [^ ]+ finding codes")),
        new Claim(
            "the finding codes only the ahead corpus covers",
            portCodes - byteEqualityCodes,
            List.of(
                "and the ([^ ]+) this port emits ahead of the pinned release",
                "the other ([^ ]+) codes are the ones this port emits ahead")),
        new Claim(
            "the finding codes the pinned reference emits",
            referenceCodes,
            List.of("the reference's ([^ ]+) finding codes")),
        new Claim(
            "the finding codes the sibling's own fixtures exercise",
            siblingCodes.size(),
            List.of("exercise ([^ ]+) of the reference's [^ ]+ finding codes")),
        new Claim(
            "the reference rules the sibling's own fixtures leave untested",
            referenceCodes - siblingCodes.size(),
            List.of("leaves ([^ ]+) rules untested")),
        new Claim(
            "the dispositions this port declares against the pinned release",
            AheadOfReferenceTest.declaredDispositions(),
            List.of(
                "([^ ]+) dispositions in this port are ahead of the pinned release",
                "bounded to ([^ ]+) declared dispositions")),
        new Claim(
            "the dispositions that withdraw or downgrade an ERROR",
            AheadOfReferenceTest.dispositionsThatRemoveAnError(),
            List.of("All ([^ ]+) withdraw or downgrade an ERROR")),
        new Claim(
            "the workflow steps pinned to a full commit SHA",
            shaPinnedWorkflowSteps(),
            List.of("all ([^ ]+) `uses:` steps carry a full commit SHA")),
        new Claim(
            "the properties the vendored snapshot declares an owl:inverseOf for",
            propertiesDeclaringAnInverse(schema),
            List.of("which ([^ ]+) properties in the snapshot do")),
        new Claim(
            "the classes the vendored snapshot declares",
            schema.classes().size(),
            List.of("none of (?:its|the) ([^ ]+) classes (?:it does declare )?reaches it by")),
        new Claim(
            "the terms in the declared range of ceterms:isSimilarTo",
            schema.property("ceterms:isSimilarTo").range().size(),
            List.of("declares it among ([^ ]+) terms")),
        new Claim(
            "the scheme-bound concept properties",
            schema.schemeBoundConceptProperties().size(),
            List.of("The covered set \u2014 ([^ ]+) properties")),
        new Claim(
            "the properties ranging on ceterms:CredentialAlignmentObject",
            propertiesRangingOn(schema, SchemaIndex.ALIGNMENT_RANGE_TERM),
            List.of("vendored snapshot ([^ ]+) properties declare")),
        new Claim(
            "the properties ranging on skos:Concept",
            propertiesRangingOn(schema, SchemaIndex.CONCEPT_RANGE_TERM),
            List.of("and ([^ ]+) declare `skos:Concept`")),
        new Claim(
            "the classes in a version property's declared domain",
            version.domain().size(),
            List.of(
                "each declares a domain of ([^ ]+) classes",
                "drops [^ ]+ of the ([^ ]+) classes in its own declared domain")),
        new Claim(
            "the classes in a version property's declared range",
            version.range().size(),
            List.of("domain of [^ ]+ classes and a range of ([^ ]+)")),
        new Claim(
            "the classes a version property's domain admits and its range drops",
            schema.domainOnlyClasses("ceterms:previousVersion").size(),
            List.of(
                "drop the identical ([^ ]+)",
                "drops ([^ ]+) of the [^ ]+ classes in its own declared domain")));
  }

  @Test
  @DisplayName("a document that compresses the arrangement to a sentence still says there are two")
  void theSecondCorpusSurvivesCompression() throws IOException {
    // The README describes parity/ahead/ at length, and CITATION.cff is where
    // the whole arrangement gets compressed into an abstract. Compression is
    // where the second corpus went missing: the abstract said both
    // implementations are tested against "one shared fixture corpus" and must
    // agree finding for finding, which is true of parity/fixtures/ and not of
    // this repository, where a second corpus exists precisely because they do
    // not agree there. A summary may be short. It may not be short by dropping
    // the exception.
    String abstractText =
        Files.readString(ROOT.resolve("CITATION.cff"), StandardCharsets.UTF_8)
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    List<String> denials =
        List.of("one shared fixture corpus", "one fixture corpus", "a single fixture corpus");
    for (String denial : denials) {
      assertTrue(
          !abstractText.contains(denial),
          "CITATION.cff says \""
              + denial
              + "\" while parity/ahead/fixtures/ exists. There is more than one corpus, and the"
              + " second one is where the port and the reference disagree on purpose.");
    }

    // ADR 0004 arranges for the ahead corpus to delete itself once the pin
    // catches up. When it does, this assertion inverts rather than lapsing, so
    // the abstract cannot go on advertising a corpus that is gone.
    if (jsonFilesIn("parity/ahead/fixtures") > 0) {
      assertTrue(
          abstractText.contains("second corpus"),
          "parity/ahead/fixtures/ exists and CITATION.cff does not mention a second corpus, so the"
              + " abstract describes an agreement that is only true of parity/fixtures/");
    } else {
      assertTrue(
          !abstractText.contains("second corpus"),
          "parity/ahead/ is gone, which is what ADR 0004 arranged for, and CITATION.cff still"
              + " advertises a second corpus. Delete the sentence with the corpus.");
    }
  }

  @Test
  @DisplayName("the three version properties really do declare the one shape the README describes")
  void theVersionPropertiesAgreeWithEachOther() {
    SchemaIndex schema = SchemaLoader.load();
    List<String> terms = List.copyOf(new TreeSet<>(SchemaIndex.VERSION_PROPERTIES));
    Set<String> dropped = schema.domainOnlyClasses(terms.get(0));
    for (String term : terms) {
      SchemaIndex.PropertyDef property = schema.property(term);
      assertNotNull(property, term + " is not in the vendored snapshot at all");
      assertTrue(
          property.range().size() < property.domain().size(),
          term + ": the README says the range is a strict subset of the domain, and it is not");
      assertEquals(
          dropped,
          schema.domainOnlyClasses(term),
          term
              + ": the README says all three version properties drop the identical classes. They no"
              + " longer do, so the sentence describing them is about a snapshot this repository no"
              + " longer vendors.");
    }
  }

  @Test
  @DisplayName("no class in the vendored snapshot reaches rdfs:Resource")
  void nothingReachesTheClassOfEverything() {
    // The ruling the universal-range disposition rests on, and the reason the
    // class count above is worth publishing at all: matching a target's declared
    // classes against rdfs:Resource rejects every entity, where the declaration
    // accepts every entity. If a refreshed snapshot ever declares the class or a
    // path to it, that inversion stops being one and the disposition has to be
    // reread rather than kept.
    SchemaIndex schema = SchemaLoader.load();
    assertTrue(
        !schema.classes().containsKey("rdfs:Resource"),
        "the vendored snapshot now declares rdfs:Resource as a class");
    List<String> reaching = new ArrayList<>();
    for (String term : schema.classes().keySet()) {
      Set<String> ancestors = new TreeSet<>(schema.ancestorsOf(term));
      ancestors.remove(term);
      if (ancestors.contains("rdfs:Resource")) {
        reaching.add(term);
      }
    }
    assertEquals(
        List.of(), reaching, "a vendored class now reaches rdfs:Resource by rdfs:subClassOf");
  }

  @Test
  @DisplayName("every fixture in both corpora is accounted for in parity/PROVENANCE.md")
  void everyFixtureHasItsProvenance() throws IOException {
    String provenance =
        Files.readString(ROOT.resolve("parity/PROVENANCE.md"), StandardCharsets.UTF_8);
    List<String> missing = new ArrayList<>();
    for (String corpus : List.of("parity/fixtures", "parity/ahead/fixtures")) {
      for (String name : jsonNamesIn(corpus)) {
        if (!provenance.contains(name)) {
          missing.add(corpus + "/" + name);
        }
      }
    }
    assertEquals(
        List.of(),
        missing,
        "a fixture nothing says where it came from or why it exists. CONTRIBUTING.md asks for a"
            + " PROVENANCE row with every fixture; two ahead fixtures once went without one.");
  }

  @Test
  @DisplayName("the fixtures PROVENANCE calls vendored from the sibling are really there")
  void theVendoredFixtureListIsReal() throws IOException {
    Set<String> listed = fixturesProvenanceCallsVendored();
    assertTrue(
        !listed.isEmpty(),
        "parity/PROVENANCE.md no longer lists the fixtures it vendored from the sibling, so the"
            + " counts derived from that list are being derived from nothing");
    Set<String> present = new TreeSet<>(jsonNamesIn("parity/fixtures"));
    Set<String> absent = new TreeSet<>(listed);
    absent.removeAll(present);
    assertEquals(
        Set.of(), absent, "parity/PROVENANCE.md names a vendored fixture that is not here");

    Set<String> reference = referenceCodes();
    Set<String> exercised = codesExercisedBy(listed);
    Set<String> foreign = new TreeSet<>(exercised);
    foreign.removeAll(reference);
    assertEquals(
        Set.of(),
        foreign,
        "a fixture vendored from the sibling produces a code the pinned reference does not emit,"
            + " so the untested-rules figure derived from it would be counting the wrong set");
  }

  /** Each live document, whitespace collapsed so a sentence can be matched across a line break. */
  private static Map<String, String> liveDocuments() throws IOException {
    Map<String, String> documents = new LinkedHashMap<>();
    for (String relative : LIVE_DOCUMENTS) {
      String raw = Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
      if (relative.endsWith(".java")) {
        // Javadoc continuation markers, so a sentence in a comment reads as prose.
        raw = raw.replaceAll("(?m)^[ \t]*\\*[ \t]?", " ");
      }
      documents.put(relative, raw.replaceAll("\\s+", " "));
    }
    return documents;
  }

  /** A published figure as an integer, in digits or spelled out, or null if it is neither. */
  private static Integer number(String token) {
    String cleaned = token.replaceAll("^[^0-9A-Za-z]+", "").replaceAll("[^0-9A-Za-z]+$", "");
    if (cleaned.matches("[0-9]+")) {
      return Integer.valueOf(cleaned);
    }
    int word = NUMBER_WORDS.indexOf(cleaned.toLowerCase(Locale.ROOT));
    return word < 0 ? null : Integer.valueOf(word);
  }

  /**
   * The branch-coverage floor {@code build.gradle.kts} enforces, as a whole percentage.
   *
   * <p>Read out of the build rather than written down, because the floor and the sentences naming
   * it are the same fact stated twice. Three sentences published it and nothing derived it: raising
   * the floor is a one-line edit to a task that {@code :test} does not depend on, so the change
   * that invalidates all three was the change least likely to be noticed.
   *
   * <p>Deliberately strict about the shape it reads. A build that no longer states the floor the
   * way this expects fails here rather than falling back on a default, because a floor this gate
   * can no longer find is a floor nothing is checking against the documents again.
   */
  private static int branchCoverageFloorPercent() throws IOException {
    String build =
        Files.readString(ROOT.resolve("build.gradle.kts"), StandardCharsets.UTF_8)
            .replaceAll("\\s+", " ");
    Matcher matcher =
        Pattern.compile("counter = \"BRANCH\" minimum = \"([0-9.]+)\"").matcher(build);
    assertTrue(
        matcher.find(),
        "build.gradle.kts no longer declares a BRANCH coverage minimum in the shape this test"
            + " reads, so the floor the documents publish is being compared against nothing");
    BigDecimal percent = new BigDecimal(matcher.group(1)).movePointRight(2);
    assertTrue(
        percent.stripTrailingZeros().scale() <= 0,
        "the branch-coverage floor is "
            + matcher.group(1)
            + ", which is not a whole percentage. The documents state it as a whole number; either"
            + " state it the way the build now expresses it, or move the floor back to one.");
    return percent.intValueExact();
  }

  /**
   * How many properties the vendored snapshot gives an {@code owl:inverseOf}.
   *
   * <p>This is the whole reach of the nested-back-reference disposition, so the README states it to
   * say how narrow the permission is. Derived rather than typed: a refreshed snapshot that declares
   * more inverse pairs widens that disposition, and the sentence claiming it is narrow has to move
   * with it.
   */
  private static int propertiesDeclaringAnInverse(SchemaIndex schema) {
    int count = 0;
    for (SchemaIndex.PropertyDef property : schema.properties().values()) {
      if (property.inverse() != null) {
        count++;
      }
    }
    return count;
  }

  /**
   * How many {@code uses:} steps the workflows carry, failing if any is not pinned to a full commit
   * SHA.
   *
   * <p>The README and the metrics ledger both state that every action is pinned. That was a
   * sentence a human kept true. It is now a count, and an unpinned step fails here rather than
   * being noticed on the next read: a supply-chain control nothing measures is a control nobody is
   * checking.
   */
  private static int shaPinnedWorkflowSteps() throws IOException {
    Pattern uses = Pattern.compile("uses:\\s*(\\S+)");
    List<String> unpinned = new ArrayList<>();
    int steps = 0;
    List<Path> workflows;
    try (Stream<Path> files = Files.list(ROOT.resolve(".github/workflows"))) {
      workflows = files.filter(path -> String.valueOf(path).endsWith(".yml")).sorted().toList();
    }
    assertTrue(!workflows.isEmpty(), "no workflows found, so this figure is derived from nothing");
    for (Path workflow : workflows) {
      Matcher matcher = uses.matcher(Files.readString(workflow, StandardCharsets.UTF_8));
      while (matcher.find()) {
        steps++;
        if (!matcher.group(1).matches(".+@[0-9a-f]{40}")) {
          unpinned.add(workflow.getFileName() + ": " + matcher.group(1));
        }
      }
    }
    assertEquals(List.of(), unpinned, "a workflow step is not pinned to a full commit SHA");
    return steps;
  }

  private static int propertiesRangingOn(SchemaIndex schema, String rangeTerm) {
    int count = 0;
    for (SchemaIndex.PropertyDef property : schema.properties().values()) {
      if (property.range().contains(rangeTerm)) {
        count++;
      }
    }
    return count;
  }

  /**
   * The fixture names {@code parity/PROVENANCE.md} lists as vendored from the sibling, read out of
   * the indented block rather than repeated here, so the figures derived from it move with it.
   */
  private static Set<String> fixturesProvenanceCallsVendored() throws IOException {
    String provenance =
        Files.readString(ROOT.resolve("parity/PROVENANCE.md"), StandardCharsets.UTF_8);
    Matcher matcher =
        Pattern.compile("(?m)^ {4}([a-z0-9_]+\\.json)$").matcher(provenance.replace("\r\n", "\n"));
    Set<String> names = new TreeSet<>();
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  /** Every distinct finding code the reference reports for the named fixtures. */
  private static Set<String> codesExercisedBy(Set<String> names) throws IOException {
    Set<String> codes = new TreeSet<>();
    for (String name : names) {
      JsonNode document =
          MAPPER.readTree(
              Files.readString(
                  ROOT.resolve("parity/expected").resolve(name), StandardCharsets.UTF_8));
      for (JsonNode finding : document.get("findings")) {
        codes.add(finding.get("code").asText());
      }
    }
    return codes;
  }

  private static Set<String> expectationNames() throws IOException {
    return new TreeSet<>(jsonNamesIn("parity/expected"));
  }

  private static Set<String> referenceCodes() throws IOException {
    JsonNode record =
        MAPPER.readTree(
            Files.readString(ROOT.resolve("parity/reference-codes.json"), StandardCharsets.UTF_8));
    Set<String> codes = new TreeSet<>();
    for (JsonNode code : record.get("codes")) {
      codes.add(code.asText());
    }
    return codes;
  }

  private static int jsonFilesIn(String relative) throws IOException {
    return jsonNamesIn(relative).size();
  }

  private static List<String> jsonNamesIn(String relative) throws IOException {
    try (Stream<Path> files = Files.list(ROOT.resolve(relative))) {
      return files
          .map(path -> String.valueOf(path.getFileName()))
          .filter(name -> name.endsWith(".json"))
          .sorted()
          .toList();
    }
  }
}
