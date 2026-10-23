package io.github.gulliby.stepvet.cmd;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.check.Report;
import io.github.gulliby.stepvet.model.Problem;
import io.github.gulliby.stepvet.outline.Units;
import io.github.gulliby.stepvet.parse.Parser;
import io.github.gulliby.stepvet.tagexpr.TagExpr;

import java.util.List;
import java.util.Set;

/** Runs the tag-filtered scenario selection command. */
public final class SelectCommand {
    private SelectCommand() {
    }

    public static Report.Output run(List<String> args, Set<String> flags, Inputs inputs) {
        if (args.isEmpty()) {
            throw new Failure("missing EXPR");
        }
        if (args.size() == 1) {
            throw new Failure("missing FILE");
        }

        TagExpr expression = TagExpr.parse(args.get(0));
        StringBuilder selectedLines = new StringBuilder();
        int total = 0;
        int selected = 0;

        for (int index = 1; index < args.size(); index++) {
            Inputs.Source source = inputs.read(args.get(index));
            Parser.Result parsed = Parser.parse(source.text());
            List<Problem> problems = Problem.sorted(parsed.problems());
            if (!problems.isEmpty()) {
                throw new Failure(problems.get(0).format(source.display()));
            }

            for (Units.Unit unit : Units.of(parsed.spec())) {
                total++;
                if (expression.matches(Set.copyOf(unit.tags()))) {
                    String line = source.display() + ":" + unit.line() + ": "
                            + Text.plain(unit.title());
                    selectedLines.append(Text.rtrim(line)).append('\n');
                    selected++;
                }
            }
        }

        selectedLines.append("selected ").append(selected).append(" of ").append(total).append('\n');
        return new Report.Output(selectedLines.toString(), selected > 0 ? 0 : 1);
    }
}
