package io.github.gulliby.stepvet.lines;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassifyTest {
    @Test
    void recognizesBlankCommentAndTagsAfterTrimming() {
        assertEquals(new Classify.Item(Classify.Kind.BLANK, 3, "", "", 3, ""),
                Classify.classify(" \t "));
        assertEquals(new Classify.Item(Classify.Kind.COMMENT, 2, "", "", 2, "# Given x"),
                Classify.classify("  # Given x  "));
        assertEquals(new Classify.Item(Classify.Kind.TAGS, 1, "", "", 1, "@a # x"),
                Classify.classify("\t@a # x"));
    }

    @Test
    void rowPrecedesStepsAndTextUsesExactCaseAndWords() {
        assertEquals(new Classify.Item(Classify.Kind.ROW, 1, "", "", 1, "| Given x |"),
                Classify.classify(" | Given x | "));
        assertEquals(new Classify.Item(Classify.Kind.TEXT, 1, "", "", 1, "Givenx"),
                Classify.classify(" Givenx "));
        assertEquals(new Classify.Item(Classify.Kind.TEXT, 0, "", "", 0, "*x"),
                Classify.classify("*x"));
        assertEquals(new Classify.Item(Classify.Kind.TEXT, 0, "", "", 0, "feature: F"),
                Classify.classify("feature: F"));
        assertEquals(new Classify.Item(Classify.Kind.TEXT, 0, "", "", 0, "Feature : F"),
                Classify.classify("Feature : F"));
    }

    @Test
    void onlySpaceAndTabAreBlanksAndWordSeparators() {
        assertEquals(new Classify.Item(Classify.Kind.TEXT, 0, "", "", 0,
                        "\u00a0Given x\u0085\ufeff"),
                Classify.classify("\u00a0Given x\u0085\ufeff"));
        assertEquals(new Classify.Item(Classify.Kind.TEXT, 0, "", "", 0, "Given\u00a0x"),
                Classify.classify("Given\u00a0x"));
    }

    @Test
    void recognizesFeatureAndEmptyBackgroundTitles() {
        assertEquals(new Classify.Item(Classify.Kind.KEYWORD, 0, "Feature:", "F", 8, "Feature:F"),
                Classify.classify("Feature:F"));
        assertEquals(new Classify.Item(Classify.Kind.KEYWORD, 0, "Background:", "", 11, "Background:"),
                Classify.classify("Background:"));
    }

    @Test
    void recognizesOutlineAndExamplesKeywordsWithSourceOffsets() {
        assertEquals(new Classify.Item(Classify.Kind.KEYWORD, 2, "Scenario Outline:", "S", 21,
                        "Scenario Outline:  S"),
                Classify.classify("  Scenario Outline:  S \t"));
        assertEquals(new Classify.Item(Classify.Kind.KEYWORD, 0, "Examples:", "two", 10, "Examples: two"),
                Classify.classify("Examples: two"));
    }

    @Test
    void preservesInteriorTabsInKeywordTitles() {
        assertEquals(new Classify.Item(Classify.Kind.KEYWORD, 1, "Scenario:", "tab\tbed", 11,
                        "Scenario:\ttab\tbed"),
                Classify.classify("\tScenario:\ttab\tbed"));
    }

    @Test
    void recognizesStepTextAndTrimsOnlyItsLeadingAndTrailingBlanks() {
        assertEquals(new Classify.Item(Classify.Kind.STEP, 3, "Given", "a step\twith tabs", 9,
                        "Given\ta step\twith tabs"),
                Classify.classify("\t  Given\ta step\twith tabs   "));
        assertEquals(new Classify.Item(Classify.Kind.STEP, 2, "Given", "", 7, "Given"),
                Classify.classify("  Given \t"));
    }

    @Test
    void recognizesEveryStepWord() {
        assertEquals(new Classify.Item(Classify.Kind.STEP, 0, "When", "x", 5, "When x"),
                Classify.classify("When x"));
        assertEquals(new Classify.Item(Classify.Kind.STEP, 0, "And", "x", 4, "And x"),
                Classify.classify("And x"));
        assertEquals(new Classify.Item(Classify.Kind.STEP, 0, "But", "x", 4, "But x"),
                Classify.classify("But x"));
        assertEquals(new Classify.Item(Classify.Kind.STEP, 0, "*", "x", 2, "*\tx"),
                Classify.classify("*\tx"));
    }

    @Test
    void preservesUnicodeStepTextAndUtf16StartOffset() {
        assertEquals(new Classify.Item(Classify.Kind.STEP, 0, "Then", "é😀", 5, "Then é😀"),
                Classify.classify("Then é😀"));
    }
}
