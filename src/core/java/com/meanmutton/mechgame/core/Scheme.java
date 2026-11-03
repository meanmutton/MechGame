package com.meanmutton.mechgame.core;

/**
 * A paint scheme: main (hull and torso), trim (turrets, arms, shoulders) and accent (stripes and markings).
 * Your company has one (Company.scheme), each faction has its own, and the campaign's enemy company has one.
 * Paint is only looks: whose unit it is shows by the ring under it on the map (blue yours, red the enemy's).
 */
public final class Scheme {
    public final int main, trim, accent;

    public Scheme(int main, int trim, int accent) {
        this.main = main | 0xFF000000;
        this.trim = trim | 0xFF000000;
        this.accent = accent | 0xFF000000;
    }

    /** The palette the company colors screen offers. */
    public static final int[] PALETTE = {
            0xFF3F7EDB, 0xFF2E5AA8, 0xFF2E9A92, 0xFF4E7A3A, 0xFF6E8A3A, 0xFF8A6A44,
            0xFFB8443A, 0xFF9A2E2A, 0xFFD9822E, 0xFFD9B24A, 0xFFE8D8A8, 0xFFF2F2F2,
            0xFFA8B4C4, 0xFF6E747E, 0xFF3A3E46, 0xFF2A2430, 0xFF6A4A8E, 0xFFC0507A,
    };

    /** Ready-made schemes the company colors screen offers. */
    public static final String[] PRESET_NAMES = {"Company Blue", "Desert", "Arctic", "Woodland", "Night", "Crimson", "Hazard", "Royal"};
    public static final Scheme[] PRESETS = {
            new Scheme(0xFF3F7EDB, 0xFFA8B4C4, 0xFFFFC94A),
            new Scheme(0xFFC9A66B, 0xFF8A6A44, 0xFFE8D8A8),
            new Scheme(0xFFE8ECF0, 0xFF8A97A8, 0xFF3F7EDB),
            new Scheme(0xFF4E6A3A, 0xFF6E5A3E, 0xFFC9B87A),
            new Scheme(0xFF2E323A, 0xFF4A5060, 0xFF7ACBFF),
            new Scheme(0xFFA8302A, 0xFF3A3E46, 0xFFF2F2F2),
            new Scheme(0xFFE8C030, 0xFF2A2A2E, 0xFF2A2A2E),
            new Scheme(0xFF4A3A8E, 0xFFD9B24A, 0xFFF2F2F2),
    };

    /** What your units wear unless you choose otherwise, and what the campaign's enemy wears. */
    public static final Scheme PLAYER_DEFAULT = PRESETS[0];
    public static final Scheme ENEMY_DEFAULT = new Scheme(0xFFB8443A, 0xFF8A93A0, 0xFFFFD27A);

    /** The schemes in use right now (set every frame by Game from the company), for drawing. */
    static volatile Scheme player = PLAYER_DEFAULT, enemy = ENEMY_DEFAULT;

    /** An old-style single team color, as a scheme (used where only a color is known). */
    public static Scheme fromColor(int color) {
        int body = Col.lerp(color, 0xFF8A93A0, 0.3f);
        return new Scheme(body, Col.lighter(body, 0.3f), 0xFFEDEDED);
    }

    public String serialize() {
        return (main & 0xFFFFFF) + ":" + (trim & 0xFFFFFF) + ":" + (accent & 0xFFFFFF);
    }

    public static Scheme parse(String s, Scheme fallback) {
        try {
            String[] p = s.split(":");
            return new Scheme(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public boolean equals(Object o) {
        return o instanceof Scheme && ((Scheme) o).main == main && ((Scheme) o).trim == trim && ((Scheme) o).accent == accent;
    }

    public int hashCode() {
        return main * 31 + trim * 7 + accent;
    }
}
