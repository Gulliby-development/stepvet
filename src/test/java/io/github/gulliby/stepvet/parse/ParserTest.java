package io.github.gulliby.stepvet.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserTest {
    @Test
    void emptyAndIgnoredInputsHaveNoFeature() {
        assertNull(Parser.parse("").spec());
        assertEquals(List.of(), Parser.parse("").problems());

        Parser.Result ignored = Parser.parse(String.join("\n", "# comment", " \t"));
        assertNull(ignored.spec());
        assertEquals(List.of(), ignored.problems());
    }

    @Test
    void minimalDocumentsPreserveNullAndEmptyFields() {
        Parser.Result feature = Parser.parse(String.join("\n", "Feature: F"));
        assertEquals(new Doc.Spec("F", 1, List.of(), null, List.of()), feature.spec());
        assertEquals(List.of(), feature.problems());

        Parser.Result background = Parser.parse(String.join("\n", "Feature: F", "Background:"));
        assertEquals(List.of(), background.spec().background());
        assertEquals(List.of(), background.problems());

        Parser.Result outline = Parser.parse(String.join("\n", "Feature: F", "Scenario Outline: S", "  Examples: empty"));
        assertEquals(new Doc.Examples(3, 3, "empty", List.of(), 0, null, List.of()),
                outline.spec().scenarios().get(0).examples().get(0));
        assertEquals(List.of(), outline.problems());
    }

    @Test
    void preservesSpacedTitlesTabsAndUtf16SourceLocations() {
        String page = String.join("\n",
                "Feature:   Spaced   title",
                "\tScenario:\ttab\tbed",
                "\t  Given\ta step\twith tabs   ",
                "Scenario:",
                "  Then bare");
        Parser.Result result = Parser.parse(page);
        Doc.Spec spec = result.spec();

        assertEquals("Spaced   title", spec.title());
        assertEquals(new Doc.Scenario(false, "tab\tbed", 2, 2, List.of(),
                List.of(new Doc.Step("Given", "a step\twith tabs", 3, 4, 10)), List.of()),
                spec.scenarios().get(0));
        assertEquals(new Doc.Scenario(false, "", 4, 1, List.of(),
                List.of(new Doc.Step("Then", "bare", 5, 3, 8)), List.of()),
                spec.scenarios().get(1));
        assertEquals(List.of(), result.problems());

        Parser.Result unicode = Parser.parse(String.join("\n", "Feature: é😀", "Scenario: 😀", "  Given é😀"));
        assertEquals("😀", unicode.spec().scenarios().get(0).title());
        assertEquals(new Doc.Step("Given", "é😀", 3, 3, 9),
                unicode.spec().scenarios().get(0).steps().get(0));
        assertEquals(List.of(), unicode.problems());
    }

    @Test
    void attachesTagsToEligibleKeywordsInTheTwelveLinePage() {
        String page = String.join("\n",
                "@smoke @a-b @x😀",
                "# retained",
                "Feature: F",
                "@a",
                "@a",
                "Scenario Outline: S",
                "Given x",
                "@eu",
                "Examples: one",
                "@other",
                "Examples: two",
                "Scenario: last");
        Parser.Result result = Parser.parse(page);
        Doc.Spec spec = result.spec();

        assertEquals(List.of("@smoke", "@a-b", "@x😀"), spec.tags());
        Doc.Scenario outline = spec.scenarios().get(0);
        assertEquals(List.of("@a", "@a"), outline.tags());
        assertEquals(List.of("@eu"), outline.examples().get(0).tags());
        assertEquals(List.of("@other"), outline.examples().get(1).tags());
        assertEquals("one", outline.examples().get(0).title());
        assertEquals("two", outline.examples().get(1).title());
        assertEquals(List.of(), spec.scenarios().get(1).tags());
        assertEquals(List.of(), result.problems());
    }

    @Test
    void pendingTagsAccumulateAcrossBlankAndCommentLines() {
        String page = String.join("\n",
                "@feature",
                "",
                "# between tags",
                "@feature-two",
                "Feature: F",
                "Scenario Outline: S",
                "@block",
                "# block comment",
                "@block-two",
                "Examples:");
        Parser.Result result = Parser.parse(page);

        assertEquals(List.of("@feature", "@feature-two"), result.spec().tags());
        assertEquals(List.of("@block", "@block-two"),
                result.spec().scenarios().get(0).examples().get(0).tags());
        assertEquals(List.of(), result.problems());
    }

    @Test
    void assemblesBackgroundScenariosAndAcceptedDescriptionsInOrder() {
        String page = String.join("\n",
                "@feature",
                "Feature: F",
                "Feature description",
                "Background:",
                "Background description",
                "  Given setup",
                "@first",
                "Scenario: First",
                "Scenario description",
                "  When work",
                "Scenario: Second",
                "  Then done");
        Parser.Result result = Parser.parse(page);
        Doc.Spec spec = result.spec();

        assertEquals(List.of("@feature"), spec.tags());
        assertEquals(List.of(new Doc.Step("Given", "setup", 6, 3, 9)), spec.background());
        assertEquals(List.of("First", "Second"),
                List.of(spec.scenarios().get(0).title(), spec.scenarios().get(1).title()));
        assertEquals(List.of("@first"), spec.scenarios().get(0).tags());
        assertEquals(new Doc.Step("When", "work", 10, 3, 8),
                spec.scenarios().get(0).steps().get(0));
        assertEquals(new Doc.Step("Then", "done", 12, 3, 8),
                spec.scenarios().get(1).steps().get(0));
        assertEquals(List.of(), result.problems());
    }

    @Test
    void preservesExamplesHeaderColumnsAndDecodedEscapedCells() {
        String page = String.join("\n",
                "Feature: F",
                "Scenario Outline: S",
                "  Given use <x>",
                "  Examples:",
                "    | z |\ty |  | é😀w |",
                "    | a\\|b | c\\\\d |  | é😀w |");
        Parser.Result result = Parser.parse(page);
        Doc.Examples examples = result.spec().scenarios().get(0).examples().get(0);

        assertEquals(List.of(new Doc.Cell("z", 7), new Doc.Cell("y", 11),
                new Doc.Cell("", 14), new Doc.Cell("é😀w", 18)), examples.header());
        assertEquals(5, examples.headerLine());
        assertEquals(6, examples.rows().get(0).line());
        assertEquals(new Doc.Row(6, List.of(new Doc.Cell("a|b", 7), new Doc.Cell("c\\d", 14),
                new Doc.Cell("", 20), new Doc.Cell("é😀w", 24))), examples.rows().get(0));
        assertEquals(List.of(), result.problems());
    }

    @Test
    void parsesAreIndependentAndEarlierResultsRemainUnchanged() {
        Parser.Result earlier = Parser.parse(String.join("\n", "@first", "Feature: One", "Scenario: A"));
        Parser.Result later = Parser.parse(String.join("\n", "@second", "Feature: Two", "Scenario: B"));

        assertNotSame(earlier.spec(), later.spec());
        assertEquals("One", earlier.spec().title());
        assertEquals(List.of("@first"), earlier.spec().tags());
        assertEquals("A", earlier.spec().scenarios().get(0).title());
        assertEquals("Two", later.spec().title());
        assertEquals(List.of("@second"), later.spec().tags());
        assertEquals(List.of(), earlier.problems());
        assertEquals(List.of(), later.problems());
    }

    @Test
    void everyNonNullReturnedDocumentListRejectsMutation() {
        String page = String.join("\n",
                "Feature: F",
                "Background:",
                "  Given setup",
                "Scenario: Plain",
                "  Then done",
                "Scenario Outline: Outline",
                "  Given <x>",
                "Examples: loaded",
                "| x |",
                "| value |",
                "Examples: empty");
        Parser.Result result = Parser.parse(page);
        Doc.Spec spec = result.spec();
        Doc.Scenario plain = spec.scenarios().get(0);
        Doc.Scenario outline = spec.scenarios().get(1);
        Doc.Examples loaded = outline.examples().get(0);
        Doc.Examples empty = outline.examples().get(1);
        Doc.Row row = loaded.rows().get(0);
        Doc.Cell cell = row.cells().get(0);
        Doc.Step step = spec.background().get(0);

        assertThrows(UnsupportedOperationException.class, () -> spec.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> spec.background().add(step));
        assertThrows(UnsupportedOperationException.class, () -> spec.scenarios().add(plain));
        assertThrows(UnsupportedOperationException.class, () -> plain.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> plain.steps().add(step));
        assertThrows(UnsupportedOperationException.class, () -> plain.examples().add(loaded));
        assertThrows(UnsupportedOperationException.class, () -> outline.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> outline.steps().add(step));
        assertThrows(UnsupportedOperationException.class, () -> outline.examples().add(loaded));
        assertThrows(UnsupportedOperationException.class, () -> loaded.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> loaded.header().add(cell));
        assertThrows(UnsupportedOperationException.class, () -> loaded.rows().add(row));
        assertThrows(UnsupportedOperationException.class, () -> empty.tags().add("@x"));
        assertThrows(UnsupportedOperationException.class, () -> empty.rows().add(row));
        assertThrows(UnsupportedOperationException.class, () -> row.cells().add(cell));
        assertThrows(UnsupportedOperationException.class, () -> result.problems().add(
                new Problem(1, 1, "SV999", "later")));
        assertNull(empty.header());
        assertTrue(plain.examples().isEmpty());
        assertFalse(loaded.rows().isEmpty());
    }

    @Test
    void resultSnapshotsCallerOwnedProblems() {
        Problem problem = new Problem(2, 3, "SV002", "invalid tag");
        List<Problem> source = new ArrayList<>(List.of(problem));
        Parser.Result result = new Parser.Result(null, source);

        source.clear();
        assertEquals(List.of(problem), result.problems());
        assertThrows(UnsupportedOperationException.class, () -> result.problems().add(problem));
    }
}
