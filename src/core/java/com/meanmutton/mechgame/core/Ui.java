package com.meanmutton.mechgame.core;

/** Shared UI drawing helpers. */
public final class Ui {
    private Ui() {}

    public static String money(int v) {
        boolean neg = v < 0;
        String s = Integer.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            b.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) b.append(',');
        }
        if (neg) b.append('-');
        return b.reverse().toString();
    }

    public static void panel(Gfx g, float x, float y, float w, float h, float r) {
        g.fillRoundRect(x, y + r * 0.25f, w, h, r, 0x55000000);
        g.fillRoundRect(x, y, w, h, r, Col.PANEL);
        g.strokeRoundRect(x, y, w, h, r, Math.max(1f, r * 0.12f), Col.BORDER);
    }

    /** A strip across the bottom of a scrolling list that says there is more below. */
    public static void moreBelow(Gfx g, float x, float bottom, float w, float u, int bg) {
        g.fillRect(x, bottom - u * 2.8f, w, u * 2.8f, bg);
        g.fillRect(x, bottom - u * 3.6f, w, u * 0.8f, Col.alpha(bg, 0.6f));
        g.text("\u25BC more", x + w - u * 1.5f, bottom - u * 1.4f, u * 1.9f, Col.ACCENT, Gfx.ALIGN_RIGHT, true);
    }

    /** Draws a button even when it is hidden from touches (a row half scrolled out of a clipped list). */
    public static void buttonAlways(Gfx g, Btn b, float u) {
        boolean v = b.visible;
        b.visible = true;
        button(g, b, u);
        b.visible = v;
    }

    public static void button(Gfx g, Btn b, float u) {
        if (!b.visible) return;
        float r = Math.min(b.h, b.w) * 0.22f;
        int base = b.color != 0 ? b.color : Col.PANEL_HI;
        if (b.selected && b.label.length() > 0) base = Col.lerp(base, Col.ACCENT, 0.55f);
        if (!b.enabled) base = Col.lerp(base, Col.BG, 0.6f);
        int top = Col.lighter(base, b.pressed ? 0.0f : 0.12f);
        int bot = Col.darker(base, b.pressed ? 0.05f : 0.25f);
        float off = b.pressed ? r * 0.15f : 0;
        g.fillRoundRect(b.x, b.y + r * 0.3f, b.w, b.h, r, 0x66000000);
        g.save();
        g.translate(0, off);
        g.fillRoundRect(b.x, b.y, b.w, b.h, r, bot);
        g.fillRoundRect(b.x, b.y, b.w, b.h * 0.6f, r, top);
        g.fillRect(b.x + r * 0.5f, b.y + b.h * 0.45f, b.w - r, b.h * 0.15f, Col.lerp(top, bot, 0.5f));
        g.strokeRoundRect(b.x, b.y, b.w, b.h, r, Math.max(1f, u * (b.selected ? 0.6f : 0.25f)),
                b.selected ? Col.ACCENT : Col.alpha(0xFFFFFFFF, 0.18f));
        int tc = b.enabled ? (b.textColor != 0 ? b.textColor : Col.TEXT) : Col.TEXT_DIM;
        float size = b.textSize > 0 ? b.textSize : Math.min(b.h * 0.4f, u * 3.6f);
        if (b.sub != null) {
            g.text(b.label, b.x + b.w / 2, b.y + b.h * 0.38f, size * 0.92f, tc, Gfx.ALIGN_CENTER, true);
            g.text(b.sub, b.x + b.w / 2, b.y + b.h * 0.72f, size * 0.62f, b.enabled ? Col.TEXT_DIM : Col.alpha(Col.TEXT_DIM, 0.6f), Gfx.ALIGN_CENTER, false);
        } else {
            g.text(fit(g, b.label, size, b.w * 0.92f), b.x + b.w / 2, b.y + b.h / 2, size, tc, Gfx.ALIGN_CENTER, true);
        }
        g.restore();
    }

    /** Shrinks nothing; just ellipsizes text that would overflow. */
    public static String fit(Gfx g, String s, float size, float maxW) {
        if (g.textWidth(s, size, true) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && g.textWidth(t + "..", size, true) > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    /** Draws word-wrapped text starting at (x, y) (center of first line). Returns the height used. */
    public static float wrap(Gfx g, String s, float x, float y, float size, float maxW, int color) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        float lh = size * 1.3f, cy = y;
        for (String word : words) {
            String cand = line.length() == 0 ? word : line + " " + word;
            if (g.textWidth(cand, size, false) > maxW && line.length() > 0) {
                g.text(line.toString(), x, cy, size, color, Gfx.ALIGN_LEFT, false);
                cy += lh;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(cand);
            }
        }
        if (line.length() > 0) g.text(line.toString(), x, cy, size, color, Gfx.ALIGN_LEFT, false);
        return cy - y + lh;
    }

    public static void title(Gfx g, String s, float x, float y, float size) {
        g.text(s, x + size * 0.06f, y + size * 0.08f, size, 0xAA000000, Gfx.ALIGN_CENTER, true);
        g.text(s, x, y, size, Col.ACCENT, Gfx.ALIGN_CENTER, true);
    }

    public static void bar(Gfx g, float x, float y, float w, float h, float frac, int color) {
        g.fillRoundRect(x, y, w, h, h / 2, 0xFF0A0F18);
        if (frac > 0) g.fillRoundRect(x, y, Math.max(h, w * Math.min(1f, frac)), h, h / 2, color);
    }

    /**
     * The campaign front from the attacker's dropships (-1) to the defender's base (6). Ground the attacker
     * holds is drawn in the attacker's color; you are blue, the enemy red. Returns the height used.
     */
    public static float frontTrack(Gfx g, float x, float y, float w, float u, int front, int playerSide) {
        int att = playerSide == Scenario.ATTACKER ? UnitArt.PLAYER_COLOR : UnitArt.ENEMY_COLOR;
        int def = playerSide == Scenario.ATTACKER ? UnitArt.ENEMY_COLOR : UnitArt.PLAYER_COLOR;
        int n = Scenario.FRONT_BASE - Scenario.FRONT_MIN + 1;
        float gap = u * 0.5f, cw = (w - gap * (n - 1)) / n, h = u * 2.6f;
        for (int i = 0; i < n; i++) {
            int f = Scenario.FRONT_MIN + i;
            float cx = x + i * (cw + gap);
            int col = f < front ? att : (f > front ? def : Col.ACCENT);
            g.fillRoundRect(cx, y, cw, h, u * 0.6f, Col.alpha(col, f == front ? 1f : 0.55f));
            if (f == front) g.strokeRoundRect(cx, y, cw, h, u * 0.6f, u * 0.35f, 0xFFFFFFFF);
        }
        g.text("Dropships", x, y + h + u * 1.8f, u * 1.8f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("Defender's base", x + w, y + h + u * 1.8f, u * 1.8f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        return h + u * 3.2f;
    }
}
