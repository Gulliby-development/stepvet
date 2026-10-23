package io.github.gulliby.stepvet.tagexpr;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A parsed, immutable tag expression. */
public final class TagExpr {
    private static final String ERROR_PREFIX = "invalid tag expression: ";

    private final List<Instruction> program;

    private TagExpr(List<Instruction> program) {
        this.program = List.copyOf(program);
    }

    /**
     * Parses a tag expression, reporting malformed input as a user-facing failure.
     *
     * @param expr expression to parse
     * @return an immutable parsed expression
     * @throws Failure if the expression is malformed or too long
     */
    public static TagExpr parse(String expr) {
        List<TagToken> tokens = TagLexer.lex(expr);
        return new TagExpr(new Parser(tokens).parse());
    }

    /**
     * Evaluates exact tag membership without modifying the supplied set.
     *
     * @param tags tags to test
     * @return whether the expression matches
     */
    public boolean matches(Set<String> tags) {
        boolean[] values = new boolean[program.size()];
        int size = 0;
        for (Instruction instruction : program) {
            switch (instruction.kind()) {
                case TAG -> values[size++] = tags.contains(instruction.tag());
                case NOT -> values[size - 1] = !values[size - 1];
                case AND -> {
                    boolean right = values[--size];
                    values[size - 1] = values[size - 1] && right;
                }
                case OR -> {
                    boolean right = values[--size];
                    values[size - 1] = values[size - 1] || right;
                }
            }
        }
        return values[0];
    }

    private enum InstructionKind {
        TAG, NOT, AND, OR
    }

    private record Instruction(InstructionKind kind, String tag) {
    }

    private enum Operator {
        LEFT_PAREN, NOT, AND, OR
    }

    /** Iterative shunting-yard parser; expression length bounds every stack. */
    private static final class Parser {
        private final List<TagToken> tokens;
        private final List<Instruction> output = new ArrayList<>();
        private final List<Operator> operators = new ArrayList<>();
        private int index;
        private int parenthesisDepth;

        private Parser(List<TagToken> tokens) {
            this.tokens = tokens;
        }

        private List<Instruction> parse() {
            boolean expectingAtom = true;
            while (true) {
                TagToken token = tokens.get(index);
                if (isBadWord(token)) {
                    throw invalidWord(token);
                }

                if (expectingAtom) {
                    if (token.eof() || isOperator(token, "and") || isOperator(token, "or")
                            || token.text().equals(")")) {
                        throw failure("expected a tag, \"not\" or \"(\" at column " + token.column());
                    }
                    if (token.text().equals("(")) {
                        operators.add(Operator.LEFT_PAREN);
                        parenthesisDepth++;
                        index++;
                    } else if (isOperator(token, "not")) {
                        operators.add(Operator.NOT);
                        index++;
                    } else {
                        output.add(new Instruction(InstructionKind.TAG, token.text()));
                        index++;
                        expectingAtom = false;
                        emitPendingNot();
                    }
                    continue;
                }

                if (isOperator(token, "and") || isOperator(token, "or")) {
                    Operator next = token.text().equals("and") ? Operator.AND : Operator.OR;
                    while (!operators.isEmpty()) {
                        Operator previous = operators.get(operators.size() - 1);
                        if (previous == Operator.LEFT_PAREN || precedence(previous) < precedence(next)) {
                            break;
                        }
                        emitOperator(operators.remove(operators.size() - 1));
                    }
                    operators.add(next);
                    index++;
                    expectingAtom = true;
                } else if (token.text().equals(")")) {
                    if (parenthesisDepth == 0) {
                        throw failure("unmatched \")\" at column " + token.column());
                    }
                    while (!operators.isEmpty()
                            && operators.get(operators.size() - 1) != Operator.LEFT_PAREN) {
                        emitOperator(operators.remove(operators.size() - 1));
                    }
                    operators.remove(operators.size() - 1);
                    parenthesisDepth--;
                    index++;
                    emitPendingNot();
                } else if (token.eof()) {
                    if (parenthesisDepth > 0) {
                        throw failure("expected \")\" at column " + token.column());
                    }
                    while (!operators.isEmpty()) {
                        emitOperator(operators.remove(operators.size() - 1));
                    }
                    return List.copyOf(output);
                } else if (parenthesisDepth > 0) {
                    throw failure("expected \"and\", \"or\" or \")\" at column " + token.column());
                } else {
                    throw failure("expected \"and\", \"or\" or the end at column " + token.column());
                }
            }
        }

        private void emitPendingNot() {
            while (!operators.isEmpty() && operators.get(operators.size() - 1) == Operator.NOT) {
                emitOperator(operators.remove(operators.size() - 1));
            }
        }

        private void emitOperator(Operator operator) {
            switch (operator) {
                case NOT -> output.add(new Instruction(InstructionKind.NOT, null));
                case AND -> output.add(new Instruction(InstructionKind.AND, null));
                case OR -> output.add(new Instruction(InstructionKind.OR, null));
                case LEFT_PAREN -> { }
            }
        }

        private static int precedence(Operator operator) {
            return switch (operator) {
                case OR -> 1;
                case AND -> 2;
                case NOT -> 3;
                case LEFT_PAREN -> 0;
            };
        }

        private static boolean isOperator(TagToken token, String operator) {
            return !token.eof() && token.text().equals(operator);
        }

        private static boolean isBadWord(TagToken token) {
            if (token.eof() || token.text().equals("(") || token.text().equals(")")
                    || isOperator(token, "and") || isOperator(token, "or") || isOperator(token, "not")) {
                return false;
            }
            String text = token.text();
            return text.length() < 2 || text.charAt(0) != '@' || text.indexOf('@', 1) >= 0;
        }

        private static Failure invalidWord(TagToken token) {
            return failure("invalid word " + Text.show(token.text()) + " at column " + token.column());
        }

        private static Failure failure(String message) {
            return new Failure(ERROR_PREFIX + message);
        }
    }
}
