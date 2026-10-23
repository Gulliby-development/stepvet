package io.github.gulliby.stepvet.parse;

import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.lines.Cells;
import io.github.gulliby.stepvet.lines.Classify;
import io.github.gulliby.stepvet.lines.Physical;
import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;

import java.util.ArrayList;
import java.util.List;

/** Assembles a parsed specification and its syntax diagnostics. */
public final class Parser {
    private Parser() {
    }

    public record Result(Doc.Spec spec, List<Problem> problems) {
        public Result {
            problems = List.copyOf(problems);
        }
    }

    public static Result parse(String text) {
        List<Problem> problems = new ArrayList<>();
        List<TagLine.Tag> pendingTags = new ArrayList<>();
        FeatureBuilder feature = null;
        BackgroundBuilder background = null;
        List<ScenarioBuilder> scenarios = new ArrayList<>();
        ScenarioBuilder currentScenario = null;
        ExamplesBuilder currentExamples = null;
        boolean descriptionAllowed = false;
        boolean firstContent = true;
        int lineNumber = 0;

        for (String line : Physical.split(text)) {
            lineNumber++;
            Classify.Item item = Classify.classify(line);
            if (item.kind() == Classify.Kind.BLANK || item.kind() == Classify.Kind.COMMENT) {
                continue;
            }
            if (item.kind() == Classify.Kind.TAGS) {
                TagLine.Result result = TagLine.read(item.body(), lineNumber, item.indent() + 1);
                problems.addAll(result.problems());
                pendingTags.addAll(result.tags());
                continue;
            }

            if (firstContent) {
                firstContent = false;
                if (item.kind() != Classify.Kind.KEYWORD || !item.keyword().equals("Feature:")) {
                    problems.add(problem(lineNumber, item.indent() + 1, "SV001",
                            "expected \"Feature:\" first"));
                    return new Result(null, Problem.sorted(problems));
                }
            }

            if (item.kind() == Classify.Kind.KEYWORD) {
                String keyword = item.keyword();
                if (!canAccept(keyword, feature, background, scenarios, currentScenario)) {
                    pendingTags.clear();
                    problems.add(problem(lineNumber, item.indent() + 1, "SV007",
                            keyword + " is not allowed here"));
                    descriptionAllowed = false;
                    continue;
                }

                List<String> tags;
                if (takesTags(keyword)) {
                    tags = takePendingTags(pendingTags);
                } else {
                    reportOrphanTags(pendingTags, problems);
                    tags = List.of();
                }

                switch (keyword) {
                    case "Feature:" -> feature = new FeatureBuilder(item.title(), lineNumber,
                            item.indent() + 1, tags);
                    case "Background:" -> background = new BackgroundBuilder();
                    case "Scenario:" -> {
                        currentScenario = new ScenarioBuilder(false, item.title(), lineNumber,
                                item.indent() + 1, tags);
                        scenarios.add(currentScenario);
                        currentExamples = null;
                    }
                    case "Scenario Outline:" -> {
                        currentScenario = new ScenarioBuilder(true, item.title(), lineNumber,
                                item.indent() + 1, tags);
                        scenarios.add(currentScenario);
                        currentExamples = null;
                    }
                    case "Examples:" -> {
                        currentExamples = new ExamplesBuilder(lineNumber, item.indent() + 1,
                                item.title(), tags);
                        currentScenario.examples.add(currentExamples);
                    }
                    default -> throw new IllegalStateException("unrecognized classified keyword");
                }
                descriptionAllowed = true;
                continue;
            }

            reportOrphanTags(pendingTags, problems);
            if (item.kind() == Classify.Kind.STEP) {
                boolean placed = currentExamples == null
                        && (currentScenario != null || (background != null && scenarios.isEmpty()));
                if (!placed) {
                    problems.add(problem(lineNumber, item.indent() + 1, "SV007",
                            "step is not allowed here"));
                    continue;
                }
                descriptionAllowed = false;
                if (item.title().isEmpty()) {
                    problems.add(problem(lineNumber, item.indent() + 1, "SV005", "step without text"));
                    continue;
                }
                Doc.Step step = new Doc.Step(item.keyword(), item.title(), lineNumber,
                        item.indent() + 1, item.textStart() + 1);
                if (currentScenario != null) {
                    currentScenario.steps.add(step);
                } else {
                    background.steps.add(step);
                }
            } else if (item.kind() == Classify.Kind.ROW) {
                if (currentExamples == null) {
                    problems.add(problem(lineNumber, item.indent() + 1, "SV007",
                            "table is not allowed here"));
                    continue;
                }
                descriptionAllowed = false;
                List<Cells.Part> parts = Cells.split(item.body());
                if (parts == null) {
                    problems.add(problem(lineNumber, item.indent() + 1, "SV006",
                            "unterminated table row"));
                    continue;
                }
                List<Doc.Cell> cells = cells(parts, item.indent());
                if (currentExamples.header == null) {
                    currentExamples.headerLine = lineNumber;
                    currentExamples.header = cells;
                } else if (cells.size() != currentExamples.header.size()) {
                    problems.add(problem(lineNumber, item.indent() + 1, "SV008",
                            "row cell count " + cells.size() + ", expected "
                                    + currentExamples.header.size()));
                } else {
                    currentExamples.rows.add(new Doc.Row(lineNumber, cells));
                }
            } else if (item.kind() == Classify.Kind.TEXT && !descriptionAllowed) {
                problems.add(problem(lineNumber, item.indent() + 1, "SV004",
                        "unexpected line " + Text.show(item.body())));
            }
        }

        reportOrphanTags(pendingTags, problems);
        Doc.Spec spec = feature == null ? null : feature.freeze(background, scenarios);
        return new Result(spec, Problem.sorted(problems));
    }

