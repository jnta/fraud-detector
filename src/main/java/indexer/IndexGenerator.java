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
        String outputPath = args.length > 1 ? args[1] : "index.bin";
        int numClusters = args.length > 2 ? Integer.parseInt(args[2]) : 10000;
        int numIterations = args.length > 3 ? Integer.parseInt(args[3]) : 5;

        InputStream fileIn = new FileInputStream(inputPath);
        if (inputPath.endsWith(".gz")) {
            fileIn = new GZIPInputStream(fileIn);
        }
        Reader reader = new InputStreamReader(fileIn, StandardCharsets.UTF_8);
        CharReader in = new CharReader(reader);

        int initialCapacity = 3000000;
        byte[] vectorsData = new byte[initialCapacity * 15];
        int totalVectors = 0;
        char[] tempBuf = new char[64];

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

            if (totalVectors >= initialCapacity) {
                initialCapacity = (int) (initialCapacity * 1.5);
                vectorsData = Arrays.copyOf(vectorsData, initialCapacity * 15);
            }

            int offset = totalVectors * 15;

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
                vectorsData[offset + d] = b;
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
            vectorsData[offset + 14] = isFraud ? (byte) 1 : (byte) 0;

            totalVectors++;
        }
        reader.close();

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
