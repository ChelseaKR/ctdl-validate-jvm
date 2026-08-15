package io.github.chelseakr.ctdlvalidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The published CTID grammar, and the two things it is commonly confused with. */
class CtidTest {

  private static final String VALID = "ce-e8a41a52-6ff6-48f0-9872-889c87b093b7";

  @Test
  @DisplayName("the example from the published page is valid")
  void publishedExample() {
    Ctid.Shape shape = Ctid.classify(VALID);
    assertTrue(shape.matchesShape());
    assertTrue(shape.lowercase());
    assertTrue(shape.uuidV4());
    assertFalse(shape.bareUuid());
    assertEquals(39, VALID.length(), "the page says 39 characters");
  }

  @Test
  @DisplayName("a bare UUID is recognised as a bare UUID, not merely as malformed")
  void bareUuid() {
    Ctid.Shape shape = Ctid.classify("e8a41a52-6ff6-48f0-9872-889c87b093b7");
    assertFalse(shape.matchesShape());
    assertTrue(shape.bareUuid());
  }

  @Test
  @DisplayName("upper case matches the shape but not the published examples")
  void upperCase() {
    Ctid.Shape shape = Ctid.classify("ce-E8A41A52-6FF6-48F0-9872-889C87B093B7");
    assertTrue(shape.matchesShape());
    assertFalse(shape.lowercase());
    assertTrue(shape.uuidV4());
  }

  @Test
  @DisplayName("the version and variant nibbles are read where RFC 4122 puts them")
  void versionAndVariant() {
    assertFalse(Ctid.classify("ce-e8a41a52-6ff6-18f0-9872-889c87b093b7").uuidV4(), "version 1");
    assertFalse(Ctid.classify("ce-e8a41a52-6ff6-48f0-c872-889c87b093b7").uuidV4(), "bad variant");
    assertTrue(Ctid.classify("ce-e8a41a52-6ff6-48f0-a872-889c87b093b7").uuidV4());
  }

  @Test
  @DisplayName("neither a CTID nor a UUID is simply malformed")
  void malformed() {
    Ctid.Shape shape = Ctid.classify("not-a-ctid");
    assertFalse(shape.matchesShape());
    assertFalse(shape.bareUuid());
  }

  @Test
  @DisplayName("Registry resource and graph URIs both surrender their tail")
  void registryUris() {
    assertEquals(VALID, Ctid.registryUriTail(Ctid.REGISTRY_RESOURCE_PREFIX + VALID));
    assertEquals(VALID, Ctid.registryUriTail(Ctid.REGISTRY_GRAPH_PREFIX + VALID));
    assertNull(Ctid.registryUriTail("https://example.org/" + VALID));
  }
}
