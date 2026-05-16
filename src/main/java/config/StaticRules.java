package config;

public class StaticRules {
    public static final float[] MCC_RISK = new float[10000];

    static {
        java.util.Arrays.fill(MCC_RISK, 0.5f);
        MCC_RISK[5411] = 0.15f;
        MCC_RISK[5812] = 0.30f;
        MCC_RISK[5912] = 0.20f;
        MCC_RISK[5944] = 0.45f;
        MCC_RISK[7801] = 0.80f;
        MCC_RISK[7802] = 0.75f;
        MCC_RISK[7995] = 0.85f;
        MCC_RISK[4511] = 0.35f;
        MCC_RISK[5311] = 0.25f;
        MCC_RISK[5999] = 0.50f;
    }

    public static final float MAX_AMOUNT = 10000f;
    public static final float MAX_INSTALLMENTS = 12f;
    public static final float AMOUNT_VS_AVG_RATIO = 10f;
    public static final float MAX_MINUTES = 1440f;
    public static final float MAX_KM = 1000f;
    public static final float MAX_TX_COUNT_24H = 20f;
    public static final float MAX_MERCHANT_AVG_AMOUNT = 10000f;

    public static float getMccRisk(int mcc) {
        if (mcc >= 0 && mcc < 10000) {
            return MCC_RISK[mcc];
        }
        return 0.5f;
    }
}
