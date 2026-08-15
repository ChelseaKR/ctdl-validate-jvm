package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a CTDL JSON-LD document into a flat, indexable node set.
 *
 * <p>This is deliberately not a general JSON-LD processor, and the port keeps that choice. Registry
 * payloads use a small, regular subset of JSON-LD: a {@code @graph} array (or a single entity, or a
 * bare array of entities), prefixed term keys, string IRIs as references, occasional inline nested
 * objects, and language-map literals. Handling that subset directly keeps the behaviour
 * deterministic and inspectable. Anything outside the subset is left alone rather than guessed at.
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
    if (data != null && data.isObject() && data.has("@graph")) {
      JsonNode top = data.get("@graph");
      if (!top.isArray()) {
        throw new Graph.DocumentException("@graph must be an array of entities");
      }
      entities = elements(top);
      prefix = "$.@graph";
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
    return new Graph(
        List.copyOf(builder.nodes), Map.copyOf(builder.byId), Map.copyOf(builder.byPath));
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

  private static final class Builder {
    private final SchemaIndex schema;
    private final List<Graph.Node> nodes = new ArrayList<>();
    private final Map<String, Graph.Node> byId = new LinkedHashMap<>();
    private final Map<String, Graph.Node> byPath = new LinkedHashMap<>();

    Builder(SchemaIndex schema) {
      this.schema = schema;
    }

    Graph.Node walk(JsonNode object, String path) {
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

      // The node is registered before its properties are read, so that nested
      // objects land in `nodes` after their parent, as the reference does. The
      // map handed to the node is an unmodifiable view of one still being
      // filled, which keeps that ordering without exposing a mutable field.
      Map<String, List<Value>> props = new LinkedHashMap<>();
      Graph.Node node =
          new Graph.Node(path, nodeId, List.copyOf(types), Collections.unmodifiableMap(props));
      nodes.add(node);
      byPath.put(path, node);
      if (nodeId != null) {
        byId.putIfAbsent(nodeId, node);
      }

      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
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
            Graph.Node child = walk(item, path + "." + prop + "[" + index + "]");
            values.add(new Value.Nested(child.path(), child.nodeId()));
          }
        }
        props.put(prop, List.copyOf(values));
      }
      return node;
    }
  }
}
