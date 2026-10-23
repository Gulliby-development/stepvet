package io.github.gulliby.stepvet.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulesTest {
    @Test
    void nullAndEmptySpecsHaveImmutableNoLintResults() {
        assertEquals(List.of(), Rules.analyze(null));
        assertEquals(List.of(), Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of())));
        assertThrows(UnsupportedOperationException.class, () -> Rules.analyze(null).add(
                new Problem(1, 1, "SV101", "scenario has no steps")));
    }

    @Test
    void scenarioRulesCoverEmptyDuplicateAndExactNames() {
        assertEquals(List.of(
                new Problem(2, 3, "SV101", "scenario has no steps"),
                new Problem(4, 3, "SV101", "scenario has no steps")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(false, "", 2, 3, List.of(), List.of(), List.of()),
                        new Doc.Scenario(false, "", 4, 3, List.of(), List.of(), List.of())))));

        assertEquals(List.of(
                new Problem(4, 3, "SV101", "scenario has no steps"),
                new Problem(4, 3, "SV102", "duplicate scenario name \"Same\" (first at line 2)"),
                new Problem(4, 3, "SV106", "Scenario Outline: has no Examples:"),
                new Problem(6, 3, "SV102", "duplicate scenario name \"Same\" (first at line 2)")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(false, "Same", 2, 3, List.of(),
                                List.of(new Doc.Step("Then", "ok", 3, 5, 10)), List.of()),
                        new Doc.Scenario(true, "Same", 4, 3, List.of(), List.of(), List.of()),
                        new Doc.Scenario(false, "Same", 6, 3, List.of(),
                                List.of(new Doc.Step("Then", "ok", 7, 5, 10)), List.of())))));

        assertEquals(List.of(new Problem(4, 1, "SV102",
                        "duplicate scenario name \"??\" (first at line 2)")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(false, "é😀", 2, 1, List.of(),
                                List.of(new Doc.Step("Then", "ok", 3, 5, 10)), List.of()),
                        new Doc.Scenario(false, "é😀", 4, 1, List.of(),
                                List.of(new Doc.Step("Then", "ok", 5, 5, 10)), List.of())))));
        assertEquals(List.of(), Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                new Doc.Scenario(false, "A", 2, 1, List.of(),
                        List.of(new Doc.Step("Then", "ok", 3, 5, 10)), List.of()),
                new Doc.Scenario(false, "a", 4, 1, List.of(),
                        List.of(new Doc.Step("Then", "ok", 5, 5, 10)), List.of())))));
    }

    @Test
    void backgroundAndFirstStepKindsAreIndependent() {
        assertEquals(List.of(
                new Problem(5, 3, "SV104", "scenario has no Then step"),
                new Problem(6, 5, "SV103", "first step starts with And")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(false, "S", 5, 3, List.of(), List.of(
                                new Doc.Step("And", "x", 6, 5, 9),
                                new Doc.Step("But", "x", 7, 5, 9),
                                new Doc.Step("*", "x", 8, 5, 7)), List.of())))));

        assertEquals(List.of(
                new Problem(2, 5, "SV103", "first step starts with But"),
                new Problem(6, 5, "SV103", "first step starts with *")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(),
                        List.of(new Doc.Step("But", "x", 2, 5, 9)),
                        List.of(new Doc.Scenario(false, "S", 5, 3, List.of(), List.of(
                                new Doc.Step("*", "x", 6, 5, 7),
                                new Doc.Step("Then", "x", 7, 5, 9),
                                new Doc.Step("And", "x", 8, 5, 9)), List.of())))));

        assertEquals(List.of(
                new Problem(2, 5, "SV103", "first step starts with *"),
                new Problem(6, 5, "SV103", "first step starts with But")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(),
                        List.of(new Doc.Step("*", "x", 2, 5, 7)),
                        List.of(new Doc.Scenario(false, "S", 5, 3, List.of(), List.of(
                                new Doc.Step("But", "x", 6, 5, 9),
                                new Doc.Step("Then", "x", 7, 5, 9)), List.of())))));

        assertEquals(List.of(new Problem(5, 3, "SV101", "scenario has no steps")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(),
                        List.of(new Doc.Step("Then", "x", 2, 5, 9),
                                new Doc.Step("Given", "x", 3, 5, 10)),
                        List.of(new Doc.Scenario(false, "S", 5, 3, List.of(), List.of(), List.of())))));
        assertEquals(List.of(new Problem(5, 3, "SV104", "scenario has no Then step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(),
                        List.of(new Doc.Step("Then", "x", 2, 5, 9)),
                        List.of(new Doc.Scenario(false, "S", 5, 3, List.of(), List.of(
                                new Doc.Step("Given", "x", 6, 5, 10),
                                new Doc.Step("And", "x", 7, 5, 9)), List.of())))));
    }

    @Test
    void scenarioKindMaximumNeverDecreasesAndResets() {
        assertEquals(List.of(
                new Problem(7, 5, "SV105", "Given step after a When step"),
                new Problem(9, 5, "SV105", "Given step after a Then step"),
                new Problem(11, 5, "SV105", "When step after a Then step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(false, "S", 5, 3, List.of(), List.of(
                                new Doc.Step("When", "x", 6, 5, 9),
                                new Doc.Step("Given", "x", 7, 5, 10),
                                new Doc.Step("Then", "x", 8, 5, 9),
                                new Doc.Step("Given", "x", 9, 5, 10),
                                new Doc.Step("And", "x", 10, 5, 9),
                                new Doc.Step("When", "x", 11, 5, 9)), List.of())))));

        assertEquals(List.of(), Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                new Doc.Scenario(false, "S", 2, 1, List.of(), List.of(
                        new Doc.Step("Given", "x", 3, 5, 10),
                        new Doc.Step("When", "x", 4, 5, 9),
                        new Doc.Step("Then", "ok", 5, 5, 9),
                        new Doc.Step("But", "x", 6, 5, 9),
                        new Doc.Step("*", "x", 7, 5, 7)), List.of()),
                new Doc.Scenario(false, "Next", 8, 1, List.of(), List.of(
                        new Doc.Step("Given", "x", 9, 5, 10),
                        new Doc.Step("Then", "ok", 10, 5, 9)), List.of())))));
    }

    @Test
    void placeholderLocationsUseUtf16AndRecognizeOnlySubstForms() {
        assertEquals(List.of(new Problem(3, 9, "SV107",
                        "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                                new Doc.Step("Given", "<a>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));

        assertEquals(List.of(new Problem(3, 10, "SV107",
                        "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                                new Doc.Step("Given", "é<a>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));

        assertEquals(List.of(new Problem(3, 11, "SV107",
                        "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                                new Doc.Step("Given", "😀<a>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));

        assertEquals(List.of(
                new Problem(3, 8, "SV107", "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(3, 11, "SV107", "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                                new Doc.Step("Given", "<a><a>", 3, 5, 8),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));

        assertEquals(List.of(
                new Problem(3, 10, "SV107", "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(3, 15, "SV107", "placeholder <a b> has no column in the Examples: at line 5"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                                new Doc.Step("Given", "<<a>> <a b> <>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));

        assertEquals(List.of(new Problem(3, 9, "SV107",
                        "placeholder <??> has no column in the Examples: at line 5"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                                new Doc.Step("Given", "<é😀>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));

        assertEquals(List.of(
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                new Doc.Scenario(true, "S", 2, 1, List.of(), List.of(
                        new Doc.Step("Given", "<> <unfinished", 3, 5, 9),
                        new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                        new Doc.Examples(5, 5, "", List.of(), 6,
                                List.of(new Doc.Cell("z", 7)),
                                List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7)))))))))));
    }

    @Test
    void outlineBlocksReportOccurrencesBeforeBlockDiagnostics() {
        assertEquals(List.of(new Problem(2, 3, "SV106", "Scenario Outline: has no Examples:")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "", 2, 3, List.of(), List.of(
                                new Doc.Step("Given", "<a>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of())))));

        assertEquals(List.of(new Problem(5, 5, "SV109", "Examples: has no rows")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "", 2, 3, List.of(), List.of(
                                new Doc.Step("Given", "<a>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 0, null, List.of())))))));

        assertEquals(List.of(
                new Problem(3, 12, "SV107",
                        "placeholder <y> has no column in the Examples: at line 5"),
                new Problem(3, 12, "SV107",
                        "placeholder <y> has no column in the Examples: at line 8")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "", 2, 3, List.of(), List.of(
                                new Doc.Step("Given", "<x><y>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("x", 7)),
                                        List.of(new Doc.Row(7, List.of(new Doc.Cell("1", 7))))),
                                new Doc.Examples(8, 5, "", List.of(), 9,
                                        List.of(new Doc.Cell("x", 7)),
                                        List.of(new Doc.Row(10, List.of(new Doc.Cell("2", 7)))))))))));

        assertEquals(List.of(
                new Problem(3, 9, "SV107",
                        "placeholder <a> has no column in the Examples: at line 5"),
                new Problem(3, 9, "SV107",
                        "placeholder <a> has no column in the Examples: at line 8"),
                new Problem(3, 12, "SV107",
                        "placeholder <b> has no column in the Examples: at line 5"),
                new Problem(3, 12, "SV107",
                        "placeholder <b> has no column in the Examples: at line 8"),
                new Problem(5, 5, "SV109", "Examples: has no rows"),
                new Problem(8, 5, "SV109", "Examples: has no rows")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "", 2, 3, List.of(), List.of(
                                new Doc.Step("Given", "<a><b>", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6, List.of(), List.of()),
                                new Doc.Examples(8, 5, "", List.of(), 9, List.of(), List.of())))))));

        assertEquals(List.of(), Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                new Doc.Scenario(true, "", 2, 3, List.of(), List.of(
                        new Doc.Step("Given", "<x>", 3, 5, 9),
                        new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                        new Doc.Examples(5, 5, "", List.of(), 6,
                                List.of(new Doc.Cell("x", 7), new Doc.Cell("x", 11)),
                                List.of(new Doc.Row(7, List.of(
                                        new Doc.Cell("1", 7), new Doc.Cell("2", 11)))))))))));
        assertEquals(List.of(
                new Problem(5, 5, "SV109", "Examples: has no rows"),
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step"),
                new Problem(6, 11, "SV108", "column \"y\" is not used by any step"),
                new Problem(6, 14, "SV108", "column \"\" is not used by any step"),
                new Problem(6, 18, "SV108", "column \"??w\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                        new Doc.Scenario(true, "S", 2, 3, List.of(), List.of(
                                new Doc.Step("Given", "plain step", 3, 5, 9),
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("z", 7), new Doc.Cell("y", 11),
                                                new Doc.Cell("", 14), new Doc.Cell("é😀w", 18)),
                                        List.of())))))));
    }

    @Test
    void outlineUsageIgnoresTitleAndBackgroundAndReportsRepeatedUnusedCells() {
        assertEquals(List.of(
                new Problem(6, 7, "SV108", "column \"x\" is not used by any step"),
                new Problem(6, 11, "SV108", "column \"x\" is not used by any step")),
                Rules.analyze(new Doc.Spec("F <x>", 1, List.of(),
                        List.of(new Doc.Step("Given", "background <x>", 2, 3, 9)),
                        List.of(new Doc.Scenario(true, "Outline <x>", 3, 1, List.of(), List.of(
                                new Doc.Step("Then", "ok", 4, 5, 9)), List.of(
                                new Doc.Examples(5, 5, "", List.of(), 6,
                                        List.of(new Doc.Cell("x", 7), new Doc.Cell("x", 11)),
                                        List.of(new Doc.Row(7, List.of(
                                                new Doc.Cell("1", 7), new Doc.Cell("2", 11)))))))))));

        assertEquals(List.of(), Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                new Doc.Scenario(false, "S", 2, 1, List.of(), List.of(
                        new Doc.Step("Then", "ok", 3, 5, 9)), List.of(
                        new Doc.Examples(4, 5, "", List.of(), 5,
                                List.of(new Doc.Cell("unused", 6)), List.of())))))));
    }

    @Test
    void nonemptyAnalysisResultsAreImmutable() {
        List<Problem> problems = Rules.analyze(new Doc.Spec("F", 1, List.of(), null, List.of(
                new Doc.Scenario(false, "", 2, 3, List.of(), List.of(), List.of()))));
        assertEquals(List.of(new Problem(2, 3, "SV101", "scenario has no steps")), problems);
        assertThrows(UnsupportedOperationException.class,
                () -> problems.add(new Problem(3, 3, "SV101", "scenario has no steps")));
        assertEquals(List.of(new Problem(2, 3, "SV101", "scenario has no steps")), problems);
    }
}
