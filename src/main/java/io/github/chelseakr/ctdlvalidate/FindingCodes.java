package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every finding code the ported checks can emit, grouped by the check that emits it.
 *
 * <p>The list is here rather than implied by the code so that the parity corpus can be held to it:
 * a code with no fixture is a rule the two implementations are not being compared on, and {@code
 * ParityTest} fails when one appears.
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
      List.of("REF_UNRESOLVED_BNODE", "REF_OUTSIDE_PAYLOAD");

  /** Check 4, domain and range. */
  public static final List<String> DOMAIN_RANGE =
      List.of(
          "DOMAIN_VIOLATION",
          "RANGE_VIOLATION",
          "ISPARTOF_FRAMEWORK_MISMATCH",
          "UNKNOWN_CLASS",
          "UNKNOWN_PROPERTY",
          "RANGE_DOCS_CONFLICT");

  /** Check 5, inverse consistency. */
  public static final List<String> INVERSES = List.of("INVERSE_MISMATCH", "INVERSE_ONE_DIRECTION");

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
    return List.copyOf(all);
  }
}
