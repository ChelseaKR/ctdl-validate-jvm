# 7. `--resolve` is ported as the reference wrote it

## Status

Accepted

## Context

The reference's `0.2.1` added `--resolve`: documents the operator already has,
indexed so that a reference into one of them stops being UNVERIFIABLE and can
be judged. Its amended `REF_OUTSIDE_PAYLOAD` message tells a reader to use the
flag, so byte parity with `0.2.1` could not be had by editing a string -- the
port would have been telling people to use an option it did not have. The pin
could not move until the feature was ported, and ROADMAP section 2 asked for
the port to be a design review rather than a step inside a version bump.

Three questions needed answers, and two of them are not about CTDL at all.

1. **Whose semantics.** The feature has edge cases a port could reasonably
   decide differently: what happens when two supplied documents declare the
   same `@id`, whether a directory is walked recursively, whether blank nodes
   are indexed, whether a supplied document's own defects are reported, and
   what an unresolved reference says once documents were supplied.
2. **How the parity corpus passes supplied documents at all.** A fixture had
   been one file, and `parity_document` took one path.
3. **How a path is printed.** The reference names the file every resolution
   came from inside the finding's message, so the path is part of what the two
   implementations must agree on byte for byte.

## Decision

- **The reference's `session.py` is the specification, edge cases included.**
  Nothing is fetched. Supplied documents go into a side index of `@id` to
  class and are never validated. The first document in the order read wins an
  `@id`. Blank nodes are not indexed. A directory is read one level deep, `.json`
  files only, in code-point order. A reference that resolves nowhere stays
  UNVERIFIABLE whatever was supplied. Where the reference's rule is
  deterministic rather than considered -- first document wins is, by its own
  docstring -- the port copies it anyway: a port that adjudicated differently
  would be making a rule change here, which this repository does not do.
- **A fixture's supplied documents live in `parity/resolve/<fixture stem>/`**,
  and both implementations are handed that directory as one `--resolve`
  argument. One directory per fixture, rather than a list inside the fixture,
  because a directory is what an operator passes and because it lets the corpus
  exercise the directory rules themselves -- which files are read and which are
  not -- as fixtures rather than as assertions about a reading of Python.
- **Paths are spelled the way Python's `pathlib` spells them.** `PurePosixPath`
  drops `.` segments, repeated and trailing separators, keeps `..`, and treats a
  leading dot as part of a name, so `./a.json` prints as `a.json` and a file
  named `.json` has no suffix. `java.nio.file.Path` normalizes none of that on
  construction. The port reproduces the reference's spelling rather than its
  own, for the same reason `PythonRepr` reproduces `repr()`.
- **The generator runs from the repository root and passes relative paths**,
  because an absolute path would put one machine's layout into a committed
  expectation.
- **`--resolve` is the one CLI flag ported.** It changes what the rules
  conclude, which is this repository's subject; the reference's other flags
  change how the rules are invoked, which is not.

## Consequences

- The pin moved to `0.2.1`, and it cost exactly what was predicted on
  2026-08-29: three expectations on message text, and not one byte of
  `parity/ahead/`.
- The corpus now carries files that exist to be ignored -- a document one level
  too deep, a `.jsonld`, a dotfile named `.json` -- each declaring an identifier
  the fixture names, so reading any of them changes the expected output.
  Measured against the reference, it read none of them.
- `ParityTest` holds every resolve directory to belonging to a fixture and to
  changing what that fixture reports, with one named exception whose point is
  that a directory holding no document supplies nothing.
- Where the reference's resolution rules are arbitrary, so are the port's, and
  they stay that way until the reference changes them. That is the cost of
  parity, and it is the right cost for a port.
