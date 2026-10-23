package io.github.gulliby.stepvet.cmd;

import io.github.gulliby.stepvet.base.Failure;
import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.base.Options;
import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.check.Report;

import java.util.List;
import java.util.Set;

/** The sole command dispatcher for stepvet. */
public final class Cli {
    private Cli() {
    }

    public static int run(List<String> argv, Inputs.Io io) {
        Inputs inputs = new Inputs(io);
        try {
            if (argv.isEmpty()) {
                throw new Failure("missing command (check, select or steps)");
            }

            if (argv.size() == 1 && argv.get(0).equals("--help")) {
                io.out("usage: stepvet check FILE...\n"
                        + "       stepvet select EXPR FILE...\n"
                        + "       stepvet steps [--all] DEFS FILE...\n");
                return 0;
            }

            String command = argv.get(0);
            if (!command.equals("check") && !command.equals("select")
                    && !command.equals("steps")) {
                throw new Failure("unknown command " + Text.show(command));
            }

            List<String> commandArgv = argv.subList(1, argv.size());
            Set<String> allowedFlags = command.equals("steps") ? Set.of("--all") : Set.of();
            Options.Parsed parsed = Options.parse(commandArgv, allowedFlags);

            Report.Output output = switch (command) {
                case "check" -> CheckCommand.run(parsed.args(), parsed.flags(), inputs);
                case "select" -> SelectCommand.run(parsed.args(), parsed.flags(), inputs);
                case "steps" -> StepsCommand.run(parsed.args(), parsed.flags(), inputs);
                default -> throw new Failure("unknown command " + Text.show(command));
            };
            io.out(output.out());
            return output.code();
        } catch (Failure failure) {
            io.err("stepvet: " + failure.getMessage() + "\n");
            return 2;
        }
    }
}
