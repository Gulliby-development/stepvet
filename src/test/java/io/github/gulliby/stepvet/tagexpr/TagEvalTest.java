package io.github.gulliby.stepvet.tagexpr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TagEvalTest {
    @Test
    void usesExactCaseSensitiveTagMembership() {
        assertTrue(TagExpr.parse("@a").matches(Set.of("@a")));
        assertFalse(TagExpr.parse("@a").matches(Set.of()));
        assertFalse(TagExpr.parse("@a").matches(Set.of("@ab", "@A", "a")));
    }

    @Test
    void evaluatesNegationAndRepeatedNegation() {
        assertTrue(TagExpr.parse("not @a").matches(Set.of()));
        assertTrue(TagExpr.parse("  not   not @a ").matches(Set.of("@a")));
        assertFalse(TagExpr.parse("not @a and @b").matches(Set.of()));
    }

    @Test
    void evaluatesConjunctionAndDisjunction() {
        assertFalse(TagExpr.parse("@a and @b").matches(Set.of("@a")));
        assertTrue(TagExpr.parse("@a and @b").matches(Set.of("@a", "@b")));
        assertTrue(TagExpr.parse("@a or @b").matches(Set.of("@b")));
    }

    @Test
    void appliesPrecedenceAndParentheses() {
        assertTrue(TagExpr.parse("@a or @b and @c").matches(Set.of("@a")));
        assertFalse(TagExpr.parse("(@a or @b) and @c").matches(Set.of("@a")));
        assertFalse(TagExpr.parse("not @a and @b").matches(Set.of()));
        assertTrue(TagExpr.parse("\t(@a)and(not @b) ").matches(Set.of("@a")));
    }

    @Test
    void evaluatesSelectionExpressions() {
        assertTrue(TagExpr.parse("@smoke and not @eu").matches(Set.of("@shop", "@smoke")));
        assertTrue(TagExpr.parse("@eu or @smoke and not @slow")
                .matches(Set.of("@shop", "@slow", "@smoke", "@eu")));
        assertTrue(TagExpr.parse("not @eu and @slow")
                .matches(Set.of("@shop", "@slow", "@smoke")));
        assertTrue(TagExpr.parse("not (@slow or @eu)").matches(Set.of("@shop", "@smoke")));
    }

    @Test
    void acceptsPunctuationAndSupplementaryCharactersInTags() {
        assertTrue(TagExpr.parse("@a-b").matches(Set.of("@a-b")));
        assertTrue(TagExpr.parse("@x😀").matches(Set.of("@x😀")));
    }

    @Test
    void treatsOnlySpaceAndTabAsSeparators() {
        assertTrue(TagExpr.parse("@a\u00a0b").matches(Set.of("@a\u00a0b")));
        assertTrue(TagExpr.parse("@a\nb").matches(Set.of("@a\nb")));
        assertTrue(TagExpr.parse("@a\u0085b").matches(Set.of("@a\u0085b")));
        assertTrue(TagExpr.parse("@a\ufeffb").matches(Set.of("@a\ufeffb")));
        assertTrue(TagExpr.parse("@a\u2028b").matches(Set.of("@a\u2028b")));
    }

    @Test
    void leavesSuppliedTagsUnchangedAndCanBeReused() {
        TagExpr expression = TagExpr.parse("@a and not @b");
        Set<String> firstTags = new LinkedHashSet<>(Set.of("@a", "@outside"));
        Set<String> secondTags = new LinkedHashSet<>(Set.of("@a", "@b"));
        assertTrue(expression.matches(firstTags));
        assertEquals(Set.of("@a", "@outside"), firstTags);
        assertFalse(expression.matches(secondTags));
        assertEquals(Set.of("@a", "@b"), secondTags);
    }

    @Test
    void acceptsExpressionsAtTheAsciiAndSupplementaryUtf16Limit() {
        String asciiBoundary = "@"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaa";
        assertTrue(TagExpr.parse(asciiBoundary).matches(Set.of(asciiBoundary)));

        String supplementaryBoundary = "@"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀" + "a";
        assertTrue(TagExpr.parse(supplementaryBoundary).matches(Set.of(supplementaryBoundary)));
    }

    @Test
    void evaluatesDeepParenthesesAndRepeatedNotWithoutRecursion() {
        String nested = "((((((((((((((" + "((((((((((((((" + "(((((((((((((("
                + "((((((((((((((" + "((((((((((((((" + "(((((((((((((("
                + "@a"
                + "))))))))))))))" + "))))))))))))))" + "))))))))))))))"
                + "))))))))))))))" + "))))))))))))))" + "))))))))))))))";
        assertTrue(TagExpr.parse(nested).matches(Set.of("@a")));
        assertFalse(TagExpr.parse(nested).matches(Set.of()));

        String repeatedNot = "not not not not not not not not not not "
                + "not not not not not not not not not not "
                + "not not not not not not not not not not @a";
        assertTrue(TagExpr.parse(repeatedNot).matches(Set.of("@a")));
        assertFalse(TagExpr.parse(repeatedNot).matches(Set.of()));
    }
}
