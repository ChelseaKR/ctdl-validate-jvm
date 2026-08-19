package io.github.chelseakr.ctdlvalidate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Rule citations.
 *
 * <p>Every check cites one of these rules, and every rule quotes or paraphrases a specific
 * published source with its URL and retrieval date. No grammar or constraint is encoded from
 * memory. The citation text is the reference implementation's, because the citation is a quotation
 * of Credential Engine's published sources and rewording it would make the two implementations
 * disagree about what a rule says while agreeing about what it does.
 *
 * <p>The vendored copies of the two machine-readable sources live in {@code
 * src/main/resources/vendor/}; see SOURCES.md there for hashes.
 */
public final class Rules {

  public static final String RETRIEVED = "2026-08-06";

  public static final String CTDL_SCHEMA_URL = "https://credreg.net/ctdl/schema/encoding/json";
  public static final String CTDLASN_SCHEMA_URL =
      "https://credreg.net/ctdlasn/schema/encoding/json";
  public static final String CTDL_CONTEXT_URL = "https://credreg.net/ctdl/schema/context/json";
  public static final String CTDLASN_CONTEXT_URL =
      "https://credreg.net/ctdlasn/schema/context/json";
  public static final String ABOUT_CTID_URL = "https://credreg.net/ctdl/ctid";
  public static final String HANDBOOK_URL = "https://credreg.net/ctdl/handbook";
  public static final String RFC_4122_URL = "https://www.rfc-editor.org/rfc/rfc4122";

  private Rules() {}

  private static String vocabSchemaUrl(String term) {
    return term.startsWith("ceasn:") ? CTDLASN_SCHEMA_URL : CTDL_SCHEMA_URL;
  }

  private static String vocabContextUrl(String term) {
    return term.startsWith("ceasn:") ? CTDLASN_CONTEXT_URL : CTDL_CONTEXT_URL;
  }

  public static final Rule CTID_STRUCTURE =
      new Rule(
          "About the CTID, section \"CTID Structure\": \"Each CTID is made up of a standard UUID v4"
              + " prefixed with ce-\" for \"a total of 39 characters (34 hexadecimal characters and"
              + " 5 hyphens)\", in the form ce- plus 8-4-4-4-12 hexadecimal digits. Example given:"
              + " ce-e8a41a52-6ff6-48f0-9872-889c87b093b7.",
          ABOUT_CTID_URL,
          RETRIEVED);

  public static final Rule CTID_URI_STRUCTURE =
      new Rule(
          "About the CTID, section \"CTID-Based URI Structure\": Registry URIs are constructed from"
              + " https://credentialengineregistry.org plus /resources/ or /graph/ plus the CTID"
              + " itself, and \"the value of a resource's CTID property will exactly match the CTID"
              + " portion of that resource's URI\".",
          ABOUT_CTID_URL,
          RETRIEVED);

  public static final Rule CTID_LOWERCASE =
      new Rule(
          "RFC 4122 section 3 defines UUID text form with hexadecimal digits \"output as lower case"
              + " characters and ... case insensitive on input\"; every CTID example published on"
              + " the About the CTID page is lower case.",
          RFC_4122_URL,
          RETRIEVED);

  public static final Rule BNODE_SCOPE =
      new Rule(
          "CTDL All Schemas Handbook, \"Blank Node Identifier\": a blank node \"is only identified,"
              + " referenced, or retrievable in the context of the graph in which it is found\". A"
              + " blank node identifier that its own payload does not define identifies nothing.",
          HANDBOOK_URL,
          RETRIEVED);

  public static final Rule SAME_GRAPH_FRAMEWORK =
      new Rule(
          "ceasn:isPartOf is defined as \"Competency framework that this competency is a part of\""
              + " (CTDL-ASN schema), and the CTDL Handbook states: \"In the Registry, Competency"
              + " Frameworks and their member Competencies are published in the same JSON-LD"
              + " Graph\". A member competency whose isPartOf identifier matches no framework in"
              + " its own payload very likely carries the wrong identifier.",
          HANDBOOK_URL,
          RETRIEVED);

  public static final Rule NO_NETWORK_POLICY =
      new Rule(
          "ctdl-validate policy: no network access at validation time. A reference that points"
              + " outside the submitted payload cannot be confirmed or denied, so it is reported"
              + " UNVERIFIABLE, never as a pass or a fail.",
          "README.md (Methodology)",
          "-");

  public static final Rule ISCHILDOF_RANGE_CONFLICT =
      new Rule(
          "Conflicting authoritative sources: the CTDL-ASN schema encoding does not list"
              + " ceasn:CompetencyFramework in schema:rangeIncludes of ceasn:isChildOf, but the"
              + " ceasn:isPartOf usage note instructs top-level statements to use isChildOf, and"
              + " the CTDL Handbook's own examples point isChildOf at the framework. Reported as"
              + " INFO, not an error, because the sources disagree.",
          CTDLASN_SCHEMA_URL,
          RETRIEVED);

  /** How many sibling properties a concept-range citation names before it starts counting. */
  private static final int SIBLING_LIMIT = 3;

