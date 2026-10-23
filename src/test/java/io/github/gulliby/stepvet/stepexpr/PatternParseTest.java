package io.github.gulliby.stepvet.stepexpr;

import io.github.gulliby.stepvet.base.Failure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatternParseTest {
    @Test
    void definitionsTrimOnlySpaceAndTabPreserveLinesAndDuplicates() {
        String page = String.join("\n", " \t", "\t# comment", "  item(s) \t", "\t{int}", "item(s)", "");
        assertEquals(" \t\n\t# comment\n  item(s) \t\n\t{int}\nitem(s)\n", page);
        List<StepPattern> patterns = Defs.parse(page, "d");

        assertEquals(3, patterns.size());
        assertEquals("item(s)", patterns.get(0).text());
        assertEquals(3, patterns.get(0).line());
        assertEquals("{int}", patterns.get(1).text());
        assertEquals(4, patterns.get(1).line());
        assertEquals("item(s)", patterns.get(2).text());
        assertEquals(5, patterns.get(2).line());
        assertThrows(UnsupportedOperationException.class, () -> patterns.add(null));
    }

    @Test
    void definitionsTreatEveryNonLfCharacterAsPatternText() {
        String page = String.join("\n",
                "a # b", "\u00a0", "\u0085", "\ufeff", "a\u2028b", "\u000b", "\u000c");
        assertEquals("a # b\n\u00a0\n\u0085\n\ufeff\na\u2028b\n\u000b\n\u000c", page);
        List<StepPattern> patterns = Defs.parse(page, "d");

        assertEquals(List.of("a # b", "\u00a0", "\u0085", "\ufeff", "a\u2028b", "\u000b", "\u000c"),
                patterns.stream().map(StepPattern::text).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), patterns.stream().map(StepPattern::line).toList());
    }

    @Test
    void emptyDefinitionsAndFinalLfHaveNoPhantomPatterns() {
        List<StepPattern> empty = Defs.parse("", "d");
        assertEquals(List.of(), empty);
        assertEquals(List.of(), Defs.parse(String.join("\n", "# comment", "\t "), "d"));
        assertThrows(UnsupportedOperationException.class, () -> empty.add(null));

        List<StepPattern> one = Defs.parse("x\n", "d");
        assertEquals(1, one.size());
        assertEquals(1, one.get(0).line());
        assertEquals("x", one.get(0).text());
    }

    @Test
    void directParserPreservesOriginalTextAndLine() {
        StepPattern pattern = StepPattern.parse(" a ", 7, 3, "d");

        assertEquals(" a ", pattern.text());
        assertEquals(7, pattern.line());
        assertEquals(List.of(), pattern.match(" a "));
    }

    @Test
    void directParserColumnsCountUtf16UnitsFromCallerOrigin() {
        Failure failure = assertThrows(Failure.class,
                () -> StepPattern.parse("😀{x}", 7, 4, "defs.txt"));

        assertEquals("defs.txt:7:6: invalid pattern: unknown parameter \"{x}\"", failure.getMessage());
    }

    @Test
    void definitionsReportUnknownAndUnterminatedParametersAtExactColumns() {
        assertEquals("d:1:7: invalid pattern: unterminated {",
                assertThrows(Failure.class, () -> Defs.parse("I add {int", "d")).getMessage());
        assertEquals("d:1:5: invalid pattern: unknown parameter \"{color}\"",
                assertThrows(Failure.class, () -> Defs.parse("  a {color} b", "d")).getMessage());
        assertEquals("d:1:4: invalid pattern: unknown parameter \"{x}\"",
                assertThrows(Failure.class, () -> Defs.parse("a😀{x}", "d")).getMessage());
        assertEquals("d:1:1: invalid pattern: unknown parameter \"{??}\"",
                assertThrows(Failure.class, () -> Defs.parse("{é😀}", "d")).getMessage());
        assertEquals("d:2:3: invalid pattern: unknown parameter \"{x}\"",
                assertThrows(Failure.class, () -> Defs.parse(String.join("\n", "# ignored", "\t {x}", "}"), "d"))
                        .getMessage());
    }

    @Test
    void definitionsReportUnmatchedAndEmptyDelimiters() {
        assertEquals("d:1:3: invalid pattern: unmatched }",
                assertThrows(Failure.class, () -> Defs.parse("a } b", "d")).getMessage());
        assertEquals("d:1:3: invalid pattern: empty ()",
                assertThrows(Failure.class, () -> Defs.parse("x () y", "d")).getMessage());
        assertEquals("d:1:3: invalid pattern: unmatched )",
                assertThrows(Failure.class, () -> Defs.parse("x ) y", "d")).getMessage());
        assertEquals("d:1:1: invalid pattern: unmatched }",
                assertThrows(Failure.class, () -> Defs.parse("} {x}", "d")).getMessage());
    }

    @Test
    void definitionsReportUnterminatedOptionalAndBadEscapes() {
        assertEquals("d:1:3: invalid pattern: unterminated (",
                assertThrows(Failure.class, () -> Defs.parse("x (a", "d")).getMessage());
        assertEquals("d:1:3: invalid pattern: bad escape \"\\q\"",
                assertThrows(Failure.class, () -> Defs.parse("a \\q b", "d")).getMessage());
        assertEquals("d:1:3: invalid pattern: bad escape \"\\\"",
                assertThrows(Failure.class, () -> Defs.parse("a \\", "d")).getMessage());
    }

    @Test
    void definitionsReportFirstEmptyAlternativeAtItsSlash() {
        assertEquals("d:1:5: invalid pattern: empty alternative",
                assertThrows(Failure.class, () -> Defs.parse("cat//dog", "d")).getMessage());
        assertEquals("d:1:1: invalid pattern: empty alternative",
                assertThrows(Failure.class, () -> Defs.parse("/x", "d")).getMessage());
        assertEquals("d:1:2: invalid pattern: empty alternative",
                assertThrows(Failure.class, () -> Defs.parse("a/", "d")).getMessage());
        assertEquals("d:1:6: invalid pattern: empty alternative",
                assertThrows(Failure.class, () -> Defs.parse("{int}/{int}", "d")).getMessage());
    }
}
