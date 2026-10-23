package io.github.gulliby.stepvet.lines;

import java.util.ArrayList;
import java.util.List;

public final class Physical {
    private Physical() {
    }

    public static List<String> split(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return List.copyOf(lines);
    }
}
