package com.jnta.search.linear;

public class SqrtLookupTable {
    private static final float[] TABLE = new float[512001];

    static {
        for (int i = 0; i < TABLE.length; i++) {
            TABLE[i] = (float) Math.sqrt(i);
        }
    }

    public static float get(long squaredDistance) {
        if (squaredDistance < 0) return 0.0f;
        if (squaredDistance >= TABLE.length) return (float) Math.sqrt(squaredDistance);
        return TABLE[(int) squaredDistance];
    }
}
