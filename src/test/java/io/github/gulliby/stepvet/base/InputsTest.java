package io.github.gulliby.stepvet.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.check.Report;
import io.github.gulliby.stepvet.cmd.CheckCommand;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InputsTest {
    @Test
    void readsUtf8FileContentsIncludingAccentedAndAstralCharacters() {
        FakeIo io = new FakeIo();
        io.files.put("a.txt", String.join("\n",
                "Feature: Café", "Scenario: 😀", "Then ok", "")
                .getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(List.of("a.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 1 scenario in 1 file\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void removesOneLeadingBomFromStdin() {
        FakeIo io = new FakeIo();
        io.stdin = String.join("\n", "\uFEFFFeature: F", "").getBytes(StandardCharsets.UTF_8);

        Report.Output output = CheckCommand.run(List.of("-"), Set.of(), new Inputs(io));

        assertEquals("ok, 0 scenarios in 1 file\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void leavesASecondLeadingBomForTheParser() {
        FakeIo io = new FakeIo();
        io.stdin = String.join("\n", "\uFEFF\uFEFFFeature: F", "")
                .getBytes(StandardCharsets.UTF_8);

        Report.Output output = CheckCommand.run(List.of("-"), Set.of(), new Inputs(io));

        assertEquals("<stdin>:1:1: SV001 expected \"Feature:\" first\n1 problem\n", output.out());
        assertEquals(1, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void normalizesCrLfToLf() {
        FakeIo io = new FakeIo();
        io.files.put("a.txt", String.join("\n",
                "Feature: F\r", "Scenario: S\r", "Then ok\r", "")
                .getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(List.of("a.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 1 scenario in 1 file\n", output.out());
        assertEquals(0, output.code());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void rejectsMalformedUtf8FromStdin() {
        FakeIo io = new FakeIo();
        io.stdin = new byte[] {(byte) 0x61, (byte) 0xFF, (byte) 0x0A};

        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("-"), Set.of(), new Inputs(io)));

        assertEquals("<stdin>: input is not valid UTF-8", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void rejectsMalformedUtf8FromAFile() {
        FakeIo io = new FakeIo();
        io.files.put("a.txt", new byte[] {(byte) 0x61, (byte) 0xFF, (byte) 0x0A});

        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("a.txt"), Set.of(), new Inputs(io)));

        assertEquals("a.txt: input is not valid UTF-8", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsBareCarriageReturnAfterLfOnTheSecondLine() {
        FakeIo io = new FakeIo();
        io.stdin = new byte[] {(byte) 0x61, (byte) 0x0A, (byte) 0x0D, (byte) 0x62, (byte) 0x0A};

        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("-"), Set.of(), new Inputs(io)));

        assertEquals("<stdin>: bare carriage return at line 2", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void reportsFinalBareCarriageReturnOnTheFirstLine() {
        FakeIo io = new FakeIo();
        io.stdin = new byte[] {(byte) 0x61, (byte) 0x0D};

        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("-"), Set.of(), new Inputs(io)));

        assertEquals("<stdin>: bare carriage return at line 1", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void normalizesMissingPathFailuresAndSanitizesDisplays() {
        FakeIo io = new FakeIo();

        Failure missing = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("nofile.txt"), Set.of(), new Inputs(io)));
        Failure astral = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("caf😀.txt"), Set.of(), new Inputs(io)));
        Failure empty = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of(""), Set.of(), new Inputs(io)));

        assertEquals("nofile.txt: cannot read the file", missing.getMessage());
        assertEquals("caf?.txt: cannot read the file", astral.getMessage());
        assertEquals(": cannot read the file", empty.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void rejectsStandardInputNamedTwice() {
        FakeIo io = new FakeIo();
        io.stdin = "Feature: F\n".getBytes(StandardCharsets.UTF_8);

        Failure failure = assertThrows(Failure.class,
                () -> CheckCommand.run(List.of("-", "-"), Set.of(), new Inputs(io)));

        assertEquals("standard input is named twice", failure.getMessage());
        assertEquals("", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void acceptsRepeatedFilePathsAndCountsEachOccurrence() {
        FakeIo io = new FakeIo();
        io.files.put("a.txt", String.join("\n", "Feature: F", "")
                .getBytes(StandardCharsets.UTF_8));

        Report.Output output = CheckCommand.run(List.of("a.txt", "a.txt"), Set.of(), new Inputs(io));

        assertEquals("ok, 0 scenarios in 2 files\n", output.out());
        assertEquals(0, output.code());
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
