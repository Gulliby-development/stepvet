package io.github.gulliby.stepvet.lines;

public final class Classify {
    private Classify() {
    }

    public record Item(Kind kind, int indent, String keyword, String title, int textStart, String body) {
    }

    public enum Kind {
        BLANK, COMMENT, TAGS, KEYWORD, ROW, STEP, TEXT
    }

    public static Item classify(String line) {
        int indent = 0;
        while (indent < line.length()
                && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t')) {
            indent++;
        }

        int end = line.length();
        while (end > indent && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        String body = line.substring(indent, end);

        if (body.isEmpty()) {
            return new Item(Kind.BLANK, indent, "", "", indent, body);
        }
        if (body.startsWith("#")) {
            return new Item(Kind.COMMENT, indent, "", "", indent, body);
        }
        if (body.startsWith("@")) {
            return new Item(Kind.TAGS, indent, "", "", indent, body);
        }

        String keyword = null;
        if (body.startsWith("Feature:")) {
            keyword = "Feature:";
        } else if (body.startsWith("Background:")) {
            keyword = "Background:";
        } else if (body.startsWith("Scenario Outline:")) {
            keyword = "Scenario Outline:";
        } else if (body.startsWith("Scenario:")) {
            keyword = "Scenario:";
        } else if (body.startsWith("Examples:")) {
            keyword = "Examples:";
        }
        if (keyword != null) {
            int titleStart = keyword.length();
            while (titleStart < body.length()
                    && (body.charAt(titleStart) == ' ' || body.charAt(titleStart) == '\t')) {
                titleStart++;
            }
            return new Item(Kind.KEYWORD, indent, keyword, body.substring(titleStart), indent + titleStart, body);
        }
        if (body.startsWith("|")) {
            return new Item(Kind.ROW, indent, "", "", indent, body);
        }

        int wordEnd = 0;
        while (wordEnd < body.length() && body.charAt(wordEnd) != ' ' && body.charAt(wordEnd) != '\t') {
            wordEnd++;
        }
        String word = body.substring(0, wordEnd);
        if (word.equals("Given") || word.equals("When") || word.equals("Then")
                || word.equals("And") || word.equals("But") || word.equals("*")) {
            int textStart = wordEnd;
            while (textStart < body.length() && (body.charAt(textStart) == ' ' || body.charAt(textStart) == '\t')) {
                textStart++;
            }
            return new Item(Kind.STEP, indent, word, body.substring(textStart), indent + textStart, body);
        }

        return new Item(Kind.TEXT, indent, "", "", indent, body);
    }
}
