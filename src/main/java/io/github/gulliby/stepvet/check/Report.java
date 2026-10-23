package io.github.gulliby.stepvet.check;

import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;

import java.util.ArrayList;
import java.util.List;

public final class Report {
    private Report() {
    }

    public record Input(String display, Doc.Spec spec, List<Problem> syntax) {
        public Input {
            syntax = List.copyOf(syntax);
        }
    }

    public record Output(String out, int code) {
    }

    public static Output check(List<Input> files) {
        StringBuilder out = new StringBuilder();
        int problemCount = 0;
        int scenarioCount = 0;

        for (Input file : files) {
            if (file.spec() != null) {
                scenarioCount += file.spec().scenarios().size();
            }

            List<Problem> found = new ArrayList<>(file.syntax());
            found.addAll(Rules.analyze(file.spec()));
            for (Problem problem : Problem.sorted(found)) {
                out.append(Text.rtrim(problem.format(file.display()))).append('\n');
                problemCount++;
            }
        }

        if (problemCount > 0) {
            out.append(Text.plural(problemCount, "problem")).append('\n');
            return new Output(out.toString(), 1);
        }
        out.append("ok, ")
                .append(Text.plural(scenarioCount, "scenario"))
                .append(" in ")
                .append(Text.plural(files.size(), "file"))
                .append('\n');
        return new Output(out.toString(), 0);
    }
}
