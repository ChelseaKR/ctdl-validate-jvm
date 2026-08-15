# 2. Java 17 floor

## Status

Accepted

## Context

The port needs a language level. Records and sealed interfaces both carry the
design here — a `Finding` is a value, and `Value` is a closed set of three
cases the compiler can check exhaustively — and both were preview features
before Java 17 finalized them.

Going further to 21 would buy pattern matching in `switch`, which would make
two or three methods read slightly better. It would also raise the floor
above what a good deal of running JVM civic-tech is on: Java 17 is the LTS
that Play-framework services and similar public-sector Java systems are
commonly deployed against.

## Decision

- `options.release = 17`. The compiler is told the target level explicitly
  rather than being trusted to match whatever JDK happens to be installed, so
  a newer local JDK cannot accidentally emit bytecode or API references the
  floor does not allow.
- The build itself runs on a newer JDK (CI uses Temurin 21); only the target
  level is pinned.
- `-Xlint:all -Werror`. A warning in a codebase this small is a defect.

## Consequences

- Pattern matching in `switch` is unavailable, so a handful of `instanceof`
  chains are longer than they would be on 21. That is a small, contained
  cost.
- Records and sealed interfaces are available, which is what the finding
  model and the property-value model needed.
- The artifact runs anywhere Java 17 or newer runs, which includes the
  environments this port exists to be legible to.
