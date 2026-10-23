package io.github.gulliby.stepvet.outline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Subst {
    private Subst() {
    }

    public record Found(String name, int offset) {
    }

    public static List<Found> find(String text) {
        List<Found> found = new ArrayList<>();
        int opening = -1;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '<') {
                opening = i;
            } else if (character == '>' && opening >= 0) {
                if (i > opening + 1) {
                    found.add(new Found(text.substring(opening + 1, i), opening));
                }
                opening = -1;
            }
        }
        return List.copyOf(found);
    }

    public static String apply(String text, Map<String, String> columns) {
        List<Found> found = find(text);
        if (found.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        int copiedThrough = 0;
        for (Found placeholder : found) {
            int start = placeholder.offset();
            int end = start + placeholder.name().length() + 2;
            result.append(text, copiedThrough, start);
            if (columns.containsKey(placeholder.name())) {
                result.append(columns.get(placeholder.name()));
            } else {
                result.append(text, start, end);
            }
            copiedThrough = end;
        }
        result.append(text, copiedThrough, text.length());
        return result.toString();
    }
}
