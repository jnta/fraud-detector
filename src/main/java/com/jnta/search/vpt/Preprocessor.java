package com.jnta.search.vpt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Preprocessor {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: preprocess <input.json.gz> <output.bin>");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args[1];

        List<float[]> vectors = new ArrayList<>();
        List<Boolean> labelsList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = new GZIPInputStream(new FileInputStream(inputPath))) {
            com.fasterxml.jackson.core.JsonParser parser = mapper.getFactory().createParser(is);
            if (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
                throw new IOException("Expected start of array");
            }

            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                Map<String, Object> item = mapper.readValue(parser, new TypeReference<Map<String, Object>>() {});
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

        int n = vectors.size();
        if (n == 0) {
            System.err.println("No vectors found.");
            return;
        }

        int dims = vectors.get(0).length;
        if (dims != 14) {
            System.err.println("Warning: expected 14 dimensions but found " + dims);
        }

        try (FileChannel channel = FileChannel.open(Paths.get(outputPath), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Write each dimension separately
            for (int d = 0; d < dims; d++) {
                int bytesForDim = n * 4;
                ByteBuffer buf = ByteBuffer.allocate(bytesForDim).order(ByteOrder.nativeOrder());
                for (int i = 0; i < n; i++) {
                    buf.putFloat(vectors.get(i)[d]);
                }
                buf.flip();
                while (buf.hasRemaining()) {
                    channel.write(buf);
                }

                // Padding to 64 bytes
                int padding = (64 - (bytesForDim % 64)) % 64;
                if (padding > 0) {
                    channel.write(ByteBuffer.allocate(padding));
                }
            }

            // Write fraud flags
            ByteBuffer fraudBuf = ByteBuffer.allocate(n);
            for (int i = 0; i < n; i++) {
                fraudBuf.put(labelsList.get(i) ? (byte) 1 : (byte) 0);
            }
            fraudBuf.flip();
            while (fraudBuf.hasRemaining()) {
                channel.write(fraudBuf);
            }
        }

        System.out.println("Saved Transposed Binary file with " + n + " vectors to " + outputPath);
    }
}
