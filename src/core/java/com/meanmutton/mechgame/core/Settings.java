package com.meanmutton.mechgame.core;

/** Player preferences. */
public final class Settings {
    public static final String[] SPEED_NAMES = {"Normal", "Fast", "Very Fast"};
    public boolean sound = true;
    public int aiSpeed = 1;

    public float aiSpeedFactor() {
        return aiSpeed == 2 ? 3f : (aiSpeed == 1 ? 1.8f : 1f);
    }

    public String serialize() {
        return "S1," + (sound ? 1 : 0) + "," + aiSpeed;
    }

    public static Settings parse(String s) {
        Settings st = new Settings();
        if (s == null || !s.startsWith("S1,")) return st;
        try {
            String[] f = s.split(",");
            st.sound = Integer.parseInt(f[1]) != 0;
            st.aiSpeed = Math.max(0, Math.min(2, Integer.parseInt(f[2])));
        } catch (RuntimeException ignored) {
        }
        return st;
    }
}
