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
              + " prefixed with ce-\", and with the prefix \"there are a total of 34 hexadecimal"
              + " characters and 5 hyphens for a total of 39 characters\", in the form ce- plus"
              + " 8-4-4-4-12 hexadecimal digits. Example given:"
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
              + " UNVERIFIABLE, never as a pass or a fail. --resolve widens what the run can see,"
              + " using documents the operator already has; it fetches nothing.",
          "README.md (Methodology)",
          "-");

  /**
   * Why a repeated {@code @id} is read as one entity and reported. The URL is the reference's own
   * ADR, quoted as it quotes it.
   */
  public static final Rule REPEATED_ID_POLICY =
      new Rule(
          "ctdl-validate policy: one identifier, one entity. A payload may write the same @id on"
              + " more than one node object; this tool reads those as a single entity, taking the"
              + " union of their @type values and of their properties, and reports that it did so."
              + " It does not keep whichever declaration it parsed first and drop the rest, because"
              + " that made a verdict depend on @graph array order rather than on the document. The"
              + " report is a disclosure, not a defect: the tool states what it merged so a reader"
              + " who did not intend one entity can see that it read one.",
          "docs/adr/0005-one-identifier-one-entity.md",
          "-");

  /** A scheme-bound value the vendored encoding does not declare, reported and not judged. */
  public static final Rule CONCEPT_OUTSIDE_SNAPSHOT =
      new Rule(
          "ctdl-validate policy: a value on a scheme-bound property that the vendored encoding does"
              + " not declare is reported UNVERIFIABLE, never as a pass or a fail. CTDL's alignment"
              + " objects are built to point at frameworks outside CTDL, and the Registry's"
              + " published documents do point at O*NET, CIP and NAICS on these very properties."
              + " This tool has not vendored those frameworks and does not fetch, so it can say only"
              + " that it did not check the value, not that the value is wrong.",
          "README.md (Methodology)",
          "-");

  /**
   * A term the encoding marks unstable, cited against the declaration itself and nothing more: the
   * vendored files do not say what {@code vs:unstable} obliges anyone to do.
   */
  public static Rule termStatus(String term) {
    return new Rule(
        "CTDL schema encoding: "
            + term
            + " is declared vs:term_status vs:unstable. This tool reports that declaration and does"
            + " not interpret it. The vendored files do not say what an unstable term obliges a"
            + " publisher to do, so neither does this finding.",
        vocabSchemaUrl(term),
        RETRIEVED);
  }

  /** A property the context declares a language map, cited against the context. */
  public static Rule languageMapShape(String prop) {
    return new Rule(
        "CTDL JSON-LD context: "
            + prop
            + " is declared {\"@container\": \"@language\"}, so its values are keyed by language"
            + " tag. A bare literal in that position carries no language, which is the one thing"
            + " the declaration exists to record.",
        vocabContextUrl(prop),
        RETRIEVED);
  }

  /** A property's {@code meta:targetScheme}, cited against the snapshot it comes from. */
  public static Rule conceptScheme(String prop, Collection<String> scheme) {
    List<String> named = new ArrayList<>(scheme);
    named.sort(CodePointOrder.COMPARATOR);
    return new Rule(
        "CTDL schema encoding: "
            + prop
            + " declares meta:targetScheme "
            + String.join(", ", named)
            + ". The same encoding declares each concept's own scheme with skos:inScheme. A value"
            + " that the encoding declares in a different scheme is a term from the wrong"
            + " vocabulary for this property.",
        vocabSchemaUrl(prop),
        RETRIEVED);
  }

  /**
   * Why a reference resolved in a supplied document is reported rather than silently accepted.
   *
   * <p>The URL is the reference implementation's own ADR, quoted as it quotes it, like every
   * citation here. It is not this repository's {@code docs/adr/0004}, which is about something else
   * and happens to share the number.
   */
  public static final Rule RESOLUTION_POLICY =
      new Rule(
          "ctdl-validate policy: --resolve is additive and is reported. A reference that resolves"
              + " in a document supplied on the command line is checked against the property's"
              + " declared range exactly as an in-payload reference is, and the document it"
              + " resolved in is named, because every judgement that follows rests on that"
              + " document having been supplied.",
          "docs/adr/0004-resolution-is-additive.md",
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

  /**
   * A version property whose own range excludes a class its own domain admits.
   *
   * <p>CTDL's three version properties relate a resource to another version of the same resource.
   * For each of them the encoding declares {@code schema:rangeIncludes} as a strict subset of
   * {@code schema:domainIncludes}, dropping the same classes from all three. For a dropped class
   * the two declarations cannot both be satisfied: the domain says an instance of that class may
   * have a version, and the range says that version may not be an instance of that class, while a
   * version of a thing is a thing of the same kind. The document is following the domain
   * declaration, so the disagreement is inside the encoding and this is reported as INFO, not an
   * error, the same disposition the isChildOf and concept-range conflicts get.
   *
   * <p>The wording is the reference implementation's rather than a paraphrase, for the reason the
   * class comment gives.
   *
   * @param prop the version property
   * @param cls the class the two declarations disagree about
   * @param dropped every class this property's domain admits and its range excludes, from {@link
   *     SchemaIndex#domainOnlyClasses}
   */
  public static Rule versionRangeConflict(String prop, String cls, Collection<String> dropped) {
    List<String> others = new ArrayList<>(dropped);
    others.remove(cls);
    others.sort(CodePointOrder.COMPARATOR);
    return new Rule(
        "Conflicting declarations inside the schema encoding: "
            + prop
            + " declares schema:domainIncludes "
            + cls
            + ", so a "
            + cls
            + " may have a version, and omits "
            + cls
            + " from schema:rangeIncludes, so that version may not be a "
            + cls
            + ". The declared range of "
            + prop
            + " is a strict subset of its own declared domain; besides "
            + cls
            + " it also drops "
            + String.join(", ", others)
            + ". Reported as INFO, not an error, because the two declarations disagree with each"
            + " other and the document satisfies one of them.",
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
