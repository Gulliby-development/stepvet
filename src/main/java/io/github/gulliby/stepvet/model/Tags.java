package io.github.gulliby.stepvet.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Tags {
    private Tags() {
    }

    public static List<String> effective(List<List<String>> lists) {
        Set<String> tags = new LinkedHashSet<>();
        for (List<String> list : lists) {
            tags.addAll(list);
        }
        return List.copyOf(tags);
    }
}
