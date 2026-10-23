package io.github.gulliby.stepvet.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ProblemTest {
    @Test
    void formatsDiagnosticExactly() {
        Problem problem = new Problem(12, 4, "SV007", "step is not allowed here");

        assertEquals("spec.feature:12:4: SV007 step is not allowed here", problem.format("spec.feature"));
    }

    @Test
    void sortsByLineBeforeColumnAndCode() {
        List<Problem> found = List.of(
                new Problem(2, 1, "SV001", "later line"),
                new Problem(1, 9, "SV001", "later column"),
                new Problem(2, 0, "SV001", "earlier column"));

        assertEquals(List.of(
                new Problem(1, 9, "SV001", "later column"),
                new Problem(2, 0, "SV001", "earlier column"),
                new Problem(2, 1, "SV001", "later line")), Problem.sorted(found));
    }

    @Test
    void sortsByColumnWhenLinesMatch() {
        List<Problem> found = List.of(
                new Problem(3, 8, "SV001", "late"),
                new Problem(3, 2, "SV999", "early"));

        assertEquals(List.of(
                new Problem(3, 2, "SV999", "early"),
                new Problem(3, 8, "SV001", "late")), Problem.sorted(found));
    }

    @Test
    void sortsByNaturalCodeWhenLineAndColumnMatch() {
        List<Problem> found = List.of(
                new Problem(3, 2, "SV2", "second"),
                new Problem(3, 2, "SV10", "first"));

        assertEquals(List.of(
                new Problem(3, 2, "SV10", "first"),
                new Problem(3, 2, "SV2", "second")), Problem.sorted(found));
    }

    @Test
    void keepsFoundOrderForStableTiesWithDifferentText() {
        List<Problem> found = List.of(
                new Problem(3, 2, "SV2", "first text"),
                new Problem(3, 2, "SV2", "second text"),
                new Problem(3, 2, "SV1", "other code"));

        assertEquals(List.of(
                new Problem(3, 2, "SV1", "other code"),
                new Problem(3, 2, "SV2", "first text"),
                new Problem(3, 2, "SV2", "second text")), Problem.sorted(found));
    }

    @Test
    void sortsAnEmptyProblemList() {
        assertEquals(List.of(), Problem.sorted(List.of()));
    }

    @Test
    void sortedResultIsImmutableAndDetachedFromInput() {
        List<Problem> found = new ArrayList<>(List.of(new Problem(2, 1, "SV2", "text")));
        List<Problem> sorted = Problem.sorted(found);

        assertNotSame(found, sorted);
        found.clear();
        assertEquals(List.of(new Problem(2, 1, "SV2", "text")), sorted);
        assertThrows(UnsupportedOperationException.class,
                () -> sorted.add(new Problem(1, 1, "SV1", "later")));
    }

    @Test
    void effectiveTagsCanBeEmpty() {
        assertEquals(List.of(), Tags.effective(List.of()));
        assertEquals(List.of(), Tags.effective(List.of(List.of(), List.of())));
    }

    @Test
    void effectiveTagsKeepFirstOccurrenceAcrossListsAndEmptyLists() {
        List<List<String>> tags = List.of(
                List.of("@feature", "@same"),
                List.of(),
                List.of("@scenario", "@same"),
                List.of("@examples"));

        assertEquals(List.of("@feature", "@same", "@scenario", "@examples"), Tags.effective(tags));
    }

    @Test
    void effectiveTagsUseExactCaseSensitiveIdentity() {
        assertEquals(List.of("@Smoke", "@smoke", "@SMOKE"),
                Tags.effective(List.of(List.of("@Smoke", "@smoke", "@SMOKE", "@Smoke"))));
    }

    @Test
    void effectiveTagsAreImmutableAndDetachedFromInput() {
        List<String> source = new ArrayList<>(List.of("@first", "@second"));
        List<List<String>> lists = new ArrayList<>(List.of(source));
        List<String> effective = Tags.effective(lists);

        source.clear();
        lists.clear();
        assertEquals(List.of("@first", "@second"), effective);
        assertThrows(UnsupportedOperationException.class, () -> effective.add("@third"));
    }

    @Test
    void documentRecordsPreserveNullableAndEmptyFields() {
        Doc.Examples examples = new Doc.Examples(1, 1, "", List.of(), 0, null, List.of());
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null, List.of());
        Doc.Examples emptyHeader = new Doc.Examples(1, 1, "", List.of(), 1, List.of(), List.of());
        Doc.Spec emptyBackground = new Doc.Spec("F", 1, List.of(), List.of(), List.of());

        assertNull(examples.header());
        assertNull(spec.background());
        assertEquals(List.of(), emptyHeader.header());
        assertEquals(List.of(), emptyBackground.background());
    }

    @Test
    void documentRecordsDefensivelySnapshotEveryListComponent() {
        List<Doc.Cell> rowCells = new ArrayList<>(List.of(new Doc.Cell("cell", 1)));
        List<String> exampleTags = new ArrayList<>(List.of("@example"));
        List<Doc.Cell> header = new ArrayList<>(List.of(new Doc.Cell("head", 2)));
        List<Doc.Row> rows = new ArrayList<>(List.of(new Doc.Row(3, List.of(new Doc.Cell("row", 4)))));
        List<String> scenarioTags = new ArrayList<>(List.of("@scenario"));
        List<Doc.Step> steps = new ArrayList<>(List.of(new Doc.Step("Given", "work", 4, 3, 9)));
        List<Doc.Examples> examples = new ArrayList<>(List.of(
                new Doc.Examples(5, 3, "", List.of(), 6, List.of(), List.of())));
        List<String> specTags = new ArrayList<>(List.of("@feature"));
        List<Doc.Step> background = new ArrayList<>(List.of(new Doc.Step("Given", "setup", 2, 3, 9)));
        List<Doc.Scenario> scenarios = new ArrayList<>(List.of(
                new Doc.Scenario(false, "S", 3, 3, List.of(), List.of(), List.of())));

        Doc.Row row = new Doc.Row(1, rowCells);
        Doc.Examples example = new Doc.Examples(1, 1, "", exampleTags, 2, header, rows);
        Doc.Scenario scenario = new Doc.Scenario(true, "S", 1, 1, scenarioTags, steps, examples);
        Doc.Spec spec = new Doc.Spec("F", 1, specTags, background, scenarios);

        rowCells.clear();
        exampleTags.clear();
        header.clear();
        rows.clear();
        scenarioTags.clear();
        steps.clear();
        examples.clear();
        specTags.clear();
        background.clear();
        scenarios.clear();

        assertEquals(List.of(new Doc.Cell("cell", 1)), row.cells());
        assertEquals(List.of("@example"), example.tags());
        assertEquals(List.of(new Doc.Cell("head", 2)), example.header());
        assertEquals(List.of(new Doc.Row(3, List.of(new Doc.Cell("row", 4)))), example.rows());
        assertEquals(List.of("@scenario"), scenario.tags());
        assertEquals(List.of(new Doc.Step("Given", "work", 4, 3, 9)), scenario.steps());
        assertEquals(List.of(new Doc.Examples(5, 3, "", List.of(), 6, List.of(), List.of())),
                scenario.examples());
        assertEquals(List.of("@feature"), spec.tags());
        assertEquals(List.of(new Doc.Step("Given", "setup", 2, 3, 9)), spec.background());
        assertEquals(List.of(new Doc.Scenario(false, "S", 3, 3, List.of(), List.of(), List.of())),
                spec.scenarios());
    }

    @Test
    void everyNonNullDocumentListAccessorRejectsMutation() {
        Doc.Cell cell = new Doc.Cell("cell", 1);
        Doc.Step step = new Doc.Step("Given", "work", 1, 1, 7);
        Doc.Row row = new Doc.Row(1, List.of(cell));
        Doc.Examples examples = new Doc.Examples(1, 1, "", List.of("@e"), 2, List.of(cell), List.of(row));
        Doc.Scenario scenario = new Doc.Scenario(true, "S", 1, 1, List.of("@s"),
                List.of(step), List.of(examples));
        Doc.Spec spec = new Doc.Spec("F", 1, List.of("@f"), List.of(step), List.of(scenario));

        assertThrows(UnsupportedOperationException.class, () -> row.cells().add(cell));
        assertThrows(UnsupportedOperationException.class, () -> examples.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> examples.header().add(cell));
        assertThrows(UnsupportedOperationException.class, () -> examples.rows().add(row));
        assertThrows(UnsupportedOperationException.class, () -> scenario.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> scenario.steps().add(step));
        assertThrows(UnsupportedOperationException.class, () -> scenario.examples().add(examples));
        assertThrows(UnsupportedOperationException.class, () -> spec.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> spec.background().add(step));
        assertThrows(UnsupportedOperationException.class, () -> spec.scenarios().add(scenario));
    }
}
