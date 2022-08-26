Explang JLine REPL
==================

This project provides REPL with editing and history features

Warning: This project is pretty much work in progress.  The
completion/autosuggestion features still are not really usable.

Dependencies
------------

This plugin depends on 
- [Explang Core](https://github.com/opsit-io/opsit-explang-core)
- [Explang Alg Parser](https://github.com/opsit-io/opsit-explang-alg-parser)
- [ANTLR](https://www.antlr.org)
- [JLine3](https://github.com/jline/jline3)

Installation
------------

Download an Explang Core executable JAR jars from Github 
[releases](https://github.com/opsit-io/opsit-explang-jline-repl/releases)


Or use maven CLI to fetch the artifacts from maven central:

```
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=io.opsit:opsit-explang-jline-repl:0.0.2:jar:runnable   -Dtransitive=false -Ddest=opsit-explang-jline-repl-0.0.2-runnable.jar
```

Using REPL
----------

Run the executable jar in a terminal emulator or windows console. The default parser is 
[alg-parser](https://github.com/opsit-io/opsit-explang-alg-parser).

```
$ java -jar opsit-explang-jline-repl-0.0.2-runnable.jar 
Welcome to the EXPLANG REPL!
Active parser is AlgParser
Loaded packages are: [base.math, base.text, io, base.bindings, ffi, base.funcs, loops, threads, base.version, base.coercion, base.logic, base.lang, base.arithmetics, base.seq, base.control, base.regex, dwim, base.docs, base.beans, base.types]
Please type an EXPLANG expression terminated by an extra NEWLINE
[0]>  print("Hello, world!\n");
Hello, world!

=> Hello, world!

[1]>
```

### REPL Command Line Arguments 


*  -d -- Enable verbose diagnostics: will print debug messages, parsed AST of expressions and compilation results.
*  -p list -- Specify comma separated list of enabled opackages. If not supplied the following packages are enabled:
  - base.arithmetics
  - base.beans
  - base.bindings
  - base.coercion
  - base.control
  - base.docs
  - base.funcs
  - base.lang
  - base.logic
  - base.math
  - base.regex
  - base.seq
  - base.text
  - base.types
  - base.version
  - dwim
  - ffi
  - io
  - loops
  - threads
* -r parser -- Specify parser. The default is `alg`, available parsers are:
  - alg -- [alg-parser](https://github.com/opsit-io/opsit-explang-alg-parser).
  - lisp -- Builtin lisp-like  parser 
  - sexp -- Builtin S-exps parser
* -f converter -- Specify function name converter. The default is `alg` , The available converters are:
     alg -- Case-insenitive converter for alg parser
     nop -- No Op converter, function lookup will be case-sensitive, the built-in functions are in UPPERCASE.
     uc -- uppercase converter. function lookup will be case insensitive.
* -h Print help message
* -v Print software version


### Key bindings

Gnu readline compatible. See more in the [JLine documentation](https://github.com/jline/jline3/wiki).
There are no custom keybindings currently defined.

### Completion

Completion support for the alg parser only that is activated on <TAB>. Very incomplete and 
not recommented for use.


Language Documentation
----------------------

See [Explang Language Documentation](https://github.com/opsit/opsit-explang-docs):

- [Language Overview](TBD)
- [REPL Reference](TBD)


Licenses
--------

Explang is licensed under the [GNU AFFERO GENERAL PUBLIC LICENSE](LICENSE).
