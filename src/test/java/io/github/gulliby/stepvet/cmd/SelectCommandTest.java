package io.github.gulliby.stepvet.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.check.Report;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SelectCommandTest {
    @Test
    void validatesMissingArgumentsBeforeParsingAnExpression() {
        FakeIo io = new FakeIo();

        Failure missingExpression = assertThrows(Failure.class,
                () -> SelectCommand.run(List.of(), Set.of(), new Inputs(io)));
        Failure missingFile = assertThrows(Failure.class,
                () -> SelectCommand.run(List.of("not-a-tag"), Set.of(), new Inputs(io)));

        assertEquals("missing EXPR", missingExpression.getMessage());
        assertEquals("missing FILE", missingFile.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void parsesMalformedExpressionBeforeTryingToReadAnyFile() {
        FakeIo io = new FakeIo();

        Failure failure = assertThrows(Failure.class,
                () -> SelectCommand.run(List.of("@a and", "absent.txt"), Set.of(), new Inputs(io)));

        assertEquals("invalid tag expression: expected a tag, \"not\" or \"(\" at column 7",
                failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsTheFirstSortedSyntaxProblemBeforeReadingLaterFiles() {
        FakeIo io = new FakeIo();
        io.files.put("first.txt", String.join("\n",
                "Feature: F",
                "  @orphan @@",
                "stray text").getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(Failure.class,
                () -> SelectCommand.run(List.of("@a", "first.txt", "missing.txt"),
                        Set.of(), new Inputs(io)));

        assertEquals("first.txt:2:3: SV003 tags must stand before Feature:, Scenario:, "
                + "Scenario Outline: or Examples:", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void selectsSyntacticallyValidScenariosEvenWhenTheyHaveSemanticDefects() {
        FakeIo io = new FakeIo();
        io.files.put("semantic.txt", String.join("\n",
                "Feature: F",
                "Scenario: No Then",
                "  Given a start").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("not @missing", "semantic.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("semantic.txt:2: No Then\nselected 1 of 1\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void appliesAndBeforeOrAndCombinesFeatureAndScenarioTags() {
        FakeIo io = new FakeIo();
        io.files.put("tags.txt", String.join("\n",
                "@c",
                "Feature: F",
                "  @a",
                "  Scenario: A",
                "    Then ok",
                "  @b",
                "  Scenario: B",
                "    Then ok").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@a or @b and not @c", "tags.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("tags.txt:4: A\nselected 1 of 2\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void inheritsExamplesTagsAndNumbersRowsAcrossBlocksAtTheirSourceLines() {
        FakeIo io = new FakeIo();
        io.files.put("outline.txt", String.join("\n",
                "@feature",
                "Feature: F",
                "@outline",
                "Scenario Outline: O",
                "  Then <n>",
                "  @eu",
                "  Examples:",
                "    | n |",
                "    | 1 |",
                "  Examples: more",
                "    | n |",
                "    | 2 |").getBytes(StandardCharsets.UTF_8));

        Report.Output eu = SelectCommand.run(
                List.of("@feature and @outline and @eu", "outline.txt"),
                Set.of(), new Inputs(io));
        Report.Output notEu = SelectCommand.run(
                List.of("@feature and @outline and not @eu", "outline.txt"),
                Set.of(), new Inputs(io));

        assertEquals(new Report.Output("outline.txt:9: O (row 1)\nselected 1 of 2\n", 0), eu);
        assertEquals(new Report.Output("outline.txt:12: O (row 2)\nselected 1 of 2\n", 0), notEu);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void preservesFileOrderAndRepeatedFileOccurrences() {
        FakeIo io = new FakeIo();
        io.files.put("a.txt", String.join("\n",
                "Feature: A",
                "Scenario: A1",
                "  Then ok",
                "Scenario: A2",
                "  Then ok").getBytes(StandardCharsets.UTF_8));
        io.files.put("b.txt", String.join("\n",
                "Feature: B",
                "Scenario: B",
                "  Then ok").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("not @missing", "a.txt", "b.txt", "a.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("a.txt:2: A1\na.txt:4: A2\nb.txt:2: B\n"
                + "a.txt:2: A1\na.txt:4: A2\nselected 5 of 5\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void returnsOneWhenNoUnitsMatchButTheTotalIsNonzero() {
        FakeIo io = new FakeIo();
        io.files.put("one.txt", String.join("\n",
                "Feature: F",
                "  @other",
                "  Scenario: Other",
                "    Then ok").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@wanted", "one.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("selected 0 of 1\n", 1), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsZeroOfZeroForEmptyFeatureOnlyAndRowlessOutlineInputs() {
        FakeIo io = new FakeIo();
        io.files.put("empty.txt", "".getBytes(StandardCharsets.UTF_8));
        io.files.put("feature.txt", "Feature: F".getBytes(StandardCharsets.UTF_8));
        io.files.put("outline.txt", String.join("\n",
                "Feature: F",
                "Scenario Outline: O",
                "  Then <x>",
                "  Examples:",
                "    | x |").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@wanted", "empty.txt", "feature.txt", "outline.txt"),
                Set.of(), new Inputs(io));

        assertEquals(new Report.Output("selected 0 of 0\n", 1), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void displaysStdinAndSanitizesUnicodePathsAndTitles() {
        FakeIo io = new FakeIo();
        io.files.put("caf😀.txt", String.join("\n",
                "@ok",
                "Feature: F",
                "Scenario: hi😀\tthere",
                "  Then ok").getBytes(StandardCharsets.UTF_8));
        io.stdin = String.join("\n",
                "@ok",
                "Feature: Stdin",
                "Scenario: standard",
                "  Then ok").getBytes(StandardCharsets.UTF_8);

        Report.Output output = SelectCommand.run(
                List.of("@ok", "caf😀.txt", "-"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("caf?.txt:3: hi??there\n<stdin>:3: standard\n"
                + "selected 2 of 2\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void trimsEmptyTitlesWithoutLeavingATrailingBlank() {
        FakeIo io = new FakeIo();
        io.files.put("lines.txt", String.join("\n",
                "Feature: F",
                "Scenario:   ",
                "  Then bare").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("not @a", "lines.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("lines.txt:2:\nselected 1 of 1\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void exitsSuccessfullyWhenExactlyOneUnitIsSelected() {
        FakeIo io = new FakeIo();
        io.files.put("one.txt", String.join("\n",
                "Feature: F",
                "  @yes",
                "  Scenario: Only",
                "    Then ok").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@yes", "one.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("one.txt:3: Only\nselected 1 of 1\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void countsSelectedAndUnselectedUnitsTogether() {
        FakeIo io = new FakeIo();
        io.files.put("mixed.txt", String.join("\n",
                "Feature: F",
                "  @yes",
                "  Scenario: Pick",
                "    Then ok",
                "  @no",
                "  Scenario: Skip",
                "    Then ok").getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@yes", "mixed.txt"), Set.of(), new Inputs(io));

        assertEquals(new Report.Output("mixed.txt:3: Pick\nselected 1 of 2\n", 0), output);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void exposesNoPartialOutputWhenALaterFileFailsAfterASelection() {
        FakeIo io = new FakeIo();
        io.files.put("present.txt", String.join("\n",
                "Feature: F",
                "  @yes",
                "  Scenario: Pick",
                "    Then ok").getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(Failure.class,
                () -> SelectCommand.run(
                        List.of("@yes", "present.txt", "absent.txt"), Set.of(), new Inputs(io)));

        assertEquals("absent.txt: cannot read the file", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    static final class FakeIo implements Inputs.Io {
        final Map<String, byte[]> files = new HashMap<>();
        byte[] stdin = new byte[0];
        final StringBuilder out = new StringBuilder();
        final StringBuilder err = new StringBuilder();

        @Override public byte[] readStdin() { return stdin; }

        @Override
        public byte[] readFile(String path) throws IOException {
            byte[] data = files.get(path);
            if (data == null) {
                throw new IOException("no such file");
            }
            return data;
        }

        @Override public void out(String text) { out.append(text); }

        @Override public void err(String text) { err.append(text); }
    }
}
