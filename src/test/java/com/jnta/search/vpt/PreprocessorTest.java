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
    @DisplayName("Should find global min and max across all vectors")
    void testFindGlobalBounds() {
        List<float[]> vectors = List.of(
            new float[]{0.5f, -1.0f, 2.0f},
            new float[]{3.0f, 0.0f, -5.0f},
            new float[]{1.5f, 4.0f, 1.0f}
        );
        
        float[] bounds = Preprocessor.findGlobalBounds(vectors);
        
        Assertions.assertEquals(-5.0f, bounds[0], "Global min mismatch");
        Assertions.assertEquals(4.0f, bounds[1], "Global max mismatch");
    }

    @Test
    @DisplayName("Should quantize floats to 16-bit shorts correctly")
    void testQuantizationMapping16Bit() {
        float min = -10.0f;
        float max = 10.0f;
        
        Assertions.assertEquals((short) -32768, Preprocessor.quantize16Bit(new float[]{-10.0f}, min, max)[0]);
        Assertions.assertEquals((short) 32767, Preprocessor.quantize16Bit(new float[]{10.0f}, min, max)[0]);
        
        short mid = Preprocessor.quantize16Bit(new float[]{0.0f}, min, max)[0];
        Assertions.assertTrue(Math.abs(mid) <= 1);
    }

    @Test
    @DisplayName("Preprocessor CLI should transform gzipped JSON into a valid VP-Tree file")
    void testCli() throws IOException {
        Path input = Files.createTempFile("refs", ".json.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(input.toFile()))) {
            gzos.write("[{\"id\": 1, \"vector\": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7]}]".getBytes());
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
