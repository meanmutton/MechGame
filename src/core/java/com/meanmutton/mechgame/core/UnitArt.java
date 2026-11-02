package com.meanmutton.mechgame.core;

/** Procedural top-down unit drawings, team colors, and the hit-location diagram. */
public final class UnitArt {
    private UnitArt() {}

    public static final int PLAYER_COLOR = 0xFF3F8EF0, ENEMY_COLOR = 0xFFE0503C;

    public static int teamColor(int team) {
        return team == Battle.PLAYER ? PLAYER_COLOR : ENEMY_COLOR;
    }

    private static final float[] PX = new float[12], PY = new float[12];

    /**
     * Draws a unit centered at (x, y) facing angle (degrees, 0 = +x), in units where a hex has radius 1.
     * scale 1 fills most of a hex.
     */
    public static void draw(Gfx g, Unit u, float x, float y, float angle, int team, float scale, float time, float condition) {
        drawChassis(g, u.ch, u.weapon, x, y, angle, teamColor(team), scale, time, condition, u.type == UnitType.INFANTRY ? u.firepowerScale() : 1f);
    }

    public static void drawChassis(Gfx g, Chassis ch, int[] weapons, float x, float y, float angle, int team, float scale,
                                   float time, float condition, float strength) {
        int body = Col.lerp(team, 0xFF8A93A0, 0.3f);
        int dark = Col.darker(body, 0.45f);
        int light = Col.lighter(body, 0.3f);
        float lw = 0.035f;
        g.save();
        g.translate(x, y);
        g.scale(scale, scale);
        // shadow
        g.save();
        g.translate(0.06f, 0.08f);
        g.scale(1f, 0.85f);
        g.fillCircle(0, 0, ch.type == UnitType.VTOL ? 0.35f : 0.55f, 0x55000000);
        g.restore();
        if (ch.type == UnitType.VTOL) {
            g.save();
            g.translate(0, -0.22f);
        }
        g.rotate(angle);
        switch (ch.type) {
            case UnitType.INFANTRY: infantry(g, team, strength, time); break;
            case UnitType.TRACKED: tank(g, ch, weapons, body, dark, light, lw); break;
            case UnitType.HOVER: hover(g, ch, body, dark, light, lw, time); break;
            case UnitType.VTOL: vtol(g, ch, body, dark, light, lw, time); break;
            case UnitType.WHEELED: car(g, ch, body, dark, light, lw); break;
            default: mech(g, ch, weapons, body, dark, light, lw, time); break;
        }
        g.restore();
        if (ch.type == UnitType.VTOL) g.restore();
    }

