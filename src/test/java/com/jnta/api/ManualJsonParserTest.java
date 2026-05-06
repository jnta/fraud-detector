package com.jnta.api;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

class ManualJsonParserTest {

    @Test
    void shouldParseAllFields() {
        String json = "{" +
            "\"transaction\": {\"amount\": 123.45, \"installments\": 12, \"requested_at\": \"2024-05-05T20:00:00Z\"}," +
            "\"customer\": {\"avg_amount\": 500.0, \"tx_count_24h\": 5, \"known_merchants\": [\"m1\", \"m2\"]}," +
            "\"merchant\": {\"id\": \"m1\", \"mcc\": \"5411\", \"avg_amount\": 1000.0}," +
            "\"terminal\": {\"is_online\": true, \"card_present\": false, \"km_from_home\": 10.5}," +
            "\"last_transaction\": {\"timestamp\": \"2024-05-05T19:00:00Z\", \"km_from_current\": 2.0}" +
            "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        
        ManualJsonParser parser = new ManualJsonParser(bytes);
        assertEquals(123.45f, parser.getAmount(), 0.001);
        assertEquals(12, parser.getInstallments());
        assertEquals("2024-05-05T20:00:00Z", parser.getTransactionTimestamp());
        assertEquals(500.0f, parser.getCustomerAvgAmount(), 0.001);
        assertEquals(5, parser.getTxCount24h());
        assertTrue(parser.isKnownMerchant("m1"));
        assertFalse(parser.isKnownMerchant("m3"));
        assertEquals("m1", parser.getMerchantId());
        assertEquals("5411", parser.getMerchantMcc());
        assertEquals(1000.0f, parser.getMerchantAvgAmount(), 0.001);
        assertTrue(parser.isOnline());
        assertFalse(parser.isCardPresent());
        assertEquals(10.5f, parser.getKmFromHome(), 0.001);
        assertEquals("2024-05-05T19:00:00Z", parser.getLastTransactionTimestamp());
        assertEquals(2.0f, parser.getKmFromLast(), 0.001);

        // Test time helpers
        assertEquals(20, parser.getHour());
        assertEquals(7, parser.getDayOfWeek()); // 2024-05-05 was Sunday (7)
        
        long t1 = parser.getTimestampEpoch(false);
        long t2 = parser.getTimestampEpoch(true);
        assertEquals(3600, t1 - t2); // 20:00 - 19:00 = 1 hour
    }

    @Test
    void shouldParseLeapYearCorrectly() {
        String json = "{\"transaction\": {\"requested_at\": \"2024-02-29T12:00:00Z\"}}";
        ManualJsonParser parser = new ManualJsonParser(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(4, parser.getDayOfWeek()); // 2024-02-29 was Thursday (4)
    }
}
