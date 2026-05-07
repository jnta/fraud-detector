package com.jnta.api;

import com.jnta.risk.MccRiskProvider;

import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class TransactionVectorizer {

    private final MccRiskProvider riskProvider;

    public TransactionVectorizer(MccRiskProvider riskProvider) {
        this.riskProvider = riskProvider;
    }

    public float[] vectorize(TransactionRequest request) {
        float[] vector = new float[14];
        
        // 0: amount
        vector[0] = clamp(request.transaction().amount() / riskProvider.getMaxAmount());
        
        // 1: installments
        vector[1] = clamp(request.transaction().installments() / riskProvider.getMaxInstallments());
        
        // 2: amount_vs_avg
        float avgAmount = request.customer().avg_amount();
        if (avgAmount > 0) {
            vector[2] = clamp((request.transaction().amount() / avgAmount) / riskProvider.getAmountVsAvgRatio());
        } else {
            vector[2] = 1.0f;
        }
        
        // Time parsing
        String ts = request.transaction().requested_at();
        int hour = 0;
        int dayOfWeek = 1;
        long txSeconds = -1;
        if (ts != null && ts.length() >= 19) {
            hour = Integer.parseInt(ts.substring(11, 13));
            dayOfWeek = calculateDayOfWeek(ts);
            txSeconds = toEpochSeconds(ts);
        }

        // 3: hour_of_day
        vector[3] = hour / 23.0f;
        
        // 4: day_of_week
        vector[4] = (dayOfWeek - 1) / 6.0f;
        
        // 5, 6: last transaction
        String lastTs = request.last_transaction() != null ? request.last_transaction().timestamp() : null;
        long lastSeconds = lastTs != null ? toEpochSeconds(lastTs) : -1;
        
        if (txSeconds != -1 && lastSeconds != -1) {
            float minutes = (txSeconds - lastSeconds) / 60.0f;
            vector[5] = clamp(minutes / riskProvider.getMaxMinutes());
            vector[6] = clamp(request.last_transaction().km_from_current() / riskProvider.getMaxKm());
        } else {
            vector[5] = -1.0f;
            vector[6] = -1.0f;
        }
        
        // 7: km_from_home
        vector[7] = clamp(request.terminal().km_from_home() / riskProvider.getMaxKm());
        
        // 8: tx_count_24h
        vector[8] = clamp(request.customer().tx_count_24h() / riskProvider.getMaxTxCount24h());
        
        // 9: is_online
        vector[9] = request.terminal().is_online() ? 1.0f : 0.0f;
        
        // 10: card_present
        vector[10] = request.terminal().card_present() ? 1.0f : 0.0f;
        
        // 11: unknown_merchant
        String merchantId = request.merchant().id();
        List<String> known = request.customer().known_merchants();
        vector[11] = (known != null && known.contains(merchantId)) ? 0.0f : 1.0f;
        
        // 12: mcc_risk
        try {
            int mcc = Integer.parseInt(request.merchant().mcc());
            vector[12] = riskProvider.getRiskScore(mcc);
        } catch (Exception e) {
            vector[12] = 0.5f;
        }
        
        // 13: merchant_avg_amount
        vector[13] = clamp(request.merchant().avg_amount() / riskProvider.getMaxMerchantAvgAmount());

        return vector;
    }

    private int calculateDayOfWeek(String ts) {
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

    private long toEpochSeconds(String ts) {
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

    private float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
