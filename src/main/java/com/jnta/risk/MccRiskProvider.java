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
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load normalization.json", e);
        }
    }

    public float getRiskScore(int mcc) {
        return riskScores.get(mcc);
    }
}
