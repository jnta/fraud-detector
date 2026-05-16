import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import api.FraudHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class Server {
    public static HttpServer server;

    public static void main(String[] args) throws IOException {
        startServer(8080);
    }

    public static void startServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/ready", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    String response = "OK";
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                    os.close();
                } catch (Throwable t) {
                }
            }
        });

        FraudHandler fraudHandler = new FraudHandler();
        server.createContext("/fraud", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    fraudHandler.handle(exchange);
                } catch (Throwable t) {
                    try {
                        String fallback = "{\"approved\": true, \"fraud_score\": 0.0}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        byte[] bytes = fallback.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, bytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(bytes);
                        os.close();
                    } catch (Exception e) {
                    }
                }
            }
        });

        server.start();
    }

    public static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }
}
