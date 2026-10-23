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

class CheckCommandTest {
    @Test
    void requiresAtLeastOneFileBeforeReading() {
        FakeIo io = new FakeIo();
        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of(), Set.of(), new Inputs(io)));

        assertEquals("missing FILE", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsOneValidScenarioExactly() {
        FakeIo io = new FakeIo();
        io.files.put("one.txt",
                "Feature: F\nScenario: S\n  Then done\n".getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(List.of("one.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 1 scenario in 1 file\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsAnEmptyFileAsZeroScenarios() {
        FakeIo io = new FakeIo();
        io.files.put("empty.txt", new byte[0]);

        Report.Output output = CheckCommand.run(List.of("empty.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 0 scenarios in 1 file\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void countsAnOutlineOnceAcrossMultipleFiles() {
        FakeIo io = new FakeIo();
        io.files.put("plain.txt",
                "Feature: F\nScenario: S\n  Then done\n".getBytes(StandardCharsets.UTF_8));
        io.files.put("outline.txt", String.join("\n",
                "Feature: F", "Scenario Outline: O", "  Given <n>", "  Then done",
                "  Examples:", "    | n |", "    | 1 |", "    | 2 |")
                .getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(
                List.of("plain.txt", "outline.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 2 scenarios in 2 files\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void preservesRepeatedPathsAndCountsEachOccurrence() {
        FakeIo io = new FakeIo();
        io.files.put("same.txt",
                "Feature: F\nScenario: S\n  Then done\n".getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(
                List.of("same.txt", "same.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 2 scenarios in 2 files\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void mergesSyntaxAndSemanticDiagnosticsIntoOneReport() {
        FakeIo io = new FakeIo();
        io.files.put("mixed.txt", String.join("\n",
                "Feature: F", "Scenario: S", "  Given step", "  stray words")
                .getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(List.of("mixed.txt"), Set.of(), new Inputs(io));

        assertEquals("mixed.txt:2:1: SV104 scenario has no Then step\n"
                + "mixed.txt:4:3: SV004 unexpected line \"stray words\"\n"
                + "2 problems\n", output.out());
        assertEquals(1, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void groupsDiagnosticsInArgumentOrderRatherThanNameOrder() {
        FakeIo io = new FakeIo();
        io.files.put("z.txt", "not a feature\n".getBytes(StandardCharsets.UTF_8));
        io.files.put("a.txt", "also not a feature\n".getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(
                List.of("z.txt", "a.txt"), Set.of(), new Inputs(io));

        assertEquals("z.txt:1:1: SV001 expected \"Feature:\" first\n"
                + "a.txt:1:1: SV001 expected \"Feature:\" first\n"
                + "2 problems\n", output.out());
        assertEquals(1, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsStdinDiagnosticsWithTheStdinDisplayName() {
        FakeIo io = new FakeIo();
        io.stdin = "Feature: F\nScenario: S\n  Given step\n".getBytes(StandardCharsets.UTF_8);

        Report.Output output = CheckCommand.run(List.of("-"), Set.of(), new Inputs(io));

        assertEquals("<stdin>:2:1: SV104 scenario has no Then step\n1 problem\n", output.out());
        assertEquals(1, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void rejectsDuplicateStdinWithoutWritingOutput() {
        FakeIo io = new FakeIo();
        io.stdin = "Feature: F\n".getBytes(StandardCharsets.UTF_8);
        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("-", "-"), Set.of(), new Inputs(io)));

        assertEquals("standard input is named twice", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void earlierMissingFileWinsOverLaterDuplicateStdin() {
        FakeIo io = new FakeIo();
        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("missing.txt", "-", "-"), Set.of(), new Inputs(io)));

        assertEquals("missing.txt: cannot read the file", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void laterMissingFileAbortsAfterEarlierDiagnostics() {
        FakeIo io = new FakeIo();
        io.files.put("bad.txt", "not a feature\n".getBytes(StandardCharsets.UTF_8));
        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("bad.txt", "missing.txt"), Set.of(), new Inputs(io)));

        assertEquals("missing.txt: cannot read the file", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void malformedUtf8AbortsBeforeLaterMissingFile() {
        FakeIo io = new FakeIo();
        io.stdin = new byte[] {(byte) 0x61, (byte) 0xFF, (byte) 0x0A};
        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("-", "missing.txt"), Set.of(), new Inputs(io)));

        assertEquals("<stdin>: input is not valid UTF-8", failure.getMessage());
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
