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
    }
}
