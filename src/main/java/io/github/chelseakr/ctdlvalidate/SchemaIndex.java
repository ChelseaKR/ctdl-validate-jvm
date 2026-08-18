package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The indexed CTDL and CTDL-ASN vocabularies: classes with their {@code rdfs:subClassOf} parents,
 * properties with their declared domain, range, inverse, and JSON-LD value coercions, and the
 * prefix table needed to compact a full IRI.
 *
 * <p>Everything here is read out of the vendored schema encodings and contexts. Nothing is encoded
 * by hand. See {@code src/main/resources/vendor/SOURCES.md}.
 */
public final class SchemaIndex {

  /**
   * Range terms that denote literals rather than entities: the {@code schema:rangeIncludes} values
   * in the vendored encodings that are not declared classes.
   */
  public static final Set<String> LITERAL_RANGE_TERMS =
      Set.of(
          "xsd:anyURI",
          "xsd:boolean",
          "xsd:date",
          "xsd:dateTime",
          "xsd:decimal",
          "xsd:duration",
          "xsd:float",
          "xsd:integer",
          "xsd:language",
          "xsd:string",
          "rdf:langString",
          "rdfs:Literal",
          "schema:Date",
          "schema:Duration");

  /**
   * Prefixes whose unknown terms are worth a WARNING. Terms in other namespaces (schema.org, dct,
   * foaf, ...) are not CTDL's to judge and are skipped.
   */
  public static final List<String> CHECKED_PREFIXES = List.of("ceterms:", "ceasn:");

  /**
   * The two classes CTDL uses, inconsistently, to range a reference to a term from one of its own
   * concept schemes. See {@link Rules#conceptRangeConflict}.
   */
  public static final String CONCEPT_RANGE_TERM = "skos:Concept";

  /**
   * @see #CONCEPT_RANGE_TERM
   */
  public static final String ALIGNMENT_RANGE_TERM = "ceterms:CredentialAlignmentObject";

  /** A declared class and its direct {@code rdfs:subClassOf} parents. */
  public record ClassDef(String term, List<String> parents) {}

  /** A declared property and everything the schema and context say about it. */
  public record PropertyDef(
      String term,
      Set<String> domain,
      Set<String> range,
      String inverse,
      boolean idCoerced,
      boolean languageMap,
      Set<String> targetScheme) {

    /** True when at least one declared range term is an entity class rather than a literal type. */
    public boolean rangeHasEntities() {
      for (String term : range) {
        if (!LITERAL_RANGE_TERMS.contains(term)) {
          return true;
        }
      }
      return false;
    }

    /**
     * True when this property names a concept scheme and ranges on {@code skos:Concept}.
     *
     * <p>These are the properties the concept-range inconsistency reaches; see {@link
     * Rules#conceptRangeConflict}. CTDL declares one kind of value — a term drawn from one of its
     * own concept schemes — with two incompatible ranges depending on the property, and {@code
     * meta:targetScheme} is what separates that case from ordinary SKOS: it is declared on both
     * families and absent from {@code ceterms:classification} and {@code skos:broader}, which mean
     * an actual {@code skos:Concept} and stay errors.
     */
    public boolean schemeBoundConcept() {
      return range.contains(CONCEPT_RANGE_TERM) && !targetScheme.isEmpty();
    }
  }

  private final Map<String, ClassDef> classes;
  private final Map<String, PropertyDef> properties;

  /** Namespace/prefix pairs, longest namespace first so the most specific prefix wins. */
  private final List<Map.Entry<String, String>> namespaces;

  private final Map<String, Set<String>> ancestorCache = new HashMap<>();

  SchemaIndex(
      Map<String, ClassDef> classes,
      Map<String, PropertyDef> properties,
      Map<String, String> prefixes) {
    this.classes = Map.copyOf(classes);
    this.properties = Map.copyOf(properties);

    List<Map.Entry<String, String>> pairs = new ArrayList<>();
    for (Map.Entry<String, String> entry : prefixes.entrySet()) {
      pairs.add(Map.entry(entry.getValue(), entry.getKey()));
    }
    // A stable sort on descending namespace length, so ties keep the order the
    // vendored context files declare them in, exactly as the reference does.
    pairs.sort(
        Comparator.comparingInt((Map.Entry<String, String> p) -> p.getKey().length()).reversed());
    this.namespaces = List.copyOf(pairs);
  }

