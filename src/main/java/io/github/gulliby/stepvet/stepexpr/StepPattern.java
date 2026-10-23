package io.github.gulliby.stepvet.stepexpr;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Text;

import java.util.ArrayList;
import java.util.List;

/** An immutable step pattern and its deterministic, full-text matcher. */
public final class StepPattern {
    private static final int LITERAL = 0;
    private static final int ALTERNATIVES = 1;
    private static final int OPTIONAL = 2;
    private static final int PARAMETER = 3;

    private static final int INT = 0;
    private static final int FLOAT = 1;
    private static final int WORD = 2;
    private static final int STRING = 3;
    private static final int ANY = 4;

    private final String text;
    private final int line;
    private final List<Operation> operations;

    private StepPattern(String text, int line, List<Operation> operations) {
        this.text = text;
        this.line = line;
        this.operations = List.copyOf(operations);
    }

    public static StepPattern parse(String text, int line, int col0, String display) {
        Parser parser = new Parser(text, line, col0, display);
        return new StepPattern(text, line, parser.parse());
    }

    public String text() {
        return text;
    }

    public int line() {
        return line;
    }

    /**
     * Returns the first successful capture sequence, or null when the whole input does not match.
     * The search is an explicit depth-first traversal, so pattern and input depth use heap state.
     */
    public List<String> match(String stepText) {
        ArrayList<String> captures = new ArrayList<>();
        ArrayList<ChoiceFrame> choices = new ArrayList<>();
        int operationIndex = 0;
        int inputOffset = 0;

        while (true) {
            if (operationIndex == operations.size()) {
                if (inputOffset == stepText.length()) {
                    return List.copyOf(captures);
                }
                Transition transition = backtrack(choices, captures, stepText);
                if (transition == null) {
                    return null;
                }
                ChoiceFrame frame = choices.get(choices.size() - 1);
                operationIndex = frame.operationIndex + 1;
                inputOffset = transition.inputEnd;
                addCapture(captures, stepText, transition);
                continue;
            }

            Operation operation = operations.get(operationIndex);
            if (operation.kind == LITERAL) {
                if (stepText.startsWith(operation.text, inputOffset)) {
                    inputOffset += operation.text.length();
                    operationIndex++;
                    continue;
                }
            } else {
                Cursor cursor;
                if (operation.kind == ALTERNATIVES) {
                    cursor = new AlternativeCursor(operation.alternatives, stepText, inputOffset);
                } else if (operation.kind == OPTIONAL) {
                    cursor = new OptionalCursor(operation.text, stepText, inputOffset);
                } else {
                    cursor = new ParameterCursor(operation.parameter, stepText, inputOffset);
                }
                Transition transition = cursor.next();
                if (transition != null) {
                    choices.add(new ChoiceFrame(operationIndex, inputOffset, captures.size(), cursor));
                    operationIndex++;
                    inputOffset = transition.inputEnd;
                    addCapture(captures, stepText, transition);
                    continue;
                }
            }

            Transition transition = backtrack(choices, captures, stepText);
            if (transition == null) {
                return null;
            }
            ChoiceFrame frame = choices.get(choices.size() - 1);
            operationIndex = frame.operationIndex + 1;
            inputOffset = transition.inputEnd;
            addCapture(captures, stepText, transition);
        }
    }

    private static void addCapture(List<String> captures, String input, Transition transition) {
        if (transition.captureStart >= 0) {
            captures.add(input.substring(transition.captureStart, transition.captureEnd));
        }
    }

