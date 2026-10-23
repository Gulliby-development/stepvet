package io.github.gulliby.stepvet.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record Problem(int line, int col, String code, String text) {
    public static List<Problem> sorted(List<Problem> found) {
        List<Problem> result = new ArrayList<>(found);
        result.sort(Comparator.comparingInt(Problem::line)
                .thenComparingInt(Problem::col)
                .thenComparing(Problem::code));
        return List.copyOf(result);
    }

    public String format(String display) {
        return display + ":" + line + ":" + col + ": " + code + " " + text;
    }
}
