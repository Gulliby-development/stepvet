package io.github.gulliby.stepvet.stepexpr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatternMatchTest {
    @Test
    void integerAndFloatParametersUseTheirSpecifiedNumericForms() {
        StepPattern integer = StepPattern.parse("{int}", 1, 1, "d");
        assertEquals(List.of("-5"), integer.match("-5"));
        assertNull(integer.match("+5"));
        assertNull(integer.match("5.0"));
        assertEquals(List.of("007"), integer.match("007"));
        assertNull(integer.match("٥"));

        StepPattern decimal = StepPattern.parse("{float}", 1, 1, "d");
        assertEquals(List.of("5"), decimal.match("5"));
        assertEquals(List.of("-0.5"), decimal.match("-0.5"));
        assertNull(decimal.match(".5"));
        assertNull(decimal.match("5."));
    }

    @Test
    void wordAndAnyParametersRequireNonemptyCapturesAndKeepLiteralCharacters() {
        StepPattern word = StepPattern.parse("{word}", 1, 1, "d");
        assertEquals(List.of("a-b"), word.match("a-b"));
        assertNull(word.match("a b"));
        assertNull(word.match(""));
        assertNull(word.match("a\tb"));
        assertEquals(List.of("a\u00a0b"), word.match("a\u00a0b"));

        StepPattern any = StepPattern.parse("{}", 1, 1, "d");
        assertNull(any.match(""));
        assertEquals(List.of("a\nb"), any.match("a\nb"));
    }

    @Test
    void stringParameterCapturesQuotedInteriorsIncludingEmptyStrings() {
        StepPattern string = StepPattern.parse("{string}", 1, 1, "d");
        assertEquals(List.of("a b"), string.match("\"a b\""));
        assertEquals(List.of("it"), string.match("'it'"));
        assertEquals(List.of(""), string.match("\"\""));
        assertEquals(List.of("a'b"), string.match("\"a'b\""));
        assertNull(string.match("\"a'"));
        assertNull(string.match("x"));
        assertEquals(List.of("a\\b"), string.match("'a\\b'"));
    }

    @Test
    void optionalLiteralsMayBePresentOrAbsentAndPreferPresence() {
        StepPattern suffix = StepPattern.parse("item(s)", 1, 1, "d");
        assertEquals(List.of(), suffix.match("item"));
        assertEquals(List.of(), suffix.match("items"));
        assertNull(suffix.match("itemz"));

        StepPattern separated = StepPattern.parse("(a )b", 1, 1, "d");
        assertEquals(List.of(), separated.match("a b"));
        assertEquals(List.of(), separated.match("b"));

        StepPattern captured = StepPattern.parse("(a){}", 1, 1, "d");
        assertEquals(List.of("b"), captured.match("ab"));
        assertEquals(List.of("a"), captured.match("a"));
        assertEquals(List.of(), StepPattern.parse("(a/b{x})", 1, 1, "d").match("a/b{x}"));
        assertEquals(List.of(), StepPattern.parse("(a\\)b)", 1, 1, "d").match("a)b"));
    }

    @Test
    void alternativesAreOrderedAndRequireOneWholeAlternative() {
        StepPattern choices = StepPattern.parse("a/b c", 1, 1, "d");
        assertEquals(List.of(), choices.match("a c"));
        assertEquals(List.of(), choices.match("b c"));
        assertNull(choices.match("a/b c"));

        assertEquals(List.of("bc"), StepPattern.parse("a/ab{}", 1, 1, "d").match("abc"));
        assertEquals(List.of("c"), StepPattern.parse("ab/a{}", 1, 1, "d").match("abc"));
    }

    @Test
    void escapesMakeReservedCharactersLiteral() {
        assertEquals(List.of(), StepPattern.parse("x\\(y\\) a\\/b", 1, 1, "d").match("x(y) a/b"));
        assertEquals(List.of(), StepPattern.parse("\\{x\\}\\\\", 1, 1, "d").match("{x}\\"));
        assertEquals(List.of("1", "2"),
                StepPattern.parse("{int}\\/{int}", 1, 1, "d").match("1/2"));
        assertNull(StepPattern.parse("{int}\\/{int}", 1, 1, "d").match("1"));
    }

    @Test
    void parameterCapturesBacktrackGreedilyUntilTheWholeSuffixFits() {
        assertEquals(List.of("a b", "c"), StepPattern.parse("{} {}", 1, 1, "d").match("a b c"));
        assertNull(StepPattern.parse("{} {}", 1, 1, "d").match("a"));
        assertEquals(List.of("12", "3"), StepPattern.parse("{int}{int}", 1, 1, "d").match("123"));
        assertEquals(List.of("5"), StepPattern.parse("{float}.0", 1, 1, "d").match("5.0"));
        assertEquals(List.of("ab"), StepPattern.parse("{word}x", 1, 1, "d").match("abx"));
    }

    @Test
    void matchingIsWholeTextCaseSensitiveAndDoesNotNormalizeBlanks() {
        assertNull(StepPattern.parse("a b", 1, 1, "d").match("a\tb"));
        assertNull(StepPattern.parse("a", 1, 1, "d").match("A"));
        assertNull(StepPattern.parse("a", 1, 1, "d").match("ba"));
        assertEquals(List.of(), StepPattern.parse("", 1, 1, "d").match(""));
        assertNull(StepPattern.parse("", 1, 1, "d").match("x"));
    }

    @Test
    void acceptanceRowsAssertEachPatternAndStepPairDirectly() {
        assertNull(StepPattern.parse("{int}", 1, 1, "d").match("5.0"));
        assertEquals(List.of("007"), StepPattern.parse("{int}", 1, 1, "d").match("007"));
        assertEquals(List.of("-0.5"), StepPattern.parse("{float}", 1, 1, "d").match("-0.5"));
        assertEquals(List.of("a-b"), StepPattern.parse("{word}", 1, 1, "d").match("a-b"));
        assertNull(StepPattern.parse("{word}", 1, 1, "d").match("a b"));
        assertNull(StepPattern.parse("{word}", 1, 1, "d").match("a\tb"));
        assertEquals(List.of("a\u00a0b"), StepPattern.parse("{word}", 1, 1, "d").match("a\u00a0b"));
        assertEquals(List.of("a b"), StepPattern.parse("{string}", 1, 1, "d").match("\"a b\""));
        assertEquals(List.of("it"), StepPattern.parse("{string}", 1, 1, "d").match("'it'"));
        assertEquals(List.of("a'b"), StepPattern.parse("{string}", 1, 1, "d").match("\"a'b\""));
        assertEquals(List.of("a\\b"), StepPattern.parse("{string}", 1, 1, "d").match("'a\\b'"));
        assertEquals(List.of("12", "3"), StepPattern.parse("{int}{int}", 1, 1, "d").match("123"));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "{int}|5.0|N||",
            "{int}|007|1|007|",
            "{float}|-0.5|1|-0.5|",
            "{word}|a-b|1|a-b|",
            "{word}|a b|N||",
            "{word}|a\tb|N||",
            "{word}|a\\u00a0b|1|a\\u00a0b|",
            "{string}|\"a b\"|1|a b|",
            "{string}|'it'|1|it|",
            "{string}|\"a'b\"|1|a'b|",
            "{string}|'a\\\\b'|1|a\\\\b|",
            "{int}{int}|123|2|12|3"
    }, delimiter = '|', quoteCharacter = '§')
    void literalRowsPreserveTheirExactExpectedCaptureResults(
            String patternText, String stepText, String resultKind, String first, String second) {
        List<String> actual = StepPattern.parse(patternText, 1, 1, "d").match(stepText);
        switch (resultKind) {
            case "N" -> assertNull(actual);
            case "1" -> assertEquals(List.of(first), actual);
            case "2" -> assertEquals(List.of(first, second), actual);
            default -> throw new AssertionError("unexpected result kind");
        }
    }

    @Test
    void matchingCollectsEveryDefinitionInSourceOrderWithZeroBasedIndices() {
        List<StepPattern> patterns = Defs.parse(String.join("\n", "# defs", "an empty cart", "{} cart", "an empty cart"), "d");
        assertEquals(List.of(
                new Matching.Hit(0, List.of()),
                new Matching.Hit(1, List.of("an empty")),
                new Matching.Hit(2, List.of())),
                Matching.of(patterns, "an empty cart"));
        assertEquals(List.of(new Matching.Hit(1, List.of("007"))),
                Matching.of(Defs.parse(String.join("\n", "x", "{int}"), "d"), "007"));
        assertEquals(List.of(), Matching.of(Defs.parse("x", "d"), "y"));
        assertEquals(List.of(), Matching.of(Defs.parse("", "d"), "x"));
    }

    @Test
    void matchesAndHitsAreImmutableAndRepeatedCallsDoNotChangeEarlierResults() {
        StepPattern capturedPattern = StepPattern.parse("{int}", 1, 1, "d");
        List<String> captures = capturedPattern.match("007");
        assertThrows(UnsupportedOperationException.class, () -> captures.add("8"));
        assertEquals(List.of("007"), capturedPattern.match("007"));
        assertEquals(List.of("007"), captures);
        assertThrows(UnsupportedOperationException.class,
                () -> StepPattern.parse("plain", 1, 1, "d").match("plain").add("value"));

        List<Matching.Hit> hits = Matching.of(List.of(capturedPattern), "007");
        assertThrows(UnsupportedOperationException.class, () -> hits.add(new Matching.Hit(1, List.of())));
        assertThrows(UnsupportedOperationException.class, () -> hits.get(0).args().add("8"));
        assertEquals(List.of(new Matching.Hit(0, List.of("007"))), hits);
        assertEquals(List.of(), Matching.of(List.of(), "plain"));
        assertThrows(UnsupportedOperationException.class,
                () -> Matching.of(List.of(), "plain").add(new Matching.Hit(0, List.of())));

        List<String> original = new ArrayList<>(List.of("007"));
        Matching.Hit copied = new Matching.Hit(2, original);
        original.set(0, "changed");
        assertEquals(List.of("007"), copied.args());
        assertThrows(UnsupportedOperationException.class, () -> copied.args().add("8"));
    }
}
