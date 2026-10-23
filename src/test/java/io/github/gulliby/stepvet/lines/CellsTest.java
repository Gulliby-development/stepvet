package io.github.gulliby.stepvet.lines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CellsTest {
    @Test
    void emptyInputIsNotARow() {
        assertNull(Cells.split(""));
    }

    @Test
    void missingOpeningBarIsNotARow() {
        assertNull(Cells.split("a|"));
    }

    @Test
    void openingBarAloneIsNotTerminated() {
        assertNull(Cells.split("|"));
    }

    @Test
    void missingClosingBarIsNotTerminated() {
        assertNull(Cells.split("| a"));
    }

    @Test
    void escapedClosingBarDoesNotTerminateTheRow() {
        assertNull(Cells.split("| a \\|"));
    }

    @Test
    void adjacentBarsGiveAnImmutableEmptyCell() {
        List<Cells.Part> parts = Cells.split("||");
        List<Cells.Part> expected = List.of(new Cells.Part("", 1));

        assertEquals(expected, parts);
        assertThrows(UnsupportedOperationException.class, () -> parts.add(new Cells.Part("x", 1)));
        assertThrows(UnsupportedOperationException.class, () -> parts.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> parts.set(0, new Cells.Part("x", 1)));
        assertEquals(expected, parts);
    }

    @Test
    void blankOnlyCellsAreTrimmedWithTheirSourceOffsets() {
        assertEquals(
                List.of(new Cells.Part("", 1), new Cells.Part("x", 6)),
                Cells.split("|  \t| x |"));
    }

    @Test
    void escapedInternalBarIsDecoded() {
        assertEquals(List.of(new Cells.Part("a|b", 1)), Cells.split("|a\\|b|"));
    }

    @Test
    void pairedBackslashBeforeClosingBarDecodesToOneBackslash() {
        assertEquals(List.of(new Cells.Part("a\\", 1)), Cells.split("|a\\\\|"));
    }

    @Test
    void unknownEscapeIsRetained() {
        assertEquals(List.of(new Cells.Part("\\q", 1)), Cells.split("|\\q|"));
    }

    @Test
    void offsetsTrackSourceCharactersAcrossCells() {
        assertEquals(
                List.of(
                        new Cells.Part("z", 2),
                        new Cells.Part("y", 6),
                        new Cells.Part("", 9),
                        new Cells.Part("é😀w", 13)),
                Cells.split("| z |\ty |  | é😀w |"));
    }

    @Test
    void followingCellOffsetCountsAstralTextAsTwoUtf16Units() {
        assertEquals(
                List.of(new Cells.Part("é😀", 1), new Cells.Part("x", 6)),
                Cells.split("|é😀| x|"));
    }

    @Test
    void onlySpaceAndTabAreTrimmed() {
        assertEquals(
                List.of(new Cells.Part("\u00a0\u0085\ufeff", 2)),
                Cells.split("| \u00a0\u0085\ufeff |"));
    }

    @Test
    void delimiterEscapingFollowsLeftToRightBackslashPairConsumption() {
        assertEquals(
                List.of(new Cells.Part("a\\", 1), new Cells.Part("b", 5)),
                Cells.split("|a\\\\|b|"));
        assertEquals(
                List.of(new Cells.Part("a\\|b", 1)),
                Cells.split("|a\\\\\\|b|"));
    }
}
