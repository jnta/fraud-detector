package search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class VectorMathTest {

    @Test
    public void testEarlyExit(@TempDir Path tempDir) throws Exception {
        Path indexPath = tempDir.resolve("early_exit.bin");
        int numVectors = 10;
        int dimension = 14;

        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(indexPath))) {
            dos.writeInt(0x49564631); // magic
            dos.writeInt(1); // numClusters
            dos.writeInt(numVectors); // totalVectors
            dos.writeInt(dimension); // dimension

            // Centroid 0
            for (int d = 0; d < dimension; d++) dos.writeFloat(0.0f);
            dos.writeLong(84); // offset of vectors
            dos.writeInt(numVectors); // count

            // Vector 0: sqDist = 80^2 = 6400
            byte[] v0 = new byte[14]; v0[0] = 80;
            dos.write(v0); dos.writeByte(1);

            // Vector 1: sqDist = 81^2 = 6561
            byte[] v1 = new byte[14]; v1[0] = 81;
            dos.write(v1); dos.writeByte(1);

            // Vector 2: sqDist = 82^2 = 6724
            byte[] v2 = new byte[14]; v2[0] = 82;
            dos.write(v2); dos.writeByte(0);

            // Vector 3: sqDist = 83^2 = 6889
            byte[] v3 = new byte[14]; v3[0] = 83;
            dos.write(v3); dos.writeByte(0);

            // Vector 4: sqDist = 84^2 = 7056
            byte[] v4 = new byte[14]; v4[0] = 84;
            dos.write(v4); dos.writeByte(0);

            // Vector 5: sqDist = 10^2 = 100 (Should be skipped by early exit!)
            byte[] v5 = new byte[14]; v5[0] = 10;
            dos.write(v5); dos.writeByte(1);

            // Vectors 6-9: sqDist = 90^2 = 8100
            for (int i = 6; i < 10; i++) {
                byte[] vi = new byte[14]; vi[0] = 90;
                dos.write(vi); dos.writeByte(0);
            }
        }

        try (IndexReader reader = new IndexReader(indexPath)) {
            byte[] query = new byte[14]; // all 0s
            VectorMath.SearchResult result = VectorMath.findClosestVectorInCluster(reader, 0, query);

            assertNotNull(result.entry());
            // Since early exit triggers after K=5 vectors (Vectors 0-4), Vector 5 is never scanned.
            // The minimum among scanned vectors (0-4) is Vector 0 with sqDist = 6400.
            assertEquals(6400, result.distance(), "Distance should match min sqDist of scanned vectors before early exit");
            assertEquals(80, result.entry().features()[0]);
        }
    }

    @Test
    public void testFullScanMatchesMinimum(@TempDir Path tempDir) throws Exception {
        Path indexPath = tempDir.resolve("full_scan.bin");
        int numVectors = 10;
        int dimension = 14;

        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(indexPath))) {
            dos.writeInt(0x49564631); // magic
            dos.writeInt(1); // numClusters
            dos.writeInt(numVectors); // totalVectors
            dos.writeInt(dimension); // dimension

            // Centroid 0
            for (int d = 0; d < dimension; d++) dos.writeFloat(0.0f);
            dos.writeLong(84); // offset of vectors
            dos.writeInt(numVectors); // count

            // Vectors 0-8: sqDist = 100^2 = 10000 (> 8000, so early exit never triggers)
            for (int i = 0; i < 9; i++) {
                byte[] vi = new byte[14]; vi[0] = 100;
                dos.write(vi); dos.writeByte(0);
            }

            // Vector 9: sqDist = 50^2 = 2500 (Minimum in the cluster)
            byte[] v9 = new byte[14]; v9[0] = 50;
            dos.write(v9); dos.writeByte(1);
        }

        try (IndexReader reader = new IndexReader(indexPath)) {
            byte[] query = new byte[14]; // all 0s
            VectorMath.SearchResult result = VectorMath.findClosestVectorInCluster(reader, 0, query);

            assertNotNull(result.entry());
            assertEquals(2500, result.distance(), "Distance should match the actual minimum squared distance over all scanned vectors");
            assertTrue(result.entry().isFraud());
        }
    }
}
