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

class SelectCountTest {
    private static final String CART = String.join("\n",
            "@shop",
            "Feature: Cart",
            "  @smoke",
            "  Scenario: Add one",
            "    Then ok",
            "  @slow @smoke",
            "  Scenario Outline: Add many",
            "    Then <n>",
            "    @eu",
            "    Examples:",
            "      | n |",
            "      | 1 |",
            "      | 2 |",
            "  @slow",
            "  Scenario: Slow",
            "    Then ok");

    @Test
    void countPrintsOnlyTheLastLine() {
        FakeIo io = new FakeIo();
        io.files.put("cart.txt", CART.getBytes(StandardCharsets.UTF_8));

        Report.Output plain = SelectCommand.run(List.of("@smoke", "cart.txt"), Set.of(), new Inputs(io));
        Report.Output counted = SelectCommand.run(
                List.of("@smoke", "cart.txt"), Set.of("--count"), new Inputs(io));

        assertEquals(new Report.Output(
                "cart.txt:4: Add one\ncart.txt:12: Add many (row 1)\ncart.txt:13: Add many (row 2)\nselected 3 of 4\n",
                0), plain);
        assertEquals(new Report.Output("selected 3 of 4\n", 0), counted);
    }

    @Test
    void countKeepsTheExitCodeOfAnEmptySelection() {
        FakeIo io = new FakeIo();
        io.files.put("cart.txt", CART.getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@absent", "cart.txt"), Set.of("--count"), new Inputs(io));

        assertEquals(new Report.Output("selected 0 of 4\n", 1), output);
    }

    @Test
    void countAddsUpAllFiles() {
        FakeIo io = new FakeIo();
        io.files.put("a.txt", CART.getBytes(StandardCharsets.UTF_8));
        io.files.put("b.txt", "Feature: B\n  @slow\n  Scenario: One\n    Then ok".getBytes(StandardCharsets.UTF_8));

        Report.Output output = SelectCommand.run(
                List.of("@slow", "a.txt", "b.txt"), Set.of("--count"), new Inputs(io));

        assertEquals(new Report.Output("selected 4 of 5\n", 0), output);
    }

    @Test
    void countStillFailsOnABadFileOrExpression() {
        FakeIo io = new FakeIo();
        io.files.put("cart.txt", CART.getBytes(StandardCharsets.UTF_8));

        Failure expression = assertThrows(Failure.class,
                () -> SelectCommand.run(List.of("@a and", "cart.txt"), Set.of("--count"), new Inputs(io)));
        Failure missing = assertThrows(Failure.class,
                () -> SelectCommand.run(List.of("@a", "none.txt"), Set.of("--count"), new Inputs(io)));

        assertEquals("invalid tag expression: expected a tag, \"not\" or \"(\" at column 7",
                expression.getMessage());
        assertEquals("none.txt: cannot read the file", missing.getMessage());
    }

    @Test
    void theCommandLineAcceptsCountForSelectAnywhereAmongTheArguments() {
        for (List<String> argv : List.of(
                List.of("select", "--count", "@smoke", "cart.txt"),
                List.of("select", "@smoke", "--count", "cart.txt"),
                List.of("select", "@smoke", "cart.txt", "--count"))) {
            FakeIo io = new FakeIo();
            io.files.put("cart.txt", CART.getBytes(StandardCharsets.UTF_8));

            assertEquals(0, Cli.run(argv, io));
            assertEquals("selected 3 of 4\n", io.out.toString());
            assertEquals("", io.err.toString());
        }
    }

    @Test
    void countIsAnUnknownOptionForTheOtherCommands() {
        for (String command : List.of("check", "steps")) {
            FakeIo io = new FakeIo();
            io.files.put("cart.txt", CART.getBytes(StandardCharsets.UTF_8));

            assertEquals(2, Cli.run(List.of(command, "--count", "cart.txt"), io));
            assertEquals("", io.out.toString());
            assertEquals("stepvet: unknown option \"--count\"\n", io.err.toString());
        }
    }

    @Test
    void helpListsCountForSelectOnly() {
        FakeIo io = new FakeIo();

        assertEquals(0, Cli.run(List.of("--help"), io));
        assertEquals("usage: stepvet check FILE...\n"
                + "       stepvet select [--count] EXPR FILE...\n"
                + "       stepvet steps [--all] DEFS FILE...\n", io.out.toString());
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
