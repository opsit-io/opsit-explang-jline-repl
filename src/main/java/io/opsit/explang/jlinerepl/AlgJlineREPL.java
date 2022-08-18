package io.opsit.explang.jlinerepl;

import static io.opsit.explang.Utils.coalesce;

import io.opsit.explang.ASTN;
import io.opsit.explang.ASTNList;
import io.opsit.explang.Backtrace;
import io.opsit.explang.Compiler;
import io.opsit.explang.Compiler.ICtx;
import io.opsit.explang.ICompiled;
import io.opsit.explang.IParser;
import io.opsit.explang.ParseCtx;
import io.opsit.explang.Utils;
import io.opsit.explang.autosuggest.IAutoSuggester;
import io.opsit.explang.autosuggest.SourceInfo;
import io.opsit.explang.autosuggest.Suggestion;
import io.opsit.explang.autosuggest.Tokenization;
import io.opsit.explang.strconv.alg.AlgConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class AlgJlineREPL implements IREPL {
  ICtx replCtx;
  Compiler compiler;
  protected boolean verbose = false;

  public Compiler getCompiler() {
    return this.compiler;
  }

  public void setCompiler(Compiler compiler) {
    this.compiler = compiler;
  }

  public class JLineParser implements Parser {
    // @Override
    // public boolean isEscapeChar(char c)) {
    //    return false;
    // }

    @Override
    public ParsedLine parse(final String line, final int cursor, final ParseContext context)
        throws SyntaxError {
      debug("ajr.parse(line='" + line + "' cursor=" + cursor + " context=" + context);
      if (ParseContext.COMPLETE == context) {
        return parseForCompletion(line, cursor);
      }
      ParseCtx pctx = new ParseCtx("parser");
      // System.err.println("{c="+cursor+",ctx="+context+"}");

      if (line == null || line.trim().length() == 0) {
        return acceptParsedLine(line, cursor, new ASTNList(new ArrayList<ASTN>(), pctx));
      }
      ASTNList exprs;
      try {
        exprs = compiler.getParser().parse(pctx, line, 1);
      } catch (Exception ex) {
        throw new SyntaxError(pctx.getLine(), pctx.getPos(), ex.getMessage());
      }
      if (verbose) {
        debug("EXPRS: " + exprs);
      }
      if (exprs.hasProblems()) {
        debug("expr has problem");
        throw new SyntaxError(pctx.getLine(), pctx.getPos(), exprs.getProblem().toString());
      }
      // may return excep
      // ParseContext.ACCEPT_LINE
      //    Try a real "final" parse. May throw EOFError in which case we have incomplete input.
      // ParseContext.SECONDARY_PROMPT
      //    Called when we need to update the secondary prompts.
      //    Specifically, when we need the 'missing' field from EOFError,
      //    which is used by a "%M" in a prompt pattern.
      // ParseContext.UNSPECIFIED
      return acceptParsedLine(line, cursor, exprs);
      // throw new EOFError(pctx.getLine(), pctx.getPos(),ex.getMessage(),null,indent,")");
      // throw new EOFError(pctx.getLine(), pctx.getPos(),ex.getMessage(),"xxxx");
    }

    @Override
    public boolean isEscapeChar(char ch) {
      return false;
      // return Parser.super.isEscapeChar(ch) +
    }
  }

  private void debug(String... strs) {
    if (verbose) {
      System.err.print("debug: ");
      for (int i = 0; i < strs.length; i++) {
        if (i > 0) {
          System.err.print(", ");
        }
        System.err.print(strs[i]);
      }
      System.err.println();
    }
  }

  private ParsedLine acceptParsedLine(String line, int cursor, ASTNList exprs) {
    debug("acceptParsedLine(line='" + line + "', cursor=" + cursor + ", exprs=" + exprs + ")");
    return new AParsedLine(line, cursor, "", 0, 0, new ArrayList<String>());
  }

  public ParsedLine parseForCompletion(final String line, final int cursor) {
    debug("completeParsedLine line='" + line + "' cursor=" + cursor);
    IParser explangParser = compiler.getParser();
    if (explangParser instanceof IAutoSuggester) {
      debug("parsing line for auto suggest");
      final IAutoSuggester as = (IAutoSuggester) explangParser;
      final Tokenization t = as.tokenize(line, cursor);
      return new AParsedLine(line, cursor, t);
    } else {
      return new AParsedLine(line, cursor, "", 0, 0, Utils.list());
    }
  }

  @Override
  public void setVerbose(boolean val) {
    this.verbose = val;
  }

  public AlgJlineREPL() {
    compiler = new Compiler(new AlgConverter(), Compiler.getAllPackages());
  }

  @Override
  public Object execute(IParser parser) throws IOException {
    Object result = null;
    replCtx = compiler.newCtx();
    compiler.setParser(parser);
    ParseCtx pctx = new ParseCtx("INPUT");
    Parser jlparser = new JLineParser();

    Terminal terminal = TerminalBuilder.builder().system(true).nativeSignals(true).build();

    Completer completer = null;
    if (parser instanceof IAutoSuggester) {
      completer = new AlgCompleter((IAutoSuggester) parser);
    }

    LineReader reader =
        LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(completer)
            .parser(jlparser)
            /// .variable(LineReader., "%M%P > ")
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%M%P > ")
            // .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P %M")
            // .variable(LineReader., "%M%P > ")
            .variable(LineReader.INDENTATION, 2)
            .option(LineReader.Option.AUTO_GROUP, true)
            .option(LineReader.Option.GROUP, true)
            .option(LineReader.Option.GROUP_PERSIST, true)
            .option(LineReader.Option.AUTO_MENU_LIST, true)
            // .variable(LineReader.
            // .option(LineReader.Option.INSERT_BRACKET, true)
            .option(LineReader.Option.INSERT_TAB, false)
            .build();

    // Map<String, CmdDesc> tailTips = new HashMap<String, CmdDesc>();
    // tailTips.put("foo", new CmdDesc(ArgDesc.doArgNames(Arrays.asList("param1", "param2",
    // "[paramN...]"))));
    // tailTips.put("bar", new CmdDesc(ArgDesc.doArgNames(Arrays.asList("param1", "param2",
    // "[paramN...]"))));

    // Object commandDescription;
    // TailTipWidgets tailtipWidgets = new TailTipWidgets(reader, tailTips, 5, TipType.COMPLETER);

    // NonBlockingReader reader = terminal.reader();
    Backtrace bt = compiler.newBacktrace();
    System.out.println("Welcome to REPL\n");
    System.out.println("Writer is " + this.getObjectWriter() + "\n");
    while (true) {
      try {
        String line = null;
        try {
          line = reader.readLine("[%N]> ");
          if (null == line || line.trim().length() == 0) {
            continue;
          }
        } catch (org.jline.reader.EndOfFileException eofex) {
          terminal.writer().println(Utils.asString("EOF"));
          break;
        }
        ASTNList exprs = parser.parse(pctx, line, 1);
        if (exprs.size() == 0) {
          continue;
        }
        for (ASTN exprASTN : exprs) {
          if (verbose) {
            debug("\nAST:\n" + exprASTN + "\n------\n");
          }
          if (null == exprASTN) {
            result = null;
          } else {
            ICompiled expr = compiler.compile(exprASTN);
            if (verbose) {
              debug("compiled:\n" + expr + "\n------\n");
            }
            result = expr.evaluate(bt, replCtx);
          }
          if (verbose) {
            debug("evaluation result:\n");
          }
          terminal.writer().print("##=> ");
          terminal.writer().println(writer.writeObject(result));
          // kluge on
          if (result instanceof Map) {
            ICtx newCtx = (ICtx) ((Map) result).get("__NEW_CONTEXT__");
            if (null != newCtx) {
              System.err.println("\nreplacing REPL context!");
              replCtx = newCtx;
            }
          }
          // kluge off
        }
      } catch (Throwable e) {
        e.printStackTrace();
      } 
    }
    return result;
  }

  public class AlgCompleter implements Completer {
    IAutoSuggester asg;

    public AlgCompleter(IAutoSuggester asg) {
      this.asg = asg;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      debug("completer: reader=" + reader + ", ParsedLine=" + line + "candidates=" + candidates);
      // System.err.println("candidates: "+candidates);
      // new Candidate(String value, String displ, String group, String descr, String suffix, String
      // key, boolean complete)
      // Candidate c = new Candidate("foo", "Do fooo", "funcs", "DO FOO DESCR", null, "foo", true) ;
      // Candidate c2 = new Candidate("bar");
      // candidates.add(c);
      // candidates.add(c2);

      // FIXME: unused currently
      // AsParsedLine apl = (AsParsedLine) line;

      IParser parser = compiler.getParser();
      if (parser instanceof IAutoSuggester) {
        debug("return autosuggester");
        SourceInfo suggestions =
            ((IAutoSuggester) parser)
                .autoSuggest(line.line(), replCtx, line.cursor(), true, true, true, false);
        // Suggestions suggestions = apl.getSuggestion();
        debug("completer: got suggestions=" + suggestions);
        for (Suggestion s : suggestions.suggestions) {
          Candidate c =
              new Candidate(
                  s.text,
                  coalesce(s.properties.get("displayName"), s.text),
                  s.kind,
                  coalesce(s.properties.get("description"), s.kind + " " + s.text),
                  s.suffix, // suffix
                  null, // key
                  true, // complete
                  0 // sort weight
                  );
          candidates.add(c);
        }
      }
      debug("completer: after: candidates=" + candidates);
    }
  }

  protected IObjectWriter writer =
      new IObjectWriter() {
        @Override
        public String writeObject(Object obj) {
          return Utils.asString(obj);
        }
        ;
      };

  @Override
  public void setObjectWriter(IObjectWriter writer) {
    this.writer = writer;
  }

  @Override
  public IObjectWriter getObjectWriter() {
    return this.writer;
  }
}
