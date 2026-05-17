package indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import search.IndexReader;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class IndexGeneratorTest {

    @Test
    public void testIndexGenerationEndToEnd(@TempDir Path tempDir) throws Exception {
        Path jsonPath = tempDir.resolve("test-references.json");
        Path fraudBinPath = tempDir.resolve("test-fraud.bin");
        Path legitBinPath = tempDir.resolve("test-legit.bin");

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
            fraudBinPath.toAbsolutePath().toString(),
            legitBinPath.toAbsolutePath().toString(),
            "1",
            "3"
        });

        assertTrue(Files.exists(fraudBinPath));
        assertTrue(Files.exists(legitBinPath));

        try (IndexReader fraudReader = new IndexReader(fraudBinPath)) {
            assertEquals(1, fraudReader.getTotalVectors());
            assertEquals(1, fraudReader.getNumClusters());
            IndexReader.VectorEntry entry = fraudReader.getVector(0, 0);
            assertTrue(entry.isFraud());
        }

        try (IndexReader legitReader = new IndexReader(legitBinPath)) {
            assertEquals(1, legitReader.getTotalVectors());
            assertEquals(1, legitReader.getNumClusters());
            IndexReader.VectorEntry entry = legitReader.getVector(0, 0);
            assertFalse(entry.isFraud());
        }
    }
}
