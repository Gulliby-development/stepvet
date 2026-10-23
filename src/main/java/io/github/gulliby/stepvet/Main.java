package io.github.gulliby.stepvet;

import io.github.gulliby.stepvet.base.Inputs;
import io.github.gulliby.stepvet.cmd.Cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Real process entry point for stepvet. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Inputs.Io io = new Inputs.Io() {
            @Override
            public byte[] readStdin() throws IOException {
                return System.in.readAllBytes();
            }

            @Override
            public byte[] readFile(String path) throws IOException {
                try {
                    return Files.readAllBytes(Path.of(path));
                } catch (SecurityException exception) {
                    throw new IOException(exception);
                }
            }

            @Override
            public void out(String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                System.out.write(bytes, 0, bytes.length);
                System.out.flush();
            }

            @Override
            public void err(String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                System.err.write(bytes, 0, bytes.length);
                System.err.flush();
            }
        };

        System.exit(Cli.run(Arrays.asList(args), io));
    }
}