    private static Transition backtrack(
            ArrayList<ChoiceFrame> choices, ArrayList<String> captures, String input) {
        while (!choices.isEmpty()) {
            ChoiceFrame frame = choices.get(choices.size() - 1);
            while (captures.size() > frame.captureCount) {
                captures.remove(captures.size() - 1);
            }
            Transition transition = frame.cursor.next();
            if (transition != null) {
                return transition;
            }
            choices.remove(choices.size() - 1);
        }
        return null;
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static final class Operation {
        private final int kind;
        private final String text;
        private final List<String> alternatives;
        private final int parameter;

        private Operation(int kind, String text, List<String> alternatives, int parameter) {
            this.kind = kind;
            this.text = text;
            this.alternatives = alternatives;
            this.parameter = parameter;
        }

        private static Operation literal(String value) {
            return new Operation(LITERAL, value, List.of(), -1);
        }

        private static Operation alternatives(List<String> values) {
            return new Operation(ALTERNATIVES, null, List.copyOf(values), -1);
        }

        private static Operation optional(String value) {
            return new Operation(OPTIONAL, value, List.of(), -1);
        }

        private static Operation parameter(int value) {
            return new Operation(PARAMETER, null, List.of(), value);
        }
    }

    private static final class Parser {
        private final String text;
        private final int line;
        private final int col0;
        private final String display;
        private int offset;
        private final ArrayList<Operation> operations = new ArrayList<>();

        private Parser(String text, int line, int col0, String display) {
            this.text = text;
            this.line = line;
            this.col0 = col0;
            this.display = display;
        }

        private List<Operation> parse() {
            while (offset < text.length()) {
                char value = text.charAt(offset);
                if (Text.isBlank(value)) {
                    parseBlanks();
                } else if (value == '{') {
                    parseParameter();
                } else if (value == '}') {
                    fail(offset, "unmatched }");
                } else if (value == '(') {
                    parseOptional();
                } else if (value == ')') {
                    fail(offset, "unmatched )");
                } else {
                    parseWord();
                }
            }
            return operations;
        }

        private void parseBlanks() {
            int start = offset++;
            while (offset < text.length() && Text.isBlank(text.charAt(offset))) {
                offset++;
            }
            operations.add(Operation.literal(text.substring(start, offset)));
        }

        private void parseParameter() {
            int start = offset;
            int close = text.indexOf('}', start + 1);
            if (close < 0) {
                fail(start, "unterminated {");
            }
            String token = text.substring(start, close + 1);
            int parameter = switch (token) {
                case "{}" -> ANY;
                case "{int}" -> INT;
                case "{float}" -> FLOAT;
                case "{word}" -> WORD;
                case "{string}" -> STRING;
                default -> {
                    fail(start, "unknown parameter " + Text.show(token));
                    yield -1;
                }
            };
            operations.add(Operation.parameter(parameter));
            offset = close + 1;
        }

        private void parseOptional() {
            int start = offset;
            int cursor = start + 1;
            StringBuilder literal = new StringBuilder();
            while (cursor < text.length()) {
                char value = text.charAt(cursor);
                if (value == ')') {
                    if (literal.length() == 0) {
                        fail(start, "empty ()");
                    }
                    operations.add(Operation.optional(literal.toString()));
                    offset = cursor + 1;
                    return;
                }
                if (value == '\\') {
                    if (cursor + 1 >= text.length()) {
                        failEscape(cursor, -1);
                    }
                    char escaped = text.charAt(cursor + 1);
                    if (!isEscapable(escaped)) {
                        failEscape(cursor, cursor + 1);
                    }
                    literal.append(escaped);
                    cursor += 2;
                } else {
                    literal.append(value);
                    cursor++;
                }
            }
            fail(start, "unterminated (");
        }

        private void parseWord() {
            ArrayList<String> alternatives = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean sawSlash = false;
            int lastSlash = -1;

            while (offset < text.length()) {
                char value = text.charAt(offset);
                if (Text.isBlank(value) || value == '{' || value == '}' || value == '(' || value == ')') {
                    break;
                }
                if (value == '\\') {
                    if (offset + 1 >= text.length()) {
                        failEscape(offset, -1);
                    }
                    char escaped = text.charAt(offset + 1);
                    if (!isEscapable(escaped)) {
                        failEscape(offset, offset + 1);
                    }
                    current.append(escaped);
                    offset += 2;
                } else if (value == '/') {
                    if (current.length() == 0) {
                        fail(offset, "empty alternative");
                    }
                    alternatives.add(current.toString());
                    current.setLength(0);
                    sawSlash = true;
                    lastSlash = offset++;
                } else {
                    current.append(value);
                    offset++;
                }
            }

            if (current.length() == 0) {
                if (sawSlash) {
                    fail(lastSlash, "empty alternative");
                }
                return;
            }
            if (sawSlash) {
                alternatives.add(current.toString());
                operations.add(Operation.alternatives(alternatives));
            } else {
                operations.add(Operation.literal(current.toString()));
            }
        }

        private void failEscape(int start, int next) {
            String escaped;
            if (next < 0) {
                escaped = text.substring(start, start + 1);
            } else {
                int end = next + 1;
                int codePoint = text.codePointAt(next);
                if (Character.isSupplementaryCodePoint(codePoint)) {
                    end++;
                }
                escaped = text.substring(start, end);
            }
            fail(start, "bad escape " + Text.show(escaped));
        }

        private void fail(int at, String message) {
            throw new Failure(display + ":" + line + ":" + (col0 + at)
                    + ": invalid pattern: " + message);
        }
    }

    private static boolean isEscapable(char value) {
        return value == '\\' || value == '(' || value == ')' || value == '{'
                || value == '}' || value == '/';
    }

    private interface Cursor {
        Transition next();
    }

    private static final class ChoiceFrame {
        private final int operationIndex;
        private final int inputOffset;
        private final int captureCount;
        private final Cursor cursor;

        private ChoiceFrame(int operationIndex, int inputOffset, int captureCount, Cursor cursor) {
            this.operationIndex = operationIndex;
            this.inputOffset = inputOffset;
            this.captureCount = captureCount;
            this.cursor = cursor;
        }
    }

    private static final class Transition {
        private final int inputEnd;
        private final int captureStart;
        private final int captureEnd;

        private Transition(int inputEnd, int captureStart, int captureEnd) {
            this.inputEnd = inputEnd;
            this.captureStart = captureStart;
            this.captureEnd = captureEnd;
        }

        private static Transition withoutCapture(int inputEnd) {
            return new Transition(inputEnd, -1, -1);
        }

        private static Transition capture(int inputEnd, int captureStart, int captureEnd) {
            return new Transition(inputEnd, captureStart, captureEnd);
        }
    }

    private static final class AlternativeCursor implements Cursor {
        private final List<String> alternatives;
        private final String input;
        private final int inputOffset;
        private int nextAlternative;

        private AlternativeCursor(List<String> alternatives, String input, int inputOffset) {
            this.alternatives = alternatives;
            this.input = input;
            this.inputOffset = inputOffset;
        }

        @Override
        public Transition next() {
            while (nextAlternative < alternatives.size()) {
                String alternative = alternatives.get(nextAlternative++);
                if (input.startsWith(alternative, inputOffset)) {
                    return Transition.withoutCapture(inputOffset + alternative.length());
                }
            }
            return null;
        }
    }

    private static final class OptionalCursor implements Cursor {
        private final String text;
        private final String input;
        private final int inputOffset;
        private boolean first = true;
        private boolean absentPending;

        private OptionalCursor(String text, String input, int inputOffset) {
            this.text = text;
            this.input = input;
            this.inputOffset = inputOffset;
        }

        @Override
        public Transition next() {
            if (first) {
                first = false;
                if (input.startsWith(text, inputOffset)) {
                    absentPending = true;
                    return Transition.withoutCapture(inputOffset + text.length());
                }
                return Transition.withoutCapture(inputOffset);
            }
            if (absentPending) {
                absentPending = false;
                return Transition.withoutCapture(inputOffset);
            }
            return null;
        }
    }

    private static final class ParameterCursor implements Cursor {
        private final int parameter;
        private final String input;
        private final int start;
        private int nextEnd;
        private int minimumEnd;
        private int quoteSearch;
        private char quote;
        private int integerEnd;
        private int integerMinimumEnd;
        private boolean floatDecimal;
        private boolean integerPending;
        private boolean available;

        private ParameterCursor(int parameter, String input, int start) {
            this.parameter = parameter;
            this.input = input;
            this.start = start;
            initialize();
        }

        private void initialize() {
            if (parameter == STRING) {
                if (start >= input.length()) {
                    return;
                }
                quote = input.charAt(start);
                if (quote != '"' && quote != '\'') {
                    return;
                }
                quoteSearch = input.indexOf(quote, start + 1);
                if (quoteSearch < 0) {
                    return;
                }
                available = true;
                return;
            }

            if (parameter == ANY) {
                nextEnd = input.length();
                minimumEnd = start + 1;
                available = nextEnd >= minimumEnd;
                return;
            }
            if (parameter == WORD) {
                int end = start;
                while (end < input.length() && !Text.isBlank(input.charAt(end))) {
                    end++;
                }
                nextEnd = end;
                minimumEnd = start + 1;
                available = nextEnd >= minimumEnd;
                return;
            }

            int digitsStart = start;
            if (digitsStart < input.length() && input.charAt(digitsStart) == '-') {
                digitsStart++;
            }
            if (digitsStart >= input.length() || !isAsciiDigit(input.charAt(digitsStart))) {
                return;
            }
            int digitsEnd = digitsStart;
            while (digitsEnd < input.length() && isAsciiDigit(input.charAt(digitsEnd))) {
                digitsEnd++;
            }

            if (parameter == INT) {
                nextEnd = digitsEnd;
                minimumEnd = digitsStart + 1;
                available = true;
                return;
            }

            integerEnd = digitsEnd;
            integerMinimumEnd = digitsStart + 1;
            if (digitsEnd + 1 < input.length() && input.charAt(digitsEnd) == '.'
                    && isAsciiDigit(input.charAt(digitsEnd + 1))) {
                int decimalEnd = digitsEnd + 1;
                while (decimalEnd < input.length() && isAsciiDigit(input.charAt(decimalEnd))) {
                    decimalEnd++;
                }
                nextEnd = decimalEnd;
                minimumEnd = digitsEnd + 2;
                floatDecimal = true;
                integerPending = true;
            } else {
                nextEnd = digitsEnd;
                minimumEnd = digitsStart + 1;
            }
            available = true;
        }

        @Override
        public Transition next() {
            if (!available) {
                return null;
            }
            if (parameter == STRING) {
                available = false;
                return Transition.capture(quoteSearch + 1, start + 1, quoteSearch);
            }

            if (nextEnd < minimumEnd && floatDecimal) {
                floatDecimal = false;
                if (integerPending) {
                    integerPending = false;
                    nextEnd = integerEnd;
                    minimumEnd = integerMinimumEnd;
                }
            }
            if (nextEnd >= minimumEnd) {
                int end = nextEnd--;
                return Transition.capture(end, start, end);
            }
            available = false;
            return null;
        }
    }
}
