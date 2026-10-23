package io.github.gulliby.stepvet.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTest {
    @Test
    void mergesSyntaxBeforeLintAndKeepsFilesInArgumentOrder() {
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null,
                List.of(new Doc.Scenario(false, "S", 2, 1, List.of(), List.of(), List.of())));
        Report.Output output = Report.check(List.of(
                new Report.Input("z", spec, List.of(
                        new Problem(2, 1, "SV101", "syntax tie"),
                        new Problem(1, 4, "SV004", "earlier"))),
                new Report.Input("a", null,
                        List.of(new Problem(1, 1, "SV001", "expected \"Feature:\" first")))));

        assertEquals("z:1:4: SV004 earlier\n"
                + "z:2:1: SV101 syntax tie\n"
                + "z:2:1: SV101 scenario has no steps\n"
                + "a:1:1: SV001 expected \"Feature:\" first\n"
                + "4 problems\n", output.out());
        assertEquals(1, output.code());
    }

    @Test
    void sortsByLineColumnAndCodeWhileKeepingStableSyntaxTies() {
        Report.Output output = Report.check(List.of(new Report.Input("f", null, List.of(
                new Problem(2, 2, "SV004", "third"),
                new Problem(2, 1, "SV008", "second"),
                new Problem(2, 1, "SV002", "first"),
                new Problem(2, 1, "SV002", "tie")))));

        assertEquals("f:2:1: SV002 first\n"
                + "f:2:1: SV002 tie\n"
                + "f:2:1: SV008 second\n"
                + "f:2:2: SV004 third\n"
                + "4 problems\n", output.out());
        assertEquals(1, output.code());
    }

    @Test
    void trimsTrailingSpacesAndTabsAndUsesSingularProblemTotal() {
        Report.Output output = Report.check(List.of(new Report.Input("f", null,
                List.of(new Problem(1, 1, "SV004", "unexpected line \"x\"  \t")))));

        assertEquals("f:1:1: SV004 unexpected line \"x\"\n1 problem\n", output.out());
        assertEquals(1, output.code());
    }

    @Test
    void emptyInputHasExactSuccessOutputAndZeroExitCode() {
        Report.Output output = Report.check(List.of());

        assertEquals("ok, 0 scenarios in 0 files\n", output.out());
        assertEquals(0, output.code());
    }

    @Test
    void reportsSingularScenarioAndFileCounts() {
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null,
                List.of(new Doc.Scenario(false, "S", 2, 1, List.of(),
                        List.of(new Doc.Step("Then", "ok", 3, 3, 8)), List.of())));

        Report.Output output = Report.check(List.of(new Report.Input("f", spec, List.of())));

        assertEquals("ok, 1 scenario in 1 file\n", output.out());
        assertEquals(0, output.code());
    }

    @Test
    void countsAnOutlineOnceRatherThanCountingItsRows() {
        Doc.Examples examples = new Doc.Examples(5, 3, "", List.of(), 6, List.of(),
                List.of(new Doc.Row(7, List.of()), new Doc.Row(8, List.of())));
        Doc.Scenario ordinary = new Doc.Scenario(false, "S", 2, 1, List.of(),
                List.of(new Doc.Step("Then", "ok", 3, 3, 8)), List.of());
        Doc.Scenario outline = new Doc.Scenario(true, "O", 4, 1, List.of(),
                List.of(new Doc.Step("Then", "ok", 5, 3, 8)), List.of(examples));
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null, List.of(ordinary, outline));

        Report.Output output = Report.check(List.of(new Report.Input("cart.txt", spec, List.of())));

        assertEquals("ok, 2 scenarios in 1 file\n", output.out());
        assertEquals(0, output.code());
    }

    @Test
    void repeatedInputsCountAsRepeatedFilesAndScenarios() {
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null,
                List.of(new Doc.Scenario(false, "S", 2, 1, List.of(),
                        List.of(new Doc.Step("Then", "ok", 3, 3, 8)), List.of())));

        Report.Output output = Report.check(List.of(
                new Report.Input("f.1", spec, List.of()),
                new Report.Input("f.1", spec, List.of())));

        assertEquals("ok, 2 scenarios in 2 files\n", output.out());
        assertEquals(0, output.code());
    }

    @Test
    void emptyAndNullSpecsCountAsZeroScenarios() {
        Doc.Spec empty = new Doc.Spec("F", 1, List.of(), null, List.of());

        Report.Output output = Report.check(List.of(
                new Report.Input("a", empty, List.of()),
                new Report.Input("b", null, List.of())));

        assertEquals("ok, 0 scenarios in 2 files\n", output.out());
        assertEquals(0, output.code());
    }

    @Test
    void preservesExamplesOrderForMissingColumnsAtAnExactTie() {
        Doc.Step given = new Doc.Step("Given", "<x>", 3, 3, 9);
        Doc.Step then = new Doc.Step("Then", "ok", 4, 3, 8);
        Doc.Examples first = new Doc.Examples(5, 3, "", List.of(), 6, List.of(),
                List.of(new Doc.Row(7, List.of())));
        Doc.Examples second = new Doc.Examples(8, 3, "", List.of(), 9, List.of(),
                List.of(new Doc.Row(10, List.of())));
        Doc.Scenario outline = new Doc.Scenario(true, "O", 2, 1, List.of(),
                List.of(given, then), List.of(first, second));
        Doc.Spec spec = new Doc.Spec("F", 1, List.of(), null, List.of(outline));

        Report.Output output = Report.check(List.of(new Report.Input("f", spec, List.of())));

        assertEquals("f:3:9: SV107 placeholder <x> has no column in the Examples: at line 5\n"
                + "f:3:9: SV107 placeholder <x> has no column in the Examples: at line 8\n"
                + "2 problems\n", output.out());
        assertEquals(1, output.code());
    }

    @Test
    void inputSnapshotsSyntaxAndExposesAnUnmodifiableList() {
        List<Problem> syntax = new ArrayList<>(List.of(new Problem(1, 1, "SV001", "first")));
        Report.Input input = new Report.Input("f", null, syntax);

        syntax.clear();

        assertEquals(List.of(new Problem(1, 1, "SV001", "first")), input.syntax());
        assertThrows(UnsupportedOperationException.class,
                () -> input.syntax().add(new Problem(2, 1, "SV002", "second")));
    }

    @Test
    void outputRecordExposesItsExactComponents() {
        Report.Output output = new Report.Output("done\n", 7);

        assertEquals("done\n", output.out());
        assertEquals(7, output.code());
    }
}
