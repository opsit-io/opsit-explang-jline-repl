package io.opsit.explang.jlinerepl;


import io.opsit.explang.Main;
import io.opsit.explang.IREPL;
import io.opsit.explang.IParser;
import io.opsit.explang.parser.alg.AlgParser;


public class JLineMain extends Main {
  /**
   * Standalone interpreter entry point.
   */
  public static void main(String[] argv) throws Exception {
    JLineMain main = new JLineMain();
    main.getParsers().add(0, "alg");
    main.getFuncConverters().add(0, "alg");
    main.runWithArgs(argv);
  }

  protected IREPL mkREPL(IParser parser) {
    if (parser instanceof AlgParser) {
      return new AlgJlineREPL();
    } else {
      return new JlineREPL();
    }
  }
}


