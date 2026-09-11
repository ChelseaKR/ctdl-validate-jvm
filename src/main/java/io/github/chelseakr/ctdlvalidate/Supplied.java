package io.github.chelseakr.ctdlvalidate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The side index built from documents passed with {@code --resolve}.
 *
 * <p>A CTDL payload is not the whole story about itself. Registry documents reference other
 * Registry documents by URI as a matter of course, and check 3 reports every one of those
 * UNVERIFIABLE, which is the honest answer when the referenced document is not in hand and a
 * permanent non-answer when there is no way to put it there. {@code --resolve} is that way: it
 * takes documents the operator already has and indexes the entities they declare, so a reference
 * into one of them stops being unknowable and check 4 can ask whether its target is of the class
 * the property's declared range requires.
 *
 * <p>Three properties are load-bearing, as they are in the reference implementation's {@code
 * session.py}, which this is a port of:
 *
 * <ul>
 *   <li><b>Nothing is fetched.</b> Supplied documents are read from the local filesystem. {@code
 *       OfflineGuaranteeTest} still holds.
 *   <li><b>Supplied documents are never validated.</b> They go into this side index of {@code @id}
 *       to class, not into the graph being checked, so adding a neighbour can never change how many
 *       entities a report is about, nor put someone else's document's defects in your report.
 *   <li><b>An unresolved reference stays UNVERIFIABLE.</b> Supplying documents can turn a
 *       non-answer into an answer; it can never turn a non-answer into a failure.
 * </ul>
 *
 * @param documents every document supplied, spelled the way the reference prints a path, in the
 *     order they were read. Recorded even when none of them resolved anything, because an
 *     UNVERIFIABLE finding has to be able to say whether it is unverifiable for want of a document
 *     or in spite of the documents given.
 * @param entities every identified entity those documents declare, keyed by {@code @id}, in the
 *     order first seen
 */
public record Supplied(List<String> documents, Map<String, Supplied.Entity> entities) {

  /**
   * One {@code @id} declared by a document passed with {@code --resolve}.
   *
   * @param nodeId the identifier
   * @param types its declared {@code @type}s, compacted, as the declaration wrote them
   * @param source the document it was read from, spelled as {@link Supplied#documents} spells it
   */
  public record Entity(String nodeId, List<String> types, String source) {
    public Entity {
      Objects.requireNonNull(nodeId, "nodeId");
      types = List.copyOf(types);
      Objects.requireNonNull(source, "source");
    }
  }

  private static final Supplied NONE = new Supplied(List.of(), Map.of());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public Supplied {
    documents = List.copyOf(documents);
    // Insertion order, not Map.copyOf: the order entities were first seen is
    // the order the reference's dict holds them in.
    entities = Collections.unmodifiableMap(new LinkedHashMap<>(entities));
  }

  /** A run with nothing supplied. */
  public static Supplied none() {
    return NONE;
  }

  /** Whether anything was supplied at all, even a document that declares nothing. */
  public boolean anyDocuments() {
    return !documents.isEmpty();
  }

  /** The supplied entity a reference names, or null. Only a string value can name one. */
  public Entity get(Value value) {
    return value instanceof Value.Text text ? entities.get(text.text()) : null;
  }

  /** A phrase for a finding message naming what was in hand and missed. */
  public String shortfall() {
    int count = documents.size();
    String noun = count == 1 ? "document" : "documents";
    return "None of the "
        + count
        + " "
        + noun
        + " supplied with --resolve ("
        + String.join(", ", documents)
        + ") declares it.";
  }

