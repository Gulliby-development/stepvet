package io.github.gulliby.stepvet.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.stepvet.model.Problem;
import io.github.gulliby.stepvet.parse.Parser;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateColumnTest {
    private static final String ACCENTED = new String(new int[] {0xE9, 0x1F600}, 0, 2);

    private static List<Problem> lint(String... lines) {
        return Problem.sorted(Rules.analyze(Parser.parse(String.join("\n", lines) + "\n").spec()));
    }

    @Test
    void aRepeatedHeaderNameIsReportedAtTheSecondCell() {
        assertEquals(List.of(new Problem(6, 11, "SV110", "duplicate column \"x\" (first at column 7)")),
                lint("Feature: F", "Scenario Outline: S", "  Given <x>", "  Then ok",
                        "  Examples:", "    | x | x |", "    | 1 | 2 |"));
    }

    @Test
    void everyLaterCopyPointsAtTheFirstCell() {
        assertEquals(List.of(
                new Problem(6, 11, "SV110", "duplicate column \"x\" (first at column 7)"),
                new Problem(6, 15, "SV110", "duplicate column \"x\" (first at column 7)")),
                lint("Feature: F", "Scenario Outline: S", "  Given <x>", "  Then ok",
                        "  Examples:", "    | x | x | x |", "    | 1 | 2 | 3 |"));
    }

    @Test
    void differentNamesAndTheSameNameInTwoBlocksAreFine() {
        assertEquals(List.of(),
                lint("Feature: F", "Scenario Outline: S", "  Given <x> and <y>", "  Then ok",
                        "  Examples:", "    | x | y |", "    | 1 | 2 |",
                        "  Examples: more", "    | x | y |", "    | 3 | 4 |"));
    }

    @Test
    void anUnusedRepeatedColumnGetsBothProblemsAtTheSamePlace() {
        assertEquals(List.of(
                new Problem(6, 7, "SV108", "column \"z\" is not used by any step"),
                new Problem(6, 11, "SV108", "column \"z\" is not used by any step"),
                new Problem(6, 11, "SV110", "duplicate column \"z\" (first at column 7)")),
                lint("Feature: F", "Scenario Outline: S", "  Given a step", "  Then ok",
                        "  Examples:", "    | z | z |", "    | 1 | 2 |"));
    }

    @Test
    void emptyAndNonAsciiNamesAreShownLikeTheOtherColumnMessages() {
        assertEquals(List.of(
                new Problem(6, 6, "SV108", "column \"\" is not used by any step"),
                new Problem(6, 9, "SV108", "column \"\" is not used by any step"),
                new Problem(6, 9, "SV110", "duplicate column \"\" (first at column 6)"),
                new Problem(6, 13, "SV108", "column \"??\" is not used by any step"),
                new Problem(6, 19, "SV108", "column \"??\" is not used by any step"),
                new Problem(6, 19, "SV110", "duplicate column \"??\" (first at column 13)")),
                lint("Feature: F", "Scenario Outline: S", "  Given a step", "  Then ok",
                        "  Examples:", "    |  |  | " + ACCENTED + " | " + ACCENTED + " |",
                        "    | 1 | 2 | 3 | 4 |"));
    }

    @Test
    void theProblemIsPrintedWithItsFileAndCode() {
        assertEquals("a.txt:6:11: SV110 duplicate column \"x\" (first at column 7)",
                lint("Feature: F", "Scenario Outline: S", "  Given <x>", "  Then ok",
                        "  Examples:", "    | x | x |", "    | 1 | 2 |").get(0).format("a.txt"));
    }
}
