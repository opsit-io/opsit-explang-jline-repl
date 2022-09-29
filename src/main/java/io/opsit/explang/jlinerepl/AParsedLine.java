package io.opsit.explang.jlinerepl;

import io.opsit.explang.autosuggest.Tokenization;
import java.util.List;
import org.jline.reader.ParsedLine;

public class AParsedLine implements ParsedLine {
  String line;
  int cursor;
  String word;
  int wordCursor;
  int wordIndex;
  List<String> words;

  /**
   * Construct parsed line given member variables.
   */
  public AParsedLine(
      String line, int cursor, String word, int wordCursor, int wordIndex, List<String> words) {
    this.line = line;
    this.cursor = cursor;
    this.word = word;
    this.wordCursor = wordCursor;
    this.wordIndex = wordIndex;
  }

  /**
   * Construct parsed line given Tokenization structure.
   */
  public AParsedLine(String line, int cursor, Tokenization tkz) {
    this.line = line;
    this.cursor = cursor;
    this.word = tkz.token;
    this.wordCursor = tkz.tokenPos;
    if (null != tkz.tokenIndex) {
      this.wordIndex = tkz.tokenIndex;
    }
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append("AParsedLine(");
    b.append("line='").append(line()).append("', ");
    b.append("cursor=").append(cursor()).append(", ");
    b.append("word='").append(word()).append("', ");
    b.append("wordCursor=").append(wordCursor()).append(", ");
    b.append("wordIndex=").append(wordIndex()).append(", ");
    b.append("words=").append(words()).append(")");
    return b.toString();
  }

  @Override
  public int cursor() {
    return cursor;
  }

  @Override
  public String line() {
    return line;
  }

  @Override
  public String word() {
    return word;
  }

  @Override
  public int wordCursor() {
    return wordCursor;
  }

  @Override
  public int wordIndex() {
    return wordIndex;
  }

  @Override
  public List<String> words() {
    return words;
  }
}
