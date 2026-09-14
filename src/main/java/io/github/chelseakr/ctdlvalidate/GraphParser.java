package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Parses a CTDL JSON-LD document into a flat, indexable node set.
 *
 * <p>This is deliberately not a general JSON-LD processor, and the port keeps that choice. Registry
 * payloads use a small, regular subset of JSON-LD: a {@code @graph} array (or a single entity, or a
 * bare array of entities), prefixed term keys, string IRIs as references, occasional inline nested
 * objects, and language-map literals. Handling that subset directly keeps the behaviour
 * deterministic and inspectable. Anything outside the subset is left alone rather than guessed at.
 *
 * <p>One rule of JSON-LD it does follow: one identifier, one entity. Where two node objects carry
 * the same {@code @id}, the second is merged into the first -- the union of their types and of
 * their property values -- rather than dropped, which made a verdict a function of array order. The
 * reference's ADR 0005 is the reason, and check 6 reports every merge.
 */
public final class GraphParser {

  private GraphParser() {}

  /**
   * Parse a decoded JSON document into a {@link Graph}.
   *
   * <p>Accepted shapes: an object with {@code @graph}, a single entity object, or an array of
   * entity objects.
   *
   * @throws Graph.DocumentException for shapes the tool does not read
   */
  public static Graph parse(JsonNode data, SchemaIndex schema) {
    List<JsonNode> entities;
    String prefix;
    String envelopeId = null;
    String envelopePath = null;
    if (data != null && data.isObject() && data.has("@graph")) {
      JsonNode top = data.get("@graph");
      if (!top.isArray()) {
        throw new Graph.DocumentException("@graph must be an array of entities");
      }
      entities = elements(top);
      prefix = "$.@graph";
      // Every other top-level key is a document-level declaration, but this one
      // is a Registry graph URI, and dropping it put it beyond every check.
      JsonNode outer = data.get("@id");
      if (outer != null && outer.isTextual()) {
        envelopeId = outer.textValue();
        envelopePath = "$.@id";
      }
    } else if (data != null && data.isObject()) {
      entities = List.of(data);
      prefix = "$";
    } else if (data != null && data.isArray()) {
      entities = elements(data);
      prefix = "$";
    } else {
      throw new Graph.DocumentException(
          "expected a JSON-LD object with @graph, a single entity object, or an array of entities");
    }

    Builder builder = new Builder(schema);
    for (int index = 0; index < entities.size(); index++) {
      JsonNode entity = entities.get(index);
      if (!entity.isObject()) {
        throw new Graph.DocumentException("entity at index " + index + " is not a JSON object");
      }
      boolean indexed = entities.size() > 1 || prefix.endsWith("@graph");
      builder.walk(entity, indexed ? prefix + "[" + index + "]" : prefix);
    }
    return builder.freeze(envelopeId, envelopePath);
  }

  private static List<JsonNode> elements(JsonNode array) {
    List<JsonNode> values = new ArrayList<>(array.size());
    array.forEach(values::add);
    return values;
  }

  /**
   * Python's notion of truth, for the one place the reference relies on it: {@code [raw_types] if
   * raw_types else []}. Null, false, zero, the empty string, and empty containers are all falsey.
   */
  private static boolean isTruthy(JsonNode node) {
    if (node == null || node.isNull()) {
      return false;
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isNumber()) {
      return node.doubleValue() != 0.0;
    }
    if (node.isTextual()) {
      return !node.textValue().isEmpty();
    }
    if (node.isContainerNode()) {
      return node.size() > 0;
    }
    return true;
  }

  private static boolean isReferenceOnly(JsonNode object) {
    return object.size() == 1 && object.has("@id") && object.get("@id").isTextual();
  }

  /** Turns a decoded JSON value into a property value. */
  private static Value toValue(JsonNode node) {
    return node.isTextual() ? new Value.Text(node.textValue()) : new Value.Json(node);
  }

  /**
   * Two declarations' {@code @type}s, unioned and sorted.
   *
   * <p>Sorted where a single declaration's types keep the order the document wrote them in, because
   * between two declarations there is no document order -- which one the walk reached first is a
   * function of array position -- and types reach the reader inside messages. The reference's
   * {@code _merged_types}, including returning the first unchanged when the second adds nothing.
   */
  static List<String> mergedTypes(List<String> first, List<String> second) {
    if (second.isEmpty() || new HashSet<>(first).containsAll(second)) {
      return first;
    }
    TreeSet<String> union = new TreeSet<>(CodePointOrder.COMPARATOR);
    union.addAll(first);
    union.addAll(second);
    return List.copyOf(union);
  }

  /** A node while the document is still being walked, before it is frozen into the graph. */
  private static final class MutableNode {
    private final String path;
    private final String nodeId;
    private List<String> types;
    private final Map<String, List<Value>> props = new LinkedHashMap<>();

    MutableNode(String path, String nodeId, List<String> types) {
      this.path = path;
      this.nodeId = nodeId;
      this.types = types;
    }
  }

