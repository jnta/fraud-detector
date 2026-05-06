package com.jnta.risk;

import java.io.InputStream;
import java.util.Map;
import java.util.Arrays;

import io.micronaut.context.annotation.Context;
import io.micronaut.json.JsonMapper;
import jakarta.annotation.PostConstruct;

@Context
public class MccRiskProvider {

    private final float[] riskScores = new float[10000];
    private final JsonMapper jsonMapper;

    private float maxAmount;
    private float maxInstallments;
    private float amountVsAvgRatio;
    private float maxMinutes;
    private float maxKm;
    private float maxTxCount24h;
    private float maxMerchantAvgAmount;

    public MccRiskProvider(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        Arrays.fill(riskScores, 0.5f);
    }

    @PostConstruct
    void init() {
        loadMccRisk();
        loadNormalization();
    }

    private void loadMccRisk() {
        try (InputStream is = getClass().getResourceAsStream("/mcc_risk.json")) {
            if (is != null) {
                Map<String, Double> rawData = jsonMapper.readValue(is, Map.class);
                for (Map.Entry<String, Double> entry : rawData.entrySet()) {
                    int mcc = Integer.parseInt(entry.getKey());
                    if (mcc >= 0 && mcc < 10000) {
                        riskScores[mcc] = entry.getValue().floatValue();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load mcc_risk.json", e);
        }
    }

    private void loadNormalization() {
        try (InputStream is = getClass().getResourceAsStream("/normalization.json")) {
            if (is != null) {
                Map<String, Double> rawData = jsonMapper.readValue(is, Map.class);
                this.maxAmount = getFloat(rawData, "max_amount", 10000.0f);
                this.maxInstallments = getFloat(rawData, "max_installments", 12.0f);
                this.amountVsAvgRatio = getFloat(rawData, "amount_vs_avg_ratio", 10.0f);
                this.maxMinutes = getFloat(rawData, "max_minutes", 1440.0f);
                this.maxKm = getFloat(rawData, "max_km", 1000.0f);
                this.maxTxCount24h = getFloat(rawData, "max_tx_count_24h", 20.0f);
                this.maxMerchantAvgAmount = getFloat(rawData, "max_merchant_avg_amount", 10000.0f);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load normalization.json", e);
        }
    }

    private float getFloat(Map<String, Double> data, String key, float defaultValue) {
        Double val = data.get(key);
        return val != null ? val.floatValue() : defaultValue;
    }

    public float getRiskScore(int mcc) {
        if (mcc < 0 || mcc >= 10000) return 0.5f;
        return riskScores[mcc];
    }

    public void warmup() {
        double sum = 0;
        for (float v : riskScores) {
            sum += v;
        }
        if (sum < -1) System.out.println(sum);
    }

    public float getMaxAmount() { return maxAmount; }
    public float getMaxInstallments() { return maxInstallments; }
    public float getAmountVsAvgRatio() { return amountVsAvgRatio; }
    public float getMaxMinutes() { return maxMinutes; }
    public float getMaxKm() { return maxKm; }
    public float getMaxTxCount24h() { return maxTxCount24h; }
    public float getMaxMerchantAvgAmount() { return maxMerchantAvgAmount; }
}
