package search;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

public class SearchTest {

    @Test
    public void testIndexReaderAndVectorMath() throws Exception {
        Path indexPath = Paths.get("index.bin");
        if (!Files.exists(indexPath)) {
            System.out.println("index.bin not found, skipping test");
            return;
        }

        try (IndexReader reader = new IndexReader(indexPath)) {
            assertTrue(reader.getNumClusters() > 0);
            assertTrue(reader.getTotalVectors() > 0);
            assertEquals(14, reader.getDimension());

            IndexReader.Centroid c0 = reader.getCentroid(0);
            assertNotNull(c0);
            assertEquals(0, c0.clusterId());
            assertEquals(14, c0.features().length);

            byte[] query = new byte[14];
            for (int i = 0; i < 14; i++) {
                query[i] = (byte) 128;
            }
            int closestCluster = VectorMath.findClosestCentroid(reader, query);
            assertTrue(closestCluster >= 0 && closestCluster < reader.getNumClusters());

            IndexReader.VectorEntry entry = VectorMath.findClosestVectorInCluster(reader, closestCluster, query);
            if (entry != null) {
                assertNotNull(entry.features());
                assertEquals(14, entry.features().length);
            }
        }
    }
}
