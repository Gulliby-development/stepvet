package io.github.gulliby.stepvet.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {
    @Test
    void plainPreservesPrintableAsciiAndEmptyText() {
        assertEquals(" ~\"", Text.plain(" ~\""));
        assertEquals("", Text.plain(""));
    }

    @Test
    void plainReplacesOneCharacterPerNonAsciiCodePoint() {
        assertEquals("????", Text.plain("é😀\t\n"));
        assertEquals("????", Text.plain("\u001f\u007f\u0085\u2028"));
        assertEquals("caf?.txt", Text.plain("caf😀.txt"));
    }

    @Test
    void showAddsQuotesWithoutEscapingEmbeddedQuotes() {
        assertEquals("\"\"", Text.show(""));
        assertEquals("\"a\"b\"", Text.show("a\"b"));
    }

    @Test
    void rtrimRemovesOnlyTrailingSpacesAndTabs() {
        assertEquals(" \ta\tb", Text.rtrim(" \ta\tb \t"));
        assertEquals("", Text.rtrim(" \t"));
        assertEquals("", Text.rtrim(""));
    }

    @Test
    void rtrimPreservesLeadingInteriorAndNonblankCharacters() {
        assertEquals("a \u00a0\u0085\ufeff\u2028\u000b\f\n",
                Text.rtrim("a \u00a0\u0085\ufeff\u2028\u000b\f\n"));
    }

    @Test
    void isBlankRecognizesSpaceAndTab() {
        assertTrue(Text.isBlank(' '));
        assertTrue(Text.isBlank('\t'));
    }

    @Test
    void isBlankRejectsOtherWhitespaceAndBom() {
        assertFalse(Text.isBlank('\u00a0'));
        assertFalse(Text.isBlank('\u0085'));
        assertFalse(Text.isBlank('\ufeff'));
        assertFalse(Text.isBlank('\n'));
    }

    @Test
    void pluralUsesSingularOnlyForOne() {
        assertEquals("0 files", Text.plural(0, "file"));
        assertEquals("1 file", Text.plural(1, "file"));
        assertEquals("2 files", Text.plural(2, "file"));
    }

    @Test
    void failurePreservesItsMessage() {
        assertEquals("example", new Failure("example").getMessage());
    }
}
