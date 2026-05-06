package com.jnta.api;

import java.nio.charset.StandardCharsets;

public class ManualJsonParser {
    private final byte[] bytes;

    public ManualJsonParser(byte[] bytes) {
        this.bytes = bytes;
    }

    public float getAmount() { return parseFloatField("transaction", "amount"); }
    public int getInstallments() { return (int) parseFloatField("transaction", "installments"); }
    public String getTransactionTimestamp() { return parseStringField("transaction", "requested_at"); }
    public float getCustomerAvgAmount() { return parseFloatField("customer", "avg_amount"); }
    public int getTxCount24h() { return (int) parseFloatField("customer", "tx_count_24h"); }
    public String getMerchantId() { return parseStringField("merchant", "id"); }
    public String getMerchantMcc() { return parseStringField("merchant", "mcc"); }
    public float getMerchantAvgAmount() { return parseFloatField("merchant", "avg_amount"); }
    public boolean isOnline() { return parseBooleanField("terminal", "is_online"); }
    public boolean isCardPresent() { return parseBooleanField("terminal", "card_present"); }
    public float getKmFromHome() { return parseFloatField("terminal", "km_from_home"); }
    public String getLastTransactionTimestamp() { return parseStringField("last_transaction", "timestamp"); }
    public float getKmFromLast() { return parseFloatField("last_transaction", "km_from_current"); }

    public boolean isKnownMerchant(String merchantId) {
        int[] range = findParentRange("customer");
        int idx = findKey("known_merchants", range[0], range[1]);
        if (idx == -1) return false;
        
        int start = skipToValue(idx);
        if (start >= bytes.length || bytes[start] != '[') return false;
        
        int end = findArrayEnd(start);
        byte[] mIdBytes = ("\"" + merchantId + "\"").getBytes(StandardCharsets.UTF_8);
        
        for (int i = start; i <= end - mIdBytes.length; i++) {
            if (matchBytes(i, mIdBytes)) return true;
        }
        return false;
    }

    private float parseFloatField(String parent, String key) {
        int[] range = findParentRange(parent);
        int idx = findKey(key, range[0], range[1]);
        if (idx == -1) return 0.0f;
        int i = skipToValue(idx);
        int start = i;
        while (i < bytes.length && ((bytes[i] >= '0' && bytes[i] <= '9') || bytes[i] == '.' || bytes[i] == '-')) i++;
        if (start == i) return 0.0f;
        return Float.parseFloat(new String(bytes, start, i - start, StandardCharsets.UTF_8));
    }

    private String parseStringField(String parent, String key) {
        int[] range = findParentRange(parent);
        int idx = findKey(key, range[0], range[1]);
        if (idx == -1) return null;
        int i = skipToValue(idx);
        if (i < bytes.length && bytes[i] == '"') {
            i++;
            int start = i;
            while (i < bytes.length && bytes[i] != '"') i++;
            return new String(bytes, start, i - start, StandardCharsets.UTF_8);
        }
        return null;
    }

    private boolean parseBooleanField(String parent, String key) {
        int[] range = findParentRange(parent);
        int idx = findKey(key, range[0], range[1]);
        if (idx == -1) return false;
        int i = skipToValue(idx);
        return matchBytes(i, "true".getBytes(StandardCharsets.UTF_8));
    }

    private int[] findParentRange(String parent) {
        if (parent == null) return new int[]{0, bytes.length};
        int pIdx = findKey(parent, 0, bytes.length);
        if (pIdx == -1) return new int[]{0, bytes.length};
        int start = skipToValue(pIdx);
        if (start >= bytes.length || bytes[start] != '{') return new int[]{start, bytes.length};
        int end = start + 1;
        int depth = 1;
        while (end < bytes.length && depth > 0) {
            if (bytes[end] == '{') depth++;
            else if (bytes[end] == '}') depth--;
            end++;
        }
        return new int[]{start, end};
    }

    private int findArrayEnd(int start) {
        int i = start + 1;
        int depth = 1;
        while (i < bytes.length && depth > 0) {
            if (bytes[i] == '[') depth++;
            else if (bytes[i] == ']') depth--;
            i++;
        }
        return i;
    }

    private int findKey(String key, int start, int end) {
        byte[] keyBytes = ("\"" + key + "\"").getBytes(StandardCharsets.UTF_8);
        for (int i = start; i <= end - keyBytes.length; i++) {
            if (matchBytes(i, keyBytes)) return i + keyBytes.length;
        }
        return -1;
    }

    private boolean matchBytes(int pos, byte[] target) {
        if (pos + target.length > bytes.length) return false;
        for (int j = 0; j < target.length; j++) {
            if (bytes[pos + j] != target[j]) return false;
        }
        return true;
    }

    private int skipToValue(int idx) {
        int i = idx;
        while (i < bytes.length && (bytes[i] == ':' || bytes[i] == ' ' || bytes[i] == '\t' || bytes[i] == '\n' || bytes[i] == '\r')) i++;
        return i;
    }
}
