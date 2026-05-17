import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServerTest {
    private static int port;

    @BeforeAll
    public static void setUp() throws IOException {
        Server.startServer(0);
        port = Server.server.getAddress().getPort();
    }

    @AfterAll
    public static void tearDown() {
        Server.stopServer();
    }

    @Test
    public void testReadyEndpoint() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/ready"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    public void testSemaphoreConcurrency() throws Exception {
        assertEquals(2, api.FraudHandler.computeSemaphore.availablePermits());
        api.FraudHandler.computeSemaphore.acquire(2);
        assertEquals(0, api.FraudHandler.computeSemaphore.availablePermits());
        api.FraudHandler.computeSemaphore.release(2);
        assertEquals(2, api.FraudHandler.computeSemaphore.availablePermits());
    }

    @Test
    public void testFraudEndpoint() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/fraud"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"transaction\":{\"amount\":100.0}}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    public void testSemaphoreBlocksConcurrentRequests() throws Exception {
        api.FraudHandler.computeSemaphore.acquire(2);

        int concurrentTasks = 3;
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(concurrentTasks);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(concurrentTasks);

        for (int i = 0; i < concurrentTasks; i++) {
            executor.submit(() -> {
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/fraud"))
                            .POST(HttpRequest.BodyPublishers.ofString("{\"transaction\":{\"amount\":100.0}}"))
                            .build();
                    client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        Thread.sleep(200);
        assertEquals(concurrentTasks, doneLatch.getCount(), "Requests should be blocked by the semaphore");

        api.FraudHandler.computeSemaphore.release(2);

        doneLatch.await();
        assertEquals(0, doneLatch.getCount(), "All requests should complete after semaphore release");
        executor.shutdown();
    }
}
