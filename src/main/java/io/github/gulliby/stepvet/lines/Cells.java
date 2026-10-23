package io.github.gulliby.stepvet.lines;

import java.util.ArrayList;
import java.util.List;

public final class Cells {
    private Cells() {
    }

    public record Part(String text, int offset) {
    }

    public static List<Part> split(String body) {
        int length = body.length();
        if (length == 0 || body.charAt(0) != '|') {
            return null;
        }

        List<Part> parts = new ArrayList<>();
        int cellStart = 1;
        int index = 1;
        while (index < length) {
            char current = body.charAt(index);
            if (current == '\\') {
                if (index + 1 < length
                        && (body.charAt(index + 1) == '|' || body.charAt(index + 1) == '\\')) {
                    index += 2;
                } else {
                    index++;
                }
            } else if (current == '|') {
                parts.add(part(body, cellStart, index));
                if (index == length - 1) {
                    return List.copyOf(parts);
                }
                cellStart = index + 1;
                index++;
            } else {
                index++;
            }
        }
        return null;
    }

    private static Part part(String body, int start, int end) {
        int first = start;
        int last = end;
        while (first < last && (body.charAt(first) == ' ' || body.charAt(first) == '\t')) {
            first++;
        }
        while (last > first && (body.charAt(last - 1) == ' ' || body.charAt(last - 1) == '\t')) {
            last--;
        }
        if (first == last) {
            return new Part("", start);
        }

        StringBuilder text = new StringBuilder(last - first);
        int index = first;
        while (index < last) {
            char current = body.charAt(index);
            if (current == '\\' && index + 1 < last
                    && (body.charAt(index + 1) == '|' || body.charAt(index + 1) == '\\')) {
                text.append(body.charAt(index + 1));
                index += 2;
            } else {
                text.append(current);
                index++;
            }
        }
        return new Part(text.toString(), first);
    }
}
