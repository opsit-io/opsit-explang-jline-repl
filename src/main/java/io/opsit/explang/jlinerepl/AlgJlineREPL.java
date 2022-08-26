package io.opsit.explang.jlinerepl;

import static io.opsit.explang.Utils.coalesce;

import io.opsit.explang.ASTN;
import io.opsit.explang.ASTNList;
import io.opsit.explang.Backtrace;
import io.opsit.explang.CompilationException;
import io.opsit.explang.Compiler;
import io.opsit.explang.Compiler.ICtx;
import io.opsit.explang.ExecutionException;
import io.opsit.explang.ICompiled;
import io.opsit.explang.IObjectWriter;
import io.opsit.explang.IParser;
import io.opsit.explang.IREPL;
import io.opsit.explang.ParseCtx;
import io.opsit.explang.Utils;
import io.opsit.explang.autosuggest.IAutoSuggester;
import io.opsit.explang.autosuggest.SourceInfo;
import io.opsit.explang.autosuggest.Suggestion;
import io.opsit.explang.autosuggest.Tokenization;
import io.opsit.explang.strconv.alg.AlgConverter;
import java.io.IOException;
import java.io.Reader;
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
  protected boolean verbose = false;
  protected boolean lineMode = false;
  protected Compiler compiler;
  protected IParser parser;

  Compiler.ICtx replCtx;

  public Compiler getCompiler() {
    return this.compiler;
  }

  public void setCompiler(Compiler compiler) {
    this.compiler = compiler;
  }


  protected String prompt1 = "[%d]> ";
  protected String prompt2 = "%P ";

  public class JLineParser implements Parser {
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
    }

    // FIXME
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

  
  protected ParsedLine parseForCompletion(final String line, final int cursor) {
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

  @Override
  public boolean getVerbose() {
    return this.verbose;
  }

  public AlgJlineREPL() {
    compiler = new Compiler(new AlgConverter(), Compiler.getAllPackages());
  }

  @Override
  public Object execute(Reader inReader, String inputName) throws IOException {
    System.setProperty(LineReader.PROP_SUPPORT_PARSEDLINE, "true");
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
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, prompt2)
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
    System.out.print(""
            + "Welcome to the EXPLANG JLine REPL!\n"
            + "Active parser is "
            + parser.getClass().getSimpleName()
            + "\n"
            + "Loaded packages are: "
            + compiler.getPackages()
            + "\n"
            + "Writer is " + this.getObjectWriter() + "\n"                 
            + "Please type an EXPLANG expression\n");
    int inputnum = 0;
    while (true) {
      try {
        String line = null;
        try {
          line = reader.readLine(String.format(prompt1, inputnum++));
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
          if (exprASTN.hasProblems()) {
            System.out.println("Failed to parse expression:");
            System.out.print(listParseErrors(exprASTN));
            break;
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
      } catch (CompilationException ex) {
        System.err.println("COMPILATION ERROR: " + ex.getMessage());
      } catch (ExecutionException ex) {
        System.err.print("EXECUTION ERROR: " + ex.getMessage());
        if (null != ex.getBacktrace()) {
          System.err.println(" at:\n" + ex.getBacktrace());
        } else {
          System.err.println();
        }
      } catch (RuntimeException ex) {
        System.err.println("RUNTIME EXCEPTION: " + ex);
      } catch (Exception ex) {
        System.err.println("EXCEPTION: " + ex);
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
      };

  @Override
  public void setObjectWriter(IObjectWriter writer) {
    this.writer = writer;
  }

  @Override
  public IObjectWriter getObjectWriter() {
    return this.writer;
  }

  @Override
  public boolean getLineMode() {
    return lineMode;
  }

  @Override
  public void setLineMode(boolean lineMode) {
    this.lineMode = lineMode;
  }

  @Override
  public void setParser(IParser parser) {
    this.parser = parser;
  }

  @Override
  public IParser getParser() {
    return this.parser;
  }

  private String listParseErrors(ASTN exprASTN) {
    final StringBuilder buf = new StringBuilder();
    ASTN.Walker errCollector =
        new ASTN.Walker() {
          public void walk(ASTN node) {
            final Exception ex = node.getProblem();
            if (null != ex) {
              buf.append(node.getPctx());
              buf.append(": ");
              buf.append(ex.getMessage()).append("\n");
            }
          }
        };
    exprASTN.dispatchWalker(errCollector);
    return buf.toString();
  }
}
