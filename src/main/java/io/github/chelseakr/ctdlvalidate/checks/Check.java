package io.github.chelseakr.ctdlvalidate.checks;

import io.github.chelseakr.ctdlvalidate.Finding;
import io.github.chelseakr.ctdlvalidate.Graph;
import io.github.chelseakr.ctdlvalidate.SchemaIndex;
import java.util.List;

/** One structural check over a parsed payload. */
@FunctionalInterface
public interface Check {

  /** The findings this check has to report about the payload. May be empty; never null. */
  List<Finding> run(Graph graph, SchemaIndex schema);
}
