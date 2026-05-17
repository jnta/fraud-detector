package api;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import search.IndexReader;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class FraudHandlerTest {
    private static int port;
    private static IndexReader originalFraudReader;
    private static IndexReader originalLegitReader;

    @BeforeAll
    public static void setUp() throws Exception {
        originalFraudReader = FraudHandler.fraudReader;
        originalLegitReader = FraudHandler.legitReader;
        Class<?> serverClass = Class.forName("Server");
        serverClass.getMethod("startServer", int.class).invoke(null, 0);
        Object serverObj = serverClass.getField("server").get(null);
        port = ((com.sun.net.httpserver.HttpServer) serverObj).getAddress().getPort();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        Class<?> serverClass = Class.forName("Server");
        serverClass.getMethod("stopServer").invoke(null);
        FraudHandler.fraudReader = originalFraudReader;
        FraudHandler.legitReader = originalLegitReader;
    }

    private static void createIndexFile(Path path, byte featureValue, boolean isFraud) throws Exception {
        int numVectors = 1;
        int dimension = 14;
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(path))) {
            dos.writeInt(0x49564631); // magic
            dos.writeInt(1); // numClusters
            dos.writeInt(numVectors); // totalVectors
            dos.writeInt(dimension); // dimension

            // Centroid 0
            for (int d = 0; d < dimension; d++) dos.writeFloat(0.0f);
            dos.writeLong(84); // offset of vectors
            dos.writeInt(numVectors); // count

            // Vector 0
            byte[] v = new byte[dimension];
            java.util.Arrays.fill(v, featureValue);
            dos.write(v);
            dos.writeByte(isFraud ? 1 : 0);
        }
    }

    @Test
    public void testDistFraudLessThanDistLegit(@TempDir Path tempDir) throws Exception {
        Path fraudPath = tempDir.resolve("fraud.bin");
        Path legitPath = tempDir.resolve("legit.bin");

        // Make fraud vector close to query (value 128), legit vector far (value 255)
        createIndexFile(fraudPath, (byte) 128, true);
        createIndexFile(legitPath, (byte) 255, false);

        FraudHandler.fraudReader = new IndexReader(fraudPath);
        FraudHandler.fraudReader.preloadIntoMemory();
        FraudHandler.legitReader = new IndexReader(legitPath);
        FraudHandler.legitReader.preloadIntoMemory();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/fraud"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"transaction\":{\"amount\":100.0}}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"approved\": false"), "Response should be approved=false when distFraud < distLegit");
    }

    @Test
    public void testDistLegitLessThanDistFraud(@TempDir Path tempDir) throws Exception {
        Path fraudPath = tempDir.resolve("fraud.bin");
        Path legitPath = tempDir.resolve("legit.bin");

        // Make legit vector close to query (value 128), fraud vector far (value 255)
        createIndexFile(fraudPath, (byte) 255, true);
        createIndexFile(legitPath, (byte) 128, false);

        FraudHandler.fraudReader = new IndexReader(fraudPath);
        FraudHandler.fraudReader.preloadIntoMemory();
        FraudHandler.legitReader = new IndexReader(legitPath);
        FraudHandler.legitReader.preloadIntoMemory();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/fraud"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"transaction\":{\"amount\":100.0}}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"approved\": true"), "Response should be approved=true when distLegit < distFraud");
    }

    @Test
    public void testDistancesEqual(@TempDir Path tempDir) throws Exception {
        Path fraudPath = tempDir.resolve("fraud.bin");
        Path legitPath = tempDir.resolve("legit.bin");

        // Make both vectors identical distance to query (value 128)
        createIndexFile(fraudPath, (byte) 128, true);
        createIndexFile(legitPath, (byte) 128, false);

        FraudHandler.fraudReader = new IndexReader(fraudPath);
        FraudHandler.fraudReader.preloadIntoMemory();
        FraudHandler.legitReader = new IndexReader(legitPath);
        FraudHandler.legitReader.preloadIntoMemory();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/fraud"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"transaction\":{\"amount\":100.0}}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"approved\": true"), "Response should be approved=true when distances are equal");
    }

    @Test
    public void testSemaphoreConcurrencyLimit() throws Exception {
        assertEquals(2, FraudHandler.computeSemaphore.availablePermits(), "Semaphore should have 2 permits initially");
        FraudHandler.computeSemaphore.acquire();
        assertEquals(1, FraudHandler.computeSemaphore.availablePermits(), "Semaphore should have 1 permit after 1 acquire");
        FraudHandler.computeSemaphore.acquire();
        assertEquals(0, FraudHandler.computeSemaphore.availablePermits(), "Semaphore should have 0 permits after 2 acquires");
        FraudHandler.computeSemaphore.release(2);
        assertEquals(2, FraudHandler.computeSemaphore.availablePermits(), "Semaphore should have 2 permits after release");
    }
}
