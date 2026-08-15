# Vendored specification files

These files are unmodified copies of Credential Engine's published CTDL and
CTDL-ASN schema and JSON-LD context documents. They are the only source of
domain, range, inverse, and identifier-coercion rules in this tool. Nothing
is encoded from memory.

All four were retrieved on 2026-08-06 with plain HTTP GET, for
[`ChelseaKR/ctdl-validate`](https://github.com/ChelseaKR/ctdl-validate), the
Python reference implementation this repository ports. They are carried here
byte for byte, with the hashes that repository recorded, unchanged. That is a
precondition for the parity suite meaning anything: two implementations that
agreed while reading different snapshots would be agreeing about nothing.

| File | Source URL | SHA-256 |
|---|---|---|
| `ctdl/schema.json` | https://credreg.net/ctdl/schema/encoding/json | `a2dd28cb08f9e5e0324fe155e7381c276d70e69cb95521dab120f042cc538776` |
| `ctdl/context.json` | https://credreg.net/ctdl/schema/context/json | `ddb6b4586c38df5fa43aa5f6a558de407dd7acdec9bc1329ce790e164bf40db5` |
| `ctdlasn/schema.json` | https://credreg.net/ctdlasn/schema/encoding/json | `8bacfcec140c03e144903acd04eeacc3f466c2245ca2a1bb703a322981fda0b7` |
| `ctdlasn/context.json` | https://credreg.net/ctdlasn/schema/context/json | `783a5a5e132deb0023f6a4ea5201b9b69e298e6c359c1fd8d3c595392f8e7e72` |

`VendorIntegrityTest` recomputes all four hashes off the classpath and checks
them against this table.

Three prose sources are cited in findings but not vendored (they are HTML
pages; the exact sentences relied on are quoted in `Rules.java`):

- "About the CTID", https://credreg.net/ctdl/ctid (page dated 5/10/2024,
  retrieved 2026-08-06). Defines the CTID grammar and the CTID-based URI
  structure.
- CTDL "All Schemas Handbook", https://credreg.net/ctdl/handbook (retrieved
  2026-08-06). Defines blank node identifier scope and documents that
  competency frameworks and their competencies are published in the same
  JSON-LD graph.
- RFC 4122, https://www.rfc-editor.org/rfc/rfc4122 (retrieved 2026-08-06).
  Section 3 for the lower-case UUID text form; sections 4.1.1 and 4.1.3 for
  where the version and variant bits sit.

To refresh the rules, refresh them in the reference implementation first:
re-download the four URLs there, update its hashes and dates, and re-run its
suite. Then carry the files and the table here and regenerate
`parity/expected/`. A schema release can change which findings either tool
emits; that is by design, and doing it in that order keeps the two in step.
