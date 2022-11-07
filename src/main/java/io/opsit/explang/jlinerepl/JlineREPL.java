package io.opsit.explang.jlinerepl;

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
import io.opsit.explang.ParserEOFException;
import io.opsit.explang.Utils;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.jline.reader.EOFError;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.Parser.ParseContext;
import org.jline.reader.SyntaxError;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class JlineREPL implements IREPL {
  protected boolean verbose = false;
  protected boolean lineMode = false;
  protected Compiler compiler;
  protected IParser parser;
  protected Terminal term;
  protected volatile boolean shutdown = false;

  @Override
  public void setVerbose(boolean val) {
    this.verbose = val;
  }

  @Override
  public boolean getVerbose() {
    return this.verbose;
  }

  public Compiler getCompiler() {
    return this.compiler;
  }

  public void setCompiler(Compiler compiler) {
    this.compiler = compiler;
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
  public boolean getLineMode() {
    return lineMode;
  }

  @Override
  public void setLineMode(boolean lineMode) {
    this.lineMode = lineMode;
  }

  protected String prompt1 = "[%d]> ";
  protected String prompt2 = "%P ";

  public class JLineParser implements Parser {
    // @Override
    // public boolean isEscapeChar(char c)) {
    //    return false;
    // }
    @Override
    public ParsedLine parse(final String line, final int cursor, final ParseContext context)
        throws SyntaxError {
      ParseCtx pctx = new ParseCtx("parser");
      ASTNList exprs;
      try {
        exprs = compiler.getParser().parse(pctx, line, 1);
      } catch (Exception ex) {
        throw new SyntaxError(pctx.getLine(), pctx.getPos(), ex.getMessage());
      }

      // context.
      if (ParseContext.COMPLETE == context) {
        // Parse to find completions (typically after a Tab). We should tolerate and ignore errors.
        return completeParsedLine(line, cursor, exprs);
      } else {
        // may return excep
        // ParseContext.ACCEPT_LINE
        //    Try a real "final" parse. May throw EOFError in which case we have incomplete input.
        // ParseContext.SECONDARY_PROMPT
        //    Called when we need to update the secondary prompts.
        //    Specifically, when we need the 'missing' field from EOFError,
        //    which is used by a "%M" in a prompt pattern.
        // ParseContext.UNSPECIFIED
        return acceptParsedLine(line, cursor, context, exprs);
        // throw new EOFError(pctx.getLine(), pctx.getPos(),ex.getMessage(),null,indent,")");
        // throw new EOFError(pctx.getLine(), pctx.getPos(),ex.getMessage(),"xxxx");
      }
    }

    @Override
    public boolean isEscapeChar(char ch) {
      return false;
      // return Parser.super.isEscapeChar(ch) +
    }
  }

  private ParsedLine acceptParsedLine(
      String line, int cursor, ParseContext context, ASTNList exprs) {
    if (exprs.hasProblems()) {
      exprs.dispatchWalker(
          new ASTN.Walker() {
            @Override
            public void walk(ASTN node) {
              Exception problem = node.getProblem();
              if (null != problem && (problem instanceof ParserEOFException)) {
                int indent = 0;
                ASTN pnode = node;
                while (null != pnode.getParent()) {
                  indent++;
                  pnode = pnode.getParent();
                }
                throw new EOFError(
                    node.getPctx().getLine(),
                    node.getPctx().getPos(),
                    problem.getMessage(),
                    null,
                    indent,
                    ")");
              }
            }
          });
    }
    return new ParsedLine() {
      @Override
      public String word() {
        return "booo";
      }

      @Override
      public int wordCursor() {
        return 0;
      }

      @Override
      public int wordIndex() {
        return 0;
      }

      @Override
      public List<String> words() {
        return new ArrayList<String>();
      }

      @Override
      public String line() {
        return line;
      }

      @Override
      public int cursor() {
        return cursor;
      }
    };
  }

  protected ParsedLine completeParsedLine(final String line, final int cursor, ASTNList exprs) {

    return new ParsedLine() {
      @Override
      public String word() {
        return "";
      }

      @Override
      public int wordCursor() {
        return 0;
      }

      @Override
      public int wordIndex() {
        return 1;
      }

      @Override
      public List<String> words() {
        return Utils.list();
      }

      @Override
      public String line() {
        return line;
      }

      @Override
      public int cursor() {
        return cursor;
      }
    };
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
  public Object execute(Reader inReader, String inputName) throws IOException {
    this.shutdown = false;
    System.setProperty(LineReader.PROP_SUPPORT_PARSEDLINE, "true");
    Object result = null;
    ICtx ctx = compiler.newCtx();
    // IParser parser = compiler.getParser();
    compiler.setParser(parser);
    ParseCtx pctx = new ParseCtx("INPUT");
    Parser jlparser = new JLineParser();

    term = TerminalBuilder.builder().system(true).build();

    LineReader reader =
        LineReaderBuilder.builder()
            .terminal(term)
            // .completer(completer)
            .parser(jlparser)
            /// .variable(LineReader., "%M%P > ")
            .variable(LineReader.SECONDARY_PROMPT_PATTERN, prompt2)
            // .variable(LineReader.SECONDARY_PROMPT_PATTERN, "%P %M")
            // .variable(LineReader., "%M%P > ")
            .variable(LineReader.INDENTATION, 2)
            // .variable(LineReader.
            // .option(LineReader.Option.INSERT_BRACKET, true)
            .option(LineReader.Option.INSERT_TAB, true)
            .build();
    term.writer().print(
        "Welcome to the EXPLANG JLine REPL!\n"
            + "Active parser is "
            + parser.getClass().getSimpleName()
            + "\n"
            + "Loaded packages are: "
            + compiler.getPackages()
            + "\n"
            + "Please type an EXPLANG expression\n"
            + "  <Ctrl-C> to cancel input.\n");
    // NonBlockingReader reader = terminal.reader();
    Backtrace bt = compiler.newBacktrace();
    int inputnum = 0;
    while (true) {
      try {
        String line = null;
        try {
          line = reader.readLine(String.format(prompt1, inputnum++));
          if (null == line) {
            continue;
          }
        } catch (org.jline.reader.EndOfFileException eofex) {
          term.writer().println(Utils.asString("EOF"));
          break;
        }
        ASTNList exprs = parser.parse(pctx, line, 1);
        if (exprs.hasProblems()) {
          term.writer().println("Failed to parse expression:");
          term.writer().print(Utils.listParseErrors(exprs));
          continue;
        }
        if (exprs.size() == 0) {
          continue;
        }
        for (ASTN exprASTN : exprs) {
          if (verbose) {
            term.writer().println("\nAST:\n" + exprASTN + "\n------\n");
          }
          if (exprASTN.hasProblems()) {
            term.writer().println("Failed to parse expression:");
            term.writer().print(Utils.listParseErrors(exprASTN));
            break;
          }
          ICompiled expr = compiler.compile(exprASTN);
          if (verbose) {
            term.writer().println("compiled:\n" + expr + "\n------\n");
          }
          result = expr.evaluate(bt, ctx);
          if (verbose) {
            term.writer().println("evaluation result:\n");
          }
          term.writer().print(";;=> ");
          term.writer().println(writer.writeObject(result));
        }
      } catch (CompilationException ex) {
        term.writer().println("COMPILATION ERROR: " + ex.getMessage());
      } catch (ExecutionException ex) {
        term.writer().print("EXECUTION ERROR: " + ex.getMessage());
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
      System.err.println("Unexpected exception closing terminal: " + ex.getMessage());
    }
    return result;
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
