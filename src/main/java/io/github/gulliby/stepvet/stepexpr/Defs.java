package io.github.gulliby.stepvet.stepexpr;

import io.github.gulliby.stepvet.base.Text;

import java.util.ArrayList;
import java.util.List;

/** Reads ordered step definitions from LF-delimited text. */
public final class Defs {
    private Defs() {
    }

    public static List<StepPattern> parse(String text, String display) {
        ArrayList<StepPattern> patterns = new ArrayList<>();
        int line = 1;
        int start = 0;

        while (start < text.length()) {
            int end = start;
            while (end < text.length() && text.charAt(end) != '\n') {
                end++;
            }

            int first = start;
            while (first < end && Text.isBlank(text.charAt(first))) {
                first++;
            }
            int last = end;
            while (last > first && Text.isBlank(text.charAt(last - 1))) {
                last--;
            }

            if (first < last && text.charAt(first) != '#') {
                patterns.add(StepPattern.parse(text.substring(first, last), line, first - start + 1, display));
            }

            if (end == text.length()) {
                break;
            }
            start = end + 1;
            line++;
        }
        return List.copyOf(patterns);
    }
}
