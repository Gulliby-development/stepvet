package io.github.gulliby.stepvet.cmd;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.check.Report;
import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;
import io.github.gulliby.stepvet.outline.Units;
import io.github.gulliby.stepvet.parse.Parser;
import io.github.gulliby.stepvet.stepexpr.Defs;
import io.github.gulliby.stepvet.stepexpr.Matching;
import io.github.gulliby.stepvet.stepexpr.StepPattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compares expanded specification steps with their definitions. */
public final class StepsCommand {
    private StepsCommand() {
    }

    public static Report.Output run(List<String> args, Set<String> flags, Inputs inputs) {
        if (args.isEmpty()) {
            throw new Failure("missing DEFS");
        }
        if (args.size() == 1) {
            throw new Failure("missing FILE");
        }

        Inputs.Source definitions = inputs.read(args.get(0));
        List<StepPattern> patterns = Defs.parse(definitions.text(), definitions.display());
        boolean all = flags.contains("--all");
        boolean[] used = new boolean[patterns.size()];
        StringBuilder out = new StringBuilder();
        int stepCount = 0;
        int undefinedCount = 0;
        int ambiguousCount = 0;

        for (int fileIndex = 1; fileIndex < args.size(); fileIndex++) {
            Inputs.Source source = inputs.read(args.get(fileIndex));
            Parser.Result parsed = Parser.parse(source.text());
            List<Problem> syntax = Problem.sorted(parsed.problems());
            if (!syntax.isEmpty()) {
                throw new Failure(syntax.get(0).format(source.display()));
            }

            List<Doc.Step> steps = uniqueSteps(parsed.spec());
            for (Doc.Step step : steps) {
                stepCount++;
                List<Matching.Hit> hits = Matching.of(patterns, step.text());
                for (Matching.Hit hit : hits) {
                    used[hit.index()] = true;
                }
                if (hits.isEmpty()) {
                    undefinedCount++;
                    appendLine(out, source.display() + ":" + step.line()
                            + ": undefined: " + Text.plain(step.text()));
                } else if (hits.size() > 1) {
                    ambiguousCount++;
                    StringBuilder locations = new StringBuilder();
                    for (Matching.Hit hit : hits) {
                        if (!locations.isEmpty()) {
                            locations.append(", ");
                        }
                        locations.append(definitions.display()).append(":")
                                .append(patterns.get(hit.index()).line());
                    }
                    appendLine(out, source.display() + ":" + step.line()
                            + ": ambiguous: " + Text.plain(step.text())
                            + " matches " + locations);
                } else if (all) {
                    Matching.Hit hit = hits.get(0);
                    StepPattern pattern = patterns.get(hit.index());
                    String line = source.display() + ":" + step.line() + ": ok: "
                            + Text.plain(step.text()) + " -> " + definitions.display()
                            + ":" + pattern.line();
                    if (!hit.args().isEmpty()) {
                        List<String> shown = new ArrayList<>(hit.args().size());
                        for (String argument : hit.args()) {
                            shown.add(Text.show(argument));
                        }
                        line += " (" + String.join(", ", shown) + ")";
                    }
                    appendLine(out, line);
                }
            }
        }

        int unusedCount = 0;
        for (int index = 0; index < patterns.size(); index++) {
            if (!used[index]) {
                unusedCount++;
                StepPattern pattern = patterns.get(index);
                appendLine(out, definitions.display() + ":" + pattern.line()
                        + ": unused: " + Text.plain(pattern.text()));
            }
        }

        appendLine(out, "steps: " + stepCount + ", undefined: " + undefinedCount
                + ", ambiguous: " + ambiguousCount + ", unused patterns: " + unusedCount);
        return new Report.Output(out.toString(),
                undefinedCount == 0 && ambiguousCount == 0 ? 0 : 1);
    }

    private static List<Doc.Step> uniqueSteps(Doc.Spec spec) {
        List<Doc.Step> result = new ArrayList<>();
        Map<Integer, Set<String>> seenByLine = new HashMap<>();
        for (Units.Unit unit : Units.of(spec)) {
            for (Doc.Step step : unit.steps()) {
                Set<String> seen = seenByLine.computeIfAbsent(step.line(), ignored -> new HashSet<>());
                if (seen.add(step.text())) {
                    result.add(step);
                }
            }
        }
        result.sort((left, right) -> Integer.compare(left.line(), right.line()));
        return result;
    }

    private static void appendLine(StringBuilder out, String line) {
        out.append(Text.rtrim(line)).append('\n');
    }
}
