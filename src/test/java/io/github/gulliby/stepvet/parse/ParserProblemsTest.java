package io.github.gulliby.stepvet.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ParserProblemsTest {
    @Test
    void reportsInvalidTagTokensAtTheirUtf16Columns() {
        assertEquals(List.of("p:2:1: SV002 invalid tag \"@a@b\""),
                Parser.parse(String.join("\n", "Feature: F", "@a@b")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:3: SV002 invalid tag \"@ok@\"",
                "p:2:8: SV002 invalid tag \"@b@c\""),
                Parser.parse(String.join("\n", "Feature: F", "  @ok@ @b@c")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV002 invalid tag \"@x@?\"",
                "p:2:7: SV002 invalid tag \"@y@\""),
                Parser.parse(String.join("\n", "Feature: F", "@x@😀 @y@")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:1:1: SV002 invalid tag \"@\"",
                "p:1:3: SV002 invalid tag \"@a@b\"",
                "p:1:8: SV002 invalid tag \"foo\""),
                Parser.parse(String.join("\n", "@ @a@b foo #c @@", "Feature: F")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:",
                "p:2:4: SV002 invalid tag \"@@\""),
                Parser.parse(String.join("\n", "Feature: F", "@a @@")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
    }

    @Test
    void enforcesTheFirstContentLineAndStopsAfterFailure() {
        Parser.Result pending = Parser.parse(String.join("\n", "@pending", "  Scenario: S", "@bad@", "Feature: Later"));
        assertNull(pending.spec());
        assertEquals(List.of("p:2:3: SV001 expected \"Feature:\" first"),
                pending.problems().stream().map(problem -> problem.format("p")).toList());

        Parser.Result invalid = Parser.parse(String.join("\n", "@bad@", "Then x", "Feature: Later"));
        assertNull(invalid.spec());
        assertEquals(List.of(
                "p:1:1: SV002 invalid tag \"@bad@\"",
                "p:2:1: SV001 expected \"Feature:\" first"),
                invalid.problems().stream().map(problem -> problem.format("p")).toList());

        Parser.Result tagOnly = Parser.parse(String.join("\n", "@only", "# ignored"));
        assertNull(tagOnly.spec());
        assertEquals(List.of(
                "p:1:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:"),
                tagOnly.problems().stream().map(problem -> problem.format("p")).toList());

        Parser.Result invalidOnly = Parser.parse("@bad@");
        assertNull(invalidOnly.spec());
        assertEquals(List.of("p:1:1: SV002 invalid tag \"@bad@\""),
                invalidOnly.problems().stream().map(problem -> problem.format("p")).toList());
        assertNull(Parser.parse(String.join("\n", "# comment", " \t", "")).spec());
    }

    @Test
    void reportsOneOrphanAtTheFirstValidTagBeforeEachIneligibleLine() {
        assertEquals(List.of(
                "p:2:1: SV002 invalid tag \"@bad@\"",
                "p:2:7: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:",
                "p:6:1: SV007 step is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "@bad@ @first", "# keep", "",
                        "@second", "Given x")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:",
                "p:3:1: SV007 table is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "@row", "| a |")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:4:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:",
                "p:5:1: SV004 unexpected line \"stray\""),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "  Given work", "@orphan", "stray"))
                        .problems().stream().map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:"),
                Parser.parse(String.join("\n", "Feature: F", "@background", "Background:")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:"),
                Parser.parse(String.join("\n", "Feature: F", "@eof")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
    }

    @Test
    void preservesTagsAcrossIgnoredLinesAndDropsThemOnIgnoredKeywords() {
        Parser.Result consumed = Parser.parse(String.join("\n", "@feature", "# before feature", "",
                "Feature: F", "@scenario", "# before scenario", "Scenario: S", "  Given work"));
        assertEquals(List.of(), consumed.problems());
        assertEquals(List.of("@feature"), consumed.spec().tags());
        assertEquals(List.of("@scenario"), consumed.spec().scenarios().get(0).tags());

        Parser.Result ignoredBackground = Parser.parse(String.join("\n",
                "Feature: F", "Scenario: S", "@lost", "Background:", "Given in scenario"));
        assertEquals(List.of("p:4:1: SV007 Background: is not allowed here"),
                ignoredBackground.problems().stream().map(problem -> problem.format("p")).toList());
        assertNull(ignoredBackground.spec().background());
        assertEquals(List.of(new Doc.Step("Given", "in scenario", 5, 1, 7)),
                ignoredBackground.spec().scenarios().get(0).steps());

        Parser.Result duplicateFeature = Parser.parse(String.join("\n",
                "Feature: F", "@lost", "Feature: G", "Scenario: S", "Given work"));
        assertEquals(List.of("p:3:1: SV007 Feature: is not allowed here"),
                duplicateFeature.problems().stream().map(problem -> problem.format("p")).toList());
        assertEquals(List.of(), duplicateFeature.spec().tags());
        assertEquals(List.of(), duplicateFeature.spec().scenarios().get(0).tags());
    }

    @Test
    void checksKeywordAndContentPlacementBeforeStepValidation() {
        assertEquals(List.of("p:2:1: SV007 Feature: is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Feature: G")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:2:2: SV007 step is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "\tGiven x")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:3:3: SV007 Background: is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "  Background:")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:3:1: SV007 Background: is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Background:", "Background:")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV007 Examples: is not allowed here",
                "p:3:1: SV007 step is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Examples:", "Given")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:3:1: SV007 Examples: is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "Examples:")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:3:1: SV007 table is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "| a")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
    }

    @Test
    void tracksDescriptionAllowanceForPlacedAndIgnoredContent() {
        assertEquals(List.of("p:2:1: SV007 step is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Given", "description")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:2:1: SV007 Feature: is not allowed here",
                "p:3:1: SV004 unexpected line \"description\""),
                Parser.parse(String.join("\n", "Feature: F", "Feature: G", "description")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:3:1: SV005 step without text",
                "p:4:1: SV004 unexpected line \"stray words\""),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "Given", "stray words")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:4:1: SV004 unexpected line \"after step\""),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "Given work", "after step")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:6:1: SV004 unexpected line \"after row\""),
                Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "Examples:",
                        "  | h |", "  | v |", "after row")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:5:1: SV006 unterminated table row",
                "p:6:1: SV004 unexpected line \"after row\""),
                Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "Examples:",
                        "  | h |", "| v", "after row")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of(
                "p:5:1: SV008 row cell count 2, expected 1",
                "p:6:1: SV004 unexpected line \"after row\""),
                Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "Examples:",
                        "  | h |", "| a | b |", "after row")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:2:1: SV007 table is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "| unterminated", "description")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
    }

    @Test
    void ignoredKeywordsClearDescriptionsWithoutReplacingOpenContext() {
        Parser.Result lateBackground = Parser.parse(String.join("\n",
                "Feature: F", "Scenario: S", "Background:", "note", "Given active"));
        assertEquals(List.of(
                "p:3:1: SV007 Background: is not allowed here",
                "p:4:1: SV004 unexpected line \"note\""),
                lateBackground.problems().stream().map(problem -> problem.format("p")).toList());
        assertEquals(List.of(new Doc.Step("Given", "active", 5, 1, 7)),
                lateBackground.spec().scenarios().get(0).steps());
        assertNull(lateBackground.spec().background());

        Parser.Result examplesStillOpen = Parser.parse(String.join("\n", "Feature: F",
                "Scenario Outline: S", "Examples:", "Given", "  | x |", "  | 1 |"));
        assertEquals(List.of("p:4:1: SV007 step is not allowed here"),
                examplesStillOpen.problems().stream().map(problem -> problem.format("p")).toList());
        assertEquals(5, examplesStillOpen.spec().scenarios().get(0).examples().get(0).headerLine());
        assertEquals(List.of(new Doc.Row(6, List.of(new Doc.Cell("1", 5)))),
                examplesStillOpen.spec().scenarios().get(0).examples().get(0).rows());
    }

    @Test
    void retainsHeadersRowsAndExactRecoveryFromTheTenLineExample() {
        Parser.Result parsed = Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S",
                "  Given <x>", "  Examples: first", "    | x |", "    | a", "    | 1 | 2 |",
                "    | 3 |", "  Examples: second", "    ||"));
        assertEquals(List.of(
                "p:6:5: SV006 unterminated table row",
                "p:7:5: SV008 row cell count 2, expected 1"),
                parsed.problems().stream().map(problem -> problem.format("p")).toList());

        Doc.Scenario scenario = parsed.spec().scenarios().get(0);
        assertEquals(new Doc.Step("Given", "<x>", 3, 3, 9), scenario.steps().get(0));
        assertEquals(2, scenario.examples().size());
        Doc.Examples first = scenario.examples().get(0);
        assertEquals(4, first.line());
        assertEquals(3, first.col());
        assertEquals(5, first.headerLine());
        assertEquals(List.of(new Doc.Cell("x", 7)), first.header());
        assertEquals(List.of(new Doc.Row(8, List.of(new Doc.Cell("3", 7)))), first.rows());

        Doc.Examples second = scenario.examples().get(1);
        assertEquals(10, second.headerLine());
        assertEquals(List.of(new Doc.Cell("", 6)), second.header());
        assertEquals(List.of(), second.rows());
    }

    @Test
    void keepsMissingHeadersAndUsesPlacementBeforeRowSyntax() {
        Doc.Examples absent = Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S",
                        "Examples: empty"))
                .spec().scenarios().get(0).examples().get(0);
        assertEquals(0, absent.headerLine());
        assertNull(absent.header());

        Parser.Result malformedBeforeHeader = Parser.parse(String.join("\n", "Feature: F",
                "Scenario Outline: S", "Examples:", "| first", "| h |", "| v |"));
        assertEquals(List.of("p:4:1: SV006 unterminated table row"),
                malformedBeforeHeader.problems().stream().map(problem -> problem.format("p")).toList());
        Doc.Examples examples = malformedBeforeHeader.spec().scenarios().get(0).examples().get(0);
        assertEquals(5, examples.headerLine());
        assertEquals(List.of(new Doc.Row(6, List.of(new Doc.Cell("v", 3)))), examples.rows());

        assertEquals(List.of("p:3:1: SV007 table is not allowed here"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario: S", "|")).problems().stream()
                        .map(problem -> problem.format("p")).toList());
    }

    @Test
    void preservesCellDecodingAndUtf16SourceColumns() {
        Parser.Result parsed = Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S",
                "  Given data", "  Examples:", "    | z |\ty |  | é😀w |",
                "    | a | b | c | d |"));
        Doc.Examples examples = parsed.spec().scenarios().get(0).examples().get(0);
        assertEquals(List.of(
                new Doc.Cell("z", 7),
                new Doc.Cell("y", 11),
                new Doc.Cell("", 14),
                new Doc.Cell("é😀w", 18)), examples.header());
        assertEquals(List.of(
                new Doc.Cell("a", 7),
                new Doc.Cell("b", 11),
                new Doc.Cell("c", 15),
                new Doc.Cell("d", 19)), examples.rows().get(0).cells());

        Parser.Result escaped = Parser.parse(String.join("\n", "Feature: F",
                "Scenario Outline: S", "Examples:", "    | head |", "    | a\\|b |",
                "    | c\\\\d |", "    | keep\\q |"));
        assertEquals(List.of(new Doc.Cell("head", 7)), escaped.spec().scenarios().get(0)
                .examples().get(0).header());
        assertEquals(List.of(
                new Doc.Row(5, List.of(new Doc.Cell("a|b", 7))),
                new Doc.Row(6, List.of(new Doc.Cell("c\\d", 7))),
                new Doc.Row(7, List.of(new Doc.Cell("keep\\q", 7)))),
                escaped.spec().scenarios().get(0).examples().get(0).rows());
    }

    @Test
    void diagnosesUnterminatedRowsButAcceptsTerminatedEmptyRows() {
        assertEquals(List.of("p:4:1: SV006 unterminated table row"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "Examples:", "|"))
                        .problems().stream().map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:4:1: SV006 unterminated table row"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "Examples:", "| a"))
                        .problems().stream().map(problem -> problem.format("p")).toList());
        assertEquals(List.of("p:4:1: SV006 unterminated table row"),
                Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "Examples:", "| a \\|"))
                        .problems().stream().map(problem -> problem.format("p")).toList());

        Doc.Examples empty = Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S",
                "Examples:", "||"))
                .spec().scenarios().get(0).examples().get(0);
        assertEquals(4, empty.headerLine());
        assertEquals(List.of(new Doc.Cell("", 2)), empty.header());
    }

    @Test
    void sortsEofOrphansBeforeLaterColumnErrorsAndEmitsNoSemanticProblems() {
        Parser.Result sorted = Parser.parse(String.join("\n", "Feature: F", "@valid @bad@"));
        assertEquals(List.of(
                "p:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:",
                "p:2:8: SV002 invalid tag \"@bad@\""),
                sorted.problems().stream().map(problem -> problem.format("p")).toList());

        Parser.Result syntaxOnly = Parser.parse(String.join("\n", "Feature: F", "Scenario: S"));
        assertTrue(syntaxOnly.problems().stream().noneMatch(problem -> problem.code().startsWith("SV1")));
        assertEquals(List.of(), syntaxOnly.problems());
    }

    @Test
    void returnsDetachedImmutableDocumentsAndProblemSnapshots() {
        Parser.Result first = Parser.parse(String.join("\n", "Feature: F", "Background:",
                "  Given setup", "Scenario Outline: S", "  Then use <x>", "Examples:",
                "  | x |", "  | one |"));
        Doc.Spec spec = first.spec();
        Doc.Scenario scenario = spec.scenarios().get(0);
        Doc.Examples examples = scenario.examples().get(0);
        Doc.Row row = examples.rows().get(0);

        Parser.parse(String.join("\n", "Feature: Another", "Scenario: Later", "When done"));
        assertEquals("F", spec.title());
        assertEquals(List.of(new Doc.Step("Given", "setup", 3, 3, 9)), spec.background());
        assertThrows(UnsupportedOperationException.class, () -> first.problems().add(
                new Problem(1, 1, "SV001", "later")));
        assertThrows(UnsupportedOperationException.class, () -> spec.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> spec.background().add(
                new Doc.Step("Given", "later", 1, 1, 7)));
        assertThrows(UnsupportedOperationException.class, () -> spec.scenarios().add(scenario));
        assertThrows(UnsupportedOperationException.class, () -> scenario.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> scenario.steps().add(
                new Doc.Step("Then", "later", 1, 1, 6)));
        assertThrows(UnsupportedOperationException.class, () -> scenario.examples().add(examples));
        assertThrows(UnsupportedOperationException.class, () -> examples.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> examples.header().add(
                new Doc.Cell("later", 1)));
        assertThrows(UnsupportedOperationException.class, () -> examples.rows().add(row));
        assertThrows(UnsupportedOperationException.class, () -> row.cells().add(
                new Doc.Cell("later", 1)));

        List<Problem> source = new ArrayList<>(List.of(new Problem(2, 3, "SV002", "tag")));
        Parser.Result snapshot = new Parser.Result(null, source);
        source.clear();
        assertEquals(List.of(new Problem(2, 3, "SV002", "tag")), snapshot.problems());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.problems().clear());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "@feature|Feature: F|@feature|-",
            "Feature: F|@eof|-|p:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:"
    }, delimiter = '|')
    void parsesMultilinePagesJoinedFromTheirLines(String firstLine, String secondLine,
                                                  String expectedFeatureTag, String expectedProblem) {
        Parser.Result result = Parser.parse(String.join("\n", firstLine, secondLine));

        assertEquals(expectedFeatureTag.equals("-") ? List.of() : List.of(expectedFeatureTag),
                result.spec().tags());
        assertEquals(expectedProblem.equals("-") ? List.of() : List.of(expectedProblem),
                result.problems().stream().map(problem -> problem.format("p")).toList());
    }
}
