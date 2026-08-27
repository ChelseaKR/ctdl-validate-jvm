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
- The build itself runs on a newer JDK; only the target level is pinned. CI
  runs the `verify` gate on a matrix of Temurin 17 and Temurin 21, because
  `--release 17` is a compile-time guarantee and the floor claimed here is a
  runtime one. See the last consequence below.
- `-Xlint:all -Werror`. A warning in a codebase this small is a defect.

## Consequences

- Pattern matching in `switch` is unavailable, so a handful of `instanceof`
  chains are longer than they would be on 21. That is a small, contained
  cost.
- Records and sealed interfaces are available, which is what the finding
  model and the property-value model needed.
- The artifact runs anywhere Java 17 or newer runs, which includes the
  environments this port exists to be legible to. That is a claim about a
  runtime, and `options.release` cannot make it: the flag rejects an API that
  postdates 17, but a dependency whose own class files target a higher version,
  a multi-release jar resolving differently, or a module 21 exposes and 17 does
  not would all pass a build that compiles on 21 and runs on 21, then fail for
  the first person who installs Temurin 17. The CI matrix is what keeps this
  consequence true; without a leg on the floor itself, nothing here could ever
  go red.
