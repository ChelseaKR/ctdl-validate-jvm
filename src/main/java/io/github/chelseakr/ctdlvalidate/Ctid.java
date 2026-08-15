package io.github.chelseakr.ctdlvalidate;

import java.util.regex.Pattern;

/**
 * CTID grammar, from the published definition.
 *
 * <p>Source ("About the CTID", <a href="https://credreg.net/ctdl/ctid">credreg.net/ctdl/ctid</a>,
 * retrieved 2026-08-06): "Each CTID is made up of a standard UUID v4 prefixed with ce-", "in the
 * form 8-4-4-4-12", "a total of 39 characters (34 hexadecimal characters and 5 hyphens)". Example:
 * ce-e8a41a52-6ff6-48f0-9872-889c87b093b7.
 */
public final class Ctid {

  private static final String HEX_GROUPS =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  /** Same shape as the published grammar, any case: separates case problems from shape problems. */
  private static final Pattern CTID_ANY_CASE =
      Pattern.compile("^ce-" + HEX_GROUPS + "$", Pattern.CASE_INSENSITIVE);

  /** A UUID with no ce- prefix: the "bare UUID where a CTID belongs" bug class. */
  private static final Pattern BARE_UUID_ANY_CASE =
      Pattern.compile("^" + HEX_GROUPS + "$", Pattern.CASE_INSENSITIVE);

  public static final String EXPECTED_GRAMMAR =
      "ce- followed by a UUID v4 in 8-4-4-4-12 form, 39 characters, lower case hexadecimal, e.g."
          + " ce-e8a41a52-6ff6-48f0-9872-889c87b093b7";

  public static final String REGISTRY_RESOURCE_PREFIX =
      "https://credentialengineregistry.org/resources/";
  public static final String REGISTRY_GRAPH_PREFIX = "https://credentialengineregistry.org/graph/";

  /**
   * Character offsets within a shape-valid CTID ("ce-" + UUID): the UUID version nibble and variant
   * nibble per RFC 4122 sections 4.1.1 and 4.1.3.
   */
  private static final int VERSION_OFFSET = 17;

  private static final int VARIANT_OFFSET = 22;

  private Ctid() {}

  /** What a candidate CTID string is and is not. */
  public record Shape(boolean matchesShape, boolean lowercase, boolean uuidV4, boolean bareUuid) {}

  public static Shape classify(String value) {
    if (CTID_ANY_CASE.matcher(value).matches()) {
      String lowered = value.toLowerCase(java.util.Locale.ROOT);
      boolean versionOk = lowered.charAt(VERSION_OFFSET) == '4';
      boolean variantOk = "89ab".indexOf(lowered.charAt(VARIANT_OFFSET)) >= 0;
      return new Shape(true, value.equals(lowered), versionOk && variantOk, false);
    }
    return new Shape(
        false,
        value.equals(value.toLowerCase(java.util.Locale.ROOT)),
        false,
        BARE_UUID_ANY_CASE.matcher(value).matches());
  }

  public static boolean isBareUuidAnyCase(String value) {
    return BARE_UUID_ANY_CASE.matcher(value).matches();
  }

  public static boolean isCtidAnyCase(String value) {
    return CTID_ANY_CASE.matcher(value).matches();
  }

  /** The CTID portion of a Registry resource/graph URI, or null when the value is neither. */
  public static String registryUriTail(String value) {
    for (String prefix : new String[] {REGISTRY_RESOURCE_PREFIX, REGISTRY_GRAPH_PREFIX}) {
      if (value.startsWith(prefix)) {
        return value.substring(prefix.length());
      }
    }
    return null;
  }
}
