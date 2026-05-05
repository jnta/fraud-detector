package com.jnta.api;

import com.jnta.risk.MccRiskProvider;
import jakarta.inject.Singleton;

@Singleton
public class TransactionVectorizer {

    private final MccRiskProvider riskProvider;

    public TransactionVectorizer(MccRiskProvider riskProvider) {
        this.riskProvider = riskProvider;
    }

    public float[] vectorize(TransactionPayload payload) {
        float[] vector = new float[14];
        
        var tx = payload.transaction();
        var customer = payload.customer();
        var merchant = payload.merchant();
        var terminal = payload.terminal();
        var lastTx = payload.last_transaction();

        // 0: amount
        vector[0] = clamp(tx.amount() / riskProvider.getMaxAmount());
        
        // 1: installments
        vector[1] = clamp(tx.installments() / riskProvider.getMaxInstallments());
        
        // 2: amount_vs_avg: clamp((transaction.amount / customer.avg_amount) / amount_vs_avg_ratio)
        float avgAmount = customer.avg_amount();
        if (avgAmount > 0) {
            vector[2] = clamp((tx.amount() / avgAmount) / riskProvider.getAmountVsAvgRatio());
        } else {
            vector[2] = 1.0f;
        }
        
        // 3: hour_of_day: hour / 23
        vector[3] = tx.requested_at().getHour() / 23.0f;
        
        // 4: day_of_week: (day - 1) / 6 (Monday=0, Sunday=6)
        vector[4] = (tx.requested_at().getDayOfWeek().getValue() - 1) / 6.0f;
        
        // 5: minutes_since_last_tx
        // 6: km_from_last_tx
        if (lastTx != null) {
            long txSeconds = tx.requested_at().toEpochSecond();
            long lastSeconds = lastTx.timestamp().toEpochSecond();
            float minutes = (txSeconds - lastSeconds) / 60.0f;
            vector[5] = clamp(minutes / riskProvider.getMaxMinutes());
            vector[6] = clamp(lastTx.km_from_current() / riskProvider.getMaxKm());
        } else {
            vector[5] = -1.0f;
            vector[6] = -1.0f;
        }
        
        // 7: km_from_home
        vector[7] = clamp(terminal.km_from_home() / riskProvider.getMaxKm());
        
        // 8: tx_count_24h
        vector[8] = clamp(customer.tx_count_24h() / riskProvider.getMaxTxCount24h());
        
        // 9: is_online
        vector[9] = terminal.is_online() ? 1.0f : 0.0f;
        
        // 10: card_present
        vector[10] = terminal.card_present() ? 1.0f : 0.0f;
        
        // 11: unknown_merchant
        boolean known = false;
        if (customer.known_merchants() != null) {
            for (String mId : customer.known_merchants()) {
                if (mId.equals(merchant.id())) {
                    known = true;
                    break;
                }
            }
        }
        vector[11] = known ? 0.0f : 1.0f;
        
        // 12: mcc_risk
        try {
            int mcc = Integer.parseInt(merchant.mcc());
            vector[12] = riskProvider.getRiskScore(mcc);
        } catch (NumberFormatException e) {
            vector[12] = 0.5f;
        }
        
        // 13: merchant_avg_amount
        vector[13] = clamp(merchant.avg_amount() / riskProvider.getMaxMerchantAvgAmount());

        return vector;
    }

    private float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
