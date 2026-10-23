package io.github.gulliby.stepvet.model;

import java.util.List;

public final class Doc {
    private Doc() {
    }

    public record Step(String keyword, String text, int line, int col, int textCol) {
    }

    public record Cell(String text, int col) {
    }

    public record Row(int line, List<Cell> cells) {
        public Row {
            cells = List.copyOf(cells);
        }
    }

    public record Examples(int line, int col, String title, List<String> tags,
                           int headerLine, List<Cell> header, List<Row> rows) {
        public Examples {
            tags = List.copyOf(tags);
            header = header == null ? null : List.copyOf(header);
            rows = List.copyOf(rows);
        }
    }

    public record Scenario(boolean outline, String title, int line, int col, List<String> tags,
                           List<Step> steps, List<Examples> examples) {
        public Scenario {
            tags = List.copyOf(tags);
            steps = List.copyOf(steps);
            examples = List.copyOf(examples);
        }
    }

    public record Spec(String title, int line, List<String> tags, List<Step> background,
                       List<Scenario> scenarios) {
        public Spec {
            tags = List.copyOf(tags);
            background = background == null ? null : List.copyOf(background);
            scenarios = List.copyOf(scenarios);
        }
    }
}
