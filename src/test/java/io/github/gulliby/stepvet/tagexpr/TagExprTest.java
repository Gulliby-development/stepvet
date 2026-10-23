package io.github.gulliby.stepvet.tagexpr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import io.github.gulliby.stepvet.base.Failure;
import org.junit.jupiter.api.Test;

class TagExprTest {
    @Test
    void reportsMissingAtomsAndTopLevelAdjacency() {
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("")).getMessage());
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 7
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a and")).getMessage());
        assertEquals("""
                invalid tag expression: expected "and", "or" or the end at column 4
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a @b")).getMessage());
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 5
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("not or @a")).getMessage());
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse(")")).getMessage());
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 2
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("()")).getMessage());
    }

    @Test
    void reportsGroupClosureAndUnmatchedClosingDiagnostics() {
        assertEquals("""
                invalid tag expression: expected ")" at column 10
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("(@a or @b")).getMessage());
        assertEquals("""
                invalid tag expression: expected "and", "or" or ")" at column 5
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("(@a @b)")).getMessage());
        assertEquals("""
                invalid tag expression: unmatched ")" at column 3
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a)")).getMessage());
    }

    @Test
    void reportsInvalidWordsWhenEncountered() {
        assertEquals("""
                invalid tag expression: invalid word "foo" at column 8
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a and foo")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "@b@" at column 8
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a and @b@")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "foo" at column 4
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a foo")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "foo" at column 5
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("(@a foo)")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "?" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("é")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "?" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("😀")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "@" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "@@" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@@")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "@a@b" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a@b")).getMessage());
        assertEquals("""
                invalid tag expression: invalid word "AND" at column 1
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("AND")).getMessage());
    }

    @Test
    void preservesEarlierSyntaxErrorsBeforeLaterWords() {
        assertEquals("""
                invalid tag expression: expected "and", "or" or the end at column 4
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a @b foo")).getMessage());
        assertEquals("""
                invalid tag expression: expected "and", "or" or ")" at column 5
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("(@a @b foo)")).getMessage());
        assertEquals("""
                invalid tag expression: unmatched ")" at column 3
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a) foo")).getMessage());
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 8
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a and or foo")).getMessage());
    }

    @Test
    void countsColumnsInUtf16Units() {
        assertEquals("""
                invalid tag expression: invalid word "x" at column 10
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a😀 and x")).getMessage());
    }

    @Test
    void reportsMissingOperandsAtEofAfterTrailingBlanks() {
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 9
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("@a and \t")).getMessage());
        assertEquals("""
                invalid tag expression: expected a tag, "not" or "(" at column 10
                """.stripTrailing(),
                assertThrows(Failure.class, () -> TagExpr.parse("(@a and \t")).getMessage());
    }

    @Test
    void enforcesAsciiLengthBoundaryAndChecksLengthFirst() {
        String accepted = "@"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaa";
        assertTrue(TagExpr.parse(accepted).matches(Set.of(accepted)));

        String overlong = "@"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa";
        assertEquals("invalid tag expression: longer than 200 characters",
                assertThrows(Failure.class, () -> TagExpr.parse(overlong)).getMessage());

        String malformed = "@@"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaaa"
                + "aaaaaaaaaaaaaaaaaaaa" + "aaaaaaaaaaaaaaaaaaa";
        assertEquals("invalid tag expression: longer than 200 characters",
                assertThrows(Failure.class, () -> TagExpr.parse(malformed)).getMessage());
    }

    @Test
    void countsSupplementaryCharactersAsTwoUnitsAtTheBoundary() {
        String accepted = "@"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀" + "aaa";
        assertTrue(TagExpr.parse(accepted).matches(Set.of(accepted)));

        String overlong = "@"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀"
                + "😀😀😀😀😀😀😀😀😀😀" + "😀😀😀😀😀😀😀😀😀😀";
        assertEquals("invalid tag expression: longer than 200 characters",
                assertThrows(Failure.class, () -> TagExpr.parse(overlong)).getMessage());
    }

    @Test
    void malformedDeepNestingAndRepeatedNotThrowFailure() {
        String nested = "((((((((((" + "((((((((((" + "(((((((((("
                + "((((((((((" + "((((((((((" + "(((((((((("
                + "((((((((((" + "((((((((((" + "(((((((((("
                + "(((((((((" + "@a";
        assertEquals("invalid tag expression: expected \")\" at column 102",
                assertThrows(Failure.class, () -> TagExpr.parse(nested)).getMessage());

        String repeatedNot = "not not not not not " + "not not not not not "
                + "not not not not not " + "not not not not not "
                + "not not not not not " + "not not not not not "
                + "not not not not not " + "not not not not not "
                + "not not not not not " + "not not not not not ";
        assertEquals("invalid tag expression: expected a tag, \"not\" or \"(\" at column 201",
                assertThrows(Failure.class, () -> TagExpr.parse(repeatedNot)).getMessage());
    }
}
