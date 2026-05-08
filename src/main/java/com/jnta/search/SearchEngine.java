package com.jnta.search;

/**
 * Core interface for similarity search engines.
 */
public interface SearchEngine extends AutoCloseable {
    /**
     * Finds the nearest neighbors for a given query vector.
     */
    void search(float[] query, KnnQueue queue, long deadlineNanos);

    /**
     * Returns the number of vectors in the engine.
     */
    int size();

    /**
     * Returns true if the vector at the given index is marked as fraud.
     */
    boolean isFraud(int index);

    /**
     * Closes the engine and releases any associated resources.
     */
    @Override
    void close();
}
