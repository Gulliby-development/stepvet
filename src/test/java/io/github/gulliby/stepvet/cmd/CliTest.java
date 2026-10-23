package io.github.gulliby.stepvet.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.stepvet.base.Inputs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliTest {
    @Test
    void reportsMissingAndUnknownCommandsBeforeParsingOptions() {
        FakeIo missing = new FakeIo();
        assertEquals(2, Cli.run(List.of(), missing));
        assertEquals("", missing.out.toString());
        assertEquals("stepvet: missing command (check, select or steps)\n", missing.err.toString());

        FakeIo unknown = new FakeIo();
        assertEquals(2, Cli.run(List.of("show", "--bad"), unknown));
        assertEquals("", unknown.out.toString());
        assertEquals("stepvet: unknown command \"show\"\n", unknown.err.toString());

        FakeIo helpAsCommand = new FakeIo();
        assertEquals(2, Cli.run(List.of("--help", "check"), helpAsCommand));
        assertEquals("", helpAsCommand.out.toString());
        assertEquals("stepvet: unknown command \"--help\"\n", helpAsCommand.err.toString());
    }

    @Test
    void printsExactHelpOnlyWhenHelpIsTheSoleArgument() {
        FakeIo io = new FakeIo();

        assertEquals(0, Cli.run(List.of("--help"), io));
        assertEquals("usage: stepvet check FILE...\n"
                + "       stepvet select EXPR FILE...\n"
                + "       stepvet steps [--all] DEFS FILE...\n", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void validatesMissingArgumentsBeforeExpressionParsingOrFileReads() {
        FakeIo check = new FakeIo();
        assertEquals(2, Cli.run(List.of("check"), check));
        assertEquals("", check.out.toString());
        assertEquals("stepvet: missing FILE\n", check.err.toString());

        FakeIo selectExpression = new FakeIo();
        assertEquals(2, Cli.run(List.of("select"), selectExpression));
        assertEquals("", selectExpression.out.toString());
        assertEquals("stepvet: missing EXPR\n", selectExpression.err.toString());

        FakeIo selectFile = new FakeIo();
        assertEquals(2, Cli.run(List.of("select", "@a and"), selectFile));
        assertEquals("", selectFile.out.toString());
        assertEquals("stepvet: missing FILE\n", selectFile.err.toString());

        FakeIo stepsDefinitions = new FakeIo();
        assertEquals(2, Cli.run(List.of("steps"), stepsDefinitions));
        assertEquals("", stepsDefinitions.out.toString());
        assertEquals("stepvet: missing DEFS\n", stepsDefinitions.err.toString());

        FakeIo stepsFile = new FakeIo();
        assertEquals(2, Cli.run(List.of("steps", "d"), stepsFile));
        assertEquals("", stepsFile.out.toString());
        assertEquals("stepvet: missing FILE\n", stepsFile.err.toString());
    }

    @Test
    void rejectsUnknownOptionsBeforeArityAndExpressionOrFileWork() {
        FakeIo assignment = new FakeIo();
        assertEquals(2, Cli.run(List.of("steps", "--all=1", "d", "f.1"), assignment));
        assertEquals("", assignment.out.toString());
        assertEquals("stepvet: unknown option \"--all=1\"\n", assignment.err.toString());

        FakeIo checkFlag = new FakeIo();
        assertEquals(2, Cli.run(List.of("check", "--all"), checkFlag));
        assertEquals("", checkFlag.out.toString());
        assertEquals("stepvet: unknown option \"--all\"\n", checkFlag.err.toString());

        FakeIo doubleDash = new FakeIo();
        assertEquals(2, Cli.run(List.of("check", "--", "--help"), doubleDash));
        assertEquals("", doubleDash.out.toString());
        assertEquals("stepvet: unknown option \"--\"\n", doubleDash.err.toString());

        FakeIo localHelp = new FakeIo();
        assertEquals(2, Cli.run(List.of("select", "--help"), localHelp));
        assertEquals("", localHelp.out.toString());
        assertEquals("stepvet: unknown option \"--help\"\n", localHelp.err.toString());

        FakeIo lateBadOption = new FakeIo();
        assertEquals(2, Cli.run(List.of("steps", "missing", "--bad"), lateBadOption));
        assertEquals("", lateBadOption.out.toString());
        assertEquals("stepvet: unknown option \"--bad\"\n", lateBadOption.err.toString());

        FakeIo invalidExpression = new FakeIo();
        assertEquals(2, Cli.run(List.of("select", "@a and", "nofile"), invalidExpression));
        assertEquals("", invalidExpression.out.toString());
        assertEquals("stepvet: invalid tag expression: expected a tag, \"not\" or \"(\" at column 7\n",
                invalidExpression.err.toString());
    }

    @Test
    void checksRepeatedPathsAndReturnsNegativeCheckResults() {
        FakeIo repeated = new FakeIo();
        repeated.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, Cli.run(List.of("check", "f.1", "f.1"), repeated));
        assertEquals("ok, 2 scenarios in 2 files\n", repeated.out.toString());
        assertEquals("", repeated.err.toString());

        FakeIo problem = new FakeIo();
        problem.files.put("bad", String.join("\n", "Feature: F", "Scenario: S")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(1, Cli.run(List.of("check", "bad"), problem));
        assertEquals("bad:2:1: SV101 scenario has no steps\n1 problem\n", problem.out.toString());
        assertEquals("", problem.err.toString());
    }

    @Test
    void selectsMatchingAndNonmatchingScenariosWithExactCounts() {
        FakeIo none = new FakeIo();
        none.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        assertEquals(1, Cli.run(List.of("select", "@a", "f.1"), none));
        assertEquals("selected 0 of 1\n", none.out.toString());
        assertEquals("", none.err.toString());

        FakeIo selected = new FakeIo();
        selected.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, Cli.run(List.of("select", "not @a", "f.1"), selected));
        assertEquals("f.1:2: S\nselected 1 of 1\n", selected.out.toString());
        assertEquals("", selected.err.toString());
    }

    @Test
    void runsStepsQuietlyAndAllowsRepeatedAllFlags() {
        FakeIo quiet = new FakeIo();
        quiet.files.put("d", "ok".getBytes(StandardCharsets.UTF_8));
        quiet.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, Cli.run(List.of("steps", "d", "f.1"), quiet));
        assertEquals("steps: 1, undefined: 0, ambiguous: 0, unused patterns: 0\n",
                quiet.out.toString());
        assertEquals("", quiet.err.toString());

        FakeIo all = new FakeIo();
        all.files.put("d", "ok".getBytes(StandardCharsets.UTF_8));
        all.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        assertEquals(0, Cli.run(List.of("steps", "--all", "d", "--all", "f.1"), all));
        assertEquals("f.1:3: ok: ok -> d:1\n"
                + "steps: 1, undefined: 0, ambiguous: 0, unused patterns: 0\n", all.out.toString());
        assertEquals("", all.err.toString());
    }

    @Test
    void returnsNegativeStepsResultWithUndefinedAndUnusedLines() {
        FakeIo io = new FakeIo();
        io.files.put("d", "x".getBytes(StandardCharsets.UTF_8));
        io.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));

        assertEquals(1, Cli.run(List.of("steps", "d", "f.1"), io));
        assertEquals("f.1:3: undefined: ok\nd:1: unused: x\n"
                + "steps: 1, undefined: 1, ambiguous: 0, unused patterns: 1\n", io.out.toString());
        assertEquals("", io.err.toString());
    }

    @Test
    void discardsPartialCommandOutputWhenLaterReadsOrSyntaxFail() {
        FakeIo lateCheckRead = new FakeIo();
        lateCheckRead.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        assertEquals(2, Cli.run(List.of("check", "f.1", "nofile.txt"), lateCheckRead));
        assertEquals("", lateCheckRead.out.toString());
        assertEquals("stepvet: nofile.txt: cannot read the file\n", lateCheckRead.err.toString());

        FakeIo selectSyntax = new FakeIo();
        selectSyntax.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        selectSyntax.files.put("bad", "oops".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, Cli.run(List.of("select", "not @a", "f.1", "bad"), selectSyntax));
        assertEquals("", selectSyntax.out.toString());
        assertEquals("stepvet: bad:1:1: SV001 expected \"Feature:\" first\n",
                selectSyntax.err.toString());

        FakeIo stepsSyntax = new FakeIo();
        stepsSyntax.files.put("d", "ok".getBytes(StandardCharsets.UTF_8));
        stepsSyntax.files.put("f.1", String.join("\n",
                "Feature: F", "Scenario: S", "  Then ok").getBytes(StandardCharsets.UTF_8));
        stepsSyntax.files.put("bad", "oops".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, Cli.run(List.of("steps", "d", "f.1", "bad"), stepsSyntax));
        assertEquals("", stepsSyntax.out.toString());
        assertEquals("stepvet: bad:1:1: SV001 expected \"Feature:\" first\n",
                stepsSyntax.err.toString());

        FakeIo invalidDefinitions = new FakeIo();
        invalidDefinitions.files.put("d", "{bad}".getBytes(StandardCharsets.UTF_8));
        assertEquals(2, Cli.run(List.of("steps", "d", "nofile.txt"), invalidDefinitions));
        assertEquals("", invalidDefinitions.out.toString());
        assertEquals("stepvet: d:1:1: invalid pattern: unknown parameter \"{bad}\"\n",
                invalidDefinitions.err.toString());
    }

    @Test
    void sanitizesUnknownCommandOptionAndPathDiagnostics() {
        FakeIo command = new FakeIo();
        assertEquals(2, Cli.run(List.of("show😀"), command));
        assertEquals("", command.out.toString());
        assertEquals("stepvet: unknown command \"show?\"\n", command.err.toString());

        FakeIo option = new FakeIo();
        assertEquals(2, Cli.run(List.of("check", "--bad😀"), option));
        assertEquals("", option.out.toString());
        assertEquals("stepvet: unknown option \"--bad?\"\n", option.err.toString());

        FakeIo path = new FakeIo();
        assertEquals(2, Cli.run(List.of("check", "caf😀.txt"), path));
        assertEquals("", path.out.toString());
        assertEquals("stepvet: caf?.txt: cannot read the file\n", path.err.toString());
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
