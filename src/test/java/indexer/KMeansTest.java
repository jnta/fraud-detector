package indexer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class KMeansTest {

    @Test
    public void testKMeansClustering() {
        int totalVectors = 4;
        byte[] vectorsData = new byte[totalVectors * 15];

        for (int i = 0; i < 14; i++) {
            vectorsData[0 * 15 + i] = 0;
            vectorsData[1 * 15 + i] = 0;
            vectorsData[2 * 15 + i] = (byte) 255;
            vectorsData[3 * 15 + i] = (byte) 255;
        }

        vectorsData[0 * 15 + 14] = 0;
        vectorsData[1 * 15 + 14] = 0;
        vectorsData[2 * 15 + 14] = 1;
        vectorsData[3 * 15 + 14] = 1;

        KMeans.KMeansResult result = KMeans.cluster(vectorsData, totalVectors, 2, 5);

        assertNotNull(result);
        assertEquals(2, result.centroids().length);
        assertEquals(4, result.vectorClusters().length);
        assertEquals(14, result.centroids()[0].length);

        int cluster0 = result.vectorClusters()[0];
        int cluster1 = result.vectorClusters()[1];
        int cluster2 = result.vectorClusters()[2];
        int cluster3 = result.vectorClusters()[3];

        assertEquals(cluster0, cluster1);
        assertEquals(cluster2, cluster3);
        assertNotEquals(cluster0, cluster2);
    }
}
