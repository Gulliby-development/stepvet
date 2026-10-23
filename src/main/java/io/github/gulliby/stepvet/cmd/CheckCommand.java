package io.github.gulliby.stepvet.cmd;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.check.Report;
import io.github.gulliby.stepvet.parse.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Orchestrates reading, parsing, and reporting for the check command. */
public final class CheckCommand {
    private CheckCommand() {
    }

    public static Report.Output run(List<String> args, Set<String> flags, Inputs inputs) {
        if (args.isEmpty()) {
            throw new Failure("missing FILE");
        }

        List<Report.Input> files = new ArrayList<>(args.size());
        for (String arg : args) {
            Inputs.Source source = inputs.read(arg);
            Parser.Result result = Parser.parse(source.text());
            files.add(new Report.Input(source.display(), result.spec(), result.problems()));
        }
        return Report.check(files);
    }
}
