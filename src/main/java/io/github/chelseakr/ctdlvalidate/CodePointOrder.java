package io.github.chelseakr.ctdlvalidate;

import java.util.Comparator;

/**
 * String ordering by Unicode code point.
 *
 * <p>This exists because of a real difference between the two languages rather than as a
 * generality. The reference implementation orders findings, property keys, and class-name lists
 * with Python's {@code sorted}, which compares strings code point by code point. Java's {@link
 * String#compareTo} compares UTF-16 code units, which disagrees whenever a supplementary character
 * (U+10000 and above, stored as a surrogate pair) is compared against a BMP character in the range
 * U+E000–U+FFFF: the surrogate pair's leading unit sorts below those characters even though the
 * code point sorts above them.
 *
 * <p>CTDL payloads are usually ASCII, so the two orderings almost always agree. "Almost always" is
 * not a property a parity test can rest on, so the port sorts by code point everywhere the
 * reference implementation sorts.
 */
public final class CodePointOrder {

  /** Comparator form, for use with {@code Comparator.comparing(..., CodePointOrder.COMPARATOR)}. */
  public static final Comparator<String> COMPARATOR = CodePointOrder::compare;

  private CodePointOrder() {}

  /** Compares two strings by Unicode code point, as Python's {@code <} does. */
  public static int compare(String left, String right) {
    int i = 0;
    int j = 0;
    while (i < left.length() && j < right.length()) {
      int a = left.codePointAt(i);
      int b = right.codePointAt(j);
      if (a != b) {
        return Integer.compare(a, b);
      }
      i += Character.charCount(a);
      j += Character.charCount(b);
    }
    return Integer.compare(left.length() - i, right.length() - j);
  }
}
