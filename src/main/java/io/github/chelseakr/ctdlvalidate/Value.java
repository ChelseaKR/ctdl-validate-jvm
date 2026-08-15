package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One value of one property, after the document has been flattened.
 *
 * <p>The reference implementation stores property values as untyped Python objects and asks {@code
 * isinstance(value, str)} and {@code isinstance(value, NestedRef)} at each use. The three cases
 * those questions distinguish are named here instead, so the checks read as pattern matches and the
 * compiler can tell when one is unhandled.
 */
public sealed interface Value {

  /** A string value: an IRI, a blank node identifier, or plain text. */
  record Text(String text) implements Value {}

  /** Any other JSON value: a number, a boolean, null, an array, or a language map. */
  record Json(JsonNode node) implements Value {}

  /**
   * A property value that was an inline (nested) object in the document. The nested object is
   * registered as a node in its own right; containment means the reference trivially resolves
   * inside the payload.
   */
  record Nested(String targetPath, String targetId) implements Value {}
}
