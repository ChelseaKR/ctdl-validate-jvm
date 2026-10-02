package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every finding code the ported checks can emit, grouped by the check that emits it.
 *
 * <p>The list is here rather than implied by the code so that the parity corpus can be held to it:
 * a code with no fixture is a rule the two implementations are not being compared on, and {@code
 * ParityTest} fails when one appears.
 *
 * <p>This list is documentation, not authority. {@code FindingCodeCensusTest} parses every {@code
 * new Finding("...")} site out of {@code src/main/java} and fails if the two disagree in either
 * direction, so grouping a code here cannot be what makes it exist and forgetting one here cannot
 * hide it.
 */
public final class FindingCodes {

  /** Check 1, CTID format. */
  public static final List<String> CTID_FORMAT =
      List.of(
          "CTID_BARE_UUID",
          "CTID_MALFORMED",
          "CTID_UPPERCASE",
          "CTID_NOT_UUIDV4",
          "REGISTRY_URI_MALFORMED",
          "CTID_URI_MISMATCH");

  /** Check 2, identifier kind. */
  public static final List<String> IDENTIFIER_KIND =
      List.of("REF_BARE_UUID", "REF_BARE_CTID", "REF_NOT_IRI");

  /** Check 3, reference resolution. */
  public static final List<String> REFERENCES =
      List.of("REF_UNRESOLVED_BNODE", "REF_OUTSIDE_PAYLOAD", "REF_RESOLVED_SUPPLIED");

  /** Check 4, domain and range. */
  public static final List<String> DOMAIN_RANGE =
      List.of(
          "DOMAIN_VIOLATION",
          "RANGE_VIOLATION",
          "ISPARTOF_FRAMEWORK_MISMATCH",
          "UNKNOWN_CLASS",
          "UNKNOWN_PROPERTY",
          "RANGE_DOCS_CONFLICT",
          "CONCEPT_RANGE_CONFLICT",
          "VERSION_RANGE_CONFLICT");

  /** Check 5, inverse consistency. */
  public static final List<String> INVERSES = List.of("INVERSE_MISMATCH", "INVERSE_ONE_DIRECTION");

  /** Check 6, identity. */
  public static final List<String> IDENTITY = List.of("ID_DECLARED_MORE_THAN_ONCE");

  /** Check 7, concept-scheme membership. */
  public static final List<String> CONCEPT_SCHEME =
      List.of("CONCEPT_OUTSIDE_SCHEME", "CONCEPT_OUTSIDE_SNAPSHOT", "CONCEPT_NOT_IDENTIFIED");

  /** Check 8, language-map shape. */
  public static final List<String> LANGUAGE_MAP = List.of("LANGUAGE_MAP_EXPECTED");

  /** Check 9, term status. */
  public static final List<String> TERM_STATUS = List.of("TERM_UNSTABLE");

  /** All of them, in check order. */
  public static final List<String> ALL = concat();

  private FindingCodes() {}

  private static List<String> concat() {
    List<String> all = new ArrayList<>();
    all.addAll(CTID_FORMAT);
    all.addAll(IDENTIFIER_KIND);
    all.addAll(REFERENCES);
    all.addAll(DOMAIN_RANGE);
    all.addAll(INVERSES);
    all.addAll(IDENTITY);
    all.addAll(CONCEPT_SCHEME);
    all.addAll(LANGUAGE_MAP);
    all.addAll(TERM_STATUS);
    return List.copyOf(all);
  }
}
