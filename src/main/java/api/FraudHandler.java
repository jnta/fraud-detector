package api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import config.StaticRules;
import search.IndexReader;
import search.VectorMath;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class FraudHandler implements HttpHandler {

    public static IndexReader fraudReader;
    public static IndexReader legitReader;
    private static final ConcurrentLinkedQueue<byte[]> bufferPool = new ConcurrentLinkedQueue<>();
    public static final java.util.concurrent.Semaphore computeSemaphore = new java.util.concurrent.Semaphore(2);

    static {
        try {
            Path fraudPath = Paths.get("fraud.bin");
            if (Files.exists(fraudPath)) {
                fraudReader = new IndexReader(fraudPath);
                fraudReader.preloadIntoMemory();
            }
            Path legitPath = Paths.get("legit.bin");
            if (Files.exists(legitPath)) {
                legitReader = new IndexReader(legitPath);
                legitReader.preloadIntoMemory();
            }
        } catch (Exception e) {
        }
    }

    private enum Scope { ROOT, TRANSACTION, CUSTOMER, MERCHANT, TERMINAL, LAST_TRANSACTION }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        byte[] buf = bufferPool.poll();
        if (buf == null) {
            buf = new byte[8192];
        }

        int len = 0;
        try (InputStream is = exchange.getRequestBody()) {
            int read;
            while (len < buf.length && (read = is.read(buf, len, buf.length - len)) != -1) {
                len += read;
            }
        }

        Scope currentScope = Scope.ROOT;
        float amount = 0;
        float installments = 1;
        String requestedAt = null;
        float customerAvgAmount = 0;
        float txCount24h = 0;
        List<String> knownMerchants = new ArrayList<>();
        String merchantId = null;
        String mccStr = null;
        float merchantAvgAmount = 0;
        boolean isOnline = false;
        boolean cardPresent = false;
        float kmFromHome = 0;
        String lastTimestamp = null;
        float lastKmFromCurrent = -1.0f;

        int i = 0;
        while (i < len) {
            char c = (char) buf[i];
            if (c == '"') {
                i++;
                int start = i;
                while (i < len && buf[i] != '"') {
                    i++;
                }
                String str = new String(buf, start, i - start, StandardCharsets.UTF_8);
                i++;
                while (i < len && buf[i] <= ' ') i++;
                if (i < len && buf[i] == ':') {
                    i++;
                    while (i < len && buf[i] <= ' ') i++;
                    String key = str;
                    if (key.equals("transaction")) { currentScope = Scope.TRANSACTION; continue; }
                    if (key.equals("customer")) { currentScope = Scope.CUSTOMER; continue; }
                    if (key.equals("merchant")) { currentScope = Scope.MERCHANT; continue; }
                    if (key.equals("terminal")) { currentScope = Scope.TERMINAL; continue; }
                    if (key.equals("last_transaction")) {
                        currentScope = Scope.LAST_TRANSACTION;
                        if (i < len && buf[i] == 'n') {
                            while (i < len && buf[i] != ',' && buf[i] != '}') i++;
                        }
                        continue;
                    }

                    if (i < len && buf[i] == '"') {
                        i++;
                        int vStart = i;
                        while (i < len && buf[i] != '"') i++;
                        String valStr = new String(buf, vStart, i - vStart, StandardCharsets.UTF_8);
                        i++;
                        if (key.equals("requested_at")) requestedAt = valStr;
                        else if (key.equals("id") && currentScope == Scope.MERCHANT) merchantId = valStr;
                        else if (key.equals("mcc")) mccStr = valStr;
                        else if (key.equals("timestamp") && currentScope == Scope.LAST_TRANSACTION) lastTimestamp = valStr;
                    } else if (i < len && buf[i] == '[') {
                        i++;
                        while (i < len && buf[i] != ']') {
                            if (buf[i] == '"') {
                                i++;
                                int aStart = i;
                                while (i < len && buf[i] != '"') i++;
                                knownMerchants.add(new String(buf, aStart, i - aStart, StandardCharsets.UTF_8));
                                i++;
                            } else {
                                i++;
                            }
                        }
                        if (i < len && buf[i] == ']') i++;
                    } else if (i < len && (buf[i] == 't' || buf[i] == 'f')) {
                        boolean bVal = (buf[i] == 't');
                        while (i < len && buf[i] != ',' && buf[i] != '}') i++;
                        if (key.equals("is_online")) isOnline = bVal;
                        else if (key.equals("card_present")) cardPresent = bVal;
                    } else if (i < len && buf[i] == 'n') {
                        while (i < len && buf[i] != ',' && buf[i] != '}') i++;
                    } else if (i < len && (Character.isDigit(buf[i]) || buf[i] == '-')) {
                        int nStart = i;
                        while (i < len && (Character.isDigit(buf[i]) || buf[i] == '.' || buf[i] == '-' || buf[i] == 'e' || buf[i] == 'E')) i++;
                        String numStr = new String(buf, nStart, i - nStart, StandardCharsets.UTF_8);
                        try {
                            float numVal = Float.parseFloat(numStr);
                            if (key.equals("amount") && currentScope == Scope.TRANSACTION) amount = numVal;
                            else if (key.equals("installments")) installments = numVal;
                            else if (key.equals("avg_amount") && currentScope == Scope.CUSTOMER) customerAvgAmount = numVal;
                            else if (key.equals("tx_count_24h")) txCount24h = numVal;
                            else if (key.equals("avg_amount") && currentScope == Scope.MERCHANT) merchantAvgAmount = numVal;
                            else if (key.equals("km_from_home")) kmFromHome = numVal;
                            else if (key.equals("km_from_current") && currentScope == Scope.LAST_TRANSACTION) lastKmFromCurrent = numVal;
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            } else {
                i++;
            }
        }

        bufferPool.offer(buf);

        float[] vector = new float[14];
        vector[0] = clamp(amount / StaticRules.MAX_AMOUNT);
        vector[1] = clamp(installments / StaticRules.MAX_INSTALLMENTS);
        if (customerAvgAmount > 0) {
            vector[2] = clamp((amount / customerAvgAmount) / StaticRules.AMOUNT_VS_AVG_RATIO);
        } else {
            vector[2] = 1.0f;
        }

        int hour = 0;
        int dayOfWeek = 1;
        long txSeconds = -1;
        if (requestedAt != null && requestedAt.length() >= 19) {
            try {
                hour = Integer.parseInt(requestedAt.substring(11, 13));
                dayOfWeek = calculateDayOfWeek(requestedAt);
                txSeconds = toEpochSeconds(requestedAt);
            } catch (Exception e) {
            }
        }
        vector[3] = hour / 23.0f;
        vector[4] = (dayOfWeek - 1) / 6.0f;

        long lastSeconds = lastTimestamp != null ? toEpochSeconds(lastTimestamp) : -1;
        if (txSeconds != -1 && lastSeconds != -1) {
            float minutes = (txSeconds - lastSeconds) / 60.0f;
            vector[5] = clamp(minutes / StaticRules.MAX_MINUTES);
            vector[6] = clamp(lastKmFromCurrent / StaticRules.MAX_KM);
        } else {
            vector[5] = -1.0f;
            vector[6] = -1.0f;
        }

        vector[7] = clamp(kmFromHome / StaticRules.MAX_KM);
        vector[8] = clamp(txCount24h / StaticRules.MAX_TX_COUNT_24H);
        vector[9] = isOnline ? 1.0f : 0.0f;
        vector[10] = cardPresent ? 1.0f : 0.0f;
        vector[11] = (knownMerchants != null && knownMerchants.contains(merchantId)) ? 0.0f : 1.0f;

        try {
            int mcc = Integer.parseInt(mccStr);
            vector[12] = StaticRules.getMccRisk(mcc);
        } catch (Exception e) {
            vector[12] = 0.5f;
        }

        vector[13] = clamp(merchantAvgAmount / StaticRules.MAX_MERCHANT_AVG_AMOUNT);

        byte[] queryFeatures = new byte[14];
        for (int d = 0; d < 14; d++) {
            float val = vector[d];
            byte b;
            if (val < -0.5f) {
                b = 0;
            } else {
                float clamped = Math.max(0.0f, Math.min(1.0f, val));
                b = (byte) Math.round(128 + clamped * 127);
            }
            queryFeatures[d] = b;
        }

        boolean approved = true;
        float fraudScore = 0.0f;

        if (fraudReader != null && legitReader != null) {
            try {
                computeSemaphore.acquire();
                try {
                    int closestFraudCluster = VectorMath.findClosestCentroid(fraudReader, queryFeatures);
                    VectorMath.SearchResult fraudRes = VectorMath.findClosestVectorInCluster(fraudReader, closestFraudCluster, queryFeatures);
                    int distFraud = fraudRes.distance();

                    int closestLegitCluster = VectorMath.findClosestCentroid(legitReader, queryFeatures);
                    VectorMath.SearchResult legitRes = VectorMath.findClosestVectorInCluster(legitReader, closestLegitCluster, queryFeatures);
                    int distLegit = legitRes.distance();

                    approved = distLegit <= distFraud;
                    fraudScore = approved ? 0.0f : 1.0f;
                } finally {
                    computeSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String responseJson = "{\"approved\": " + approved + ", \"fraud_score\": " + (approved ? "0.0" : "1.0") + "}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private static int calculateDayOfWeek(String ts) {
        try {
            int y = Integer.parseInt(ts.substring(0, 4));
            int m = Integer.parseInt(ts.substring(5, 7));
            int d = Integer.parseInt(ts.substring(8, 10));
            int[] t = { 0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4 };
            if (m < 3) y -= 1;
            int dow = (y + y/4 - y/100 + y/400 + t[m-1] + d) % 7;
            return dow == 0 ? 7 : dow;
        } catch (Exception e) {
            return 1;
        }
    }

    private static long toEpochSeconds(String ts) {
        try {
            int year = Integer.parseInt(ts.substring(0, 4));
            int month = Integer.parseInt(ts.substring(5, 7));
            int day = Integer.parseInt(ts.substring(8, 10));
            int hour = Integer.parseInt(ts.substring(11, 13));
            int min = Integer.parseInt(ts.substring(14, 16));
            int sec = Integer.parseInt(ts.substring(17, 19));
            long days = (year - 1970) * 365L + (year - 1969) / 4;
            int[] monthDays = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
            days += monthDays[month - 1];
            if (month > 2 && (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) days++;
            days += (day - 1);
            return days * 86400L + hour * 3600L + min * 60L + sec;
        } catch (Exception e) {
            return -1;
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
