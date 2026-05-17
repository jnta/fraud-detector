package indexer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

public class IndexGenerator {

    static class CharReader {
        private final Reader reader;
        private final char[] buf = new char[65536];
        private int pos = 0;
        private int limit = 0;

        public CharReader(Reader reader) {
            this.reader = reader;
        }

        public int next() throws IOException {
            if (pos >= limit) {
                limit = reader.read(buf);
                pos = 0;
                if (limit <= 0) {
                    return -1;
                }
            }
            return buf[pos++];
        }
    }

    public static void main(String[] args) throws Exception {
        String inputPath = args.length > 0 ? args[0] : "resources/references.json.gz";
        String fraudOutputPath = args.length > 1 ? args[1] : "fraud.bin";
        String legitOutputPath = args.length > 2 ? args[2] : "legit.bin";
        int numClusters = args.length > 3 ? Integer.parseInt(args[3]) : 512;
        int numIterations = args.length > 4 ? Integer.parseInt(args[4]) : 5;

        InputStream fileIn = new FileInputStream(inputPath);
        if (inputPath.endsWith(".gz")) {
            fileIn = new GZIPInputStream(fileIn);
        }
        Reader reader = new InputStreamReader(fileIn, StandardCharsets.UTF_8);
        CharReader in = new CharReader(reader);

        int fraudCapacity = 2000000;
        int legitCapacity = 2000000;
        byte[] fraudVectorsData = new byte[fraudCapacity * 15];
        byte[] legitVectorsData = new byte[legitCapacity * 15];
        int totalFraudVectors = 0;
        int totalLegitVectors = 0;
        char[] tempBuf = new char[64];
        byte[] tempVector = new byte[15];

        while (true) {
            int c;
            while ((c = in.next()) != -1 && c != 'v');
            if (c == -1) break;

            if (in.next() != 'e') continue;
            if (in.next() != 'c') continue;
            if (in.next() != 't') continue;
            if (in.next() != 'o') continue;
            if (in.next() != 'r') continue;
            if (in.next() != '"') continue;
            while ((c = in.next()) != -1 && c != '[');
            if (c == -1) break;

            for (int d = 0; d < 14; d++) {
                int len = 0;
                while (true) {
                    c = in.next();
                    if (c == -1 || c == ',' || c == ']') break;
                    if (c > ' ') tempBuf[len++] = (char) c;
                }
                float val = Float.parseFloat(new String(tempBuf, 0, len));
                byte b;
                if (val < -0.5f) {
                    b = 0;
                } else {
                    float clamped = Math.max(0.0f, Math.min(1.0f, val));
                    b = (byte) Math.round(128 + clamped * 127);
                }
                tempVector[d] = b;
            }

            while ((c = in.next()) != -1 && c != 'l');
            if (c == -1) break;

            if (in.next() != 'a') continue;
            if (in.next() != 'b') continue;
            if (in.next() != 'e') continue;
            if (in.next() != 'l') continue;
            if (in.next() != '"') continue;
            while ((c = in.next()) != -1 && c != ':');
            if (c == -1) break;
            while ((c = in.next()) != -1 && c <= ' ');
            if (c == -1) break;
            if (c == '"') {
                c = in.next();
            }

            boolean isFraud = (c == 'f');
            tempVector[14] = isFraud ? (byte) 1 : (byte) 0;

            if (isFraud) {
                if (totalFraudVectors >= fraudCapacity) {
                    fraudCapacity = (int) (fraudCapacity * 1.5);
                    fraudVectorsData = Arrays.copyOf(fraudVectorsData, fraudCapacity * 15);
                }
                System.arraycopy(tempVector, 0, fraudVectorsData, totalFraudVectors * 15, 15);
                totalFraudVectors++;
            } else {
                if (totalLegitVectors >= legitCapacity) {
                    legitCapacity = (int) (legitCapacity * 1.5);
                    legitVectorsData = Arrays.copyOf(legitVectorsData, legitCapacity * 15);
                }
                System.arraycopy(tempVector, 0, legitVectorsData, totalLegitVectors * 15, 15);
                totalLegitVectors++;
            }
        }
        reader.close();

        clusterAndWrite(fraudVectorsData, totalFraudVectors, numClusters, numIterations, fraudOutputPath);
        clusterAndWrite(legitVectorsData, totalLegitVectors, numClusters, numIterations, legitOutputPath);
    }

    private static void clusterAndWrite(byte[] vectorsData, int totalVectors, int numClusters, int numIterations, String outputPath) throws Exception {
        if (totalVectors == 0) {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputPath)))) {
                out.writeInt(0x49564631);
                out.writeInt(0);
                out.writeInt(0);
                out.writeInt(14);
            }
            return;
        }

        KMeans.KMeansResult result = KMeans.cluster(vectorsData, totalVectors, numClusters, numIterations);
        float[][] centroids = result.centroids();
        int[] vectorClusters = result.vectorClusters();

        int actualClusters = centroids.length;
        int[] clusterSizes = new int[actualClusters];
        for (int i = 0; i < totalVectors; i++) {
            clusterSizes[vectorClusters[i]]++;
        }

        long currentOffset = 16 + (long) actualClusters * 68;
        long[] clusterOffsets = new long[actualClusters];
        for (int c = 0; c < actualClusters; c++) {
            clusterOffsets[c] = currentOffset;
            currentOffset += (long) clusterSizes[c] * 15;
        }

        int[][] clusterVectorIndices = new int[actualClusters][];
        for (int c = 0; c < actualClusters; c++) {
            clusterVectorIndices[c] = new int[clusterSizes[c]];
        }
        int[] clusterWritePos = new int[actualClusters];
        for (int i = 0; i < totalVectors; i++) {
            int c = vectorClusters[i];
            clusterVectorIndices[c][clusterWritePos[c]++] = i;
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outputPath)))) {
            out.writeInt(0x49564631);
            out.writeInt(actualClusters);
            out.writeInt(totalVectors);
            out.writeInt(14);

            for (int c = 0; c < actualClusters; c++) {
                for (int d = 0; d < 14; d++) {
                    out.writeFloat(centroids[c][d]);
                }
                out.writeLong(clusterOffsets[c]);
                out.writeInt(clusterSizes[c]);
            }

            for (int c = 0; c < actualClusters; c++) {
                int[] indices = clusterVectorIndices[c];
                for (int vecIndex : indices) {
                    int offset = vecIndex * 15;
                    out.write(vectorsData, offset, 15);
                }
            }
        }
    }
}
