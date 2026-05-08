package com.jnta.api;

import com.jnta.search.KnnQueue;
import com.jnta.search.SearchService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;

@Controller("/fraud-score")
public class FraudController {

    private final TransactionVectorizer vectorizer;
    private final SearchService searchService;

    @io.micronaut.context.annotation.Value("${search.timeout-ms:400}")
    int timeoutMs;

    public FraudController(TransactionVectorizer vectorizer, SearchService searchService) {
        this.vectorizer = vectorizer;
        this.searchService = searchService;
    }

    @Post
    public FraudResponse score(@Body TransactionRequest request) {
        float[] query = vectorizer.vectorize(request);
        var engine = searchService.getEngine();
        
        if (engine == null) {
            return new FraudResponse(true, 0.0f);
        }

        long deadlineNanos = System.nanoTime() + (timeoutMs * 1_000_000L);

        KnnQueue queue = new KnnQueue(5);
        engine.search(query, queue, deadlineNanos);

        int fraudCount = 0;
        int[] indices = queue.getIndices();
        int found = queue.size();
        
        for (int i = 0; i < found; i++) {
            if (engine.isFraud(indices[i])) {
                fraudCount++;
            }
        }

        float score = (float) fraudCount / 5.0f;
        boolean approved = score < 0.6f;

        return new FraudResponse(approved, score);
    }

    @Serdeable
    public record FraudResponse(boolean approved, float fraud_score) {}
}
