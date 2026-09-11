package io.github.chelseakr.ctdlvalidate;

import java.util.Objects;

/**
 * What one validation run has in front of it: the primary payload, the schema, and whatever was
 * supplied with {@code --resolve}.
 *
 * <p>Every check takes one of these rather than reaching for a global, so the single thing that can
 * change a finding's severity -- whether the referenced document was in hand -- is explicit at
 * every use. The reference implementation's checks take its {@code Session} for the same reason.
 *
 * @param graph the payload being validated
 * @param schema the vendored vocabularies
 * @param supplied the side index built from {@code --resolve} documents; {@link Supplied#none()}
 *     when nothing was supplied
 */
public record Session(Graph graph, SchemaIndex schema, Supplied supplied) {
  public Session {
    Objects.requireNonNull(graph, "graph");
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(supplied, "supplied");
  }
}
