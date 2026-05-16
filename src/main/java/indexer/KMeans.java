package indexer;

import java.util.Arrays;
import java.util.stream.IntStream;

public class KMeans {

    public record KMeansResult(float[][] centroids, int[] vectorClusters) {}

    public static KMeansResult cluster(byte[] vectorsData, int totalVectors, int numClusters, int numIterations) {
        int actualClusters = Math.min(totalVectors, numClusters);
        float[][] centroids = new float[actualClusters][14];

        for (int i = 0; i < actualClusters; i++) {
            int vecIndex = (int) (((long) i * totalVectors) / actualClusters);
            int offset = vecIndex * 15;
            for (int d = 0; d < 14; d++) {
                centroids[i][d] = Byte.toUnsignedInt(vectorsData[offset + d]);
            }
        }

        int sampleCount = Math.min(totalVectors, 200000);
        int[] sampleIndices = new int[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            sampleIndices[i] = (int) (((long) i * totalVectors) / sampleCount);
        }

        for (int iter = 0; iter < numIterations; iter++) {
            int[] closestCentroid = new int[sampleCount];
            IntStream.range(0, sampleCount).parallel().forEach(s -> {
                int vecIndex = sampleIndices[s];
                int offset = vecIndex * 15;
                float v0 = Byte.toUnsignedInt(vectorsData[offset]);
                float v1 = Byte.toUnsignedInt(vectorsData[offset + 1]);
                float v2 = Byte.toUnsignedInt(vectorsData[offset + 2]);
                float v3 = Byte.toUnsignedInt(vectorsData[offset + 3]);
                float v4 = Byte.toUnsignedInt(vectorsData[offset + 4]);
                float v5 = Byte.toUnsignedInt(vectorsData[offset + 5]);
                float v6 = Byte.toUnsignedInt(vectorsData[offset + 6]);
                float v7 = Byte.toUnsignedInt(vectorsData[offset + 7]);
                float v8 = Byte.toUnsignedInt(vectorsData[offset + 8]);
                float v9 = Byte.toUnsignedInt(vectorsData[offset + 9]);
                float v10 = Byte.toUnsignedInt(vectorsData[offset + 10]);
                float v11 = Byte.toUnsignedInt(vectorsData[offset + 11]);
                float v12 = Byte.toUnsignedInt(vectorsData[offset + 12]);
                float v13 = Byte.toUnsignedInt(vectorsData[offset + 13]);

                float minDist = Float.MAX_VALUE;
                int bestCluster = 0;
                for (int c = 0; c < actualClusters; c++) {
                    float[] centroid = centroids[c];
                    float d0 = v0 - centroid[0];
                    float d1 = v1 - centroid[1];
                    float d2 = v2 - centroid[2];
                    float d3 = v3 - centroid[3];
                    float d4 = v4 - centroid[4];
                    float d5 = v5 - centroid[5];
                    float d6 = v6 - centroid[6];
                    float d7 = v7 - centroid[7];
                    float d8 = v8 - centroid[8];
                    float d9 = v9 - centroid[9];
                    float d10 = v10 - centroid[10];
                    float d11 = v11 - centroid[11];
                    float d12 = v12 - centroid[12];
                    float d13 = v13 - centroid[13];

                    float dist = d0 * d0 + d1 * d1 + d2 * d2 + d3 * d3 + d4 * d4 + d5 * d5 + d6 * d6 + d7 * d7 + d8 * d8 + d9 * d9 + d10 * d10 + d11 * d11 + d12 * d12 + d13 * d13;
                    if (dist < minDist) {
                        minDist = dist;
                        bestCluster = c;
                    }
                }
                closestCentroid[s] = bestCluster;
            });

            float[][] newCentroids = new float[actualClusters][14];
            int[] counts = new int[actualClusters];
            for (int s = 0; s < sampleCount; s++) {
                int c = closestCentroid[s];
                int vecIndex = sampleIndices[s];
                int offset = vecIndex * 15;
                for (int d = 0; d < 14; d++) {
                    newCentroids[c][d] += Byte.toUnsignedInt(vectorsData[offset + d]);
                }
                counts[c]++;
            }

            for (int c = 0; c < actualClusters; c++) {
                if (counts[c] > 0) {
                    for (int d = 0; d < 14; d++) {
                        centroids[c][d] = newCentroids[c][d] / counts[c];
                    }
                }
            }
        }

        int[] vectorClusters = new int[totalVectors];
        IntStream.range(0, totalVectors).parallel().forEach(vecIndex -> {
            int offset = vecIndex * 15;
            float v0 = Byte.toUnsignedInt(vectorsData[offset]);
            float v1 = Byte.toUnsignedInt(vectorsData[offset + 1]);
            float v2 = Byte.toUnsignedInt(vectorsData[offset + 2]);
            float v3 = Byte.toUnsignedInt(vectorsData[offset + 3]);
            float v4 = Byte.toUnsignedInt(vectorsData[offset + 4]);
            float v5 = Byte.toUnsignedInt(vectorsData[offset + 5]);
            float v6 = Byte.toUnsignedInt(vectorsData[offset + 6]);
            float v7 = Byte.toUnsignedInt(vectorsData[offset + 7]);
            float v8 = Byte.toUnsignedInt(vectorsData[offset + 8]);
            float v9 = Byte.toUnsignedInt(vectorsData[offset + 9]);
            float v10 = Byte.toUnsignedInt(vectorsData[offset + 10]);
            float v11 = Byte.toUnsignedInt(vectorsData[offset + 11]);
            float v12 = Byte.toUnsignedInt(vectorsData[offset + 12]);
            float v13 = Byte.toUnsignedInt(vectorsData[offset + 13]);

            float minDist = Float.MAX_VALUE;
            int bestCluster = 0;
            for (int c = 0; c < actualClusters; c++) {
                float[] centroid = centroids[c];
                float d0 = v0 - centroid[0];
                float d1 = v1 - centroid[1];
                float d2 = v2 - centroid[2];
                float d3 = v3 - centroid[3];
                float d4 = v4 - centroid[4];
                float d5 = v5 - centroid[5];
                float d6 = v6 - centroid[6];
                float d7 = v7 - centroid[7];
                float d8 = v8 - centroid[8];
                float d9 = v9 - centroid[9];
                float d10 = v10 - centroid[10];
                float d11 = v11 - centroid[11];
                float d12 = v12 - centroid[12];
                float d13 = v13 - centroid[13];

                float dist = d0 * d0 + d1 * d1 + d2 * d2 + d3 * d3 + d4 * d4 + d5 * d5 + d6 * d6 + d7 * d7 + d8 * d8 + d9 * d9 + d10 * d10 + d11 * d11 + d12 * d12 + d13 * d13;
                if (dist < minDist) {
                    minDist = dist;
                    bestCluster = c;
                }
            }
            vectorClusters[vecIndex] = bestCluster;
        });

        return new KMeansResult(centroids, vectorClusters);
    }
}
