package io.github.gulliby.stepvet.stepexpr;

import java.util.ArrayList;
import java.util.List;

/** Collects every matching definition in source order. */
public final class Matching {
    private Matching() {
    }

    public record Hit(int index, List<String> args) {
        public Hit {
            args = List.copyOf(args);
        }
    }

    public static List<Hit> of(List<StepPattern> patterns, String stepText) {
        ArrayList<Hit> hits = new ArrayList<>();
        for (int index = 0; index < patterns.size(); index++) {
            List<String> args = patterns.get(index).match(stepText);
            if (args != null) {
                hits.add(new Hit(index, args));
            }
        }
        return List.copyOf(hits);
    }
}
