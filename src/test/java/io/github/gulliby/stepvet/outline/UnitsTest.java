package io.github.gulliby.stepvet.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.stepvet.model.Doc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UnitsTest {
    @Test
    void findReturnsEmptyForEmptyAndMalformedFragments() {
        assertEquals(List.of(), Subst.find(""));
        assertEquals(List.of(), Subst.find("<> > <"));
        assertEquals(List.of(new Subst.Found("ok", 5)), Subst.find("<bad <ok> <tail"));
    }

    @Test
    void findReturnsAdjacentRepeatedAndUntrimmedNames() {
        assertEquals(List.of(
                new Subst.Found("n", 0),
                new Subst.Found("first name", 3),
                new Subst.Found("n", 15)),
                Subst.find("<n><first name><n>"));
        assertEquals(List.of(new Subst.Found(" a ", 0)), Subst.find("< a >"));
    }

    @Test
    void findRecognizesInnerPlaceholdersAndCountsUtf16Offsets() {
        assertEquals(List.of(
                new Subst.Found("a", 1),
                new Subst.Found("a b", 6)), Subst.find("<<a>> <a b> <>"));
        assertEquals(List.of(new Subst.Found("a", 3)), Subst.find("é😀<a>"));
    }

    @Test
    void applyPreservesUnknownNamesAndMatchesExactCase() {
        assertEquals("xx <A> <missing>",
                Subst.apply("<a><a> <A> <missing>", Map.of("a", "x")));
        assertEquals("<a> <a>", Subst.apply("<a> <a>", Map.of("A", "upper")));
        assertEquals("<a> <tail", Subst.apply("<a> <tail", Map.of()));
    }

    @Test
    void applyHandlesInnerAndNonrecursivePlaceholders() {
        assertEquals("<X> <> Y",
                Subst.apply("<<a>> <> <a b>", Map.of("a", "X", "a b", "Y")));
        assertEquals("<b> done",
                Subst.apply("<a> <b>", Map.of("a", "<b>", "b", "done")));
    }

    @Test
    void applyInsertsEmptyAndVerbatimValues() {
        assertEquals("xy", Subst.apply("x<a>y", Map.of("a", "")));
        assertEquals("é😀\t\n", Subst.apply("<a>", Map.of("a", "é😀\t\n")));
    }

    @Test
    void unitsReturnNoRowsForNullAndEmptySpecs() {
        assertEquals(List.of(), Units.of(null));
        assertEquals(List.of(), Units.of(new Doc.Spec("F", 1, List.of(), null, List.of())));
    }

    @Test
    void unitsKeepPlainScenarioOrderAndMetadata() {
        Doc.Spec spec = new Doc.Spec("F", 1, List.of("@f", "@same"),
                List.of(new Doc.Step("Given", "base <x>", 2, 3, 9)),
                List.of(
                        new Doc.Scenario(false, "S <x>", 3, 1, List.of("@same", "@s"),
                                List.of(new Doc.Step("Then", "own <x>", 4, 3, 8)), List.of()),
                        new Doc.Scenario(false, "", 5, 1, List.of(), List.of(), List.of())));

        assertEquals(List.of(
                new Units.Unit("S <x>", 3, List.of("@f", "@same", "@s"), List.of(
                        new Doc.Step("Given", "base <x>", 2, 3, 9),
                        new Doc.Step("Then", "own <x>", 4, 3, 8))),
                new Units.Unit("", 5, List.of("@f", "@same"),
                        List.of(new Doc.Step("Given", "base <x>", 2, 3, 9)))),
                Units.of(spec));
        assertEquals(List.of(new Units.Unit("S", 2, List.of(),
                        List.of(new Doc.Step("Then", "ok", 3, 1, 6)))),
                Units.of(new Doc.Spec("F", 1, List.of(), null,
                        List.of(new Doc.Scenario(false, "S", 2, 1, List.of(),
                                List.of(new Doc.Step("Then", "ok", 3, 1, 6)), List.of())))));
    }

    @Test
    void unitsExpandRowsAcrossBlocksWithFirstDuplicateHeader() {
        Doc.Step background = new Doc.Step("Given", "base <x>", 2, 3, 9);
        Doc.Step outlineStep = new Doc.Step("When", "<x> <y> <z>", 4, 3, 8);
        Doc.Examples first = new Doc.Examples(5, 1, "", List.of("@s", "@e"), 6,
                List.of(new Doc.Cell("x", 2), new Doc.Cell("x", 6), new Doc.Cell("y", 10)),
                List.of(
                        new Doc.Row(7, List.of(new Doc.Cell("<y>", 2),
                                new Doc.Cell("ignored", 6), new Doc.Cell("B", 10))),
                        new Doc.Row(8, List.of(new Doc.Cell("A", 2),
                                new Doc.Cell("ignored", 6), new Doc.Cell("C", 10)))));
        Doc.Examples second = new Doc.Examples(9, 1, "more", List.of("@two"), 10,
                List.of(new Doc.Cell("y", 2), new Doc.Cell("x", 6)),
                List.of(new Doc.Row(11, List.of(new Doc.Cell("D", 2), new Doc.Cell("E", 6)))));
        Doc.Scenario outline = new Doc.Scenario(true, "O <x>", 3, 1, List.of("@same", "@s"),
                List.of(outlineStep), List.of(first, second));
        Doc.Scenario emptyTitle = new Doc.Scenario(true, "", 12, 1, List.of(),
                List.of(), List.of(new Doc.Examples(13, 1, "", List.of(), 14,
                        List.of(new Doc.Cell("x", 2)),
                        List.of(new Doc.Row(15, List.of(new Doc.Cell("Q", 2)))))));
        Doc.Spec spec = new Doc.Spec("F", 1, List.of("@f", "@same"),
                List.of(background), List.of(outline, emptyTitle));

        assertEquals(List.of(
                new Units.Unit("O <x> (row 1)", 7, List.of("@f", "@same", "@s", "@e"),
                        List.of(background, new Doc.Step("When", "<y> B <z>", 4, 3, 8))),
                new Units.Unit("O <x> (row 2)", 8, List.of("@f", "@same", "@s", "@e"),
                        List.of(background, new Doc.Step("When", "A C <z>", 4, 3, 8))),
                new Units.Unit("O <x> (row 3)", 11, List.of("@f", "@same", "@s", "@two"),
                        List.of(background, new Doc.Step("When", "E D <z>", 4, 3, 8))),
                new Units.Unit("row 1", 15, List.of("@f", "@same"), List.of(background))),
                Units.of(spec));
        assertEquals(outlineStep, outline.steps().get(0));
        assertEquals(background, spec.background().get(0));
    }

    @Test
    void unitsSkipMissingHeadersAndEmptyOutlineData() {
        assertEquals(List.of(), Units.of(new Doc.Spec("F", 1, List.of(), null,
                List.of(new Doc.Scenario(true, "O", 2, 1, List.of(), List.of(), List.of())))));
        assertEquals(List.of(), Units.of(new Doc.Spec("F", 1, List.of(), null,
                List.of(new Doc.Scenario(true, "O", 2, 1, List.of(), List.of(),
                        List.of(
                                new Doc.Examples(3, 1, "", List.of(), 0, null,
                                        List.of(new Doc.Row(4, List.of(new Doc.Cell("ignored", 2))))),
                                new Doc.Examples(5, 1, "", List.of(), 6,
                                        List.of(new Doc.Cell("x", 2)), List.of())))))));
        assertEquals(List.of(new Units.Unit("O (row 1)", 7, List.of(), List.of())),
                Units.of(new Doc.Spec("F", 1, List.of(), null,
                        List.of(new Doc.Scenario(true, "O", 2, 1, List.of(), List.of(),
                                List.of(
                                        new Doc.Examples(3, 1, "", List.of(), 0, null,
                                                List.of(new Doc.Row(4,
                                                        List.of(new Doc.Cell("ignored", 2))))),
                                        new Doc.Examples(5, 1, "", List.of(), 6,
                                                List.of(new Doc.Cell("x", 2)),
                                                List.of(new Doc.Row(7,
                                                        List.of(new Doc.Cell("value", 2)))))))))));
    }

    @Test
    void unitDefensivelyCopiesConstructorLists() {
        List<String> tags = new ArrayList<>(List.of("@a"));
        List<Doc.Step> steps = new ArrayList<>(List.of(new Doc.Step("Then", "ok", 3, 1, 6)));
        Units.Unit unit = new Units.Unit("S", 2, tags, steps);

        tags.clear();
        steps.clear();

        assertEquals(List.of("@a"), unit.tags());
        assertEquals(List.of(new Doc.Step("Then", "ok", 3, 1, 6)), unit.steps());
        assertThrows(UnsupportedOperationException.class, () -> unit.tags().add("@b"));
        assertThrows(UnsupportedOperationException.class,
                () -> unit.steps().add(new Doc.Step("Given", "later", 4, 1, 7)));
    }

    @Test
    void returnedCollectionsAreImmutableAndExpansionLeavesInputsUnchanged() {
        Doc.Step original = new Doc.Step("When", "<x>", 4, 3, 8);
        Doc.Row row = new Doc.Row(7, List.of(new Doc.Cell("value", 2)));
        Doc.Examples examples = new Doc.Examples(5, 1, "", List.of(), 6,
                List.of(new Doc.Cell("x", 2)), List.of(row));
        Doc.Scenario scenario = new Doc.Scenario(true, "O", 3, 1, List.of(),
                List.of(original), List.of(examples));
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null, List.of(scenario));
        List<Subst.Found> found = Subst.find("<x>");
        List<Units.Unit> units = Units.of(spec);

        assertNotSame(scenario.steps(), units.get(0).steps());
        assertEquals(List.of(original), scenario.steps());
        assertEquals(List.of(new Doc.Step("When", "value", 4, 3, 8)), units.get(0).steps());
        assertThrows(UnsupportedOperationException.class, () -> found.add(new Subst.Found("y", 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> units.add(new Units.Unit("other", 8, List.of(), List.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> units.get(0).steps().add(original));
        assertThrows(UnsupportedOperationException.class, () -> Subst.find("").add(new Subst.Found("a", 0)));
        assertThrows(UnsupportedOperationException.class, () -> Units.of(null).add(
                new Units.Unit("other", 8, List.of(), List.of())));
    }
}
