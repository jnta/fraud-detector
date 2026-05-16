package search;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class VectorMath {

    private static final VectorSpecies<Float> FLOAT_SPECIES = FloatVector.SPECIES_256;
    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_64;
    private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_256;

    /**
     * Finds the closest centroid to the given query features using FloatVector SIMD acceleration.
     */
    public static int findClosestCentroid(IndexReader reader, byte[] queryFeatures) {
        int dimension = reader.getDimension();
        float[] qf = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            qf[i] = Byte.toUnsignedInt(queryFeatures[i]);
        }

        IndexReader.Centroid[] centroids = reader.getCentroids();
        float minDist = Float.MAX_VALUE;
        int bestCluster = 0;

        for (int c = 0; c < centroids.length; c++) {
            float[] cf = centroids[c].features();
            
            FloatVector vq = FloatVector.fromArray(FLOAT_SPECIES, qf, 0);
            FloatVector vc = FloatVector.fromArray(FLOAT_SPECIES, cf, 0);
            FloatVector diff = vq.sub(vc);
            FloatVector sq = diff.mul(diff);
            float dist = sq.reduceLanes(VectorOperators.ADD);

            for (int d = FLOAT_SPECIES.length(); d < dimension; d++) {
                float delta = qf[d] - cf[d];
                dist += delta * delta;
            }

            if (dist < minDist) {
                minDist = dist;
                bestCluster = c;
            }
        }

        return bestCluster;
    }

    /**
     * Finds the closest vector entry within a specific cluster using ByteVector/IntVector SIMD acceleration.
     */
    public static IndexReader.VectorEntry findClosestVectorInCluster(IndexReader reader, int clusterId, byte[] queryFeatures) {
        int dimension = reader.getDimension();
        int count = reader.getCentroid(clusterId).count();
        if (count == 0) {
            return null;
        }

        IntVector vq = ((IntVector) ByteVector.fromArray(BYTE_SPECIES, queryFeatures, 0)
                                 .castShape(INT_SPECIES, 0))
                                 .and(0xFF);

        int minSqDist = Integer.MAX_VALUE;
        IndexReader.VectorEntry bestEntry = null;

        for (int i = 0; i < count; i++) {
            IndexReader.VectorEntry entry = reader.getVector(clusterId, i);
            byte[] tf = entry.features();

            IntVector vt = ((IntVector) ByteVector.fromArray(BYTE_SPECIES, tf, 0)
                                     .castShape(INT_SPECIES, 0))
                                     .and(0xFF);

            IntVector diff = vq.sub(vt);
            IntVector sq = diff.mul(diff);
            int sqDist = sq.reduceLanes(VectorOperators.ADD);

            for (int d = BYTE_SPECIES.length(); d < dimension; d++) {
                int delta = (queryFeatures[d] & 0xFF) - (tf[d] & 0xFF);
                sqDist += delta * delta;
            }

            if (sqDist < minSqDist) {
                minSqDist = sqDist;
                bestEntry = entry;
            }
        }

        return bestEntry;
    }
}