    private static void rect(Gfx g, float x0, float y0, float x1, float y1, int c) {
        g.fillRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0), c);
    }

    private static void mech(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw, float time) {
        float s = ch.type == UnitType.LIGHT_MECH ? 0.62f : (ch.type == UnitType.MEDIUM_MECH ? 0.72f : (ch.type == UnitType.HEAVY_MECH ? 0.8f : 0.88f));
        g.scale(s, s);
        // legs (stride animates slightly)
        float stride = (float) Math.sin(time * 3 + ch.id) * 0.06f;
        g.fillRoundRect(-0.28f + stride, -0.38f, 0.5f, 0.2f, 0.08f, dark);
        g.fillRoundRect(-0.28f - stride, 0.18f, 0.5f, 0.2f, 0.08f, dark);
        // arms and their weapons
        for (int side = -1; side <= 1; side += 2) {
            float ay = side * 0.5f;
            int mountLoc = side < 0 ? UnitType.M_LARM : UnitType.M_RARM;
            int w = weaponAt(ch, weapons, mountLoc);
            g.fillRoundRect(-0.18f, ay - 0.1f, 0.48f, 0.2f, 0.07f, Col.darker(body, 0.2f));
            if (w >= 0) {
                Wpn wp = Wpn.ALL[w];
                if (wp.isMelee()) {
                    int blade = wp.id == Wpn.HAMMER ? 0xFFB0B8C8 : 0xFFE0E8F0;
                    if (wp.id == Wpn.BATTLE_AXE || wp.id == Wpn.GREAT_AXE) {
                        g.line(0.25f, ay, 0.75f, ay, 0.06f, 0xFF6A5A4A);
                        PX[0] = 0.62f; PY[0] = ay; PX[1] = 0.8f; PY[1] = ay - side * 0.0f - 0.18f; PX[2] = 0.86f; PY[2] = ay + 0.18f;
                        g.fillPoly(PX, PY, 3, blade);
                    } else if (wp.id == Wpn.HAMMER) {
                        g.line(0.25f, ay, 0.7f, ay, 0.06f, 0xFF6A5A4A);
                        g.fillRoundRect(0.62f, ay - 0.14f, 0.18f, 0.28f, 0.04f, blade);
                    } else {
                        g.line(0.28f, ay, wp.cls >= 2 ? 0.95f : 0.7f, ay, 0.07f, blade);
                        g.line(0.28f, ay, wp.cls >= 2 ? 0.95f : 0.7f, ay, 0.025f, 0xFF7AD8FF);
                    }
                } else {
                    float len = 0.42f + wp.cls * 0.12f;
                    g.line(0.25f, ay, 0.25f + len, ay, 0.05f + wp.cls * 0.02f, 0xFF2A2E36);
                    if (wp.family == Wpn.F_MISSILE) g.fillRoundRect(0.12f, ay - 0.12f, 0.22f, 0.24f, 0.04f, 0xFF3A3E46);
                }
            }
        }
        // torso
        g.fillRoundRect(-0.34f, -0.34f, 0.6f, 0.68f, 0.14f, body);
        g.fillRoundRect(-0.3f, -0.3f, 0.26f, 0.6f, 0.1f, Col.darker(body, 0.15f));
        g.strokeRoundRect(-0.34f, -0.34f, 0.6f, 0.68f, 0.14f, lw, dark);
        // core-mounted weapons
        int coreW = weaponAt(ch, weapons, UnitType.M_CORE);
        if (coreW >= 0) {
            Wpn wp = Wpn.ALL[coreW];
            if (wp.family == Wpn.F_ARTILLERY) g.line(-0.1f, 0, 0.85f, 0, 0.12f, 0xFF2A2E36);
            else if (wp.family == Wpn.F_MISSILE) {
                g.fillRoundRect(-0.25f, -0.2f, 0.22f, 0.4f, 0.04f, 0xFF3A3E46);
                for (int i = 0; i < 3; i++) g.fillCircle(-0.14f, -0.12f + i * 0.12f, 0.035f, 0xFF15181E);
            }
        }
        // cockpit glass
        g.fillCircle(0.12f, 0, 0.13f, 0xFF7ACBFF);
        g.fillCircle(0.15f, -0.04f, 0.05f, 0xCCFFFFFF);
        if (ch.role == Chassis.R_COMMAND) g.line(-0.2f, -0.25f, -0.45f, -0.45f, 0.03f, 0xFFDDDDDD);
    }

    private static int weaponAt(Chassis ch, int[] weapons, int loc) {
        int best = -1, bestCls = -1;
        for (int i = 0; i < ch.mounts.length && i < weapons.length; i++) {
            if (ch.mounts[i].loc == loc && weapons[i] >= 0 && Wpn.ALL[weapons[i]].cls > bestCls) {
                best = weapons[i];
                bestCls = Wpn.ALL[weapons[i]].cls;
            }
        }
        return best;
    }

    private static void tank(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw) {
        float big = ch.role == Chassis.R_BRAWLER ? 1.08f : 1f;
        g.scale(big, big);
        g.fillRoundRect(-0.55f, -0.44f, 1.1f, 0.2f, 0.06f, 0xFF2A2D33);
        g.fillRoundRect(-0.55f, 0.24f, 1.1f, 0.2f, 0.06f, 0xFF2A2D33);
        for (int i = 0; i < 6; i++) {
            g.fillRect(-0.5f + i * 0.18f, -0.44f, 0.05f, 0.2f, 0xFF4A4F58);
            g.fillRect(-0.5f + i * 0.18f, 0.24f, 0.05f, 0.2f, 0xFF4A4F58);
        }
        g.fillRoundRect(-0.5f, -0.28f, 1.0f, 0.56f, 0.08f, body);
        g.strokeRoundRect(-0.5f, -0.28f, 1.0f, 0.56f, 0.08f, lw, dark);
        g.fillRect(0.3f, -0.24f, 0.14f, 0.48f, Col.darker(body, 0.15f));
        if (ch.role == Chassis.R_ARTILLERY) {
            g.fillRoundRect(-0.38f, -0.22f, 0.46f, 0.44f, 0.08f, light);
            g.line(-0.05f, 0, 0.95f, 0, 0.12f, 0xFF2A2E36);
        } else if (ch.role == Chassis.R_SUPPORT) {
            g.fillRoundRect(-0.3f, -0.24f, 0.44f, 0.48f, 0.06f, 0xFF3A3E46);
            for (int i = 0; i < 2; i++) for (int j = 0; j < 3; j++) g.fillCircle(-0.18f + i * 0.18f, -0.14f + j * 0.14f, 0.045f, 0xFF15181E);
        } else {
            g.line(0, 0, 0.82f, 0, ch.role == Chassis.R_BRAWLER ? 0.12f : 0.09f, 0xFF2A2E36);
            g.fillCircle(0, 0, 0.26f, light);
            g.strokeCircle(0, 0, 0.26f, lw, dark);
            g.fillCircle(-0.08f, 0.06f, 0.06f, dark);
        }
    }

    private static void hover(Gfx g, Chassis ch, int body, int dark, int light, float lw, float time) {
        g.fillRoundRect(-0.56f, -0.4f, 1.12f, 0.8f, 0.36f, 0xFF20242A);
        g.fillRoundRect(-0.5f, -0.34f, 1.0f, 0.68f, 0.3f, body);
        g.strokeRoundRect(-0.5f, -0.34f, 1.0f, 0.68f, 0.3f, lw, dark);
        float fan = time * 12;
        for (int s = -1; s <= 1; s += 2) {
            g.fillCircle(-0.3f, s * 0.18f, 0.12f, 0xFF15181E);
            g.line(-0.3f - (float) Math.cos(fan) * 0.1f, s * 0.18f - (float) Math.sin(fan) * 0.1f,
                    -0.3f + (float) Math.cos(fan) * 0.1f, s * 0.18f + (float) Math.sin(fan) * 0.1f, 0.03f, 0xFF7A8090);
        }
        if (ch.role == Chassis.R_SUPPORT) {
            g.fillRoundRect(-0.05f, -0.22f, 0.36f, 0.44f, 0.06f, 0xFF3A3E46);
            for (int i = 0; i < 3; i++) g.fillCircle(0.06f + (i % 2) * 0.12f, -0.12f + i * 0.12f, 0.04f, 0xFF15181E);
        } else {
            g.line(0.1f, 0, 0.75f, 0, 0.07f, 0xFF2A2E36);
            g.fillCircle(0.1f, 0, 0.17f, light);
            g.strokeCircle(0.1f, 0, 0.17f, lw, dark);
        }
    }

    private static void vtol(Gfx g, Chassis ch, int body, int dark, int light, float lw, float time) {
        g.fillRect(-0.75f, -0.05f, 0.6f, 0.1f, dark);
        g.fillRect(-0.8f, -0.16f, 0.1f, 0.32f, dark);
        g.save();
        g.scale(1f, 0.42f);
        g.fillCircle(0.05f, 0, 0.42f, body);
        g.strokeCircle(0.05f, 0, 0.42f, lw * 2, dark);
        g.restore();
        g.fillCircle(0.3f, 0, 0.1f, 0xFF7ACBFF);
        if (ch.role != Chassis.R_SCOUT) {
            g.fillRect(-0.05f, -0.32f, 0.18f, 0.64f, Col.darker(body, 0.2f));
            g.fillCircle(0.06f, -0.32f, 0.07f, 0xFF3A3E46);
            g.fillCircle(0.06f, 0.32f, 0.07f, 0xFF3A3E46);
        }
        // rotor
        g.fillCircle(0.0f, 0, 0.62f, 0x22FFFFFF);
        float a = time * 25;
        for (int i = 0; i < 2; i++) {
            float ca = (float) Math.cos(a + i * Math.PI / 2) * 0.62f, sa = (float) Math.sin(a + i * Math.PI / 2) * 0.62f;
            g.line(-ca, -sa, ca, sa, 0.04f, 0xAA20242A);
        }
        g.fillCircle(0, 0, 0.06f, 0xFF20242A);
    }

    private static void car(Gfx g, Chassis ch, int body, int dark, int light, float lw) {
        for (int i = 0; i < 3; i++) {
            float wx = -0.35f + i * 0.3f;
            g.fillRoundRect(wx - 0.09f, -0.42f, 0.18f, 0.12f, 0.04f, 0xFF15181E);
            g.fillRoundRect(wx - 0.09f, 0.3f, 0.18f, 0.12f, 0.04f, 0xFF15181E);
        }
        g.fillRoundRect(-0.5f, -0.32f, 1.0f, 0.64f, 0.12f, body);
        g.strokeRoundRect(-0.5f, -0.32f, 1.0f, 0.64f, 0.12f, lw, dark);
        g.fillRoundRect(0.22f, -0.26f, 0.22f, 0.52f, 0.06f, Col.darker(body, 0.2f));
        if (ch.role == Chassis.R_ARTILLERY) {
            g.line(-0.35f, 0, 0.75f, 0, 0.1f, 0xFF2A2E36);
            g.fillRoundRect(-0.45f, -0.2f, 0.36f, 0.4f, 0.06f, light);
        } else if (ch.role == Chassis.R_SUPPORT) {
            g.fillRoundRect(-0.3f, -0.2f, 0.4f, 0.4f, 0.05f, 0xFF3A3E46);
            for (int i = 0; i < 4; i++) g.fillCircle(-0.2f + (i % 2) * 0.2f, -0.1f + (i / 2) * 0.2f, 0.045f, 0xFF15181E);
        } else {
            g.line(-0.05f, 0, 0.62f, 0, 0.06f, 0xFF2A2E36);
            g.fillCircle(-0.05f, 0, 0.16f, light);
            g.strokeCircle(-0.05f, 0, 0.16f, lw, dark);
        }
    }

    private static void infantry(Gfx g, int team, float strength, float time) {
        int n = Math.max(1, Math.round(5 * strength));
        float[][] pos = {{0.25f, 0}, {-0.05f, -0.3f}, {-0.05f, 0.3f}, {-0.35f, -0.12f}, {-0.35f, 0.18f}};
        for (int i = 0; i < n; i++) {
            float bob = (float) Math.sin(time * 4 + i) * 0.02f;
            float x = pos[i][0], y = pos[i][1] + bob;
            g.fillCircle(x, y, 0.13f, 0xFF20242A);
            g.fillCircle(x, y, 0.1f, Col.lerp(team, 0xFF6A7A5A, 0.35f));
            g.line(x, y, x + 0.2f, y + 0.04f, 0.03f, 0xFF15181E);
        }
    }

    // =====================================================================
    // Hit-location diagram
    // =====================================================================

    /** Grid cells {col, row, colSpan, rowSpan} on a 5x5 grid for each location. */
    private static int[][] layout(int type) {
        if (type == UnitType.INFANTRY) return new int[][]{{1, 1, 3, 3}};
        if (UnitType.isMech(type)) return new int[][]{
                {2, 0, 1, 1},   // cockpit
                {1, 1, 3, 2},   // core
                {2, 3, 1, 1},   // back
                {0, 1, 1, 2},   // left arm
                {4, 1, 1, 2},   // right arm
                {1, 3, 1, 2},   // left leg
                {3, 3, 1, 2}};  // right leg
        return new int[][]{
                {1, 0, 3, 1},   // front
                {0, 1, 1, 3},   // left
                {4, 1, 1, 3},   // right
                {1, 4, 3, 1},   // rear
                {1, 1, 3, 3}};  // turret/rotor
    }

    public static void doll(Gfx g, Unit u, float x, float y, float w, float h, float textSize, int highlightLoc) {
        int[][] lay = layout(u.type);
        float cw = w / 5f, chh = h / 5f, gap = Math.min(cw, chh) * 0.08f;
        String[] names = UnitType.locShort(u.type);
        for (int i = 0; i < lay.length; i++) {
            int[] c = lay[i];
            float bx = x + c[0] * cw + gap, by = y + c[1] * chh + gap;
            float bw = c[2] * cw - gap * 2, bh = c[3] * chh - gap * 2;
            if (!UnitType.isMech(u.type) && u.type != UnitType.INFANTRY && i == UnitType.V_TOP) {
                bx += cw * 0.35f;
                by += chh * 0.35f;
                bw -= cw * 0.7f;
                bh -= chh * 0.7f;
            }
            int il = UnitType.infraLoc(u.type, i);
            float inf = u.infraMax[il] <= 0 ? 1f : u.infra[il] / (float) u.infraMax[il];
            float arm = u.armorMax[i] <= 0 ? 0 : u.armor[i] / (float) u.armorMax[i];
            boolean dead = u.infra[il] <= 0;
            int fill = dead ? 0xFF1A1A1A : Col.lerp(0xFF8A2A22, 0xFF2E6A3A, Math.min(1f, inf));
            g.fillRoundRect(bx, by, bw, bh, gap * 1.5f, fill);
            // armor as a lighter overlay from the bottom
            if (!dead && arm > 0) g.fillRoundRect(bx, by + bh * (1 - arm), bw, bh * arm, gap * 1.5f, 0x3388C8FF);
            g.strokeRoundRect(bx, by, bw, bh, gap * 1.5f, Math.max(1, gap * 0.6f), i == highlightLoc ? Col.ACCENT : 0x66FFFFFF);
            float cx = bx + bw / 2, cy = by + bh / 2;
            float ts = Math.min(textSize, Math.min(bw, bh) * 0.42f);
            if (dead) {
                g.line(bx + gap, by + gap, bx + bw - gap, by + bh - gap, Math.max(1, gap * 0.5f), 0xFFAA3A30);
                g.line(bx + bw - gap, by + gap, bx + gap, by + bh - gap, Math.max(1, gap * 0.5f), 0xFFAA3A30);
            } else {
                g.text(Integer.toString(u.armor[i]), cx, cy - ts * 0.35f, ts, 0xFFFFFFFF, Gfx.ALIGN_CENTER, true);
                if (u.infraMax[i] > 0) g.text(u.infra[il] + "", cx, cy + ts * 0.6f, ts * 0.62f, 0xFFFFD27A, Gfx.ALIGN_CENTER, false);
            }
            if (bh > ts * 2.6f && bw > ts * 2f) g.text(names[i], cx, by + ts * 0.5f, ts * 0.45f, 0xAAFFFFFF, Gfx.ALIGN_CENTER, false);
        }
    }
}
