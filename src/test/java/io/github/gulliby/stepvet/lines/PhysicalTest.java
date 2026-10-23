package io.github.gulliby.stepvet.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicalTest {
    @Test
    void splitsEmptyTextIntoNoLines() {
        assertEquals(List.of(), Physical.split(""));
    }

    @Test
    void splitsSingleLine() {
        assertEquals(List.of("a"), Physical.split("a"));
    }

    @Test
    void doesNotAddLineAfterFinalLf() {
        assertEquals(List.of("a"), Physical.split("a\n"));
    }

    @Test
    void retainsOneEmptyPhysicalLine() {
        assertEquals(List.of(""), Physical.split("\n"));
    }

    @Test
    void retainsTwoEmptyPhysicalLines() {
        assertEquals(List.of("", ""), Physical.split("\n\n"));
    }

    @Test
    void retainsInteriorEmptyLines() {
        assertEquals(List.of("a", "", "b"), Physical.split("a\n\nb\n"));
    }

    @Test
    void splitsOnlyLfAndPreservesCarriageReturns() {
        assertEquals(List.of("a\r", "b\r"), Physical.split("a\r\nb\r"));
    }

    @Test
    void preservesOtherSeparatorsAndControlCharacters() {
        assertEquals(List.of("a\u2028b\u0085c\u000bd\fe"),
                Physical.split("a\u2028b\u0085c\u000bd\fe"));
    }

    @Test
    void emptyResultIsImmutable() {
        List<String> lines = Physical.split("");
        assertThrows(UnsupportedOperationException.class, () -> lines.add("x"));
        assertEquals(List.of(), lines);
    }

    @Test
    void nonemptyResultIsImmutable() {
        List<String> lines = Physical.split("a\n");
        assertThrows(UnsupportedOperationException.class, () -> lines.add("b"));
        assertThrows(UnsupportedOperationException.class, () -> lines.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> lines.set(0, "b"));
        assertEquals(List.of("a"), lines);
    }
}
