package io.opsit.explang.jlinerepl;

import static io.opsit.explang.Utils.coalesce;

import io.opsit.explang.ASTN;
import io.opsit.explang.ASTNList;
import io.opsit.explang.Backtrace;
import io.opsit.explang.CompilationException;
import io.opsit.explang.Compiler;
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
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EOFError;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.SyntaxError;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
 
public class AlgJlineREPL implements IREPL {
  protected boolean verbose = false;
  protected boolean lineMode = false;
  protected Compiler compiler;
  protected IParser parser;
  protected Terminal term;
  protected volatile boolean shutdown = false;
  
  Compiler.ICtx replCtx;

  public Compiler getCompiler() {
    return this.compiler;
  }

  public void setCompiler(Compiler compiler) {
    this.compiler = compiler;
  }


  protected String prompt1 = "[%d]> ";
  protected String prompt2 = "%P ";


  private static int nwsidx(String s, int start) {
    for (int i = start + 1; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isWhitespace(c)) {
        return i;
      }
    }
    return start + 1;
  }

  private static int llidx(String s, int idx) {
    final int length = s.length();
    for (int i = idx; i >= 0; i--) {
      if (i <  length && '\n' == s.charAt(i)) {
        return idx - i;
      }
    }
    // on first line
    return idx;
  }
  
  
  private static int bcount(String s, String[] ncb) {
    // FIXME: not very robust or smart,
    //        does not take in account braces inside literals
    //        should take in account structural operators like if
    int nlidx = s.lastIndexOf("\n") + 1;
    // operate on the last string only
    //s = s.substring(nlidx);
    int pos = s.length() - 1;
    List<Character> stack = new ArrayList<Character>();
    for (int i = pos; i >= 0; i--) {
      char c = s.charAt(i);
      if (c == ')' || c == ']' || c == '}') {
        stack.add(c);
      }
      if (c == '(' || c == '[' || c == '{') {
        // stop on first unmatched paren
        if (stack.isEmpty()) {
          ncb[0] = c == '(' ? ")" : (c == '[' ? "]" : (c == '{' ? "}" : null));
          return llidx(s, nwsidx(s, i));
        }
        char p = stack.remove(stack.size() - 1);
        // stop on first mismatched paren as well
        //  FIXME: or it would be better to ignore it?
        if (! ((c == '(' && p == ')') 
               || (c == '[' && p == ']') 
               || (c == '{' && p == '}'))) {
          ncb[0] = "" + p;
          return llidx(s, nwsidx(s, i));
        }
      }
    }
    // find first non-whitespace character on the last line
    s = s.substring(nlidx);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        continue;
      }
      return i;
    }
    return 0;
  }
  
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
        if (line.endsWith("\n")) {
          throw new SyntaxError(pctx.getLine(), pctx.getPos(), exprs.getProblem().toString());
        } else {
          // next closing bracket
          String[] ncb = new String[1];
          throw new EOFError(pctx.getLine(),
                             pctx.getPos(),
                             exprs.getProblem().toString(),
                             null,
                             bcount(line,ncb),
                             ncb[0]);
        }
        //throw new SyntaxError(pctx.getLine(), pctx.getPos(), exprs.getProblem().toString());
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
      term.writer().print("debug: ");
      for (int i = 0; i < strs.length; i++) {
        if (i > 0) {
          term.writer().print(", ");
        }
        term.writer().print(strs[i]);
      }
      term.writer().println();
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
    this.shutdown = false;
    System.setProperty(LineReader.PROP_SUPPORT_PARSEDLINE, "true");
    Object result = null;
    replCtx = compiler.newCtx();
    compiler.setParser(parser);
    ParseCtx pctx = new ParseCtx("INPUT");
    Parser jlparser = new JLineParser();
    term = TerminalBuilder.builder().system(true).nativeSignals(true).build();
    

    Completer completer = null;
    if (parser instanceof IAutoSuggester) {
      completer = new AlgCompleter((IAutoSuggester) parser);
    }

    LineReader reader =
        LineReaderBuilder.builder()
            .terminal(term)
            .completer(completer)
            .parser(jlparser)
            /// .variable(LineReader., "%M%P > ")
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, prompt2)
            // .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P %M")
            // .variable(LineReader., "%M%P > ")
            .variable(LineReader.INDENTATION, 1)
            .option(LineReader.Option.AUTO_GROUP, true)
            .option(LineReader.Option.GROUP, true)
            .option(LineReader.Option.GROUP_PERSIST, true)
            .option(LineReader.Option.AUTO_MENU_LIST, true)
            // .variable(LineReader.
            // .option(LineReader.Option.INSERT_BRACKET, true)
            .build();

    reader.setOpt(LineReader.Option.INSERT_TAB);
    // Map<String, CmdDesc> tailTips = new HashMap<String, CmdDesc>();
    // tailTips.put("foo", new CmdDesc(ArgDesc.doArgNames(Arrays.asList("param1", "param2",
    // "[paramN...]"))));
    // tailTips.put("bar", new CmdDesc(ArgDesc.doArgNames(Arrays.asList("param1", "param2",
    // "[paramN...]"))));

    // Object commandDescription;
    // TailTipWidgets tailtipWidgets = new TailTipWidgets(reader, tailTips, 5, TipType.COMPLETER);

    // NonBlockingReader reader = terminal.reader();
    Backtrace bt = compiler.newBacktrace();
    term.writer().println(""
            + "Welcome to the EXPLANG JLine REPL!\n"
            + "Active parser is "
            + parser.getClass().getSimpleName()
            + "\n"
            + "Loaded packages are: "
            + compiler.getPackages()
            + "\n"
            + "Writer is " + this.getObjectWriter() + "\n"                 
            + "Please type an EXPLANG expressions. \n"
            + "  Press <Enter> on empty line to submit.\n"
            + "  Press <Ctrl-C> to cancel input.\n");
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
          term.writer().println(Utils.asString("EOF"));
          break;
        }
        ASTNList exprs = parser.parse(pctx, line, 1);
        if (exprs.hasProblems()) {
          term.writer().println("Failed to parse expression:");
          term.writer().println(Utils.listParseErrors(exprs));
          if (verbose) {
            debug("\nAST:\n" + exprs + "\n------\n");
          }
          continue;
        }
        if (exprs.size() == 0) {
          continue;
        }
        for (ASTN exprASTN : exprs) {
          if (verbose) {
            debug("\nAST:\n" + exprASTN + "\n------\n");
          }
          if (exprASTN.hasProblems()) {
            term.writer().println("Failed to parse expression:");
            term.writer().println(Utils.listParseErrors(exprASTN));
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
          term.writer().print("##=> ");
          term.writer().println(writer.writeObject(result));
          // kluge on
          //if (result instanceof Map) {
          //  ICtx newCtx = (ICtx) ((Map) result).get("__NEW_CONTEXT__");
          //  if (null != newCtx) {
          //    System.err.println("\nreplacing REPL context!");
          //    replCtx = newCtx;
          //  }
          // }
          // kluge off
        }
      } catch (CompilationException ex) {
        term.writer().println("COMPILATION ERROR: " + ex.getMessage());
      } catch (ExecutionException ex) {
        term.writer().println("EXECUTION ERROR: " + ex.getMessage());
        if (null != ex.getBacktrace()) {
          term.writer().println(" at:\n" + ex.getBacktrace());
        } else {
          term.writer().println();
        }
      } catch (UserInterruptException ex) {
        if (shutdown) {
          term.writer().println("REPL EXIT REQUESTED");
          term.writer().flush();
          break;
        } else {
          term.writer().println("USER INTERRUPT");
        }
      } catch (RuntimeException ex) {
        term.writer().println("RUNTIME EXCEPTION: " + ex);
      } catch (Exception ex) {
        term.writer().println("EXCEPTION: " + ex);
      } catch (Throwable e) {
        term.writer().println("UNEXPECTED ERROR: " + e);
      }
    }
    try {
      term.close();
    } catch (Exception ex) {
      System.err.println("Unexpected exception closing terminal: "+ex.getMessage());
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

  @Override
  public void requestExit() {
    this.shutdown = true;
  }

  @Override
  public boolean isExitRequested() {
    return this.shutdown;
  }
}