  public Map<String, ClassDef> classes() {
    return classes;
  }

  public Map<String, PropertyDef> properties() {
    return properties;
  }

  public PropertyDef property(String term) {
    return properties.get(term);
  }

  /** Compact a full IRI to {@code prefix:local} using the vendored contexts. */
  public String compactIri(String iri) {
    if (!iri.contains("://")) {
      return iri;
    }
    for (Map.Entry<String, String> pair : namespaces) {
      String namespace = pair.getKey();
      if (iri.startsWith(namespace) && iri.length() > namespace.length()) {
        return pair.getValue() + ":" + iri.substring(namespace.length());
      }
    }
    return iri;
  }

  /** The class itself plus its transitive {@code rdfs:subClassOf} parents. */
  public Set<String> ancestorsOf(String term) {
    Set<String> cached = ancestorCache.get(term);
    if (cached != null) {
      return cached;
    }
    Set<String> seen = new HashSet<>();
    Deque<String> stack = new ArrayDeque<>();
    stack.push(term);
    while (!stack.isEmpty()) {
      String current = stack.pop();
      if (!seen.add(current)) {
        continue;
      }
      ClassDef definition = classes.get(current);
      if (definition != null) {
        for (String parent : definition.parents()) {
          stack.push(parent);
        }
      }
    }
    Set<String> result = Set.copyOf(seen);
    ancestorCache.put(term, result);
    return result;
  }

  /** True when any node type, or an ancestor of it, is in {@code allowed}. */
  public boolean classMatches(List<String> nodeTypes, Set<String> allowed) {
    for (String type : nodeTypes) {
      for (String ancestor : ancestorsOf(type)) {
        if (allowed.contains(ancestor)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Properties naming the same concept scheme as {@code term} but ranged on the other class.
   *
   * <p>The demonstration that CTDL's two concept ranges describe one kind of value: these
   * properties draw from the <em>same</em> {@code meta:targetScheme} as {@code term} and declare
   * {@code ceterms:CredentialAlignmentObject} where {@code term} declares {@code skos:Concept}.
   * Derived from the vendored snapshot on every call rather than written down, so refreshing the
   * snapshot refreshes the evidence.
   */
  public List<String> alignmentRangedSiblings(String term) {
    PropertyDef propDef = properties.get(term);
    if (propDef == null || propDef.targetScheme().isEmpty()) {
      return List.of();
    }
    List<String> siblings = new ArrayList<>();
    for (PropertyDef other : properties.values()) {
      if (!other.term().equals(term)
          && other.range().contains(ALIGNMENT_RANGE_TERM)
          && !Collections.disjoint(other.targetScheme(), propDef.targetScheme())) {
        siblings.add(other.term());
      }
    }
    siblings.sort(CodePointOrder.COMPARATOR);
    return List.copyOf(siblings);
  }

  /** Every property the concept-range conflict disposition can apply to. */
  public List<String> schemeBoundConceptProperties() {
    List<String> terms = new ArrayList<>();
    for (PropertyDef propDef : properties.values()) {
      if (propDef.schemeBoundConcept()) {
        terms.add(propDef.term());
      }
    }
    terms.sort(CodePointOrder.COMPARATOR);
    return List.copyOf(terms);
  }

  /** The subset of a node's types that the vendored snapshot actually declares, in order. */
  public List<String> knownTypes(List<String> nodeTypes) {
    List<String> known = new ArrayList<>();
    for (String type : nodeTypes) {
      if (classes.containsKey(type)) {
        known.add(type);
      }
    }
    return List.copyOf(known);
  }

  /** True for terms in the namespaces this tool is entitled to judge. */
  public static boolean isCheckedTerm(String term) {
    for (String prefix : CHECKED_PREFIXES) {
      if (term.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /** The prefix of a compacted term: everything before the first colon. */
  public static String vocabPrefix(String term) {
    int colon = term.indexOf(':');
    return colon < 0 ? term : term.substring(0, colon);
  }
}
