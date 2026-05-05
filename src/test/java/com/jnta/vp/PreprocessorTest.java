package com.jnta.vp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

class PreprocessorTest {

    @Test
    @DisplayName("Preprocessor CLI should transform gzipped JSON into a valid VP-Tree file")
    void testCli() throws IOException {
        Path input = Files.createTempFile("refs", ".json.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(input.toFile()))) {
            gzos.write("[{\"id\": 1, \"vector\": [0.1, 0.2]}]".getBytes());
        }

        Path output = Files.createTempFile("refs", ".vpt");
        Files.deleteIfExists(output);

        Preprocessor.main(new String[]{input.toString(), output.toString()});

        Assertions.assertTrue(Files.exists(output));
        Assertions.assertTrue(Files.size(output) > 0);

        Files.deleteIfExists(input);
        Files.deleteIfExists(output);
    }
}
