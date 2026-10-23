package io.github.gulliby.stepvet.outline;

import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Units {
    private Units() {
    }

    public record Unit(String title, int line, List<String> tags, List<Doc.Step> steps) {
        public Unit {
            tags = List.copyOf(tags);
            steps = List.copyOf(steps);
        }
    }

    public static List<Unit> of(Doc.Spec spec) {
        if (spec == null) {
            return List.of();
        }

        List<Unit> units = new ArrayList<>();
        for (Doc.Scenario scenario : spec.scenarios()) {
            if (scenario.outline()) {
                expandOutline(spec, scenario, units);
            } else {
                units.add(new Unit(
                        scenario.title(),
                        scenario.line(),
                        Tags.effective(List.of(spec.tags(), scenario.tags())),
                        steps(spec.background(), scenario.steps())));
            }
        }
        return List.copyOf(units);
    }

    private static void expandOutline(Doc.Spec spec, Doc.Scenario scenario, List<Unit> units) {
        int rowNumber = 0;
        for (Doc.Examples examples : scenario.examples()) {
            if (examples.header() == null) {
                continue;
            }

            Map<String, Integer> columns = new HashMap<>();
            for (int i = 0; i < examples.header().size(); i++) {
                columns.putIfAbsent(examples.header().get(i).text(), i);
            }
            for (Doc.Row row : examples.rows()) {
                rowNumber++;
                Map<String, String> values = new HashMap<>();
                for (Map.Entry<String, Integer> column : columns.entrySet()) {
                    values.put(column.getKey(), row.cells().get(column.getValue()).text());
                }

                String title = scenario.title().isEmpty()
                        ? "row " + rowNumber
                        : scenario.title() + " (row " + rowNumber + ")";
                List<Doc.Step> expandedSteps = steps(spec.background(), List.of());
                for (Doc.Step step : scenario.steps()) {
                    String text = Subst.apply(step.text(), values);
                    expandedSteps.add(text.equals(step.text())
                            ? step
                            : new Doc.Step(step.keyword(), text, step.line(), step.col(), step.textCol()));
                }
                units.add(new Unit(title, row.line(),
                        Tags.effective(List.of(spec.tags(), scenario.tags(), examples.tags())),
                        expandedSteps));
            }
        }
    }

    private static List<Doc.Step> steps(List<Doc.Step> background, List<Doc.Step> own) {
        List<Doc.Step> combined = new ArrayList<>();
        if (background != null) {
            combined.addAll(background);
        }
        combined.addAll(own);
        return combined;
    }
}
