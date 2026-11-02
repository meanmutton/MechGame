package com.meanmutton.mechgame.core;

/**
 * Full-screen detail sheet for one unit: every location's armor and structure, every weapon's
 * numbers, and what each piece of equipment does. Opened by double-tapping a unit anywhere.
 */
public final class UnitDetail {
    private UnitDetail() {}

    /** Area of the close button (screen coordinates), set by render. */
    public static float closeX, closeY, closeW, closeH;

    public static void render(Gfx g, Unit un, int team, Battle b, float w, float h, float u, float t) {
        g.fillRect(0, 0, w, h, 0xD8000000);
        float px = w * 0.03f, py = h * 0.04f, pw = w * 0.94f, ph = h * 0.92f;
        Ui.panel(g, px, py, pw, ph, u * 2.5f);
        closeW = u * 22;
        closeH = u * 8;
        closeX = px + pw - closeW - u * 2;
        closeY = py + u * 2;
        g.fillRoundRect(closeX, closeY, closeW, closeH, u * 1.5f, Col.PANEL_HI);
        g.strokeRoundRect(closeX, closeY, closeW, closeH, u * 1.5f, u * 0.3f, Col.BORDER);
        g.text("Close", closeX + closeW / 2, closeY + closeH / 2, u * 3, Col.TEXT, Gfx.ALIGN_CENTER, true);

        // ---- left: picture and core stats ----
        float lx = px + u * 3, lw = pw * 0.27f;
        int tc = UnitArt.teamColor(team);
        g.fillRect(px + u * 0.6f, py + u * 2, u, u * 9, tc);
        g.text(un.name, lx, py + u * 5, u * 4.4f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text(UnitType.NAMES[un.type] + " · " + Chassis.ROLES[un.ch.role] + (un.name.equals(un.ch.name) ? "" : " · " + un.ch.name),
                lx, py + u * 9.5f, u * 2.5f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        UnitArt.draw(g, un, lx + lw / 2, py + u * 25, -90 + (float) Math.sin(t * 0.8f) * 20, team, u * 12, t, un.condition());
        float y = py + u * 40;
        float lh = u * 3.6f, ts = u * 2.4f;
        String heat = un.type == UnitType.INFANTRY ? "none (infantry)" : Math.round(un.heat) + " / " + un.ch.heatCap + ", cools " + un.dissipation() + "/round";
        String mv = un.baseMp() + " MP" + (b != null ? "  (now " + b.mpAvailable(un) + ")" : "");
        String[][] rows = {
                {"Movement", mv},
                {"Armor", un.totalArmor() + " / " + un.armorTotal + "  (type max " + un.ch.maxArmor + ")"},
                {"Structure", un.totalInfra() + " / " + un.totalInfraMax()},
                {"Heat", heat},
                {"Weight", RefitScreen.fmt(un.mass()) + " / " + RefitScreen.fmt(un.ch.capacity) + " t"},
                {"Weapon class", "up to " + (UnitType.MAX_CLASS[un.type] == 1 ? "Light" : (UnitType.MAX_CLASS[un.type] == 2 ? "Medium" : "Heavy"))},
                {"Value", "$" + Ui.money(un.value())},
        };
        for (String[] r : rows) {
            g.text(r[0], lx, y, ts, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            g.text(r[1], lx + lw * 0.38f, y, ts, Col.TEXT, Gfx.ALIGN_LEFT, true);
            y += lh;
        }
        String st = un.statusText();
        if (b != null && b.inEcm(un)) st = (st.length() > 0 ? st + "  " : "") + "IN ECM COVER";
        if (st.length() > 0) g.text(st, lx, y + u, ts, Col.BAD, Gfx.ALIGN_LEFT, true);
        y += lh * 1.5f;
        Ui.wrap(g, un.ch.desc, lx, y, u * 2.2f, lw, Col.TEXT_DIM);

        // ---- middle: hit locations ----
        float mx = px + pw * 0.31f, mw = pw * 0.26f;
        g.text("Hit locations", mx, py + u * 5, u * 3, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("white = armor, gold = structure", mx, py + u * 9, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        float ds = Math.min(mw, ph * 0.42f);
        UnitArt.doll(g, un, mx, py + u * 12, ds, ds, u * 3.6f, -1);
        y = py + u * 14 + ds;
        String[] names = UnitType.locNames(un.type);
        for (int i = 0; i < names.length; i++) {
            int il = UnitType.infraLoc(un.type, i);
            StringBuilder ws = new StringBuilder();
            for (int m = 0; m < un.weapon.length; m++) {
                if (un.ch.mounts[m].loc == i && un.weapon[m] >= 0) ws.append(ws.length() > 0 ? ", " : "").append(Wpn.ALL[un.weapon[m]].name);
            }
            boolean dead = un.infra[il] <= 0;
            String line = names[i] + ": " + un.armor[i] + "/" + un.armorMax[i]
                    + (un.infraMax[i] > 0 ? "  str " + un.infra[il] + "/" + un.infraMax[il] : "  (uses core structure)")
                    + (dead ? "  DESTROYED" : "");
            g.text(line, mx, y, u * 2.15f, dead ? Col.BAD : Col.TEXT, Gfx.ALIGN_LEFT, true);
            if (ws.length() > 0) {
                y += u * 2.7f;
                g.text(Ui.fit(g, "   " + ws, u * 1.9f, mw), mx, y, u * 1.9f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            }
            y += u * 3.3f;
        }

        // ---- right: weapons and equipment ----
        float rx = px + pw * 0.6f, rw = pw * 0.38f;
        g.text("Weapons", rx, py + u * 14, u * 3, Col.TEXT, Gfx.ALIGN_LEFT, true);
        y = py + u * 18.5f;
        for (int m = 0; m < un.weapon.length; m++) {
            Wpn wp = un.wpn(m);
            Chassis.Mount mt = un.ch.mounts[m];
            String loc = names[mt.loc] + ", " + Chassis.ARC_NAMES[mt.arc];
            if (wp == null) {
                g.text("(empty " + Info.classTag(mt.cls) + " mount)  " + loc, rx, y, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                y += u * 4;
                continue;
            }
            boolean ok = un.weaponUsable(m);
            String state = ok ? "" : (un.wpnBroken[m] || un.locDestroyed(mt.loc) ? "  DESTROYED" : "  OUT OF AMMO");
            g.text(wp.name + " [" + Info.classTag(wp.cls) + "]" + state, rx, y, u * 2.5f, ok ? Col.TEXT : Col.BAD, Gfx.ALIGN_LEFT, true);
            g.text(loc, rx + rw, y, u * 2f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            y += u * 2.9f;
            String nums = Wpn.FAMILY[wp.family] + ": " + wp.dmg + " dmg, " + wp.heat + " heat, range " + wp.rangeText()
                    + (wp.acc != 0 ? ", accuracy " + (wp.acc > 0 ? "+" : "") + wp.acc + "%" : "")
                    + (wp.usesAmmo() ? ", ammo " + un.ammo[m] + "/" + un.maxAmmo(m) : "");
            g.text(Ui.fit(g, nums, u * 2f, rw), rx, y, u * 2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            y += u * 2.6f;
            g.text(Ui.fit(g, wp.desc, u * 1.9f, rw), rx, y, u * 1.9f, 0xFF7E8AA0, Gfx.ALIGN_LEFT, false);
            y += u * 3.6f;
        }
        y += u;
        g.text("Equipment", rx, y, u * 3, Col.TEXT, Gfx.ALIGN_LEFT, true);
        y += u * 4.5f;
        boolean any = false;
        boolean elecDown = b != null && b.map.weather.electronicsDown;
        for (int e = 0; e < un.equip.length; e++) {
            if (un.equip[e] < 0) continue;
            any = true;
            Equip eq = Equip.ALL[un.equip[e]];
            String uses = eq.uses > 0 ? "  (" + (b != null ? un.eqUses[e] + " of " : "") + eq.uses + " uses per battle)" : "  (always on)";
            boolean electronic = eq.id == Equip.TARGETING || eq.id == Equip.ECM || eq.id == Equip.SENSORS || eq.id == Equip.UPLINK;
            String state = un.eqBroken[e] ? "  DESTROYED" : (electronic && elecDown ? "  OFFLINE (magnetic storm)" : "");
            g.text(eq.name + uses + state, rx, y, u * 2.4f, un.eqBroken[e] || state.length() > 0 ? Col.BAD : Col.SHIELD, Gfx.ALIGN_LEFT, true);
            y += u * 2.8f;
            y += Ui.wrap(g, eq.desc, rx, y, u * 1.95f, rw, Col.TEXT_DIM) + u * 0.6f;
        }
        if (!any) g.text("None", rx, y, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
    }

    public static boolean closeHit(float x, float y) {
        return x >= closeX && x <= closeX + closeW && y >= closeY && y <= closeY + closeH;
    }
}
