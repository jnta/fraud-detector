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
        List<Boolean> labelsList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = new GZIPInputStream(new FileInputStream(inputPath))) {
            List<Map<String, Object>> data = mapper.readValue(is, new TypeReference<>() {});
            for (Map<String, Object> item : data) {
                List<Number> vecList = (List<Number>) item.get("vector");
                if (vecList == null) continue;
                float[] vec = new float[vecList.size()];
                for (int i = 0; i < vecList.size(); i++) {
                    vec[i] = vecList.get(i).floatValue();
                }
                vectors.add(vec);
                
                Object fraudObj = item.get("fraud");
                Object labelObj = item.get("label");
                boolean isFraud = false;
                if (fraudObj instanceof Boolean) {
                    isFraud = (Boolean) fraudObj;
                } else if ("fraud".equals(labelObj)) {
                    isFraud = true;
                }
                labelsList.add(isFraud);
            }
        }

        boolean[] labels = new boolean[labelsList.size()];
        for (int i = 0; i < labelsList.size(); i++) labels[i] = labelsList.get(i);

        VpTree tree = VpTree.build(vectors, labels);
        tree.save(Paths.get(outputPath));
        System.out.println("Saved VP-Tree with " + vectors.size() + " vectors to " + outputPath);
    }
}