  private static final class Builder {
    private final SchemaIndex schema;
    private final List<MutableNode> nodes = new ArrayList<>();
    private final Map<String, MutableNode> byId = new LinkedHashMap<>();
    private final Map<String, MutableNode> byPath = new LinkedHashMap<>();
    private final Map<String, List<String>> declarations = new LinkedHashMap<>();

    Builder(SchemaIndex schema) {
      this.schema = schema;
    }

    /** The node this declaration belongs to: a new one, or the one its identifier already names. */
    private MutableNode nodeFor(String path, String nodeId, List<String> types) {
      if (nodeId != null) {
        declarations.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(path);
      }
      MutableNode existing = nodeId != null ? byId.get(nodeId) : null;
      if (existing != null) {
        existing.types = mergedTypes(existing.types, types);
        byPath.put(path, existing);
        return existing;
      }
      MutableNode node = new MutableNode(path, nodeId, types);
      nodes.add(node);
      if (nodeId != null) {
        byId.put(nodeId, node);
      }
      byPath.put(path, node);
      return node;
    }

    MutableNode walk(JsonNode object, String path) {
      JsonNode rawId = object.get("@id");
      String nodeId = rawId != null && rawId.isTextual() ? rawId.textValue() : null;

      JsonNode rawTypes = object.get("@type");
      List<JsonNode> typeNodes;
      if (rawTypes != null && rawTypes.isArray()) {
        typeNodes = elements(rawTypes);
      } else if (isTruthy(rawTypes)) {
        typeNodes = List.of(rawTypes);
      } else {
        typeNodes = List.of();
      }
      List<String> types = new ArrayList<>();
      for (JsonNode type : typeNodes) {
        if (type.isTextual()) {
          types.add(schema.compactIri(type.textValue()));
        }
      }

      // Registered before its properties are read, so nested objects land in
      // `nodes` after their parent, as the reference does.
      MutableNode node = nodeFor(path, nodeId, List.copyOf(types));

      for (Map.Entry<String, JsonNode> field : object.properties()) {
        String key = field.getKey();
        if (key.startsWith("@")) {
          continue;
        }
        String prop = schema.compactIri(key);
        SchemaIndex.PropertyDef propDef = schema.property(prop);
        JsonNode raw = field.getValue();
        List<JsonNode> rawValues = raw.isArray() ? elements(raw) : List.of(raw);

        List<Value> values = new ArrayList<>(rawValues.size());
        for (int index = 0; index < rawValues.size(); index++) {
          JsonNode item = rawValues.get(index);
          if (!item.isObject()) {
            values.add(toValue(item));
          } else if (isReferenceOnly(item)) {
            values.add(new Value.Text(item.get("@id").textValue()));
          } else if (item.has("@value")) {
            values.add(toValue(item.get("@value")));
          } else if (propDef != null && propDef.languageMap()) {
            values.add(new Value.Json(item));
          } else {
            // The child may be merged into a node declared earlier, in which case
            // the reference points at that node's first path, as `child.path`
            // does in the reference.
            MutableNode child = walk(item, path + "." + prop + "[" + index + "]");
            values.add(new Value.Nested(child.path, child.nodeId));
          }
        }
        // Read after the values are built, because walking a nested object that
        // repeats this node's own identifier can already have set this property.
        List<Value> already = node.props.get(prop);
        if (already == null) {
          node.props.put(prop, values);
        } else {
          // A merge, or two keys compacting to one term. Values the node already
          // holds are not repeated; within one declaration they are kept as written.
          List<Value> merged = new ArrayList<>(already);
          for (Value value : values) {
            if (!PythonEquality.in(value, already)) {
              merged.add(value);
            }
          }
          node.props.put(prop, merged);
        }
      }
      return node;
    }

    Graph freeze(String envelopeId, String envelopePath) {
      Map<MutableNode, Graph.Node> frozen = new IdentityHashMap<>();
      List<Graph.Node> list = new ArrayList<>(nodes.size());
      for (MutableNode node : nodes) {
        Map<String, List<Value>> props = new LinkedHashMap<>();
        node.props.forEach((key, values) -> props.put(key, List.copyOf(values)));
        Graph.Node done =
            new Graph.Node(
                node.path,
                node.nodeId,
                List.copyOf(node.types),
                Collections.unmodifiableMap(props));
        frozen.put(node, done);
        list.add(done);
      }
      Map<String, Graph.Node> ids = new LinkedHashMap<>();
      byId.forEach((key, node) -> ids.put(key, frozen.get(node)));
      Map<String, Graph.Node> paths = new LinkedHashMap<>();
      byPath.forEach((key, node) -> paths.put(key, frozen.get(node)));
      Map<String, List<String>> declared = new LinkedHashMap<>();
      declarations.forEach((key, where) -> declared.put(key, List.copyOf(where)));
      return new Graph(
          List.copyOf(list),
          Collections.unmodifiableMap(ids),
          Collections.unmodifiableMap(paths),
          Collections.unmodifiableMap(declared),
          envelopeId,
          envelopePath);
    }
  }
}
