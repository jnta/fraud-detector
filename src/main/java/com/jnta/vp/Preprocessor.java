package com.jnta.vp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Preprocessor {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: preprocess <input.json.gz> <output.vpt>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        List<float[]> vectors = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = new GZIPInputStream(new FileInputStream(inputPath))) {
            List<Map<String, Object>> data = mapper.readValue(is, new TypeReference<>() {});
            for (Map<String, Object> item : data) {
                List<Double> vecList = (List<Double>) item.get("vector");
                float[] vec = new float[vecList.size()];
                for (int i = 0; i < vecList.size(); i++) {
                    vec[i] = vecList.get(i).floatValue();
                }
                vectors.add(vec);
            }
        }

        VpTree tree = VpTree.build(vectors);
        tree.save(Paths.get(outputPath));
        System.out.println("Saved VP-Tree with " + vectors.size() + " vectors to " + outputPath);
    }
}
