package com.meanmutton.mechgame.core;

/** Draws a HexMap in map units (hex radius = 1, flat-topped). */
public final class HexRender {
    private HexRender() {}

    static final class Palette {
        int clear, clear2, tree, treeDark, rough, rock, shallow, deep, town, roof, road, contour;
    }

    static Palette palette(int biome) {
        Palette p = new Palette();
        switch (biome) {
            case HexMap.BIOME_DESERT:
                p.clear = 0xFFD9B97C; p.clear2 = 0xFFCFAE70; p.tree = 0xFF7F8E42; p.treeDark = 0xFF5C6B2E; p.rough = 0xFFB97A50;
                p.rock = 0xFF8A5236; p.shallow = 0xFF4FA6BD; p.deep = 0xFF2C7393; p.town = 0xFFC8B38E; p.roof = 0xFFB0694A;
                p.road = 0xFFEDE0B4; p.contour = 0xFF8A6A3A;
                break;
            case HexMap.BIOME_ARCTIC:
                p.clear = 0xFFE3EAF0; p.clear2 = 0xFFD6DFE8; p.tree = 0xFF325247; p.treeDark = 0xFF203A32; p.rough = 0xFFA4AEB8;
                p.rock = 0xFF6E7883; p.shallow = 0xFF9ACBEA; p.deep = 0xFF5A8CBC; p.town = 0xFFBFC4C9; p.roof = 0xFF7A4A3A;
                p.road = 0xFFB4AC9C; p.contour = 0xFF8A98A8;
                break;
            case HexMap.BIOME_BADLANDS:
                p.clear = 0xFF6E5D50; p.clear2 = 0xFF655548; p.tree = 0xFF5E4D3C; p.treeDark = 0xFF3E3026; p.rough = 0xFF4E3E36;
                p.rock = 0xFF2E2420; p.shallow = 0xFF5E7E6C; p.deep = 0xFF3C5C52; p.town = 0xFF7E7266; p.roof = 0xFF4A3A30;
                p.road = 0xFF9E8E72; p.contour = 0xFF2E2420;
                break;
            default:
                p.clear = 0xFF739F4D; p.clear2 = 0xFF6A9646; p.tree = 0xFF337033; p.treeDark = 0xFF214D25; p.rough = 0xFF8E8E7E;
                p.rock = 0xFF5E5E52; p.shallow = 0xFF4C8DCB; p.deep = 0xFF2B5C9D; p.town = 0xFFA09E8E; p.roof = 0xFF9A4A3A;
                p.road = 0xFFCDB98E; p.contour = 0xFF3E5A2A;
                break;
        }
        return p;
    }

    private static final float[] HX = new float[6], HY = new float[6];
    private static final float[] TX = new float[8], TY = new float[8];

