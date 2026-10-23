package io.github.gulliby.stepvet.tagexpr;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Text;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes bounded tag expressions without validating words eagerly. */
public final class TagLexer {
    static final int MAX_LENGTH = 200;

    private TagLexer() {
    }

    /**
     * Returns the raw words, parentheses and explicit end token of an expression.
     * Only space and TAB separate words. Columns count UTF-16 units.
     *
     * @param expression expression to tokenize
     * @return an immutable token list
     * @throws Failure if the expression exceeds the supported UTF-16 length
     */
    public static List<TagToken> lex(String expression) {
        if (expression.length() > MAX_LENGTH) {
            throw new Failure("invalid tag expression: longer than 200 characters");
        }

        List<TagToken> tokens = new ArrayList<>();
        int offset = 0;
        while (offset < expression.length()) {
            char current = expression.charAt(offset);
            if (Text.isBlank(current)) {
                offset++;
            } else if (current == '(' || current == ')') {
                tokens.add(new TagToken(String.valueOf(current), offset + 1, false));
                offset++;
            } else {
                int start = offset;
                while (offset < expression.length()) {
                    char character = expression.charAt(offset);
                    if (Text.isBlank(character) || character == '(' || character == ')') {
                        break;
                    }
                    offset++;
                }
                tokens.add(new TagToken(expression.substring(start, offset), start + 1, false));
            }
        }

        tokens.add(new TagToken("", expression.length() + 1, true));
        return List.copyOf(tokens);
    }
}
