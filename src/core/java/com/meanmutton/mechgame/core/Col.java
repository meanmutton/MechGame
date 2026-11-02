package com.meanmutton.mechgame.core;

/** Color helpers for ARGB ints. */
public final class Col {
    private Col() {}

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int alpha(int c, float a) {
        int na = (int) (((c >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (na << 24) | (c & 0xFFFFFF);
    }

    public static int withAlpha(int c, int a) {
        return (clamp(a) << 24) | (c & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
        int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
        int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
        int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
        return argb(a, r, g, b);
    }

    public static int darker(int c, float f) {
        return lerp(c, (c & 0xFF000000), f);
    }

    public static int lighter(int c, float f) {
        return lerp(c, (c & 0xFF000000) | 0xFFFFFF, f);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // Shared UI palette
    public static final int BG = 0xFF101826;
    public static final int PANEL = 0xFF1C2638;
    public static final int PANEL_HI = 0xFF27344D;
    public static final int BORDER = 0xFF3D4E6E;
    public static final int TEXT = 0xFFE8EEF8;
    public static final int TEXT_DIM = 0xFF8E9BB3;
    public static final int ACCENT = 0xFFFFB02E;
    public static final int GOOD = 0xFF5BD46A;
    public static final int BAD = 0xFFFF5A4E;
    public static final int SHIELD = 0xFF55C8FF;
    public static final int MONEY = 0xFF8CF06A;
}