    static void hexPath(float cx, float cy, float r) {
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            HX[i] = cx + (float) Math.cos(a) * r;
            HY[i] = cy + (float) Math.sin(a) * r;
        }
    }

    public static void fillHex(Gfx g, int c, int r, float radius, int color) {
        hexPath(HexMap.px(c, r), HexMap.py(c, r), radius);
        g.fillPoly(HX, HY, 6, color);
    }

    public static void strokeHex(Gfx g, int c, int r, float radius, float lw, int color) {
        float cx = HexMap.px(c, r), cy = HexMap.py(c, r);
        hexPath(cx, cy, radius);
        for (int i = 0; i < 6; i++) g.line(HX[i], HY[i], HX[(i + 1) % 6], HY[(i + 1) % 6], lw, color);
    }

    private static float hash(int c, int r, int k) {
        int h = c * 73856093 ^ r * 19349663 ^ k * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535f;
    }

    public static void draw(Gfx g, HexMap map, float time, boolean detail) {
        Palette p = palette(map.biome);
        // ground
        for (int c = 0; c < map.cols; c++) {
            for (int r = 0; r < map.rows; r++) {
                int k = map.idx(c, r);
                int t = map.terrain[k];
                int base;
                switch (t) {
                    case HexMap.SHALLOW: base = p.shallow; break;
                    case HexMap.DEEP: base = p.deep; break;
                    case HexMap.ROUGH: base = p.rough; break;
                    case HexMap.TOWN: base = p.town; break;
                    case HexMap.LIGHT_WOODS:
                    case HexMap.HEAVY_WOODS: base = Col.lerp(p.clear, p.treeDark, t == HexMap.HEAVY_WOODS ? 0.45f : 0.25f); break;
                    default: base = hash(c, r, 1) < 0.5f ? p.clear : p.clear2;
                }
                if (t != HexMap.SHALLOW && t != HexMap.DEEP) base = Col.lighter(base, map.elev[k] * 0.13f);
                fillHex(g, c, r, 1.01f, base);
            }
        }
        // elevation contours: a dark rim on edges that drop to lower ground
        for (int c = 0; c < map.cols; c++) {
            for (int r = 0; r < map.rows; r++) {
                int k = map.idx(c, r);
                if (map.elev[k] == 0) continue;
                float cx = HexMap.px(c, r), cy = HexMap.py(c, r);
                for (int d = 0; d < 6; d++) {
                    int nc = map.ncol(c, r, d), nr = map.nrow(c, r, d);
                    int drop = map.in(nc, nr) ? map.elev[k] - map.elev[map.idx(nc, nr)] : 0;
                    if (drop <= 0) continue;
                    double a0 = Math.toRadians(HexMap.DIR_ANGLE[d] - 30), a1 = Math.toRadians(HexMap.DIR_ANGLE[d] + 30);
                    float x0 = cx + (float) Math.cos(a0) * 0.96f, y0 = cy + (float) Math.sin(a0) * 0.96f;
                    float x1 = cx + (float) Math.cos(a1) * 0.96f, y1 = cy + (float) Math.sin(a1) * 0.96f;
                    g.line(x0, y0, x1, y1, 0.09f + 0.06f * drop, Col.alpha(p.contour, 0.9f));
                }
            }
        }
        // features
        for (int c = 0; c < map.cols; c++) {
            for (int r = 0; r < map.rows; r++) {
                int k = map.idx(c, r);
                float cx = HexMap.px(c, r), cy = HexMap.py(c, r);
                switch (map.terrain[k]) {
                    case HexMap.LIGHT_WOODS: trees(g, c, r, cx, cy, 4, p, map.biome); break;
                    case HexMap.HEAVY_WOODS: trees(g, c, r, cx, cy, 8, p, map.biome); break;
                    case HexMap.ROUGH: rocks(g, c, r, cx, cy, p); break;
                    case HexMap.SHALLOW:
                    case HexMap.DEEP: water(g, c, r, cx, cy, time, map.terrain[k] == HexMap.DEEP); break;
                    case HexMap.TOWN: town(g, c, r, cx, cy, p); break;
                    default:
                        if (detail && hash(c, r, 7) < 0.35f) {
                            g.fillCircle(cx + (hash(c, r, 8) - 0.5f), cy + (hash(c, r, 9) - 0.5f), 0.05f, Col.darker(p.clear, 0.2f));
                        }
                }
            }
        }
        // roads
        for (int c = 0; c < map.cols; c++) {
            for (int r = 0; r < map.rows; r++) {
                int k = map.idx(c, r);
                if (!map.road[k]) continue;
                float cx = HexMap.px(c, r), cy = HexMap.py(c, r);
                for (int d = 0; d < 3; d++) {
                    int nc = map.ncol(c, r, d), nr = map.nrow(c, r, d);
                    if (!map.in(nc, nr) || !map.road[map.idx(nc, nr)]) continue;
                    float nx = HexMap.px(nc, nr), ny = HexMap.py(nc, nr);
                    g.line(cx, cy, nx, ny, 0.42f, Col.darker(p.road, 0.25f));
                    g.line(cx, cy, nx, ny, 0.34f, p.road);
                }
                if (map.terrain[k] == HexMap.SHALLOW) {
                    g.fillRoundRect(cx - 0.45f, cy - 0.25f, 0.9f, 0.5f, 0.08f, 0xFF8A6A48);
                    for (int i = 0; i < 5; i++) g.line(cx - 0.4f + i * 0.2f, cy - 0.25f, cx - 0.4f + i * 0.2f, cy + 0.25f, 0.03f, 0xFF5A4630);
                } else {
                    g.fillCircle(cx, cy, 0.17f, p.road);
                }
            }
        }
        // grid
        for (int c = 0; c < map.cols; c++) {
            for (int r = 0; r < map.rows; r++) strokeHex(g, c, r, 1f, 0.025f, 0x2A000000);
        }
        // elevation marks
        if (detail) {
            for (int c = 0; c < map.cols; c++) {
                for (int r = 0; r < map.rows; r++) {
                    int e = map.elev[map.idx(c, r)];
                    if (e <= 0) continue;
                    float cx = HexMap.px(c, r), cy = HexMap.py(c, r);
                    g.fillCircle(cx - 0.5f, cy - 0.52f, 0.2f, 0x88000000);
                    g.text(Integer.toString(e), cx - 0.5f, cy - 0.52f, 0.28f, 0xFFFFFFFF, Gfx.ALIGN_CENTER, true);
                }
            }
        }
        // smoke
        for (int c = 0; c < map.cols; c++) {
            for (int r = 0; r < map.rows; r++) {
                int k = map.idx(c, r);
                if (map.smoke[k] <= 0) continue;
                float cx = HexMap.px(c, r), cy = HexMap.py(c, r);
                for (int i = 0; i < 6; i++) {
                    float a = time * 0.4f + i;
                    g.fillCircle(cx + (float) Math.cos(a) * 0.4f, cy + (float) Math.sin(a * 1.3f) * 0.35f, 0.45f, 0x55D8D8D8);
                }
            }
        }
    }

    private static void trees(Gfx g, int c, int r, float cx, float cy, int n, Palette p, int biome) {
        for (int i = 0; i < n; i++) {
            float a = hash(c, r, i * 3) * 6.283f, d = 0.15f + hash(c, r, i * 3 + 1) * 0.55f;
            float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d;
            float s = 0.2f + hash(c, r, i * 3 + 2) * 0.12f;
            int col = hash(c, r, i + 50) < 0.5f ? p.tree : p.treeDark;
            g.fillCircle(x + 0.05f, y + 0.06f, s, 0x44000000);
            if (biome == HexMap.BIOME_ARCTIC) {
                TX[0] = x; TY[0] = y - s * 1.2f; TX[1] = x + s; TY[1] = y + s * 0.8f; TX[2] = x - s; TY[2] = y + s * 0.8f;
                g.fillPoly(TX, TY, 3, col);
                g.fillCircle(x, y - s * 0.6f, s * 0.25f, 0xCCFFFFFF);
            } else if (biome == HexMap.BIOME_BADLANDS) {
                g.line(x, y + s, x, y - s, 0.05f, col);
                g.line(x, y - s * 0.2f, x + s * 0.7f, y - s * 0.8f, 0.04f, col);
                g.line(x, y, x - s * 0.6f, y - s * 0.6f, 0.04f, col);
            } else {
                g.fillCircle(x, y, s, col);
                g.fillCircle(x - s * 0.3f, y - s * 0.3f, s * 0.45f, Col.lighter(col, 0.2f));
            }
        }
    }

    private static void rocks(Gfx g, int c, int r, float cx, float cy, Palette p) {
        for (int i = 0; i < 4; i++) {
            float a = hash(c, r, i * 5) * 6.283f, d = hash(c, r, i * 5 + 1) * 0.55f;
            float x = cx + (float) Math.cos(a) * d, y = cy + (float) Math.sin(a) * d;
            float s = 0.12f + hash(c, r, i * 5 + 2) * 0.14f;
            for (int k = 0; k < 5; k++) {
                double aa = k * Math.PI * 2 / 5 + hash(c, r, k + i * 9) * 0.6;
                float rr = s * (0.7f + hash(c, r, k * 3 + i) * 0.5f);
                TX[k] = x + (float) Math.cos(aa) * rr;
                TY[k] = y + (float) Math.sin(aa) * rr;
            }
            g.fillPoly(TX, TY, 5, p.rock);
            g.fillCircle(x - s * 0.25f, y - s * 0.25f, s * 0.3f, Col.lighter(p.rock, 0.25f));
        }
    }

    private static void water(Gfx g, int c, int r, float cx, float cy, float time, boolean deep) {
        for (int i = 0; i < 3; i++) {
            float y = cy - 0.4f + i * 0.4f + (float) Math.sin(time * 1.5f + c + i) * 0.04f;
            float x = cx - 0.3f + hash(c, r, i) * 0.3f;
            g.line(x, y, x + 0.25f, y - 0.05f, 0.035f, deep ? 0x44FFFFFF : 0x66FFFFFF);
        }
    }

    private static void town(Gfx g, int c, int r, float cx, float cy, Palette p) {
        for (int i = 0; i < 3; i++) {
            float x = cx - 0.45f + (i % 2) * 0.5f + hash(c, r, i) * 0.1f;
            float y = cy - 0.45f + (i / 2) * 0.5f + hash(c, r, i + 3) * 0.12f + (i == 2 ? 0.05f : 0);
            float w = 0.32f + hash(c, r, i + 6) * 0.12f, h = 0.3f + hash(c, r, i + 9) * 0.1f;
            g.fillRect(x + 0.04f, y + 0.05f, w, h, 0x55000000);
            g.fillRect(x, y, w, h, Col.lighter(p.town, 0.2f));
            g.fillRect(x, y, w, h * 0.45f, p.roof);
        }
    }
}
