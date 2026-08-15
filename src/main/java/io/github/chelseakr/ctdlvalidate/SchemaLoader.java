package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads and indexes the vendored CTDL and CTDL-ASN schema and context files.
 *
 * <p>The schema encodings supply class declarations (with {@code rdfs:subClassOf}) and property
 * declarations ({@code schema:domainIncludes}, {@code schema:rangeIncludes}, {@code
 * owl:inverseOf}). The JSON-LD contexts supply per-property value coercions: {@code {"@type":
 * "@id"}} marks identifier-valued properties, {@code {"@container": "@language"}} marks language
 * maps.
 *
 * <p>The files are read from the classpath and never from the network. See {@code
 * src/main/resources/vendor/SOURCES.md} for their source URLs, retrieval date, and SHA-256 hashes.
 */
public final class SchemaLoader {

  private static final String VENDOR_ROOT = "/vendor/";

  private static final List<String> SCHEMA_FILES =
      List.of("ctdl/schema.json", "ctdlasn/schema.json");
  private static final List<String> CONTEXT_FILES =
      List.of("ctdl/context.json", "ctdlasn/context.json");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static volatile SchemaIndex cached;

  private SchemaLoader() {}

  /** The indexed vocabularies, loaded once. */
  public static SchemaIndex load() {
    SchemaIndex local = cached;
    if (local == null) {
      synchronized (SchemaLoader.class) {
        local = cached;
        if (local == null) {
          local = build();
          cached = local;
        }
      }
    }
    return local;
  }

  /** Reads one vendored file off the classpath. */
  public static JsonNode readVendored(String relativePath) {
    String resource = VENDOR_ROOT + relativePath;
    try (InputStream stream = SchemaLoader.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IllegalStateException("vendored resource missing from the jar: " + resource);
      }
      return MAPPER.readTree(stream);
    } catch (IOException exception) {
      throw new UncheckedIOException("cannot read vendored " + resource, exception);
    }
  }

  private static SchemaIndex build() {
    Map<String, SchemaIndex.ClassDef> classes = new HashMap<>();
    Map<String, RawProperty> rawProperties = new HashMap<>();

    for (String file : SCHEMA_FILES) {
      JsonNode graph = readVendored(file).get("@graph");
      for (JsonNode entry : graph) {
        indexSchemaEntry(entry, classes, rawProperties);
      }
    }

    // First declaration wins, matching the reference's setdefault, so the CTDL
    // files are read before the CTDL-ASN ones and the order is load-bearing.
    Map<String, JsonNode> coercions = new LinkedHashMap<>();
    Map<String, String> prefixes = new LinkedHashMap<>();
    for (String file : CONTEXT_FILES) {
      JsonNode context = readVendored(file).get("@context");
      Iterator<Map.Entry<String, JsonNode>> fields = context.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (field.getValue().isTextual()) {
          prefixes.putIfAbsent(field.getKey(), field.getValue().textValue());
        } else if (field.getValue().isObject()) {
          coercions.putIfAbsent(field.getKey(), field.getValue());
        }
      }
    }

    Map<String, SchemaIndex.PropertyDef> properties = new HashMap<>();
    for (Map.Entry<String, RawProperty> entry : rawProperties.entrySet()) {
      JsonNode coercion = coercions.get(entry.getKey());
      boolean idCoerced = coercion != null && "@id".equals(text(coercion.get("@type")));
      boolean languageMap =
          coercion != null && "@language".equals(text(coercion.get("@container")));
      properties.put(
          entry.getKey(),
          new SchemaIndex.PropertyDef(
              entry.getKey(),
              Set.copyOf(entry.getValue().domain),
              Set.copyOf(entry.getValue().range),
              entry.getValue().inverse,
              idCoerced,
              languageMap));
    }

    return new SchemaIndex(classes, properties, prefixes);
  }

  private static void indexSchemaEntry(
      JsonNode entry,
      Map<String, SchemaIndex.ClassDef> classes,
      Map<String, RawProperty> rawProperties) {
    String term = text(entry.get("@id"));
    String entryType = text(entry.get("@type"));
    if (term == null) {
      return;
    }
    if ("rdfs:Class".equals(entryType)) {
      // Parents are sorted and merged with anything already declared, so a term
      // split across the two encodings ends up with the union of its parents.
      Set<String> parents = new TreeSet<>(CodePointOrder.COMPARATOR);
      parents.addAll(asStringList(entry.get("rdfs:subClassOf")));
      SchemaIndex.ClassDef existing = classes.get(term);
      if (existing != null) {
        parents.addAll(existing.parents());
      }
      classes.put(term, new SchemaIndex.ClassDef(term, List.copyOf(new ArrayList<>(parents))));
    } else if ("rdf:Property".equals(entryType)) {
      RawProperty raw = rawProperties.computeIfAbsent(term, key -> new RawProperty());
      raw.domain.addAll(asStringList(entry.get("schema:domainIncludes")));
      raw.range.addAll(asStringList(entry.get("schema:rangeIncludes")));
      List<String> inverse = asStringList(entry.get("owl:inverseOf"));
      if (!inverse.isEmpty()) {
        raw.inverse = inverse.get(0);
      }
    }
  }

  private static String text(JsonNode node) {
    return node != null && node.isTextual() ? node.textValue() : null;
  }

  /** A JSON value read as a list of strings: absent is empty, a scalar is a list of one. */
  private static List<String> asStringList(JsonNode node) {
    if (node == null || node.isNull()) {
      return List.of();
    }
    if (node.isArray()) {
      List<String> values = new ArrayList<>();
      for (JsonNode element : node) {
        if (element.isTextual()) {
          values.add(element.textValue());
        }
      }
      return values;
    }
    return node.isTextual() ? List.of(node.textValue()) : List.of();
  }

  /** Property declarations accumulated across both encodings before they are frozen. */
  private static final class RawProperty {
    private final Set<String> domain = new LinkedHashSet<>();
    private final Set<String> range = new LinkedHashSet<>();
    private String inverse;
  }

  /**
   * The {@code @graph} array of a vendored schema encoding, unmodified. Exposed so tests can check
   * the index against the files rather than against a copy of them.
   */
  public static List<JsonNode> vendorGraph(String relativePath) {
    JsonNode graph = readVendored(relativePath).get("@graph");
    if (graph == null || !graph.isArray()) {
      throw new IllegalStateException("vendored " + relativePath + " has no @graph array");
    }
    List<JsonNode> entries = new ArrayList<>();
    graph.forEach(entries::add);
    return Collections.unmodifiableList(entries);
  }
}
