package com.meanmutton.mechgame.core;

/** Shared text and drawing for unit statistics. */
public final class Info {
    private Info() {}

    /** The melee weapon that would swing (the hardest-hitting one), or -1: only one swings per turn. */
    private static int bestMelee(Unit u) {
        int best = -1;
        for (int m = 0; m < u.weapon.length; m++) {
            Wpn w = u.wpn(m);
            if (w != null && w.isMelee() && (best < 0 || w.dmg > u.wpn(best).dmg)) best = m;
        }
        return best;
    }

    /** Heat of firing everything that can fire in one turn (one melee weapon at most). */
    public static int alphaHeat(Unit u) {
        int h = 0, melee = bestMelee(u);
        for (int m = 0; m < u.weapon.length; m++) {
            Wpn w = u.wpn(m);
            if (w != null && (!w.isMelee() || m == melee)) h += w.heat;
        }
        return h;
    }

    /** Damage of firing everything that can fire in one turn (one melee weapon at most). */
    public static int alphaDamage(Unit u) {
        int d = 0, melee = bestMelee(u);
        for (int m = 0; m < u.weapon.length; m++) {
            Wpn w = u.wpn(m);
            if (w != null && (!w.isMelee() || m == melee)) d += w.dmg;
        }
        return d;
    }

    public static int maxRange(Unit u) {
        int r = 0;
        for (int m = 0; m < u.weapon.length; m++) {
            Wpn w = u.wpn(m);
            if (w != null) r = Math.max(r, w.rLong);
        }
        return r;
    }

    public static String locOf(Unit u, int mount) {
        return UnitType.locShort(u.type)[u.ch.mounts[mount].loc];
    }

    public static String classTag(int cls) {
        return cls == 1 ? "L" : (cls == 2 ? "M" : "H");
    }

    /** Draws a compact stat block. Returns the y below it. */
    public static float stats(Gfx g, Unit u, float x, float y, float w, float ts) {
        String[] labels = {"Move", "Armor", "Structure", "Heat sinks", "Firepower", "Range"};
        String heat = u.dissipation() + "/round (cap " + u.ch.heatCap + ")";
        String[] vals = {u.ch.mp + " MP", u.totalArmor() + " / " + u.armorTotal + "  (max " + u.ch.maxArmor + ")",
                u.totalInfra() + " / " + u.totalInfraMax(), heat,
                alphaDamage(u) + " dmg, " + alphaHeat(u) + " heat", maxRange(u) + " hexes"};
        float lh = ts * 1.45f;
        for (int i = 0; i < labels.length; i++) {
            g.text(labels[i], x, y + i * lh, ts, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            g.text(vals[i], x + w * 0.36f, y + i * lh, ts, Col.TEXT, Gfx.ALIGN_LEFT, true);
        }
        return y + labels.length * lh;
    }

    /** One line per weapon and equipment item. Returns the y below. */
    public static float loadout(Gfx g, Unit u, float x, float y, float w, float ts) {
        float lh = ts * 1.4f;
        for (int m = 0; m < u.weapon.length; m++) {
            Wpn wp = u.wpn(m);
            String loc = locOf(u, m);
            if (wp == null) {
                g.text(loc + "  (empty " + classTag(u.ch.mounts[m].cls) + " mount)", x, y, ts, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            } else {
                int c = u.wpnBroken[m] ? Col.BAD : RefitScreen.makerColor(wp.maker, Col.TEXT);
                g.text(loc, x, y, ts * 0.9f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                g.text(wp.name + (u.wpnBroken[m] ? " (broken)" : ""), x + w * 0.16f, y, ts, c, Gfx.ALIGN_LEFT, true);
                String extra = wp.dmg + "d " + wp.heat + "h " + wp.rangeText() + (wp.usesAmmo() ? "  ammo " + u.ammo[m] : "");
                g.text(extra, x + w, y, ts * 0.85f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            }
            y += lh;
        }
        StringBuilder eq = new StringBuilder();
        for (int e : u.equip) {
            if (e < 0) continue;
            if (eq.length() > 0) eq.append(", ");
            eq.append(Equip.ALL[e].name);
        }
        if (eq.length() > 0) {
            g.text(Ui.fit(g, "Equipment: " + eq, ts, w), x, y, ts, Col.SHIELD, Gfx.ALIGN_LEFT, false);
            y += lh;
        }
        return y;
    }
}
