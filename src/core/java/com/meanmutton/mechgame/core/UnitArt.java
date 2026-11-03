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
        boolean parts = ch.look != null;
        switch (ch.type) {
            case UnitType.INFANTRY:
                if (parts) squad(g, ch, team, strength, time);
                else infantry(g, ch, team, strength, time);
                break;
            case UnitType.TRACKED:
                if (parts) tracked(g, ch, weapons, body, dark, light, lw);
                else tank(g, ch, weapons, body, dark, light, lw);
                break;
            case UnitType.HOVER:
                if (parts) skimmer(g, ch, weapons, body, dark, light, lw, time);
                else hover(g, ch, body, dark, light, lw, time);
                break;
            case UnitType.VTOL:
                if (parts) flyer(g, ch, weapons, body, dark, light, lw, time);
                else vtol(g, ch, body, dark, light, lw, time);
                break;
            case UnitType.WHEELED:
                if (parts) wheeled(g, ch, weapons, body, dark, light, lw);
                else car(g, ch, body, dark, light, lw);
                break;
            case UnitType.EMPLACEMENT: emplacement(g, ch, body, dark, light, lw); break;
            default: mech(g, ch, weapons, body, dark, light, lw, time); break;
        }
        g.restore();
        if (ch.type == UnitType.VTOL) g.restore();
    }

    private static void rect(Gfx g, float x0, float y0, float x1, float y1, int c) {
        g.fillRect(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0), c);
    }

    // =====================================================================
    // Per-design looks. Every mech and vehicle combines these parts differently.
    // =====================================================================

    // mech parts
    private static final int T_BOX = 0, T_WEDGE = 1, T_HEX = 2, T_WIDE = 3, T_DOME = 4, T_ARROW = 5, T_SPINE = 6;
    private static final int S_NONE = 0, S_PADS = 1, S_SPIKES = 2, S_ROUND = 3;
    private static final int B_NONE = 0, B_RACK = 1, B_FINS = 2, B_RADAR = 3, B_THRUST = 4, B_TWINRACK = 5, B_ANTENNA = 6,
            B_CANNON = 7, B_RADIATOR = 8;
    private static final int L_HUMAN = 0, L_BIRD = 1, L_STOMP = 2, L_QUAD = 3;
    private static final int C_ROUND = 0, C_VISOR = 1, C_OFFSET = 2, C_TWIN = 3, C_SLIT = 4;

    /** Accent color for a design: its faction's for faction designs, otherwise the look's own. */
    static int factionAccent(Chassis ch, int fallback) {
        int ex = Faction.exclusive(ch.factions);
        if (ch.look == null) return fallback;
        if (ex >= 0) return Faction.ALL[ex].color;
        if (ch.factions == Faction.blocMask(Faction.BLOC_ACCORD)) return 0xFFE8F0FF;
        if (ch.factions == Faction.blocMask(Faction.BLOC_PACT)) return 0xFF8A8F98;
        return fallback;
    }

    /** {torso, shoulders, back, legs, cockpit, accent color} by chassis id (18..32). */
    private static final int[][] MECH_LOOK = {
            {T_WEDGE, S_NONE, B_ANTENNA, L_BIRD, C_VISOR, 0xFFF5D33B},     // Skitter
            {T_HEX, S_ROUND, B_THRUST, L_HUMAN, C_TWIN, 0xFFE84A3C},       // Needle
            {T_DOME, S_NONE, B_RADAR, L_BIRD, C_ROUND, 0xFF45C8F0},        // Lantern
            {T_BOX, S_PADS, B_NONE, L_HUMAN, C_ROUND, 0xFFEDEDED},         // Halberd
            {T_WEDGE, S_ROUND, B_THRUST, L_BIRD, C_VISOR, 0xFFFF8E2B},     // Ricochet
            {T_WIDE, S_PADS, B_RACK, L_HUMAN, C_OFFSET, 0xFF9BE043},       // Cinder
            {T_HEX, S_SPIKES, B_FINS, L_HUMAN, C_TWIN, 0xFFB07AFF},        // Sabre Dancer
            {T_BOX, S_SPIKES, B_FINS, L_STOMP, C_VISOR, 0xFFDDDDDD},       // Bastille
            {T_WEDGE, S_PADS, B_RADAR, L_HUMAN, C_OFFSET, 0xFF3F8EF0},     // Overwatch
            {T_HEX, S_SPIKES, B_THRUST, L_STOMP, C_TWIN, 0xFFE84A3C},      // Cleaver
            {T_WIDE, S_ROUND, B_NONE, L_STOMP, C_ROUND, 0xFFF5D33B},       // Rainmaker
            {T_BOX, S_PADS, B_FINS, L_STOMP, C_VISOR, 0xFFB0B8C0},         // Monolith
            {T_DOME, S_SPIKES, B_THRUST, L_STOMP, C_TWIN, 0xFFFF8E2B},     // Pulverizer
            {T_WIDE, S_ROUND, B_TWINRACK, L_STOMP, C_VISOR, 0xFF9BE043},   // Skyfall
            {T_HEX, S_PADS, B_RADAR, L_STOMP, C_ROUND, 0xFF45C8F0},        // Aegis
    };

    private static void poly(Gfx g, int color, float... pts) {
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            PX[i] = pts[i * 2];
            PY[i] = pts[i * 2 + 1];
        }
        g.fillPoly(PX, PY, n, color);
    }

    private static void mech(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw, float time) {
        float s = ch.type == UnitType.LIGHT_MECH ? 0.74f : (ch.type == UnitType.MEDIUM_MECH ? 0.85f : (ch.type == UnitType.HEAVY_MECH ? 0.94f : 1.02f));
        int[] look = ch.look != null ? ch.look : (ch.id >= 18 && ch.id - 18 < MECH_LOOK.length ? MECH_LOOK[ch.id - 18] : MECH_LOOK[3]);
        int torso = look[0], shoulders = look[1], back = look[2], legs = look[3], cockpit = look[4];
        int accent = factionAccent(ch, look.length > 5 ? look[5] : 0xFFEDEDED);
        g.scale(s, s);
        float stride = (float) Math.sin(time * 3 + ch.id) * 0.06f;
        // legs
        if (legs == L_BIRD) {
            for (int side = -1; side <= 1; side += 2) {
                float st = side * stride;
                g.line(-0.1f, side * 0.2f, -0.42f + st, side * 0.3f, 0.12f, dark);
                g.line(-0.42f + st, side * 0.3f, -0.2f + st, side * 0.36f, 0.1f, dark);
                poly(g, Col.darker(dark, 0.2f), -0.24f + st, side * 0.28f, -0.06f + st, side * 0.32f, -0.08f + st, side * 0.44f, -0.26f + st, side * 0.42f);
            }
        } else if (legs == L_QUAD) {
            // four legs, splayed like a spider tank
            for (int fx = -1; fx <= 1; fx += 2) {
                for (int side = -1; side <= 1; side += 2) {
                    float st = side * fx * stride;
                    g.line(fx * 0.1f, side * 0.16f, fx * 0.42f + st, side * 0.44f, 0.11f, dark);
                    g.fillRoundRect(fx * 0.42f + st - 0.09f, side * 0.44f - 0.07f, 0.18f, 0.14f, 0.04f, Col.darker(dark, 0.25f));
                }
            }
        } else if (legs == L_STOMP) {
            g.fillRoundRect(-0.32f + stride, -0.44f, 0.58f, 0.26f, 0.06f, dark);
            g.fillRoundRect(-0.32f - stride, 0.18f, 0.58f, 0.26f, 0.06f, dark);
            g.fillRect(0.14f + stride, -0.44f, 0.1f, 0.26f, Col.darker(dark, 0.3f));
            g.fillRect(0.14f - stride, 0.18f, 0.1f, 0.26f, Col.darker(dark, 0.3f));
        } else {
            g.fillRoundRect(-0.28f + stride, -0.38f, 0.5f, 0.2f, 0.08f, dark);
            g.fillRoundRect(-0.28f - stride, 0.18f, 0.5f, 0.2f, 0.08f, dark);
        }
        // back equipment (drawn under the torso edge)
        switch (back) {
            case B_RACK:
            case B_TWINRACK: {
                int racks = back == B_TWINRACK ? 2 : 1;
                for (int i = 0; i < racks; i++) {
                    float ry = racks == 1 ? -0.17f : (i == 0 ? -0.36f : 0.04f);
                    g.fillRoundRect(-0.5f, ry, 0.26f, 0.34f, 0.04f, 0xFF3A3E46);
                    for (int k = 0; k < 4; k++) g.fillCircle(-0.43f + (k % 2) * 0.12f, ry + 0.09f + (k / 2) * 0.16f, 0.035f, 0xFF15181E);
                }
                break;
            }
            case B_FINS:
                for (int side = -1; side <= 1; side += 2) poly(g, Col.darker(body, 0.3f), -0.3f, side * 0.12f, -0.6f, side * 0.3f, -0.55f, side * 0.38f, -0.25f, side * 0.26f);
                break;
            case B_RADAR:
                g.fillCircle(-0.38f, 0.12f, 0.17f, 0xFFC8CCD4);
                g.strokeCircle(-0.38f, 0.12f, 0.17f, lw, dark);
                g.line(-0.38f, 0.12f, -0.38f + (float) Math.cos(time * 2) * 0.15f, 0.12f + (float) Math.sin(time * 2) * 0.15f, 0.03f, dark);
                break;
            case B_THRUST:
                for (int side = -1; side <= 1; side += 2) {
                    g.fillRoundRect(-0.52f, side * 0.17f - 0.08f, 0.2f, 0.16f, 0.05f, 0xFF3A3E46);
                    g.fillRadial(-0.56f, side * 0.17f, 0.1f, 0xCCFFAA44, 0x00FF6A00);
                }
                break;
            case B_CANNON:
                // a long gun over one shoulder
                g.fillRoundRect(-0.46f, -0.36f, 0.3f, 0.2f, 0.04f, 0xFF3A3E46);
                g.line(-0.2f, -0.26f, 0.72f, -0.26f, 0.09f, 0xFF2A2E36);
                g.fillRect(0.64f, -0.31f, 0.09f, 0.1f, 0xFF1A1E24);
                break;
            case B_RADIATOR:
                for (int i = 0; i < 4; i++) g.fillRect(-0.58f + i * 0.07f, -0.3f, 0.04f, 0.6f, Col.lerp(0xFFFF8A4A, 0xFF5A3020, i * 0.25f));
                break;
            case B_ANTENNA:
                g.line(-0.2f, -0.18f, -0.6f, -0.38f, 0.03f, 0xFFDDDDDD);
                g.line(-0.2f, 0.1f, -0.55f, 0.32f, 0.03f, 0xFFDDDDDD);
                g.fillCircle(-0.6f, -0.38f, 0.04f, 0xFFFF4A4A);
                break;
            default:
                break;
        }
        // arms and the weapons actually mounted there
        for (int side = -1; side <= 1; side += 2) {
            float ay = side * 0.5f;
            int mountLoc = side < 0 ? UnitType.M_LARM : UnitType.M_RARM;
            int w = weaponAt(ch, weapons, mountLoc);
            g.fillRoundRect(-0.18f, ay - 0.1f, 0.48f, 0.2f, 0.07f, Col.darker(body, 0.2f));
            if (w >= 0) drawArmWeapon(g, Wpn.ALL[w], ay);
        }
        // torso
        int tc = body, tcd = Col.darker(body, 0.15f);
        switch (torso) {
            case T_WEDGE:
                poly(g, tc, -0.34f, -0.34f, 0.08f, -0.32f, 0.36f, 0f, 0.08f, 0.32f, -0.34f, 0.34f);
                poly(g, tcd, -0.3f, -0.28f, -0.06f, -0.27f, -0.06f, 0.27f, -0.3f, 0.28f);
                break;
            case T_HEX:
                poly(g, tc, -0.32f, -0.2f, -0.14f, -0.36f, 0.16f, -0.36f, 0.3f, -0.18f, 0.3f, 0.18f, 0.16f, 0.36f, -0.14f, 0.36f, -0.32f, 0.2f);
                poly(g, tcd, -0.28f, -0.17f, -0.12f, -0.3f, -0.04f, -0.3f, -0.04f, 0.3f, -0.12f, 0.3f, -0.28f, 0.17f);
                break;
            case T_WIDE:
                g.fillRoundRect(-0.3f, -0.42f, 0.54f, 0.84f, 0.1f, tc);
                g.fillRoundRect(-0.26f, -0.38f, 0.22f, 0.76f, 0.08f, tcd);
                break;
            case T_DOME:
                g.fillCircle(-0.02f, 0, 0.38f, tc);
                g.fillCircle(-0.1f, 0, 0.26f, tcd);
                break;
            case T_ARROW:
                poly(g, tc, -0.36f, -0.3f, 0.02f, -0.36f, 0.4f, -0.08f, 0.4f, 0.08f, 0.02f, 0.36f, -0.36f, 0.3f, -0.22f, 0f);
                poly(g, tcd, -0.3f, -0.24f, -0.06f, -0.28f, -0.06f, 0.28f, -0.3f, 0.24f, -0.18f, 0f);
                break;
            case T_SPINE:
                g.fillRoundRect(-0.4f, -0.26f, 0.7f, 0.52f, 0.2f, tc);
                g.fillRoundRect(-0.36f, -0.06f, 0.6f, 0.12f, 0.05f, tcd);
                for (int i = 0; i < 4; i++) g.fillCircle(-0.3f + i * 0.14f, 0, 0.045f, Col.darker(tc, 0.35f));
                break;
            default:
                g.fillRoundRect(-0.34f, -0.34f, 0.6f, 0.68f, 0.14f, tc);
                g.fillRoundRect(-0.3f, -0.3f, 0.26f, 0.6f, 0.1f, tcd);
        }
        // accent stripe
        g.fillRect(-0.02f, -0.3f, 0.06f, 0.6f, Col.alpha(accent, 0.9f));
        // shoulders
        for (int side = -1; side <= 1; side += 2) {
            float sy = side * 0.38f;
            switch (shoulders) {
                case S_PADS: g.fillRoundRect(-0.2f, sy - 0.11f, 0.32f, 0.22f, 0.05f, light); break;
                case S_ROUND: g.fillCircle(-0.04f, sy, 0.14f, light); g.strokeCircle(-0.04f, sy, 0.14f, lw, dark); break;
                case S_SPIKES:
                    g.fillRoundRect(-0.2f, sy - 0.11f, 0.32f, 0.22f, 0.05f, light);
                    poly(g, 0xFFD0D4DA, -0.12f, sy + side * 0.08f, -0.02f, sy + side * 0.08f, -0.07f, sy + side * 0.24f);
                    poly(g, 0xFFD0D4DA, 0.02f, sy + side * 0.08f, 0.1f, sy + side * 0.08f, 0.06f, sy + side * 0.22f);
                    break;
                default: break;
            }
        }
        // weapons mounted in the core
        int coreW = weaponAt(ch, weapons, UnitType.M_CORE);
        if (coreW >= 0) {
            Wpn wp = Wpn.ALL[coreW];
            if (wp.family == Wpn.F_ARTILLERY) {
                g.fillRoundRect(-0.25f, -0.13f, 0.34f, 0.26f, 0.05f, 0xFF3A3E46);
                g.line(-0.1f, 0, 0.95f, 0, 0.13f, 0xFF2A2E36);
                g.fillRect(0.8f, -0.08f, 0.1f, 0.16f, 0xFF1A1E24);
            } else if (wp.family == Wpn.F_MISSILE && back != B_RACK && back != B_TWINRACK) {
                g.fillRoundRect(-0.25f, -0.2f, 0.22f, 0.4f, 0.04f, 0xFF3A3E46);
                for (int i = 0; i < 3; i++) g.fillCircle(-0.14f, -0.12f + i * 0.12f, 0.035f, 0xFF15181E);
            } else if (wp.family == Wpn.F_ENERGY || wp.family == Wpn.F_BALLISTIC) {
                g.line(0.15f, 0.16f, 0.5f, 0.16f, 0.05f, 0xFF2A2E36);
            }
        }
        // cockpit
        switch (cockpit) {
            case C_VISOR:
                g.fillRoundRect(0.1f, -0.16f, 0.1f, 0.32f, 0.04f, 0xFF7ACBFF);
                g.fillRect(0.12f, -0.12f, 0.03f, 0.12f, 0xCCFFFFFF);
                break;
            case C_OFFSET:
                g.fillCircle(0.1f, -0.13f, 0.11f, 0xFF7ACBFF);
                g.fillCircle(0.12f, -0.16f, 0.04f, 0xCCFFFFFF);
                break;
            case C_TWIN:
                g.fillCircle(0.14f, -0.09f, 0.06f, 0xFFFF6A5A);
                g.fillCircle(0.14f, 0.09f, 0.06f, 0xFFFF6A5A);
                break;
            case C_SLIT:
                g.fillRect(0.16f, -0.18f, 0.05f, 0.36f, Col.alpha(accent, 0.95f));
                g.fillRect(0.12f, -0.2f, 0.03f, 0.4f, 0xFF1A1E24);
                break;
            default:
                g.fillCircle(0.12f, 0, 0.13f, 0xFF7ACBFF);
                g.fillCircle(0.15f, -0.04f, 0.05f, 0xCCFFFFFF);
        }
    }

    private static void drawArmWeapon(Gfx g, Wpn wp, float ay) {
        if (wp.isMelee()) {
            int blade = wp.base == Wpn.HAMMER ? 0xFFB0B8C8 : 0xFFE0E8F0;
            if (wp.base == Wpn.BATTLE_AXE || wp.base == Wpn.GREAT_AXE) {
                g.line(0.25f, ay, 0.78f, ay, 0.06f, 0xFF6A5A4A);
                float sz = wp.base == Wpn.GREAT_AXE ? 0.24f : 0.18f;
                poly(g, blade, 0.6f, ay, 0.8f, ay - sz, 0.88f, ay + sz);
            } else if (wp.base == Wpn.HAMMER) {
                g.line(0.25f, ay, 0.7f, ay, 0.06f, 0xFF6A5A4A);
                g.fillRoundRect(0.62f, ay - 0.15f, 0.2f, 0.3f, 0.04f, blade);
            } else {
                float len = wp.cls >= 2 ? 0.95f : 0.7f;
                g.line(0.28f, ay, len, ay, 0.07f, blade);
                g.line(0.28f, ay, len, ay, 0.025f, wp.base == Wpn.ARC_SWORD ? 0xFF7AD8FF : 0xFFC0C8D0);
            }
        } else if (wp.family == Wpn.F_MISSILE) {
            g.fillRoundRect(0.06f, ay - 0.14f, 0.34f, 0.28f, 0.04f, 0xFF3A3E46);
            for (int k = 0; k < 4; k++) g.fillCircle(0.15f + (k % 2) * 0.14f, ay - 0.06f + (k / 2) * 0.12f, 0.035f, 0xFF15181E);
        } else if (wp.family == Wpn.F_ARTILLERY) {
            g.line(0.2f, ay, 0.95f, ay, 0.1f, 0xFF2A2E36);
        } else if (wp.family == Wpn.F_ENERGY) {
            float len = 0.42f + wp.cls * 0.12f;
            g.line(0.25f, ay, 0.25f + len, ay, 0.05f + wp.cls * 0.02f, 0xFF2A2E36);
            g.fillCircle(0.25f + len, ay, 0.03f + wp.cls * 0.012f, wp.base == Wpn.PLASMA ? 0xFFFF7AD8 : 0xFFFF6A5A);
        } else {
            float len = 0.42f + wp.cls * 0.14f;
            g.line(0.25f, ay, 0.25f + len, ay, 0.07f + wp.cls * 0.025f, 0xFF2A2E36);
            if (wp.base == Wpn.RAIL) g.line(0.3f, ay, 0.25f + len, ay, 0.025f, 0xFF9AC8FF);
            if (wp.base == Wpn.CHAINGUN || wp.base == Wpn.FLAK) g.fillCircle(0.25f + len * 0.3f, ay, 0.07f, 0xFF4A4E56);
        }
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

    // ---------------- vehicles ----------------

    /** Fixed defenses: a sandbagged concrete pad with a weapon turret on top. */
    private static void emplacement(Gfx g, Chassis ch, int body, int dark, int light, float lw) {
        // octagonal concrete pad ringed with sandbags
        float r = 0.56f;
        float[] xs = new float[8], ys = new float[8];
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 8 + i * Math.PI / 4;
            xs[i] = (float) Math.cos(a) * r;
            ys[i] = (float) Math.sin(a) * r;
        }
        g.fillPoly(xs, ys, 8, 0xFF6E6A60);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            g.fillCircle((float) Math.cos(a) * 0.5f, (float) Math.sin(a) * 0.5f, 0.09f, 0xFF9C8F6E);
        }
        g.fillCircle(0, 0, 0.42f, 0xFF58554E);
        switch (ch.id) {
            case 34: // Missile Bunker: squat box with a launcher grid
                g.fillRoundRect(-0.3f, -0.3f, 0.6f, 0.6f, 0.06f, body);
                g.strokeRoundRect(-0.3f, -0.3f, 0.6f, 0.6f, 0.06f, lw, dark);
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) g.fillCircle(-0.16f + i * 0.16f, -0.16f + j * 0.16f, 0.05f, 0xFF15181E);
                break;
            case 35: // Laser Turret: dome with a lens barrel and power cables
                g.line(-0.42f, -0.3f, -0.15f, -0.1f, 0.04f, 0xFF2E86C8);
                g.line(-0.42f, 0.3f, -0.15f, 0.1f, 0.04f, 0xFF2E86C8);
                g.fillCircle(0, 0, 0.27f, light);
                g.strokeCircle(0, 0, 0.27f, lw, dark);
                g.line(0.1f, 0, 0.62f, 0, 0.1f, 0xFF3A3E46);
                g.fillCircle(0.62f, 0, 0.07f, 0xFF7FE0FF);
                break;
            default: // Gun Emplacement: armored casemate with a long cannon
                g.fillRoundRect(-0.28f, -0.26f, 0.5f, 0.52f, 0.08f, body);
                g.strokeRoundRect(-0.28f, -0.26f, 0.5f, 0.52f, 0.08f, lw, dark);
                g.line(0.1f, 0, 0.78f, 0, 0.13f, 0xFF2A2E36);
                g.fillRect(0.7f, -0.08f, 0.1f, 0.16f, 0xFF1A1E24);
                g.fillRect(-0.18f, -0.16f, 0.16f, 0.32f, light);
                break;
        }
    }

    private static void tank(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw) {
        int id = ch.id;
        float big = id == 5 ? 1.08f : 1f;
        g.scale(big, big);
        float len = id == 6 ? 0.62f : 0.55f;
        // tracks
        g.fillRoundRect(-len, -0.44f, len * 2, 0.2f, 0.06f, 0xFF2A2D33);
        g.fillRoundRect(-len, 0.24f, len * 2, 0.2f, 0.06f, 0xFF2A2D33);
        for (int i = 0; i < 7; i++) {
            g.fillRect(-len + 0.05f + i * (len * 2 - 0.1f) / 6.5f, -0.44f, 0.05f, 0.2f, 0xFF4A4F58);
            g.fillRect(-len + 0.05f + i * (len * 2 - 0.1f) / 6.5f, 0.24f, 0.05f, 0.2f, 0xFF4A4F58);
        }
        switch (id) {
            case 5: // Breaker: wedge hull, dozer blade, square turret, short thick gun
                poly(g, body, -0.52f, -0.3f, 0.36f, -0.3f, 0.56f, 0f, 0.36f, 0.3f, -0.52f, 0.3f);
                g.fillRoundRect(0.56f, -0.46f, 0.1f, 0.92f, 0.03f, 0xFF8A8F98);
                g.line(-0.05f, 0, 0.62f, 0, 0.17f, 0xFF2A2E36);
                g.fillRoundRect(-0.3f, -0.22f, 0.42f, 0.44f, 0.05f, light);
                g.strokeRoundRect(-0.3f, -0.22f, 0.42f, 0.44f, 0.05f, lw, dark);
                exhaust(g, -0.5f, 0.2f);
                break;
            case 6: // Thunder SPG: long hull, big casemate, huge barrel, rear spades
                g.fillRoundRect(-0.58f, -0.28f, 1.16f, 0.56f, 0.06f, body);
                g.fillRect(-0.72f, -0.32f, 0.12f, 0.1f, 0xFF5A5F68);
                g.fillRect(-0.72f, 0.22f, 0.12f, 0.1f, 0xFF5A5F68);
                g.fillRoundRect(-0.48f, -0.26f, 0.6f, 0.52f, 0.06f, light);
                g.line(0f, 0, 1.0f, 0, 0.14f, 0xFF2A2E36);
                g.fillRect(0.9f, -0.09f, 0.12f, 0.18f, 0xFF1A1E24);
                g.line(-0.4f, -0.2f, -0.6f, -0.42f, 0.025f, 0xFFDDDDDD);
                break;
            case 7: // Quiver: missile carrier with twin launch boxes and radar
                g.fillRoundRect(-0.52f, -0.28f, 1.04f, 0.56f, 0.08f, body);
                for (int k = -1; k <= 1; k += 2) {
                    g.fillRoundRect(-0.36f, k * 0.13f - 0.11f, 0.5f, 0.22f, 0.04f, 0xFF3A3E46);
                    for (int i = 0; i < 4; i++) g.fillCircle(-0.28f + i * 0.12f, k * 0.13f, 0.035f, 0xFF15181E);
                }
                g.fillCircle(0.3f, 0, 0.12f, 0xFFC8CCD4);
                g.strokeCircle(0.3f, 0, 0.12f, lw, dark);
                break;
            default: // Ironhide MBT: classic hull, side skirts, round turret, long gun
                g.fillRoundRect(-0.5f, -0.3f, 1.0f, 0.6f, 0.08f, body);
                g.fillRect(-0.48f, -0.34f, 0.96f, 0.06f, Col.darker(body, 0.3f));
                g.fillRect(-0.48f, 0.28f, 0.96f, 0.06f, Col.darker(body, 0.3f));
                g.line(0, 0, 0.88f, 0, 0.09f, 0xFF2A2E36);
                g.fillCircle(0, 0, 0.27f, light);
                g.strokeCircle(0, 0, 0.27f, lw, dark);
                g.fillCircle(-0.1f, 0.08f, 0.06f, dark);
                exhaust(g, -0.48f, -0.18f);
                exhaust(g, -0.48f, 0.18f);
        }
        g.strokeRoundRect(-len + 0.05f, -0.3f, len * 2 - 0.1f, 0.6f, 0.08f, lw, dark);
    }

    private static void exhaust(Gfx g, float x, float y) {
        g.fillCircle(x, y, 0.05f, 0xFF15181E);
    }

    private static void hover(Gfx g, Chassis ch, int body, int dark, int light, float lw, float time) {
        int id = ch.id;
        float fan = time * 12;
        if (id == 8) { // Dart: small arrowhead skimmer
            poly(g, 0xFF20242A, -0.5f, -0.38f, 0.3f, -0.3f, 0.62f, 0f, 0.3f, 0.3f, -0.5f, 0.38f);
            poly(g, body, -0.44f, -0.32f, 0.26f, -0.25f, 0.54f, 0f, 0.26f, 0.25f, -0.44f, 0.32f);
            fanAt(g, -0.3f, 0, 0.14f, fan);
            g.fillCircle(0.12f, 0, 0.1f, 0xFF7ACBFF);
            g.line(-0.1f, 0.15f, -0.45f, 0.42f, 0.025f, 0xFFDDDDDD);
            g.line(0.1f, -0.15f, 0.55f, -0.15f, 0.04f, 0xFF2A2E36);
        } else if (id == 9) { // Riptide: wedge attack hover with side rocket pods
            poly(g, 0xFF20242A, -0.56f, -0.4f, 0.4f, -0.36f, 0.62f, 0f, 0.4f, 0.36f, -0.56f, 0.4f);
            poly(g, body, -0.5f, -0.34f, 0.36f, -0.3f, 0.54f, 0f, 0.36f, 0.3f, -0.5f, 0.34f);
            for (int s = -1; s <= 1; s += 2) {
                g.fillRoundRect(-0.1f, s * 0.36f - 0.08f, 0.36f, 0.16f, 0.04f, 0xFF3A3E46);
                fanAt(g, -0.34f, s * 0.17f, 0.11f, fan);
            }
            poly(g, light, -0.1f, -0.17f, 0.22f, 0f, -0.1f, 0.17f);
            g.line(0.1f, 0, 0.72f, 0, 0.06f, 0xFF2A2E36);
        } else { // Squall: rounded missile hover with radar
            g.fillRoundRect(-0.56f, -0.4f, 1.12f, 0.8f, 0.36f, 0xFF20242A);
            g.fillRoundRect(-0.5f, -0.34f, 1.0f, 0.68f, 0.3f, body);
            for (int s = -1; s <= 1; s += 2) fanAt(g, -0.32f, s * 0.18f, 0.12f, fan);
            for (int k = -1; k <= 1; k += 2) {
                g.fillRoundRect(-0.06f, k * 0.12f - 0.09f, 0.4f, 0.18f, 0.04f, 0xFF3A3E46);
                for (int i = 0; i < 3; i++) g.fillCircle(0.02f + i * 0.12f, k * 0.12f, 0.03f, 0xFF15181E);
            }
            g.fillCircle(0.4f, 0, 0.08f, 0xFFC8CCD4);
        }
        g.strokeRoundRect(-0.5f, -0.34f, 1.0f, 0.68f, 0.3f, lw * 0.6f, Col.alpha(dark, 0.6f));
    }

    private static void fanAt(Gfx g, float x, float y, float r, float a) {
        g.fillCircle(x, y, r, 0xFF15181E);
        g.line(x - (float) Math.cos(a) * r * 0.8f, y - (float) Math.sin(a) * r * 0.8f,
                x + (float) Math.cos(a) * r * 0.8f, y + (float) Math.sin(a) * r * 0.8f, 0.03f, 0xFF7A8090);
    }

    private static void vtol(Gfx g, Chassis ch, int body, int dark, int light, float lw, float time) {
        int id = ch.id;
        float a = time * 25;
        if (id == 13) { // Talon: tandem-rotor gunship with wide missile stubs
            g.fillRect(-0.6f, -0.08f, 1.1f, 0.16f, dark);
            g.save();
            g.scale(1f, 0.38f);
            g.fillCircle(0f, 0, 0.55f, body);
            g.restore();
            g.fillRect(-0.12f, -0.48f, 0.2f, 0.96f, Col.darker(body, 0.2f));
            for (int s = -1; s <= 1; s += 2) {
                g.fillRoundRect(-0.12f, s * 0.48f - 0.08f, 0.32f, 0.16f, 0.04f, 0xFF3A3E46);
            }
            g.fillCircle(0.42f, 0, 0.09f, 0xFF7ACBFF);
            rotor(g, 0.32f, 0, 0.45f, a);
            rotor(g, -0.42f, 0, 0.45f, -a);
            return;
        }
        // tail boom
        g.fillRect(-0.78f, -0.05f, 0.62f, 0.1f, dark);
        g.fillRect(-0.82f, -0.16f, 0.1f, 0.32f, dark);
        g.save();
        g.scale(1f, id == 11 ? 0.34f : 0.44f);
        g.fillCircle(0.05f, 0, id == 11 ? 0.36f : 0.44f, body);
        g.strokeCircle(0.05f, 0, id == 11 ? 0.36f : 0.44f, lw * 2, dark);
        g.restore();
        g.fillCircle(0.3f, 0, 0.1f, 0xFF7ACBFF);
        if (id == 11) { // Mosquito: slim scout with a sensor ball
            g.fillCircle(0.42f, 0, 0.07f, 0xFFC8CCD4);
            g.line(-0.3f, 0, -0.5f, -0.25f, 0.025f, 0xFFDDDDDD);
            rotor(g, 0, 0, 0.55f, a);
        } else { // Dragonfly: stub wings with rocket pods and a chin gun
            g.fillRect(-0.06f, -0.36f, 0.16f, 0.72f, Col.darker(body, 0.2f));
            for (int s = -1; s <= 1; s += 2) {
                g.fillRoundRect(-0.08f, s * 0.36f - 0.07f, 0.26f, 0.14f, 0.05f, 0xFF3A3E46);
                g.fillCircle(0.18f, s * 0.36f, 0.04f, 0xFF15181E);
            }
            g.line(0.3f, 0.06f, 0.55f, 0.06f, 0.04f, 0xFF2A2E36);
            rotor(g, 0, 0, 0.62f, a);
        }
    }

    private static void rotor(Gfx g, float x, float y, float r, float a) {
        g.fillCircle(x, y, r, 0x22FFFFFF);
        for (int i = 0; i < 2; i++) {
            float ca = (float) Math.cos(a + i * Math.PI / 2) * r, sa = (float) Math.sin(a + i * Math.PI / 2) * r;
            g.line(x - ca, y - sa, x + ca, y + sa, 0.04f, 0xAA20242A);
        }
        g.fillCircle(x, y, 0.06f, 0xFF20242A);
    }

    private static void wheels(Gfx g, int n, float x0, float x1, float y) {
        for (int i = 0; i < n; i++) {
            float wx = n == 1 ? x0 : x0 + (x1 - x0) * i / (n - 1);
            g.fillRoundRect(wx - 0.09f, -y - 0.12f, 0.18f, 0.12f, 0.04f, 0xFF15181E);
            g.fillRoundRect(wx - 0.09f, y, 0.18f, 0.12f, 0.04f, 0xFF15181E);
        }
    }

    private static void car(Gfx g, Chassis ch, int body, int dark, int light, float lw) {
        switch (ch.id) {
            case 14: // Jackal: four-wheeled scout with a pointed nose
                wheels(g, 2, -0.3f, 0.28f, 0.28f);
                poly(g, body, -0.46f, -0.28f, 0.28f, -0.28f, 0.52f, 0f, 0.28f, 0.28f, -0.46f, 0.28f);
                g.fillRoundRect(0.08f, -0.2f, 0.18f, 0.4f, 0.05f, 0xFF7ACBFF);
                g.line(-0.1f, 0, 0.42f, 0, 0.045f, 0xFF2A2E36);
                g.fillCircle(-0.1f, 0, 0.12f, light);
                g.line(-0.35f, 0.15f, -0.6f, 0.4f, 0.025f, 0xFFDDDDDD);
                break;
            case 16: // Mule: truck with a cab up front and the howitzer behind
                wheels(g, 3, -0.4f, 0.32f, 0.3f);
                g.fillRoundRect(0.18f, -0.28f, 0.34f, 0.56f, 0.08f, light);
                g.fillRect(0.4f, -0.22f, 0.08f, 0.44f, 0xFF7ACBFF);
                g.fillRoundRect(-0.6f, -0.3f, 0.76f, 0.6f, 0.05f, body);
                g.fillRect(-0.7f, -0.34f, 0.12f, 0.1f, 0xFF5A5F68);
                g.fillRect(-0.7f, 0.24f, 0.12f, 0.1f, 0xFF5A5F68);
                g.line(-0.35f, 0, 0.85f, 0.0f, 0.1f, 0xFF2A2E36);
                g.fillCircle(-0.35f, 0, 0.15f, Col.darker(body, 0.2f));
                break;
            case 17: // Lancer: wedge tank-hunter with a missile box and laser
                wheels(g, 3, -0.36f, 0.28f, 0.3f);
                poly(g, body, -0.5f, -0.32f, 0.3f, -0.32f, 0.56f, -0.1f, 0.56f, 0.1f, 0.3f, 0.32f, -0.5f, 0.32f);
                g.fillRoundRect(-0.38f, -0.24f, 0.36f, 0.48f, 0.05f, 0xFF3A3E46);
                for (int i = 0; i < 4; i++) g.fillCircle(-0.3f + (i % 2) * 0.18f, -0.1f + (i / 2) * 0.2f, 0.04f, 0xFF15181E);
                g.line(0.05f, 0, 0.7f, 0, 0.05f, 0xFF2A2E36);
                g.fillCircle(0.05f, 0, 0.12f, light);
                break;
            default: // Rhino: six-wheeled brawler with armor plates and a big turret
                wheels(g, 3, -0.35f, 0.3f, 0.3f);
                g.fillRoundRect(-0.52f, -0.33f, 1.04f, 0.66f, 0.12f, body);
                for (int i = 0; i < 3; i++) g.fillRect(-0.4f + i * 0.3f, -0.33f, 0.04f, 0.66f, Col.darker(body, 0.25f));
                g.fillRoundRect(0.3f, -0.26f, 0.18f, 0.52f, 0.06f, 0xFF7ACBFF);
                g.line(-0.05f, 0, 0.74f, 0, 0.09f, 0xFF2A2E36);
                g.fillCircle(-0.05f, 0, 0.2f, light);
                g.strokeCircle(-0.05f, 0, 0.2f, lw, dark);
        }
    }

    private static void infantry(Gfx g, Chassis ch, int team, float strength, float time) {
        int n = Math.max(1, Math.round(5 * strength));
        float[][] pos = {{0.25f, 0}, {-0.05f, -0.3f}, {-0.05f, 0.3f}, {-0.35f, -0.12f}, {-0.35f, 0.18f}};
        int coat = Col.lerp(team, ch.id == 2 ? 0xFF8A7A4A : 0xFF6A7A5A, 0.35f);
        for (int i = 0; i < n; i++) {
            float bob = (float) Math.sin(time * 4 + i) * 0.02f;
            float x = pos[i][0], y = pos[i][1] + bob;
            g.fillCircle(x, y, 0.13f, 0xFF20242A);
            g.fillCircle(x, y, 0.1f, coat);
            if (ch.id == 1 && i % 2 == 0) g.line(x - 0.1f, y + 0.06f, x + 0.26f, y + 0.06f, 0.06f, 0xFF4A5040);   // rocket tube
            else g.line(x, y, x + 0.2f, y + 0.04f, 0.03f, 0xFF15181E);
            if (ch.id == 2 && i == 0) g.line(x - 0.05f, y, x - 0.3f, y - 0.25f, 0.02f, 0xFFDDDDDD);             // radio
        }
        if (ch.id == 3) { // mortar tube in the middle of the section
            g.fillCircle(0.05f, 0.02f, 0.12f, 0xFF3A3E46);
            g.fillCircle(0.05f, 0.02f, 0.06f, 0xFF15181E);
        }
    }


    // =====================================================================
    // Parts-based looks for the faction designs (Chassis.look)
    // =====================================================================

    // vehicle features (bitmask in look[2])
    private static final int F_SKIRTS = 1, F_RADAR = 2, F_BOXES = 4, F_BLADE = 8, F_ANTENNA = 16, F_SPADES = 32, F_SENSOR = 64, F_PLATES = 128;

    /** The heaviest weapon at a location (vehicle turret or hull), or null. */
    private static Wpn mainAt(Chassis ch, int[] weapons, int loc) {
        int w = weaponAt(ch, weapons, loc);
        return w < 0 ? null : Wpn.ALL[w];
    }

    /** A gun pointing along +x from (x, y), drawn by the weapon's family and size. */
    private static void gun(Gfx g, Wpn wp, float x, float y, float len) {
        if (wp == null) return;
        switch (wp.family) {
            case Wpn.F_ENERGY:
                g.line(x, y, x + len, y, 0.05f + wp.cls * 0.018f, 0xFF2A2E36);
                g.fillCircle(x + len, y, 0.03f + wp.cls * 0.012f, wp.base == Wpn.PLASMA ? 0xFFFF7AD8 : (wp.base == Wpn.ION_LANCE ? 0xFF7AD8FF : 0xFFFF6A5A));
                break;
            case Wpn.F_MISSILE:
                g.fillRoundRect(x - 0.06f, y - 0.1f - wp.cls * 0.02f, 0.26f + wp.cls * 0.04f, 0.2f + wp.cls * 0.04f, 0.04f, 0xFF3A3E46);
                for (int k = 0; k < 3; k++) g.fillCircle(x + 0.02f + k * 0.07f, y, 0.028f, 0xFF15181E);
                break;
            case Wpn.F_ARTILLERY:
                g.line(x, y, x + len * 1.35f, y, 0.08f + wp.cls * 0.03f, 0xFF2A2E36);
                g.fillRect(x + len * 1.35f - 0.06f, y - 0.07f - wp.cls * 0.01f, 0.1f, 0.14f + wp.cls * 0.02f, 0xFF1A1E24);
                break;
            default:
                g.line(x, y, x + len * (0.8f + wp.cls * 0.15f), y, 0.06f + wp.cls * 0.03f, 0xFF2A2E36);
                if (wp.base == Wpn.RAIL) g.line(x + 0.05f, y, x + len * 1.25f, y, 0.025f, 0xFF9AC8FF);
                if (wp.base == Wpn.CHAINGUN || wp.base == Wpn.FLAK) g.fillCircle(x + len * 0.25f, y, 0.06f, 0xFF4A4E56);
        }
    }

    private static void turret(Gfx g, int kind, Wpn main, float x, int light, int dark, float lw) {
        switch (kind) {
            case 1:
                gun(g, main, x, 0, 0.62f);
                g.fillCircle(x, 0, 0.24f, light);
                g.strokeCircle(x, 0, 0.24f, lw, dark);
                break;
            case 2:
                gun(g, main, x + 0.05f, 0, 0.6f);
                g.fillRoundRect(x - 0.22f, -0.2f, 0.4f, 0.4f, 0.05f, light);
                g.strokeRoundRect(x - 0.22f, -0.2f, 0.4f, 0.4f, 0.05f, lw, dark);
                break;
            case 3:
                for (int s = -1; s <= 1; s += 2) {
                    gun(g, main, x + s * 0.18f, s * 0.04f, 0.45f);
                    g.fillCircle(x + s * 0.18f, s * 0.04f, 0.15f, light);
                    g.strokeCircle(x + s * 0.18f, s * 0.04f, 0.15f, lw, dark);
                }
                break;
            case 4:
                gun(g, main, x, 0, 0.55f);
                g.fillCircle(x, 0, 0.26f, light);
                g.fillCircle(x - 0.06f, 0, 0.16f, Col.lighter(light, 0.2f));
                break;
            default:
                break;
        }
    }

    private static void features(Gfx g, int f, float front, float rear, float half, int body, int dark, float lw, float time) {
        if ((f & F_PLATES) != 0) for (int i = 0; i < 4; i++) g.fillRect(rear + 0.12f + i * (front - rear - 0.24f) / 3.5f, -half, 0.04f, half * 2, Col.darker(body, 0.28f));
        if ((f & F_SKIRTS) != 0) {
            g.fillRect(rear + 0.04f, -half - 0.04f, front - rear - 0.08f, 0.06f, Col.darker(body, 0.32f));
            g.fillRect(rear + 0.04f, half - 0.02f, front - rear - 0.08f, 0.06f, Col.darker(body, 0.32f));
        }
        if ((f & F_BLADE) != 0) g.fillRoundRect(front + 0.02f, -half - 0.12f, 0.09f, half * 2 + 0.24f, 0.03f, 0xFF8A8F98);
        if ((f & F_SPADES) != 0) {
            g.fillRect(rear - 0.12f, -half - 0.02f, 0.12f, 0.1f, 0xFF5A5F68);
            g.fillRect(rear - 0.12f, half - 0.08f, 0.12f, 0.1f, 0xFF5A5F68);
        }
        if ((f & F_BOXES) != 0) {
            g.fillRoundRect(rear + 0.06f, -half + 0.04f, 0.26f, half * 2 - 0.08f, 0.04f, 0xFF3A3E46);
            for (int i = 0; i < 4; i++) g.fillCircle(rear + 0.13f + (i % 2) * 0.12f, -half * 0.45f + (i / 2) * half * 0.9f, 0.032f, 0xFF15181E);
        }
        if ((f & F_RADAR) != 0) {
            float rx = rear + 0.2f, ry = half * 0.45f;
            g.fillCircle(rx, ry, 0.13f, 0xFFC8CCD4);
            g.strokeCircle(rx, ry, 0.13f, lw, dark);
            g.line(rx, ry, rx + (float) Math.cos(time * 2) * 0.12f, ry + (float) Math.sin(time * 2) * 0.12f, 0.025f, dark);
        }
        if ((f & F_ANTENNA) != 0) {
            g.line(rear + 0.15f, -half * 0.5f, rear - 0.12f, -half - 0.12f, 0.025f, 0xFFDDDDDD);
            g.fillCircle(rear - 0.12f, -half - 0.12f, 0.03f, 0xFFFF4A4A);
        }
        if ((f & F_SENSOR) != 0) g.fillCircle(front - 0.08f, -half * 0.5f, 0.08f, 0xFFC8CCD4);
    }

    /** Tracked vehicles: look = {hull 0 box / 1 wedge / 2 long / 3 round, turret, features}. */
    private static void tracked(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw) {
        int[] k = ch.look;
        int hull = k[0];
        float len = hull == 2 ? 0.62f : 0.55f;
        g.fillRoundRect(-len, -0.44f, len * 2, 0.2f, 0.06f, 0xFF2A2D33);
        g.fillRoundRect(-len, 0.24f, len * 2, 0.2f, 0.06f, 0xFF2A2D33);
        for (int i = 0; i < 7; i++) {
            g.fillRect(-len + 0.05f + i * (len * 2 - 0.1f) / 6.5f, -0.44f, 0.05f, 0.2f, 0xFF4A4F58);
            g.fillRect(-len + 0.05f + i * (len * 2 - 0.1f) / 6.5f, 0.24f, 0.05f, 0.2f, 0xFF4A4F58);
        }
        float front = len - 0.04f, rear = -len + 0.04f, half = 0.3f;
        switch (hull) {
            case 1: poly(g, body, rear, -half, front - 0.18f, -half, front + 0.04f, 0f, front - 0.18f, half, rear, half); break;
            case 2: g.fillRoundRect(rear, -0.28f, front - rear, 0.56f, 0.06f, body); half = 0.28f; break;
            case 3: g.fillRoundRect(rear, -half, front - rear, half * 2, 0.24f, body); break;
            default: g.fillRoundRect(rear, -half, front - rear, half * 2, 0.08f, body);
        }
        g.fillRect(rear + 0.02f, -0.03f, 0.1f, 0.06f, Col.alpha(factionAccent(ch, light), 0.9f));
        features(g, k[2], front, rear, half, body, dark, lw, 0);
        Wpn top = mainAt(ch, weapons, UnitType.V_TOP), hullGun = mainAt(ch, weapons, UnitType.V_FRONT);
        if (k[1] == 0) {   // casemate: the main gun sits in the hull
            g.fillRoundRect(rear + 0.1f, -0.24f, 0.62f, 0.48f, 0.06f, light);
            gun(g, top != null ? top : hullGun, rear + 0.6f, 0, 0.7f);
        } else {
            if (hullGun != null) gun(g, hullGun, front - 0.1f, 0.16f, 0.3f);
            turret(g, k[1], top, hull == 2 ? 0.05f : 0f, light, dark, lw);
        }
        g.strokeRoundRect(rear, -0.3f, front - rear, 0.6f, 0.08f, lw, dark);
    }

    /** Hovertanks: look = {hull 0 arrow / 1 wedge / 2 round / 3 catamaran, turret, features, fans}. */
    private static void skimmer(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw, float time) {
        int[] k = ch.look;
        float fan = time * 12;
        int fans = Math.max(1, Math.min(3, k[3]));
        switch (k[0]) {
            case 0:
                poly(g, 0xFF20242A, -0.5f, -0.38f, 0.3f, -0.3f, 0.62f, 0f, 0.3f, 0.3f, -0.5f, 0.38f);
                poly(g, body, -0.44f, -0.32f, 0.26f, -0.25f, 0.54f, 0f, 0.26f, 0.25f, -0.44f, 0.32f);
                break;
            case 1:
                poly(g, 0xFF20242A, -0.56f, -0.4f, 0.4f, -0.36f, 0.62f, 0f, 0.4f, 0.36f, -0.56f, 0.4f);
                poly(g, body, -0.5f, -0.34f, 0.36f, -0.3f, 0.54f, 0f, 0.36f, 0.3f, -0.5f, 0.34f);
                break;
            case 3:
                for (int s = -1; s <= 1; s += 2) {
                    g.fillRoundRect(-0.56f, s * 0.26f - 0.13f, 1.1f, 0.26f, 0.12f, 0xFF20242A);
                    g.fillRoundRect(-0.52f, s * 0.26f - 0.1f, 1.02f, 0.2f, 0.09f, body);
                }
                g.fillRoundRect(-0.3f, -0.18f, 0.6f, 0.36f, 0.06f, Col.darker(body, 0.15f));
                break;
            default:
                g.fillRoundRect(-0.56f, -0.4f, 1.12f, 0.8f, 0.36f, 0xFF20242A);
                g.fillRoundRect(-0.5f, -0.34f, 1.0f, 0.68f, 0.3f, body);
        }
        for (int i = 0; i < fans; i++) {
            float fy = fans == 1 ? 0 : (fans == 2 ? (i == 0 ? -0.17f : 0.17f) : (i - 1) * 0.22f);
            fanAt(g, -0.32f, fy, fans == 3 ? 0.09f : 0.12f, fan + i);
        }
        g.fillRect(-0.5f, -0.03f, 0.08f, 0.06f, Col.alpha(factionAccent(ch, light), 0.9f));
        features(g, k[2] & ~F_SKIRTS, 0.5f, -0.5f, 0.32f, body, dark, lw, time);
        Wpn top = mainAt(ch, weapons, UnitType.V_TOP), hullGun = mainAt(ch, weapons, UnitType.V_FRONT);
        if (hullGun != null) gun(g, hullGun, 0.3f, -0.16f, 0.3f);
        if (k[1] == 0) gun(g, top, 0.1f, 0, 0.55f);
        else turret(g, k[1], top, 0.06f, light, dark, lw);
    }

    /** Wheeled vehicles: look = {hull 0 pointed / 1 box / 2 truck / 3 wedge, turret, features, axles}. */
    private static void wheeled(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw) {
        int[] k = ch.look;
        int axles = Math.max(2, Math.min(4, k[3]));
        float len = axles >= 4 ? 0.62f : 0.52f;
        wheels(g, axles, -len + 0.15f, len - 0.2f, 0.3f);
        float front = len, rear = -len, half = 0.3f;
        switch (k[0]) {
            case 0: poly(g, body, rear, -0.28f, front - 0.24f, -0.28f, front, 0f, front - 0.24f, 0.28f, rear, 0.28f); half = 0.28f; break;
            case 2:
                g.fillRoundRect(front - 0.34f, -0.28f, 0.34f, 0.56f, 0.08f, light);
                g.fillRect(front - 0.12f, -0.22f, 0.08f, 0.44f, 0xFF7ACBFF);
                g.fillRoundRect(rear, -0.3f, front - rear - 0.36f, 0.6f, 0.05f, body);
                break;
            case 3: poly(g, body, rear, -0.32f, front - 0.26f, -0.32f, front, -0.1f, front, 0.1f, front - 0.26f, 0.32f, rear, 0.32f); half = 0.32f; break;
            default: g.fillRoundRect(rear, -0.32f, front - rear, 0.64f, 0.12f, body); half = 0.32f;
        }
        g.fillRect(rear + 0.02f, -0.03f, 0.1f, 0.06f, Col.alpha(factionAccent(ch, light), 0.9f));
        features(g, k[2], front, rear, half, body, dark, lw, 0);
        if (k[0] != 2) g.fillRoundRect(front - 0.28f, -0.2f, 0.12f, 0.4f, 0.04f, 0xFF7ACBFF);
        Wpn top = mainAt(ch, weapons, UnitType.V_TOP), hullGun = mainAt(ch, weapons, UnitType.V_FRONT);
        if (hullGun != null) gun(g, hullGun, front - 0.2f, 0.18f, 0.28f);
        float tx = k[0] == 2 ? rear + 0.3f : -0.05f;
        if (k[1] == 0) gun(g, top, tx, 0, 0.6f);
        else turret(g, k[1], top, tx, light, dark, lw);
    }

    /** VTOLs: look = {style 0 slim / 1 stub wings / 2 tandem / 3 tiltrotor, -, features}. */
    private static void flyer(Gfx g, Chassis ch, int[] weapons, int body, int dark, int light, float lw, float time) {
        int[] k = ch.look;
        float a = time * 25;
        int accent = factionAccent(ch, light);
        Wpn nose = mainAt(ch, weapons, UnitType.V_FRONT), chin = mainAt(ch, weapons, UnitType.V_TOP);
        if (k[0] == 2) {   // tandem rotors
            g.fillRect(-0.6f, -0.08f, 1.1f, 0.16f, dark);
            g.save();
            g.scale(1f, 0.4f);
            g.fillCircle(0f, 0, 0.55f, body);
            g.restore();
            g.fillRect(-0.05f, -0.06f, 0.1f, 0.12f, accent);
            gun(g, nose, 0.4f, 0.08f, 0.25f);
            g.fillCircle(0.42f, -0.02f, 0.08f, 0xFF7ACBFF);
            rotor(g, 0.32f, 0, 0.45f, a);
            rotor(g, -0.42f, 0, 0.45f, -a);
            return;
        }
        g.fillRect(-0.78f, -0.05f, 0.62f, 0.1f, dark);
        g.fillRect(-0.82f, -0.16f, 0.1f, 0.32f, dark);
        g.fillRect(-0.8f, -0.04f, 0.06f, 0.08f, accent);
        g.save();
        g.scale(1f, 0.42f);
        g.fillCircle(0.05f, 0, 0.42f, body);
        g.strokeCircle(0.05f, 0, 0.42f, lw * 2, dark);
        g.restore();
        if (k[0] == 1 || k[0] == 3) {
            g.fillRect(-0.06f, -0.4f, 0.16f, 0.8f, Col.darker(body, 0.2f));
            for (int s = -1; s <= 1; s += 2) {
                if (nose != null && nose.family == Wpn.F_MISSILE) {
                    g.fillRoundRect(-0.08f, s * 0.36f - 0.07f, 0.26f, 0.14f, 0.05f, 0xFF3A3E46);
                    g.fillCircle(0.18f, s * 0.36f, 0.04f, 0xFF15181E);
                }
            }
        }
        if (nose != null && nose.family != Wpn.F_MISSILE) gun(g, nose, 0.35f, 0, 0.3f);
        if (chin != null) gun(g, chin, 0.3f, 0.07f, 0.22f);
        g.fillCircle(0.3f, 0, 0.1f, 0xFF7ACBFF);
        features(g, k[2] & (F_SENSOR | F_ANTENNA), 0.5f, -0.3f, 0.18f, body, dark, lw, time);
        if (k[0] == 3) {   // tiltrotor: two rotors at the wingtips
            rotor(g, 0.02f, -0.46f, 0.32f, a);
            rotor(g, 0.02f, 0.46f, 0.32f, -a);
        } else rotor(g, 0, 0, k[0] == 0 ? 0.55f : 0.62f, a);
    }

    /** Infantry: look = {style 0 rifles / 1 rockets / 2 power armor / 3 recon / 4 heavy weapons / 5 sappers}. */
    private static void squad(Gfx g, Chassis ch, int team, float strength, float time) {
        int style = ch.look[0];
        int accent = factionAccent(ch, 0xFFDDDDDD);
        boolean armored = style == 2;
        int max = armored ? 4 : 5;
        int n = Math.max(1, Math.round(max * strength));
        float[][] pos = armored ? new float[][]{{0.22f, -0.15f}, {0.22f, 0.15f}, {-0.2f, -0.18f}, {-0.2f, 0.18f}}
                : new float[][]{{0.25f, 0}, {-0.05f, -0.3f}, {-0.05f, 0.3f}, {-0.35f, -0.12f}, {-0.35f, 0.18f}};
        int coat = Col.lerp(team, style == 3 ? 0xFF8A7A4A : (style == 5 ? 0xFF5A5A5A : 0xFF6A7A5A), 0.35f);
        for (int i = 0; i < n; i++) {
            float bob = (float) Math.sin(time * 4 + i) * 0.02f;
            float x = pos[i][0], y = pos[i][1] + bob, r = armored ? 0.16f : 0.13f;
            g.fillCircle(x, y, r, 0xFF20242A);
            g.fillCircle(x, y, r - 0.03f, coat);
            g.fillCircle(x - 0.02f, y, r * 0.35f, accent);
            if (style == 1 || (style == 4 && i % 2 == 1) || (armored && i % 2 == 0)) g.line(x - 0.1f, y + 0.06f, x + 0.28f, y + 0.06f, 0.06f, 0xFF4A5040);
            else g.line(x, y, x + 0.2f, y + 0.04f, 0.03f, 0xFF15181E);
            if (style == 3 && i == 0) g.line(x - 0.05f, y, x - 0.3f, y - 0.25f, 0.02f, 0xFFDDDDDD);
            if (style == 5 && i % 2 == 0) g.fillRect(x - 0.12f, y - 0.05f, 0.08f, 0.1f, 0xFFB0703A);
        }
        if (style == 4) {
            g.fillCircle(0.05f, 0.02f, 0.12f, 0xFF3A3E46);
            g.fillCircle(0.05f, 0.02f, 0.06f, 0xFF15181E);
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

    /** Screen rectangle {x, y, w, h} of one location's box in a diagram drawn at (x, y, w, h). */
    public static float[] locRect(int type, int i, float x, float y, float w, float h) {
        int[] c = layout(type)[i];
        float cw = w / 5f, chh = h / 5f, gap = Math.min(cw, chh) * 0.08f;
        float bx = x + c[0] * cw + gap, by = y + c[1] * chh + gap;
        float bw = c[2] * cw - gap * 2, bh = c[3] * chh - gap * 2;
        if (!UnitType.isMech(type) && type != UnitType.INFANTRY && i == UnitType.V_TOP) {
            bx += cw * 0.35f;
            by += chh * 0.35f;
            bw -= cw * 0.7f;
            bh -= chh * 0.7f;
        }
        return new float[]{bx, by, bw, bh};
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
