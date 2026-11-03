package com.meanmutton.mechgame.core;

/**
 * Faction emblems: a round badge in the faction's colors with its symbol. Drawn wherever a faction is named
 * (the Market, unit sheets, the factions help page, and contracts in mercenary play), always beside its name in
 * the same color, so a faction reads the same everywhere.
 */
public final class Emblem {
    private Emblem() {}

    private static final float[] PX = new float[16], PY = new float[16];

    private static void poly(Gfx g, int color, float... pts) {
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            PX[i] = pts[i * 2];
            PY[i] = pts[i * 2 + 1];
        }
        g.fillPoly(PX, PY, n, color);
    }

    /** A faction's badge centered at (cx, cy) with radius r. */
    public static void draw(Gfx g, int faction, float cx, float cy, float r) {
        Faction f = Faction.ALL[faction];
        int sym = f.color, ground = Col.darker(f.scheme.main, 0.45f);
        g.save();
        g.translate(cx, cy);
        g.scale(r, r);
        g.fillCircle(0, 0, 1f, ground);
        g.strokeCircle(0, 0, 0.92f, 0.12f, sym);
        switch (faction) {
            case Faction.HELION:   // a sun with eight rays
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    float c = (float) Math.cos(a), s = (float) Math.sin(a);
                    g.line(c * 0.36f, s * 0.36f, c * 0.7f, s * 0.7f, 0.12f, sym);
                }
                g.fillCircle(0, 0, 0.3f, sym);
                break;
            case Faction.VARDAN:   // a shield with a chevron
                poly(g, sym, -0.5f, -0.55f, 0.5f, -0.55f, 0.5f, 0.05f, 0f, 0.62f, -0.5f, 0.05f);
                poly(g, ground, -0.36f, -0.1f, 0f, 0.2f, 0.36f, -0.1f, 0.36f, 0.08f, 0f, 0.38f, -0.36f, 0.08f);
                break;
            case Faction.KESTREL:  // swept wings
                poly(g, sym, 0f, -0.1f, -0.7f, -0.42f, -0.5f, 0.05f, 0f, 0.3f);
                poly(g, sym, 0f, -0.1f, 0.7f, -0.42f, 0.5f, 0.05f, 0f, 0.3f);
                g.fillCircle(0f, -0.28f, 0.12f, sym);
                break;
            case Faction.ASHFALL:  // a flame rising from a mountain
                poly(g, sym, -0.62f, 0.5f, -0.1f, -0.1f, 0.1f, 0.12f, 0.3f, -0.05f, 0.62f, 0.5f);
                poly(g, sym, -0.12f, -0.18f, 0f, -0.7f, 0.18f, -0.3f, 0.06f, -0.12f);
                break;
            case Faction.MERIDIAN: // a globe: rings and a meridian line
                g.strokeCircle(0, 0, 0.55f, 0.1f, sym);
                g.strokeCircle(0, 0, 0.28f, 0.08f, sym);
                g.line(0, -0.62f, 0, 0.62f, 0.09f, sym);
                g.line(-0.62f, 0, 0.62f, 0, 0.07f, sym);
                break;
            case Faction.THORNWALL: // a crenellated tower ringed with thorns
                poly(g, sym, -0.35f, 0.55f, -0.35f, -0.3f, -0.2f, -0.3f, -0.2f, -0.45f, -0.07f, -0.45f, -0.07f, -0.3f,
                        0.07f, -0.3f, 0.07f, -0.45f, 0.2f, -0.45f, 0.2f, -0.3f, 0.35f, -0.3f, 0.35f, 0.55f);
                g.fillRect(-0.08f, 0.2f, 0.16f, 0.35f, ground);
                for (int sd = -1; sd <= 1; sd += 2) {
                    poly(g, sym, sd * 0.42f, 0.0f, sd * 0.68f, -0.1f, sd * 0.42f, 0.18f);
                    poly(g, sym, sd * 0.42f, 0.32f, sd * 0.66f, 0.3f, sd * 0.42f, 0.48f);
                }
                break;
            case Faction.IRONROOT: // a tree with spreading roots
                g.fillCircle(0, -0.3f, 0.32f, sym);
                g.fillRect(-0.07f, -0.1f, 0.14f, 0.42f, sym);
                g.line(0, 0.3f, -0.5f, 0.6f, 0.09f, sym);
                g.line(0, 0.3f, 0.5f, 0.6f, 0.09f, sym);
                g.line(0, 0.3f, 0f, 0.66f, 0.09f, sym);
                break;
            default:               // Obsidian Veil: an eye in a diamond
                poly(g, sym, 0f, -0.66f, 0.66f, 0f, 0f, 0.66f, -0.66f, 0f);
                poly(g, ground, 0f, -0.48f, 0.48f, 0f, 0f, 0.48f, -0.48f, 0f);
                g.save();
                g.scale(1f, 0.55f);
                g.fillCircle(0, 0, 0.38f, sym);
                g.restore();
                g.fillCircle(0, 0, 0.13f, ground);
                break;
        }
        g.restore();
    }

    /** The badge followed by the faction's name in its color, vertically centered on y. Returns the width used. */
    public static float label(Gfx g, int faction, float x, float y, float size, boolean full) {
        Faction f = Faction.ALL[faction];
        draw(g, faction, x + size * 0.55f, y, size * 0.55f);
        String name = full ? f.name : f.shortName;
        g.text(name, x + size * 1.3f, y, size, f.color, Gfx.ALIGN_LEFT, true);
        return size * 1.3f + g.textWidth(name, size, true);
    }
}
