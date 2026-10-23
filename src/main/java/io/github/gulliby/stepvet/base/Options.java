package io.github.gulliby.stepvet.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses command-line options without implicit option syntax. */
public final class Options {
    private Options() {
    }

    /**
     * Immutable snapshot of recognized flags and positional arguments.
     */
    public record Parsed(Set<String> flags, List<String> args) {
        public Parsed {
            flags = Collections.unmodifiableSet(new LinkedHashSet<>(flags));
            args = List.copyOf(args);
        }
    }

    /**
     * Parses arguments from left to right, treating only tokens beginning with
     * {@code --} as flags.
     *
     * @param argv arguments to parse
     * @param flags the explicitly allowed flag names
     * @return an immutable snapshot of recognized flags and positional arguments
     * @throws Failure if an unrecognized flag is encountered
     */
    public static Parsed parse(List<String> argv, Set<String> flags) {
        Set<String> foundFlags = new LinkedHashSet<>();
        List<String> args = new ArrayList<>();

        for (String token : argv) {
            if (token.startsWith("--")) {
                if (!flags.contains(token)) {
                    throw new Failure("unknown option " + Text.show(token));
                }
                foundFlags.add(token);
            } else {
                args.add(token);
            }
        }

        return new Parsed(foundFlags, args);
    }
}
