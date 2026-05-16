package indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class IndexGeneratorTest {

    @Test
    public void testIndexGenerationEndToEnd(@TempDir Path tempDir) throws Exception {
        Path jsonPath = tempDir.resolve("test-references.json");
        Path binPath = tempDir.resolve("test-index.bin");

        String jsonContent = """
        [
            {
                "vector": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 0.0, -1, -1, 0.5],
                "label": "legit"
            },
            {
                "vector": [0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5],
                "label": "fraud"
            }
        ]
        """;

        Files.writeString(jsonPath, jsonContent);

        IndexGenerator.main(new String[]{
            jsonPath.toAbsolutePath().toString(),
            binPath.toAbsolutePath().toString(),
            "2",
            "3"
        });

        assertTrue(Files.exists(binPath));

        try (DataInputStream in = new DataInputStream(new FileInputStream(binPath.toFile()))) {
            int magic = in.readInt();
            assertEquals(0x49564631, magic);

            int clusters = in.readInt();
            assertEquals(2, clusters);

            int totalVectors = in.readInt();
            assertEquals(2, totalVectors);

            int dimensions = in.readInt();
            assertEquals(14, dimensions);

            int totalClusterSizes = 0;
            for (int c = 0; c < clusters; c++) {
                for (int d = 0; d < 14; d++) {
                    in.readFloat();
                }
                in.readLong();
                totalClusterSizes += in.readInt();
            }
            assertEquals(2, totalClusterSizes);

            byte[] vectorBytes = in.readAllBytes();
            assertEquals(2 * 15, vectorBytes.length);
        }
    }
}
