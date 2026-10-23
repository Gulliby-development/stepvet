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

class StepsCommandTest {
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

    @Test
    void validatesDefinitionAndFileCountsBeforeReading() {
        FakeIo io = new FakeIo();

        Failure missingDefs = assertThrows(Failure.class,
                () -> StepsCommand.run(List.of(), Set.of(), new Inputs(io)));
        Failure missingFile = assertThrows(Failure.class,
                () -> StepsCommand.run(List.of("absent"), Set.of(), new Inputs(io)));

        assertEquals("missing DEFS", missingDefs.getMessage());
        assertEquals("missing FILE", missingFile.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void readsDefinitionsFirstAndPropagatesPatternFailures() {
        FakeIo io = new FakeIo();
        io.files.put("d", "I add {int".getBytes(StandardCharsets.UTF_8));

        Failure cannotReadDefinitions = assertThrows(Failure.class,
                () -> StepsCommand.run(List.of("missing-defs", "missing-spec"),
                        Set.of(), new Inputs(io)));
        Failure invalidPattern = assertThrows(Failure.class,
                () -> StepsCommand.run(List.of("d", "missing-spec"), Set.of(), new Inputs(io)));

        assertEquals("missing-defs: cannot read the file", cannotReadDefinitions.getMessage());
        assertEquals("d:1:7: invalid pattern: unterminated {", invalidPattern.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void gatesOnFirstSortedSyntaxProblemAndDoesNotReturnLateFailureOutput() {
        FakeIo io = new FakeIo();
        io.files.put("d", "ok".getBytes(StandardCharsets.UTF_8));
        io.files.put("a", String.join("\n",
                "Feature: F",
                "Scenario: S",
                "Then ok").getBytes(StandardCharsets.UTF_8));
        io.files.put("b", String.join("\n",
                "Feature: F",
                "@a @@").getBytes(StandardCharsets.UTF_8));

        Failure syntax = assertThrows(Failure.class,
                () -> StepsCommand.run(List.of("d", "a", "b", "missing"),
                        Set.of("--all"), new Inputs(io)));
        assertEquals("b:2:1: SV003 tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:",
                syntax.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());

        Failure lateRead = assertThrows(Failure.class,
                () -> StepsCommand.run(List.of("d", "a", "missing"), Set.of(), new Inputs(io)));
        assertEquals("missing: cannot read the file", lateRead.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void deduplicatesExpandedStepsAndPreservesLineAndAppearanceOrder() {
        FakeIo io = new FakeIo();
        io.files.put("d", "base\nx {int}\ny {int}".getBytes(StandardCharsets.UTF_8));
        io.files.put("f", String.join("\n",
                "Feature: F",
                "Background:",
                "Given base",
                "Scenario Outline: O",
                "When x <n>",
                "Then y <n>",
                "Examples:",
                "| n |",
                "| 2 |",
                "| 1 |",
                "| 2 |").getBytes(StandardCharsets.UTF_8));

        Report.Output output = StepsCommand.run(List.of("d", "f"), Set.of("--all"), new Inputs(io));

        assertEquals(0, output.code());
        assertEquals("f:3: ok: base -> d:1\n"
                + "f:5: ok: x 2 -> d:2 (\"2\")\n"
                + "f:5: ok: x 1 -> d:2 (\"1\")\n"
                + "f:6: ok: y 2 -> d:3 (\"2\")\n"
                + "f:6: ok: y 1 -> d:3 (\"1\")\n"
                + "steps: 5, undefined: 0, ambiguous: 0, unused patterns: 0\n", output.out());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsAmbiguousAndUndefinedStepsAndUnusedDefinitionsByDefault() {
        FakeIo io = new FakeIo();
        io.files.put("d", String.join("\n",
                "# definitions",
                "an empty cart",
                "{} cart",
                "I add {int} {string}",
                "done",
                "unused").getBytes(StandardCharsets.UTF_8));
        io.files.put("f", String.join("\n",
                "Feature: F",
                "Scenario: S",
                "Given an empty cart",
                "When I add 1 \"apple\"",
                "Then done",
                "And missing").getBytes(StandardCharsets.UTF_8));

        Report.Output output = StepsCommand.run(List.of("d", "f"), Set.of(), new Inputs(io));

        assertEquals(1, output.code());
        assertEquals("f:3: ambiguous: an empty cart matches d:2, d:3\n"
                + "f:6: undefined: missing\n"
                + "d:6: unused: unused\n"
                + "steps: 4, undefined: 1, ambiguous: 1, unused patterns: 1\n", output.out());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void allFlagIncludesOrderedCapturesAndCaptureFreeSuccesses() {
        FakeIo io = new FakeIo();
        io.files.put("d", String.join("\n",
                "# definitions",
                "an empty cart",
                "{} cart",
                "I add {int} {string}",
                "done",
                "unused").getBytes(StandardCharsets.UTF_8));
        io.files.put("f", String.join("\n",
                "Feature: F",
                "Scenario: S",
                "Given an empty cart",
                "When I add 1 \"apple\"",
                "Then done",
                "And missing").getBytes(StandardCharsets.UTF_8));

        Report.Output output = StepsCommand.run(List.of("d", "f"), Set.of("--all"), new Inputs(io));

        assertEquals(1, output.code());
        assertEquals("f:3: ambiguous: an empty cart matches d:2, d:3\n"
                + "f:4: ok: I add 1 \"apple\" -> d:4 (\"1\", \"apple\")\n"
                + "f:5: ok: done -> d:5\n"
                + "f:6: undefined: missing\n"
                + "d:6: unused: unused\n"
                + "steps: 4, undefined: 1, ambiguous: 1, unused patterns: 1\n", output.out());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void processesFilesAndRepeatedOccurrencesIndependentlyWithoutLinting() {
        FakeIo io = new FakeIo();
        io.files.put("d", "ok".getBytes(StandardCharsets.UTF_8));
        io.files.put("a", String.join("\n",
                "Feature: F",
                "Scenario: S",
                "Given ok",
                "When ok").getBytes(StandardCharsets.UTF_8));
        io.files.put("b", String.join("\n",
                "Feature: G",
                "Scenario: T",
                "Then ok").getBytes(StandardCharsets.UTF_8));

        Report.Output output = StepsCommand.run(List.of("d", "b", "a", "a"),
                Set.of("--all"), new Inputs(io));

        assertEquals(0, output.code());
        assertEquals("b:3: ok: ok -> d:1\n"
                + "a:3: ok: ok -> d:1\n"
                + "a:4: ok: ok -> d:1\n"
                + "a:3: ok: ok -> d:1\n"
                + "a:4: ok: ok -> d:1\n"
                + "steps: 5, undefined: 0, ambiguous: 0, unused patterns: 0\n", output.out());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void supportsEmptyFilesRowlessOutlinesUnusedOnlyAndNoDefinitions() {
        FakeIo io = new FakeIo();
        io.files.put("d", "# comment\n  unused  ".getBytes(StandardCharsets.UTF_8));
        io.files.put("none", "# comment".getBytes(StandardCharsets.UTF_8));
        io.files.put("empty", new byte[0]);
        io.files.put("rowless", String.join("\n",
                "Feature: F",
                "Background:",
                "Given base",
                "Scenario Outline: O",
                "Then <n>",
                "Examples:",
                "| n |").getBytes(StandardCharsets.UTF_8));

        Report.Output unused = StepsCommand.run(List.of("d", "empty", "rowless"),
                Set.of(), new Inputs(io));
        Report.Output noDefinitions = StepsCommand.run(List.of("none", "empty"),
                Set.of(), new Inputs(io));

        assertEquals(new Report.Output("d:2: unused: unused\n"
                + "steps: 0, undefined: 0, ambiguous: 0, unused patterns: 1\n", 0), unused);
        assertEquals(new Report.Output("steps: 0, undefined: 0, ambiguous: 0, unused patterns: 0\n",
                0), noDefinitions);
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void sanitizesUnicodeOnlyAfterRawStepDeduplication() {
        FakeIo io = new FakeIo();
        io.files.put("dé😀", String.join("\n",
                "value {string}",
                "{word}",
                "unusedé😀").getBytes(StandardCharsets.UTF_8));
        io.files.put("f", String.join("\n",
                "Feature: F",
                "Scenario Outline: O",
                "Then <v>",
                "Examples:",
                "| v |",
                "| é |",
                "| 😀 |",
                "Scenario: S",
                "Then value \"\"",
                "Then value \"é😀\"").getBytes(StandardCharsets.UTF_8));

        Report.Output output = StepsCommand.run(List.of("dé😀", "f"),
                Set.of("--all"), new Inputs(io));

        assertEquals(0, output.code());
        assertEquals("f:3: ok: ? -> d??:2 (\"?\")\n"
                + "f:3: ok: ? -> d??:2 (\"?\")\n"
                + "f:9: ok: value \"\" -> d??:1 (\"\")\n"
                + "f:10: ok: value \"??\" -> d??:1 (\"??\")\n"
                + "d??:3: unused: unused??\n"
                + "steps: 4, undefined: 0, ambiguous: 0, unused patterns: 1\n", output.out());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void emptyDefinitionsMakeNonemptyStepsUndefined() {
        FakeIo io = new FakeIo();
        io.files.put("d", "# no patterns".getBytes(StandardCharsets.UTF_8));
        io.files.put("f", String.join("\n",
                "Feature: F",
                "Scenario: S",
                "Given missing").getBytes(StandardCharsets.UTF_8));

        Report.Output output = StepsCommand.run(List.of("d", "f"), Set.of("--all"), new Inputs(io));

        assertEquals(1, output.code());
        assertEquals("f:3: undefined: missing\n"
                + "steps: 1, undefined: 1, ambiguous: 0, unused patterns: 0\n", output.out());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }
}
