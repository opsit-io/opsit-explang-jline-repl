package io.opsit.explang.jlinerepl;

import io.opsit.explang.IParser;
import io.opsit.explang.parser.alg.AlgParser;
import io.opsit.explang.parser.lisp.LispParser;
import io.opsit.explang.parser.sexp.SexpParser;

public class JLineMain {
  public static void main(String[] args) throws Exception {
    IREPL repl = null;
    IParser parser = null;
    boolean verbose = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-s")) {
        parser = new SexpParser();
        repl = new JlineREPL();
      }
      if (args[i].equals("-a")) {
        parser = new AlgParser();
        repl = new AlgJlineREPL();
      }
      if (args[i].equals("-v")) {
        verbose = true;
      }
    }
    if (null == parser) {
      parser = new LispParser();
      repl = new JlineREPL();
    }
    repl.setVerbose(verbose);
    System.err.println("Using parser " + parser.getClass());
    repl.execute(parser);
  }
}
