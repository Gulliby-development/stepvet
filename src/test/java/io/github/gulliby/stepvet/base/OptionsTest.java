package io.github.gulliby.stepvet.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class OptionsTest {
    @Test
    void parsesEmptyInput() {
        Options.Parsed parsed = Options.parse(List.of(), Set.of());

        assertEquals(Set.of(), parsed.flags());
        assertEquals(List.of(), parsed.args());
    }

    @Test
    void parsesInterspersedRepeatedFlagsOnce() {
        Options.Parsed parsed = Options.parse(
                List.of("d", "--all", "f", "--all"), Set.of("--all"));

        assertEquals(Set.of("--all"), parsed.flags());
        assertEquals(List.of("d", "f"), parsed.args());
    }

    @Test
    void preservesEveryPositionalArgumentInOrder() {
        Options.Parsed parsed = Options.parse(
                List.of("-", "-5", "", "f", "f"), Set.of());

        assertEquals(Set.of(), parsed.flags());
        assertEquals(List.of("-", "-5", "", "f", "f"), parsed.args());
    }

    @Test
    void acceptsDoubleDashTokensOnlyWhenExplicitlyAllowed() {
        Options.Parsed parsed = Options.parse(
                List.of("--", "--help", "--all=1"),
                Set.of("--", "--help", "--all=1"));

        assertEquals(Set.of("--", "--help", "--all=1"), parsed.flags());
        assertEquals(List.of(), parsed.args());
    }

    @Test
    void rejectsBareDoubleDash() {
        Failure failure = assertThrows(Failure.class,
                () -> Options.parse(List.of("--"), Set.of()));

        assertEquals("unknown option \"--\"", failure.getMessage());
    }

    @Test
    void doesNotTreatHelpAsAnImplicitOption() {
        Failure failure = assertThrows(Failure.class,
                () -> Options.parse(List.of("--help"), Set.of()));

        assertEquals("unknown option \"--help\"", failure.getMessage());
    }

    @Test
    void doesNotParseFlagAssignments() {
        Failure failure = assertThrows(Failure.class,
                () -> Options.parse(List.of("--all=1"), Set.of("--all")));

        assertEquals("unknown option \"--all=1\"", failure.getMessage());
    }

    @Test
    void reportsTheFirstUnknownOption() {
        Failure failure = assertThrows(Failure.class,
                () -> Options.parse(List.of("--bad", "--worse"), Set.of()));

        assertEquals("unknown option \"--bad\"", failure.getMessage());
    }

    @Test
    void sanitizesUnicodeInUnknownOptionMessages() {
        Failure failure = assertThrows(Failure.class,
                () -> Options.parse(List.of("--é😀"), Set.of()));

        assertEquals("unknown option \"--??\"", failure.getMessage());
    }

    @Test
    void parseReturnsImmutableSnapshotsAndPreservesDuplicates() {
        List<String> argv = new ArrayList<>(List.of("x", "--all", "x"));
        Set<String> allowed = new LinkedHashSet<>(Set.of("--all"));
        Options.Parsed parsed = Options.parse(argv, allowed);

        argv.add("later");
        allowed.clear();

        assertEquals(Set.of("--all"), parsed.flags());
        assertEquals(List.of("x", "x"), parsed.args());
        assertThrows(UnsupportedOperationException.class,
                () -> parsed.flags().add("--other"));
        assertThrows(UnsupportedOperationException.class,
                () -> parsed.args().add("later"));
    }

    @Test
    void directParsedConstructionAlsoSnapshotsAndFreezesCollections() {
        Set<String> flags = new LinkedHashSet<>(Set.of("--all"));
        List<String> args = new ArrayList<>(List.of("a", "a"));
        Options.Parsed parsed = new Options.Parsed(flags, args);

        flags.clear();
        args.add("b");

        assertEquals(Set.of("--all"), parsed.flags());
        assertEquals(List.of("a", "a"), parsed.args());
        assertThrows(UnsupportedOperationException.class,
                () -> parsed.flags().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> parsed.args().clear());
    }
}