  /**
   * The concept-range inconsistency, cited against the snapshot it comes from.
   *
   * <p>CTDL declares references to terms from its own concept schemes with two incompatible ranges.
   * {@code prop} declares {@code skos:Concept}; other properties naming the same kind of value
   * declare {@code ceterms:CredentialAlignmentObject}, whose only declared parent is {@code
   * schema:AlignmentObject} — no path to {@code skos:Concept} exists in the encoding. The published
   * corpus encodes both families as {@code CredentialAlignmentObject}, so the declaration, not the
   * document, is what is inconsistent. Reported as INFO, not an error, because the sources
   * disagree; see {@link #ISCHILDOF_RANGE_CONFLICT} for the same disposition applied to the same
   * kind of problem.
   *
   * @param prop the property whose declared range is {@code skos:Concept}
   * @param scheme its {@code meta:targetScheme} declarations
   * @param siblings properties over the same scheme ranged on the other class, from {@link
   *     SchemaIndex#alignmentRangedSiblings}
   */
  public static Rule conceptRangeConflict(
      String prop, Collection<String> scheme, List<String> siblings) {
    List<String> named = new ArrayList<>(scheme);
    named.sort(CodePointOrder.COMPARATOR);
    String demonstration;
    if (siblings.isEmpty()) {
      demonstration =
          " Across the snapshot, CTDL ranges scheme-bound concept references on"
              + " ceterms:CredentialAlignmentObject and on skos:Concept interchangeably; three"
              + " concept schemes are named by properties in both families.";
    } else {
      String shown =
          String.join(", ", siblings.subList(0, Math.min(SIBLING_LIMIT, siblings.size())));
      if (siblings.size() > SIBLING_LIMIT) {
        shown += ", ... (" + siblings.size() + " properties total)";
      }
      demonstration =
          " The same snapshot declares "
              + shown
              + " over the same concept scheme with schema:rangeIncludes"
              + " ceterms:CredentialAlignmentObject, so the two declarations describe one kind of"
              + " value.";
    }
    return new Rule(
        "Conflicting declarations inside the schema encoding: "
            + prop
            + " declares schema:rangeIncludes skos:Concept and meta:targetScheme ["
            + String.join(", ", named)
            + "]."
            + demonstration
            + " ceterms:CredentialAlignmentObject declares only rdfs:subClassOf"
            + " schema:AlignmentObject, so it cannot satisfy a skos:Concept range on the face of"
            + " the encoding. Reported as INFO, not an error, because the encoding and Credential"
            + " Engine's own published documents disagree.",
        vocabSchemaUrl(prop),
        RETRIEVED);
  }

  public static Rule idCoercion(String prop) {
    return new Rule(
        "The CTDL JSON-LD context declares "
            + prop
            + " with {\"@type\": \"@id\"}: its values are IRIs that identify entities, not"
            + " literals. For Registry resources the IRI form is the CTID-based URI (see About the"
            + " CTID).",
        vocabContextUrl(prop),
        RETRIEVED);
  }

  private static final int ABBREVIATE_LIMIT = 6;

  /** The first six terms of a set, in code-point order, with a count when there are more. */
  static String abbreviate(Collection<String> terms) {
    List<String> ordered = new ArrayList<>(terms);
    ordered.sort(CodePointOrder.COMPARATOR);
    String shown =
        String.join(", ", ordered.subList(0, Math.min(ABBREVIATE_LIMIT, ordered.size())));
    if (ordered.size() > ABBREVIATE_LIMIT) {
      shown += ", ... (" + ordered.size() + " classes total)";
    }
    return shown;
  }

  public static Rule domain(String prop, Collection<String> domain) {
    return new Rule(
        prop
            + " declares schema:domainIncludes ["
            + abbreviate(domain)
            + "] in the schema encoding; the subject's class is not among them or their subclasses.",
        vocabSchemaUrl(prop),
        RETRIEVED);
  }

  public static Rule range(String prop, Collection<String> range) {
    return new Rule(
        prop
            + " declares schema:rangeIncludes ["
            + abbreviate(range)
            + "] in the schema encoding; the referenced entity's class is not among them or their"
            + " subclasses.",
        vocabSchemaUrl(prop),
        RETRIEVED);
  }

  public static Rule inverse(String prop, String inverse) {
    return new Rule(
        prop
            + " declares owl:inverseOf "
            + inverse
            + " in the schema encoding: if A "
            + prop
            + " B is asserted, then B "
            + inverse
            + " A must hold wherever both directions are stated.",
        vocabSchemaUrl(prop),
        RETRIEVED);
  }

  public static Rule unknownTerm(String kind, String vocabPrefix) {
    return new Rule(
        "The "
            + kind
            + " is not declared in the vendored "
            + vocabPrefix
            + " schema encoding snapshot (retrieved "
            + RETRIEVED
            + "). Either a typo or a term newer than the snapshot; refresh the vendored schema to"
            + " rule out the latter.",
        "ceasn".equals(vocabPrefix) ? CTDLASN_SCHEMA_URL : CTDL_SCHEMA_URL,
        RETRIEVED);
  }
}
