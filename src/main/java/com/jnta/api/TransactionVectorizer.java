package com.jnta.api;

import com.jnta.risk.MccRiskProvider;

import jakarta.inject.Singleton;

@Singleton
public class TransactionVectorizer {

    private final MccRiskProvider riskProvider;

    public TransactionVectorizer(MccRiskProvider riskProvider) {
        this.riskProvider = riskProvider;
    }

    public float[] vectorize(ManualJsonParser parser) {
        float[] vector = new float[14];
        
        // 0: amount
        vector[0] = clamp(parser.getAmount() / riskProvider.getMaxAmount());
        
        // 1: installments
        vector[1] = clamp(parser.getInstallments() / riskProvider.getMaxInstallments());
        
        // 2: amount_vs_avg: clamp((transaction.amount / customer.avg_amount) / amount_vs_avg_ratio)
        float avgAmount = parser.getCustomerAvgAmount();
        if (avgAmount > 0) {
            vector[2] = clamp((parser.getAmount() / avgAmount) / riskProvider.getAmountVsAvgRatio());
        } else {
            vector[2] = 1.0f;
        }
        
        // 3: hour_of_day: hour / 23
        vector[3] = parser.getHour() / 23.0f;
        
        // 4: day_of_week: (day - 1) / 6 (Monday=0, Sunday=6)
        vector[4] = (parser.getDayOfWeek() - 1) / 6.0f;
        
        // 5, 6: last transaction
        long txSeconds = parser.getTimestampEpoch(false);
        long lastSeconds = parser.getTimestampEpoch(true);
        if (txSeconds != -1 && lastSeconds != -1) {
            float minutes = (txSeconds - lastSeconds) / 60.0f;
            vector[5] = clamp(minutes / riskProvider.getMaxMinutes());
            vector[6] = clamp(parser.getKmFromLast() / riskProvider.getMaxKm());
        } else {
            vector[5] = -1.0f;
            vector[6] = -1.0f;
        }
        
        // 7: km_from_home
        vector[7] = clamp(parser.getKmFromHome() / riskProvider.getMaxKm());
        
        // 8: tx_count_24h
        vector[8] = clamp(parser.getTxCount24h() / riskProvider.getMaxTxCount24h());
        
        // 9: is_online
        vector[9] = parser.isOnline() ? 1.0f : 0.0f;
        
        // 10: card_present
        vector[10] = parser.isCardPresent() ? 1.0f : 0.0f;
        
        // 11: unknown_merchant
        vector[11] = parser.isKnownMerchant(parser.getMerchantId()) ? 0.0f : 1.0f;
        
        // 12: mcc_risk
        try {
            int mcc = Integer.parseInt(parser.getMerchantMcc());
            vector[12] = riskProvider.getRiskScore(mcc);
        } catch (Exception e) {
            vector[12] = 0.5f;
        }
        
        // 13: merchant_avg_amount
        vector[13] = clamp(parser.getMerchantAvgAmount() / riskProvider.getMaxMerchantAvgAmount());

        return vector;
    }

    private float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
