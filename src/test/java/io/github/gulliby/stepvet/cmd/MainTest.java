package io.github.gulliby.stepvet.cmd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void rejectsMalformedUtf8ReceivedFromTheRealStdinChannel() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toRealPath();
        Process process = new ProcessBuilder(java.toString(), "-cp", "target/classes",
                "io.github.gulliby.stepvet.Main", "check", "-")
                .directory(workingDirectory.toFile())
                .start();
        try {
            process.getOutputStream().write(new byte[] {(byte) 0x61, (byte) 0xFF, (byte) 0x0A});
            process.getOutputStream().close();

            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "stepvet process should exit");
            assertEquals(2, process.exitValue());
            assertArrayEquals(new byte[0], process.getInputStream().readAllBytes());
            assertArrayEquals("stepvet: <stdin>: input is not valid UTF-8\n"
                    .getBytes(StandardCharsets.UTF_8), process.getErrorStream().readAllBytes());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }
            process.getOutputStream().close();
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    @Test
    void readmeContainsTheFourRequiredHeadings() throws IOException {
        String readme = java.nio.file.Files.readString(
                Path.of(System.getProperty("user.dir"), "README.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertTrue(readme.contains("\n## Install\n"));
        assertTrue(readme.contains("\n## Usage\n"));
        assertTrue(readme.contains("\n## Step patterns\n"));
        assertTrue(readme.contains("\n## Development\n"));
    }
}
