package io.github.chelseakr.ctdlvalidate;

import java.util.Objects;

/**
 * Where a rule comes from. Every finding carries one.
 *
 * @param citation the published sentence or declaration the rule rests on, quoted or paraphrased
 * @param url where that source lives
 * @param retrieved the date the cited source was downloaded, or {@code "-"} when the citation is
 *     tool policy rather than an external document
 */
public record Rule(String citation, String url, String retrieved) {
  public Rule {
    Objects.requireNonNull(citation, "citation");
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(retrieved, "retrieved");
  }
}
