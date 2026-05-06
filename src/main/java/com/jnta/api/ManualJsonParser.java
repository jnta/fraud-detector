package com.jnta.api;

import java.nio.charset.StandardCharsets;

public class ManualJsonParser {
    private static final byte[] K_TRANSACTION = "\"transaction\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_AMOUNT = "\"amount\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_INSTALLMENTS = "\"installments\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_REQUESTED_AT = "\"requested_at\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_CUSTOMER = "\"customer\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_AVG_AMOUNT = "\"avg_amount\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_TX_COUNT_24H = "\"tx_count_24h\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_MERCHANT = "\"merchant\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_ID = "\"id\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_MCC = "\"mcc\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_TERMINAL = "\"terminal\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_IS_ONLINE = "\"is_online\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_CARD_PRESENT = "\"card_present\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_KM_FROM_HOME = "\"km_from_home\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_LAST_TRANSACTION = "\"last_transaction\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_TIMESTAMP = "\"timestamp\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_KM_FROM_CURRENT = "\"km_from_current\"".getBytes(StandardCharsets.UTF_8);
    private static final byte[] K_KNOWN_MERCHANTS = "\"known_merchants\"".getBytes(StandardCharsets.UTF_8);

    private final byte[] bytes;

    public ManualJsonParser(byte[] bytes) {
        this.bytes = bytes;
    }

    public float getAmount() { return parseFloatField(K_TRANSACTION, K_AMOUNT); }
    public int getInstallments() { return (int) parseFloatField(K_TRANSACTION, K_INSTALLMENTS); }
    public String getTransactionTimestamp() { return parseStringField(K_TRANSACTION, K_REQUESTED_AT); }
    public float getCustomerAvgAmount() { return parseFloatField(K_CUSTOMER, K_AVG_AMOUNT); }
    public int getTxCount24h() { return (int) parseFloatField(K_CUSTOMER, K_TX_COUNT_24H); }
    public String getMerchantId() { return parseStringField(K_MERCHANT, K_ID); }
    public String getMerchantMcc() { return parseStringField(K_MERCHANT, K_MCC); }
    public float getMerchantAvgAmount() { return parseFloatField(K_MERCHANT, K_AVG_AMOUNT); }
    public boolean isOnline() { return parseBooleanField(K_TERMINAL, K_IS_ONLINE); }
    public boolean isCardPresent() { return parseBooleanField(K_TERMINAL, K_CARD_PRESENT); }
    public float getKmFromHome() { return parseFloatField(K_TERMINAL, K_KM_FROM_HOME); }
    public String getLastTransactionTimestamp() { return parseStringField(K_LAST_TRANSACTION, K_TIMESTAMP); }
    public float getKmFromLast() { return parseFloatField(K_LAST_TRANSACTION, K_KM_FROM_CURRENT); }

    public int getHour() {
        String ts = getTransactionTimestamp();
        if (ts == null || ts.length() < 13) return 0;
        return Integer.parseInt(ts.substring(11, 13));
    }

    public int getDayOfWeek() {
        String ts = getTransactionTimestamp();
        if (ts == null || ts.length() < 10) return 0;
        int y = Integer.parseInt(ts.substring(0, 4));
        int m = Integer.parseInt(ts.substring(5, 7));
        int d = Integer.parseInt(ts.substring(8, 10));
        
        // Sakamoto's methods for day of week (0=Sunday, 1=Monday, ..., 6=Saturday)
        int[] t = { 0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4 };
        if (m < 3) y -= 1;
        int dow = (y + y/4 - y/100 + y/400 + t[m-1] + d) % 7;
        
        // Convert to 1=Monday, ..., 7=Sunday (ISO)
        // Sakamoto: 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat
        if (dow == 0) return 7;
        return dow;
    }

    public long getTimestampEpoch(boolean last) {
        String ts = last ? getLastTransactionTimestamp() : getTransactionTimestamp();
        if (ts == null) return -1;
        // Fast ISO-8601 to epoch seconds: YYYY-MM-DDTHH:mm:ssZ
        try {
            int year = Integer.parseInt(ts.substring(0, 4));
            int month = Integer.parseInt(ts.substring(5, 7));
            int day = Integer.parseInt(ts.substring(8, 10));
            int hour = Integer.parseInt(ts.substring(11, 13));
            int min = Integer.parseInt(ts.substring(14, 16));
            int sec = Integer.parseInt(ts.substring(17, 19));
            
            // Simplified epoch calculation for 21st century
            return toEpochSeconds(year, month, day, hour, min, sec);
        } catch (Exception e) {
            return -1;
        }
    }

    private long toEpochSeconds(int year, int month, int day, int hour, int min, int sec) {
        long days = (year - 1970) * 365L + (year - 1969) / 4;
        int[] monthDays = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        days += monthDays[month - 1];
        if (month > 2 && (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) days++;
        days += (day - 1);
        return days * 86400L + hour * 3600L + min * 60L + sec;
    }

    public boolean isKnownMerchant(String merchantId) {
        int[] range = findParentRange(K_CUSTOMER);
        int idx = findKey(K_KNOWN_MERCHANTS, range[0], range[1]);
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

    private float parseFloatField(byte[] parent, byte[] key) {
        int[] range = findParentRange(parent);
        int idx = findKey(key, range[0], range[1]);
        if (idx == -1) return 0.0f;
        int i = skipToValue(idx);
        int start = i;
        while (i < bytes.length && ((bytes[i] >= '0' && bytes[i] <= '9') || bytes[i] == '.' || bytes[i] == '-')) i++;
        if (start == i) return 0.0f;
        // Still using Float.parseFloat for safety, but with a reusable string if needed?
        // Actually, we can implement a zero-allocation parseFloat if really necessary.
        return Float.parseFloat(new String(bytes, start, i - start, StandardCharsets.UTF_8));
    }

    private String parseStringField(byte[] parent, byte[] key) {
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

    private boolean parseBooleanField(byte[] parent, byte[] key) {
        int[] range = findParentRange(parent);
        int idx = findKey(key, range[0], range[1]);
        if (idx == -1) return false;
        int i = skipToValue(idx);
        return matchBytes(i, "true".getBytes(StandardCharsets.UTF_8));
    }

    private int[] findParentRange(byte[] parent) {
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

    private int findKey(byte[] keyBytes, int start, int end) {
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

