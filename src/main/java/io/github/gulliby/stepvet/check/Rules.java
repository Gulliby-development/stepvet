package io.github.gulliby.stepvet.check;

import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.model.Doc;
import io.github.gulliby.stepvet.model.Problem;
import io.github.gulliby.stepvet.outline.Subst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Rules {
    private Rules() {
    }

    public static List<Problem> analyze(Doc.Spec spec) {
        if (spec == null) {
            return List.of();
        }

        List<Problem> problems = new ArrayList<>();
        if (spec.background() != null) {
            analyzeSteps(spec.background(), false, problems);
        }

        Map<String, Integer> firstTitles = new HashMap<>();
        for (Doc.Scenario scenario : spec.scenarios()) {
            if (scenario.steps().isEmpty()) {
                problems.add(new Problem(scenario.line(), scenario.col(),
                        "SV101", "scenario has no steps"));
            }

            if (!scenario.title().isEmpty()) {
                Integer firstLine = firstTitles.putIfAbsent(scenario.title(), scenario.line());
                if (firstLine != null) {
                    problems.add(new Problem(scenario.line(), scenario.col(), "SV102",
                            "duplicate scenario name " + Text.show(scenario.title())
                                    + " (first at line " + firstLine + ")"));
                }
            }

            if (!scenario.steps().isEmpty() && !hasThen(scenario.steps())) {
                problems.add(new Problem(scenario.line(), scenario.col(),
                        "SV104", "scenario has no Then step"));
            }

            if (scenario.outline() && scenario.examples().isEmpty()) {
                problems.add(new Problem(scenario.line(), scenario.col(),
                        "SV106", "Scenario Outline: has no Examples:"));
            }

            analyzeSteps(scenario.steps(), true, problems);
            if (scenario.outline()) {
                analyzeOutline(scenario, problems);
            }
        }
        return List.copyOf(problems);
    }

    private static boolean hasThen(List<Doc.Step> steps) {
        int previous = -1;
        for (Doc.Step step : steps) {
            int explicit = explicitKind(step.keyword());
            int effective = explicit >= 0 ? explicit : previous;
            if (effective == 2) {
                return true;
            }
            if (explicit >= 0) {
                previous = explicit;
            }
        }
        return false;
    }

    private static void analyzeSteps(List<Doc.Step> steps, boolean checkOrder,
                                     List<Problem> problems) {
        int previous = -1;
        int highest = -1;
        for (int i = 0; i < steps.size(); i++) {
            Doc.Step step = steps.get(i);
            String keyword = step.keyword();
            int explicit = explicitKind(keyword);
            if (i == 0 && isInherited(keyword)) {
                problems.add(new Problem(step.line(), step.col(), "SV103",
                        "first step starts with " + keyword));
            }
            if (checkOrder && explicit >= 0 && explicit < highest) {
                problems.add(new Problem(step.line(), step.col(), "SV105",
                        keyword + " step after a " + kindName(highest) + " step"));
            }
            int effective = explicit >= 0 ? explicit : isInherited(keyword) ? previous : -1;
            previous = effective;
            if (explicit >= 0) {
                highest = Math.max(highest, explicit);
            }
        }
    }

    private static int explicitKind(String keyword) {
        return switch (keyword) {
            case "Given" -> 0;
            case "When" -> 1;
            case "Then" -> 2;
            default -> -1;
        };
    }

    private static boolean isInherited(String keyword) {
        return keyword.equals("And") || keyword.equals("But") || keyword.equals("*");
    }

    private static String kindName(int kind) {
        return switch (kind) {
            case 0 -> "Given";
            case 1 -> "When";
            default -> "Then";
        };
    }

    private static void analyzeOutline(Doc.Scenario scenario, List<Problem> problems) {
        Set<String> usedNames = new HashSet<>();
        for (Doc.Step step : scenario.steps()) {
            for (Subst.Found placeholder : Subst.find(step.text())) {
                usedNames.add(placeholder.name());
                for (Doc.Examples examples : scenario.examples()) {
                    if (examples.header() != null && !hasColumn(examples.header(), placeholder.name())) {
                        problems.add(new Problem(step.line(), step.textCol() + placeholder.offset(),
                                "SV107", "placeholder <" + Text.plain(placeholder.name())
                                        + "> has no column in the Examples: at line "
                                        + examples.line()));
                    }
                }
            }
        }

        for (Doc.Examples examples : scenario.examples()) {
            if (examples.rows().isEmpty()) {
                problems.add(new Problem(examples.line(), examples.col(),
                        "SV109", "Examples: has no rows"));
            }
            if (examples.header() != null) {
                for (Doc.Cell cell : examples.header()) {
                    if (!usedNames.contains(cell.text())) {
                        problems.add(new Problem(examples.headerLine(), cell.col(), "SV108",
                                "column " + Text.show(cell.text()) + " is not used by any step"));
                    }
                }
            }
        }
    }

    private static boolean hasColumn(List<Doc.Cell> header, String name) {
        for (Doc.Cell cell : header) {
            if (cell.text().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
