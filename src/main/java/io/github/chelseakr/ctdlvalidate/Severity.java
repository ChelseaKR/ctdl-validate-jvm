package io.github.chelseakr.ctdlvalidate;

import java.util.List;

/**
 * Severity of a finding. These four values are the contract of the whole tool, and the port keeps
 * their meanings exactly as the reference implementation defines them:
 *
 * <ul>
 *   <li>{@code ERROR} — the payload violates a cited structural rule. Gates the exit code.
 *   <li>{@code WARNING} — a cited signal that something is very likely wrong, where the rule is not
 *       absolute or Registry enforcement of it is not documented.
 *   <li>{@code INFO} — worth a human look; not a defect on its own.
 *   <li>{@code UNVERIFIABLE} — the answer cannot be determined from the payload alone and the tool
 *       refuses to guess. Never counted as a pass or a fail.
 * </ul>
 *
 * <p>Only {@code ERROR} findings make the CLI exit nonzero.
 */
public enum Severity {
  ERROR,
  WARNING,
  INFO,
  UNVERIFIABLE;

  /** The order severities are counted and printed in, everywhere. */
  public static final List<Severity> REPORT_ORDER = List.of(ERROR, WARNING, INFO, UNVERIFIABLE);
}
