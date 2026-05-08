package com.jnta.search.vpt;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

class PreprocessorTest {

    @Test
    @DisplayName("Preprocessor CLI should transform gzipped JSON into an aligned memory-mapped binary file")
    void testCli() throws IOException {
        Path input = Files.createTempFile("refs", ".json.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(input.toFile()))) {
            gzos.write("[{\"id\": 1, \"fraud\": true, \"vector\": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4]}, {\"id\": 2, \"fraud\": false, \"vector\": [1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2, 2.3, 2.4]}]".getBytes());
        }

        Path output = Files.createTempFile("refs", ".bin");
        Files.deleteIfExists(output);

        Preprocessor.main(new String[]{input.toString(), output.toString()});

        Assertions.assertTrue(Files.exists(output));
        long fileSize = Files.size(output);
        Assertions.assertTrue(fileSize > 0);

        int N = 2; // Two vectors
        int dims = 14;
        
        // Check 64-byte alignment offsets for each dimension
        long expectedDataSize = 0;
        for (int i = 0; i < dims; i++) {
            long dimSize = N * 4L; // N floats
            long padding = (64 - (dimSize % 64)) % 64;
            expectedDataSize += dimSize + padding;
        }

        // The file should have the dimensions + N bytes for the fraud flags + any padding? 
        // No need for padding after fraud flags unless required, but let's assume it's just appended
        long expectedFileSize = expectedDataSize + N;
        Assertions.assertEquals(expectedFileSize, fileSize, "File size mismatch");

        Files.deleteIfExists(input);
        Files.deleteIfExists(output);
    }
}
