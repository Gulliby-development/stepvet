package io.github.gulliby.stepvet.base;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;

/** Reads named inputs through an injected I/O boundary. */
public final class Inputs {
    private final Io io;
    private boolean stdinConsumed;

    public Inputs(Io io) {
        this.io = io;
    }

    public Source read(String arg) {
        String display;
        byte[] bytes;
        if (arg.equals("-")) {
            display = "<stdin>";
            if (stdinConsumed) {
                throw new Failure("standard input is named twice");
            }
            stdinConsumed = true;
            try {
                bytes = io.readStdin();
            } catch (IOException exception) {
                throw cannotRead(display);
            }
        } else {
            display = Text.plain(arg);
            try {
                bytes = io.readFile(arg);
            } catch (IOException | InvalidPathException exception) {
                throw cannotRead(display);
            }
        }

        String decoded;
        try {
            CharBuffer characters = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            decoded = characters.toString();
        } catch (CharacterCodingException exception) {
            throw new Failure(display + ": input is not valid UTF-8");
        }

        if (!decoded.isEmpty() && decoded.charAt(0) == '\uFEFF') {
            decoded = decoded.substring(1);
        }
        return new Source(display, normalizeNewlines(decoded, display));
    }

    private static Failure cannotRead(String display) {
        return new Failure(display + ": cannot read the file");
    }

    private static String normalizeNewlines(String text, String display) {
        StringBuilder normalized = new StringBuilder(text.length());
        int line = 1;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\r') {
                if (index + 1 >= text.length() || text.charAt(index + 1) != '\n') {
                    throw new Failure(display + ": bare carriage return at line " + line);
                }
                normalized.append('\n');
                line++;
                index++;
            } else {
                normalized.append(character);
                if (character == '\n') {
                    line++;
                }
            }
        }
        return normalized.toString();
    }

    public interface Io {
        byte[] readStdin() throws IOException;

        byte[] readFile(String path) throws IOException;

        void out(String text);

        void err(String text);
    }

    public record Source(String display, String text) {
    }
}
