package com.jnta.risk;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import jakarta.annotation.PostConstruct;
import io.micronaut.context.annotation.Context;
import io.micronaut.json.JsonMapper;
import java.io.InputStream;
import java.util.Map;

@Context
public class MccRiskProvider {

    private final Int2FloatMap riskScores = new Int2FloatOpenHashMap();
    private final Object2DoubleMap<String> normalizationConstants = new Object2DoubleOpenHashMap<>();
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
                riskScores.defaultReturnValue(0.5f);
                for (Map.Entry<String, Double> entry : rawData.entrySet()) {
                    riskScores.put(Integer.parseInt(entry.getKey()), entry.getValue().floatValue());
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
                this.normalizationConstants.putAll(rawData);
                this.maxAmount = (float) normalizationConstants.getOrDefault("max_amount", 10000.0);
                this.maxInstallments = (float) normalizationConstants.getOrDefault("max_installments", 12.0);
                this.amountVsAvgRatio = (float) normalizationConstants.getOrDefault("amount_vs_avg_ratio", 10.0);
                this.maxMinutes = (float) normalizationConstants.getOrDefault("max_minutes", 1440.0);
                this.maxKm = (float) normalizationConstants.getOrDefault("max_km", 1000.0);
                this.maxTxCount24h = (float) normalizationConstants.getOrDefault("max_tx_count_24h", 20.0);
                this.maxMerchantAvgAmount = (float) normalizationConstants.getOrDefault("max_merchant_avg_amount", 10000.0);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load normalization.json", e);
        }
    }

    public float getRiskScore(int mcc) {
        return riskScores.get(mcc);
    }

    public float getMaxAmount() { return maxAmount; }
    public float getMaxInstallments() { return maxInstallments; }
    public float getAmountVsAvgRatio() { return amountVsAvgRatio; }
    public float getMaxMinutes() { return maxMinutes; }
    public float getMaxKm() { return maxKm; }
    public float getMaxTxCount24h() { return maxTxCount24h; }
    public float getMaxMerchantAvgAmount() { return maxMerchantAvgAmount; }
}
