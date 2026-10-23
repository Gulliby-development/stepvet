package io.github.gulliby.stepvet.parse;

import io.github.gulliby.stepvet.base.Text;
import io.github.gulliby.stepvet.model.Problem;

import java.util.ArrayList;
import java.util.List;

/** Reads the tokens and diagnostics from one tags line. */
public final class TagLine {
    private TagLine() {
    }

    public record Tag(String text, int line, int col) {
    }

    public record Result(List<Tag> tags, List<Problem> problems) {
        public Result {
            tags = List.copyOf(tags);
            problems = List.copyOf(problems);
        }
    }

    public static Result read(String body, int line, int startCol) {
        List<Tag> tags = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        int index = 0;
        while (index < body.length()) {
            while (index < body.length() && Text.isBlank(body.charAt(index))) {
                index++;
            }
            if (index == body.length()) {
                break;
            }

            int start = index;
            while (index < body.length() && !Text.isBlank(body.charAt(index))) {
                index++;
            }
            String token = body.substring(start, index);
            if (token.charAt(0) == '#') {
                break;
            }
            int col = startCol + start;
            if (valid(token)) {
                tags.add(new Tag(token, line, col));
            } else {
                problems.add(new Problem(line, col, "SV002",
                        "invalid tag " + Text.show(token)));
            }
        }
        return new Result(tags, problems);
    }

    private static boolean valid(String token) {
        if (token.length() < 2 || token.charAt(0) != '@') {
            return false;
        }
        for (int index = 1; index < token.length(); index++) {
            if (token.charAt(index) == '@') {
                return false;
            }
        }
        return true;
    }
}