    private static boolean canAccept(String keyword, FeatureBuilder feature,
                                     BackgroundBuilder background, List<ScenarioBuilder> scenarios,
                                     ScenarioBuilder currentScenario) {
        return switch (keyword) {
            case "Feature:" -> feature == null;
            case "Background:" -> feature != null && background == null && scenarios.isEmpty();
            case "Scenario:", "Scenario Outline:" -> feature != null;
            case "Examples:" -> currentScenario != null && currentScenario.outline;
            default -> false;
        };
    }

    private static boolean takesTags(String keyword) {
        return keyword.equals("Feature:") || keyword.equals("Scenario:")
                || keyword.equals("Scenario Outline:") || keyword.equals("Examples:");
    }

    private static List<String> takePendingTags(List<TagLine.Tag> pendingTags) {
        List<String> tags = new ArrayList<>(pendingTags.size());
        for (TagLine.Tag tag : pendingTags) {
            tags.add(tag.text());
        }
        pendingTags.clear();
        return List.copyOf(tags);
    }

    private static void reportOrphanTags(List<TagLine.Tag> pendingTags, List<Problem> problems) {
        if (!pendingTags.isEmpty()) {
            TagLine.Tag first = pendingTags.get(0);
            problems.add(problem(first.line(), first.col(), "SV003",
                    "tags must stand before Feature:, Scenario:, Scenario Outline: or Examples:"));
            pendingTags.clear();
        }
    }

    private static List<Doc.Cell> cells(List<Cells.Part> parts, int indent) {
        List<Doc.Cell> cells = new ArrayList<>(parts.size());
        for (Cells.Part part : parts) {
            cells.add(new Doc.Cell(part.text(), indent + part.offset() + 1));
        }
        return List.copyOf(cells);
    }

    private static Problem problem(int line, int col, String code, String text) {
        return new Problem(line, col, code, text);
    }

    private static final class FeatureBuilder {
        private final String title;
        private final int line;
        private final int col;
        private final List<String> tags;

        private FeatureBuilder(String title, int line, int col, List<String> tags) {
            this.title = title;
            this.line = line;
            this.col = col;
            this.tags = List.copyOf(tags);
        }

        private Doc.Spec freeze(BackgroundBuilder background, List<ScenarioBuilder> scenarios) {
            List<Doc.Scenario> frozen = new ArrayList<>(scenarios.size());
            for (ScenarioBuilder scenario : scenarios) {
                frozen.add(scenario.freeze());
            }
            return new Doc.Spec(title, line, tags, background == null ? null : background.steps,
                    frozen);
        }
    }

    private static final class BackgroundBuilder {
        private final List<Doc.Step> steps = new ArrayList<>();
    }

    private static final class ScenarioBuilder {
        private final boolean outline;
        private final String title;
        private final int line;
        private final int col;
        private final List<String> tags;
        private final List<Doc.Step> steps = new ArrayList<>();
        private final List<ExamplesBuilder> examples = new ArrayList<>();

        private ScenarioBuilder(boolean outline, String title, int line, int col, List<String> tags) {
            this.outline = outline;
            this.title = title;
            this.line = line;
            this.col = col;
            this.tags = List.copyOf(tags);
        }

        private Doc.Scenario freeze() {
            List<Doc.Examples> frozen = new ArrayList<>(examples.size());
            for (ExamplesBuilder example : examples) {
                frozen.add(example.freeze());
            }
            return new Doc.Scenario(outline, title, line, col, tags, steps, frozen);
        }
    }

    private static final class ExamplesBuilder {
        private final int line;
        private final int col;
        private final String title;
        private final List<String> tags;
        private int headerLine;
        private List<Doc.Cell> header;
        private final List<Doc.Row> rows = new ArrayList<>();

        private ExamplesBuilder(int line, int col, String title, List<String> tags) {
            this.line = line;
            this.col = col;
            this.title = title;
            this.tags = List.copyOf(tags);
        }

        private Doc.Examples freeze() {
            return new Doc.Examples(line, col, title, tags, headerLine, header, rows);
        }
    }
}
