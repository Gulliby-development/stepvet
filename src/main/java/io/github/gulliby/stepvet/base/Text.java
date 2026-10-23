package io.github.gulliby.stepvet.base;

/** Deterministic text helpers used by stepvet's diagnostics and parsing layers. */
public final class Text {
    private Text() {
    }

    public static String plain(String text) {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (codePoint >= ' ' && codePoint <= '~') {
                result.append((char) codePoint);
            } else {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    public static String show(String text) {
        return "\"" + plain(text) + "\"";
    }

    public static String rtrim(String text) {
        int end = text.length();
        while (end > 0 && isBlank(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    public static boolean isBlank(char character) {
        return character == ' ' || character == '\t';
    }

    public static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
