package io.github.gulliby.stepvet.tagexpr;

/**
 * A raw tag-expression token and its 1-based UTF-16 column.
 *
 * @param text the exact token text, or the empty string for end of input
 * @param column the token's 1-based UTF-16 column
 * @param eof whether this token marks end of input
 */
public record TagToken(String text, int column, boolean eof) {
}
