package search;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class IndexReader implements AutoCloseable {

    public static record Centroid(int clusterId, float[] features, long offset, int count) {}
    public static record VectorEntry(byte[] features, boolean isFraud) {}

    private static final ValueLayout.OfInt INT_LAYOUT = ValueLayout.JAVA_INT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat FLOAT_LAYOUT = ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong LONG_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(java.nio.ByteOrder.BIG_ENDIAN);

    private final FileChannel channel;
    private final Arena arena;
    private final MemorySegment mappedSegment;
    private final int numClusters;
    private final int totalVectors;
    private final int dimension;
    private final Centroid[] centroids;

    private byte[] preloadedData;

    public IndexReader(Path indexPath) throws IOException {
        this.channel = FileChannel.open(indexPath, StandardOpenOption.READ);
        this.arena = Arena.ofShared();
        this.mappedSegment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(), arena);
        
        int magic = mappedSegment.get(INT_LAYOUT, 0);
        if (magic != 0x49564631) {
            throw new IOException("Invalid index file magic: " + Integer.toHexString(magic));
        }
        this.numClusters = mappedSegment.get(INT_LAYOUT, 4);
        this.totalVectors = mappedSegment.get(INT_LAYOUT, 8);
        this.dimension = mappedSegment.get(INT_LAYOUT, 12);
        
        this.centroids = new Centroid[numClusters];
        long pos = 16;
        for (int c = 0; c < numClusters; c++) {
            float[] features = new float[dimension];
            for (int d = 0; d < dimension; d++) {
                features[d] = mappedSegment.get(FLOAT_LAYOUT, pos);
                pos += 4;
            }
            long offset = mappedSegment.get(LONG_LAYOUT, pos);
            pos += 8;
            int count = mappedSegment.get(INT_LAYOUT, pos);
            pos += 4;
            centroids[c] = new Centroid(c, features, offset, count);
        }
    }

    public void preloadIntoMemory() {
        if (mappedSegment != null) {
            this.preloadedData = mappedSegment.toArray(ValueLayout.JAVA_BYTE);
        }
    }

    public byte[] getPreloadedData() {
        return preloadedData;
    }

    public int getNumClusters() {
        return numClusters;
    }

    public int getTotalVectors() {
        return totalVectors;
    }

    public int getDimension() {
        return dimension;
    }

    public Centroid[] getCentroids() {
        return centroids;
    }

    public Centroid getCentroid(int clusterId) {
        return centroids[clusterId];
    }

    public MemorySegment getMappedSegment() {
        return mappedSegment;
    }

    /**
     * Returns a slice of the MemorySegment containing the vectors for a given cluster.
     */
    public MemorySegment getClusterVectorsSegment(int clusterId) {
        Centroid centroid = centroids[clusterId];
        return mappedSegment.asSlice(centroid.offset(), (long) centroid.count() * (dimension + 1));
    }

    /**
     * Reads a specific vector entry from a cluster.
     */
    public VectorEntry getVector(int clusterId, int indexInCluster) {
        Centroid centroid = centroids[clusterId];
        if (indexInCluster < 0 || indexInCluster >= centroid.count()) {
            throw new IndexOutOfBoundsException("Index " + indexInCluster + " out of bounds for cluster size " + centroid.count());
        }
        long vectorOffset = centroid.offset() + (long) indexInCluster * (dimension + 1);
        byte[] features = new byte[dimension];
        if (preloadedData != null) {
            System.arraycopy(preloadedData, (int) vectorOffset, features, 0, dimension);
            byte fraudByte = preloadedData[(int) vectorOffset + dimension];
            return new VectorEntry(features, fraudByte == 1);
        } else {
            features = mappedSegment.asSlice(vectorOffset, dimension).toArray(ValueLayout.JAVA_BYTE);
            byte fraudByte = mappedSegment.get(ValueLayout.JAVA_BYTE, vectorOffset + dimension);
            return new VectorEntry(features, fraudByte == 1);
        }
    }

    @Override
    public void close() throws IOException {
        if (arena != null) {
            arena.close();
        }
        if (channel != null) {
            channel.close();
        }
    }
}
