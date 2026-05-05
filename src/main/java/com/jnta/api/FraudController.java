package com.jnta.api;

import com.jnta.vp.KnnQueue;
import com.jnta.vp.VpTreeService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Controller("/fraud-score")
public class FraudController {

    private final TransactionVectorizer vectorizer;
    private final VpTreeService treeService;

    public FraudController(TransactionVectorizer vectorizer, VpTreeService treeService) {
        this.vectorizer = vectorizer;
        this.treeService = treeService;
    }

    @Post
    public FraudResponse score(@Body TransactionPayload payload) {
        float[] query = vectorizer.vectorize(payload);
        var tree = treeService.getTree();
        
        if (tree == null) {
            return new FraudResponse(true, 0.0f);
        }

        KnnQueue queue = new KnnQueue(5);
        tree.search(query, queue);

        int fraudCount = 0;
        int[] indices = queue.getIndices();
        int found = queue.size();
        
        for (int i = 0; i < found; i++) {
            if (tree.isFraud(indices[i])) {
                fraudCount++;
            }
        }

        float score = found > 0 ? (float) fraudCount / 5.0f : 0.0f;
        boolean approved = score < 0.6f;

        return new FraudResponse(approved, score);
    }

    @Serdeable
    public record FraudResponse(boolean approved, float fraud_score) {}
}