  /**
   * Index every identified entity in the supplied documents.
   *
   * <p>Where two supplied documents declare the same {@code @id}, the first in the order read wins
   * and the second is ignored. That is the reference's deterministic rule rather than a considered
   * one: two documents disagreeing about what an {@code @id} is are a problem neither
   * implementation tries to adjudicate, and every resolution names the file it came from so the
   * disagreement is visible rather than silent. Blank nodes are never indexed, because a blank node
   * identifier means nothing outside the graph that declares it.
   *
   * @param given the paths as given on the command line, each a document or a directory of them
   * @param base what a relative path is read against; the paths printed are the ones given
   * @throws Graph.DocumentException for a document that cannot be read, is not JSON, or is not a
   *     shape the tool reads
   */
  public static Supplied build(List<String> given, Path base, SchemaIndex schema) {
    List<String> documents = new ArrayList<>();
    Map<String, Entity> entities = new LinkedHashMap<>();
    for (String path : collectPaths(given, base)) {
      Graph graph = load(path, base, schema);
      documents.add(path);
      for (Graph.Node node : graph.nodes()) {
        String nodeId = node.nodeId();
        if (nodeId == null || nodeId.startsWith("_:")) {
          continue; // blank nodes mean nothing outside the graph declaring them
        }
        entities.putIfAbsent(nodeId, new Entity(nodeId, node.types(), path));
      }
    }
    return new Supplied(documents, entities);
  }

  /**
   * Expand directories into the {@code .json} files directly inside them.
   *
   * <p>Sorted, and one level deep only, as the reference does: a run must not depend on filesystem
   * ordering, and walking a tree the operator did not point at is a surprise. A path that is not a
   * directory is taken as given, whatever it is called.
   */
  static List<String> collectPaths(List<String> given, Path base) {
    List<String> found = new ArrayList<>();
    for (String raw : given) {
      String path = pythonPath(raw);
      Path onDisk = base.resolve(path);
      if (!Files.isDirectory(onDisk)) {
        found.add(path);
        continue;
      }
      List<String> names = new ArrayList<>();
      try (DirectoryStream<Path> entries = Files.newDirectoryStream(onDisk)) {
        for (Path entry : entries) {
          String name = String.valueOf(entry.getFileName());
          if (".json".equals(suffix(name))) {
            names.add(name);
          }
        }
      } catch (IOException exception) {
        throw new Graph.DocumentException("cannot read " + path + ": " + exception.getMessage());
      }
      names.sort(CodePointOrder.COMPARATOR);
      for (String name : names) {
        found.add(child(path, name));
      }
    }
    return List.copyOf(found);
  }

  /**
   * A path spelled the way Python's {@code pathlib} spells it, because the reference prints the
   * path of every supplied document inside its findings and the two must agree to the byte.
   *
   * <p>{@code PurePosixPath} collapses repeated separators, drops {@code .} segments and a trailing
   * separator, keeps {@code ..}, spells an empty path {@code .}, and preserves a leading {@code //}
   * but not a leading {@code ///}. {@code java.nio.file.Path} does not normalize any of that on
   * construction, so {@code ./a.json} would print differently in the two implementations.
   */
  static String pythonPath(String given) {
    String root;
    String rest;
    if (given.startsWith("//") && !given.startsWith("///")) {
      root = "//";
      rest = given.substring(2);
    } else if (given.startsWith("/")) {
      root = "/";
      rest = given.replaceFirst("^/+", "");
    } else {
      root = "";
      rest = given;
    }
    List<String> parts = new ArrayList<>();
    for (String part : rest.split("/", -1)) {
      if (!part.isEmpty() && !".".equals(part)) {
        parts.add(part);
      }
    }
    String joined = String.join("/", parts);
    if (root.isEmpty()) {
      return joined.isEmpty() ? "." : joined;
    }
    return root + joined;
  }

  /** {@code PurePath.suffix}: a leading dot is part of the name, not a suffix. */
  static String suffix(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : "";
  }

  /** {@code Path(directory) / name}, spelled as {@code pathlib} spells it. */
  private static String child(String directory, String name) {
    if (".".equals(directory)) {
      return name;
    }
    return directory.endsWith("/") ? directory + name : directory + "/" + name;
  }

  private static Graph load(String path, Path base, SchemaIndex schema) {
    String raw;
    try {
      raw = Files.readString(base.resolve(path), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new Graph.DocumentException("cannot read " + path + ": " + exception.getMessage());
    }
    JsonNode data;
    try {
      data = MAPPER.readTree(raw);
    } catch (JsonProcessingException exception) {
      throw new Graph.DocumentException(
          path + " is not valid JSON: " + exception.getOriginalMessage());
    }
    return GraphParser.parse(data, schema);
  }
}
