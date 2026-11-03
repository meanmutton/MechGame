package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Flat-topped hex grid in "odd-q" offset coordinates (odd columns are shifted down half a hex).
 * Generation builds terrain in layers that depend on each other: elevation first, then lakes in the
 * low ground, rivers that run downhill, forests where it's wet, rocks on high ground, a town, and a
 * road that connects both edges (bridging rivers).
 */
public final class HexMap {
    public static final int CLEAR = 0, LIGHT_WOODS = 1, HEAVY_WOODS = 2, ROUGH = 3, SHALLOW = 4, DEEP = 5, TOWN = 6;
    public static final String[] TERRAIN_NAMES = {"Open ground", "Light woods", "Heavy woods", "Rough", "Shallow water",
            "Deep water", "Town"};
    public static final int BIOME_TEMPERATE = 0, BIOME_DESERT = 1, BIOME_ARCTIC = 2, BIOME_BADLANDS = 3;
    public static final String[] BIOME_NAMES = {"Temperate", "Desert", "Arctic", "Badlands"};
    public static final float IMPASSABLE = 999f;

    /** Scenario requests: always generate a river / a town. */
    public boolean forceRiver, forceTown;
    public final int cols, rows, biome;
    public final int[] terrain, elev;
    public final boolean[] road;
    public final float[] smoke;      // rounds of smoke remaining
    public final Weather weather;

    // direction tables (dcol, drow) for even and odd columns; direction d points at angle DIR_ANGLE[d]
    private static final int[][] EVEN = {{1, -1}, {1, 0}, {0, 1}, {-1, 0}, {-1, -1}, {0, -1}};
    private static final int[][] ODD = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {0, -1}};
    public static final float[] DIR_ANGLE = {-30, 30, 90, 150, 210, 270};

    public HexMap(int cols, int rows, int biome, Weather weather) {
        this.cols = cols;
        this.rows = rows;
        this.biome = biome;
        this.weather = weather;
        int n = cols * rows;
        terrain = new int[n];
        elev = new int[n];
        road = new boolean[n];
        smoke = new float[n];
    }

    public int idx(int c, int r) {
        return r * cols + c;
    }

    public boolean in(int c, int r) {
        return c >= 0 && r >= 0 && c < cols && r < rows;
    }

    public int neighbor(int c, int r, int d, boolean wantCol) {
        int[] o = (c & 1) == 0 ? EVEN[d] : ODD[d];
        return wantCol ? c + o[0] : r + o[1];
    }

    public int ncol(int c, int r, int d) {
        return c + ((c & 1) == 0 ? EVEN[d][0] : ODD[d][0]);
    }

    public int nrow(int c, int r, int d) {
        return r + ((c & 1) == 0 ? EVEN[d][1] : ODD[d][1]);
    }

    // ---- cube coordinates ----

    public static int cubeX(int c, int r) {
        return c;
    }

    public static int cubeZ(int c, int r) {
        return r - (c - (c & 1)) / 2;
    }

    public static int distance(int c1, int r1, int c2, int r2) {
        int x1 = c1, z1 = cubeZ(c1, r1), y1 = -x1 - z1;
        int x2 = c2, z2 = cubeZ(c2, r2), y2 = -x2 - z2;
        return Math.max(Math.abs(x1 - x2), Math.max(Math.abs(y1 - y2), Math.abs(z1 - z2)));
    }

    /** Pixel center for a hex of radius 1 (flat-top). */
    public static float px(int c, int r) {
        return 1.5f * c;
    }

    public static float py(int c, int r) {
        return (float) (Math.sqrt(3) * (r + 0.5f * (c & 1)));
    }

    public static float angleBetween(int c1, int r1, int c2, int r2) {
        return (float) Math.toDegrees(Math.atan2(py(c2, r2) - py(c1, r1), px(c2, r2) - px(c1, r1)));
    }

    public static float angleDiff(float a, float b) {
        float d = a - b;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    /** Direction (0..5) whose angle is closest to the given angle. */
    public static int dirFromAngle(float a) {
        int best = 0;
        float bd = 999;
        for (int d = 0; d < 6; d++) {
            float diff = Math.abs(angleDiff(a, DIR_ANGLE[d]));
            if (diff < bd) {
                bd = diff;
                best = d;
            }
        }
        return best;
    }

    /** Hexes on the line between two hexes (excluding both ends). Uses two nudged lines; returns both. */
    public List<int[]> line(int c1, int r1, int c2, int r2, float nudge) {
        List<int[]> out = new ArrayList<int[]>();
        int n = distance(c1, r1, c2, r2);
        double ax = c1 + 1e-6 * nudge, az = cubeZ(c1, r1) + 2e-6 * nudge, ay = -ax - az;
        double bx = c2 + 1e-6 * nudge, bz = cubeZ(c2, r2) + 2e-6 * nudge, by = -bx - bz;
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            double x = ax + (bx - ax) * t, y = ay + (by - ay) * t, z = az + (bz - az) * t;
            long rx = Math.round(x), ry = Math.round(y), rz = Math.round(z);
            double dx = Math.abs(rx - x), dy = Math.abs(ry - y), dz = Math.abs(rz - z);
            if (dx > dy && dx > dz) rx = -ry - rz;
            else if (dy > dz) ry = -rx - rz;
            else rz = -rx - ry;
            int col = (int) rx;
            int row = (int) (rz + (col - (col & 1)) / 2);
            out.add(new int[]{col, row});
        }
        return out;
    }

    // =====================================================================
    // Line of sight
    // =====================================================================

    /**
     * Returns -1 if sight is blocked, otherwise the number of "obscuring points" along the way
     * (light woods 1, heavy woods 2, smoke 3) which each cost accuracy. 3 or more points blocks.
     */
    public int los(int c1, int r1, int h1, int c2, int r2, int h2) {
        int a = losLine(c1, r1, h1, c2, r2, h2, 1f);
        int b = losLine(c1, r1, h1, c2, r2, h2, -1f);
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    private int losLine(int c1, int r1, int h1, int c2, int r2, int h2, float nudge) {
        float eye = elev[idx(c1, r1)] + h1 - 0.5f;
        float tgt = elev[idx(c2, r2)] + h2 - 0.5f;
        List<int[]> l = line(c1, r1, c2, r2, nudge);
        int n = l.size() + 1;
        int points = 0;
        for (int i = 0; i < l.size(); i++) {
            int[] h = l.get(i);
            if (!in(h[0], h[1])) continue;
            int k = idx(h[0], h[1]);
            float lineH = eye + (tgt - eye) * ((i + 1) / (float) n);
            float ground = elev[k];
            if (terrain[k] == TOWN) ground += 1;
            if (ground >= lineH) return -1;
            if (smoke[k] > 0 && ground + 2 >= lineH) points += 3;
            if (terrain[k] == LIGHT_WOODS && ground + 1 >= lineH) points += 1;
            if (terrain[k] == HEAVY_WOODS && ground + 2 >= lineH) points += 2;
            if (points >= 3) return -1;
        }
        return points;
    }

    // =====================================================================
    // Movement
    // =====================================================================

    public float moveCost(int type, int fc, int fr, int tc, int tr) {
        int t = idx(tc, tr), f = idx(fc, fr);
        if (type == UnitType.VTOL) return 1f;
        int ter = terrain[t];
        boolean onRoad = road[t];
        float cost;
        boolean mech = UnitType.isMech(type);
        if (onRoad) {
            cost = (type == UnitType.WHEELED && road[f]) ? 0.5f : 1f;
        } else {
            switch (ter) {
                case CLEAR: cost = 1; break;
                case LIGHT_WOODS:
                    cost = mech || type == UnitType.TRACKED || type == UnitType.HOVER ? 2 : (type == UnitType.INFANTRY ? 1 : IMPASSABLE);
                    break;
                case HEAVY_WOODS:
                    cost = mech ? 3 : (type == UnitType.INFANTRY ? 2 : IMPASSABLE);
                    break;
                case ROUGH:
                    cost = type == UnitType.WHEELED ? 3 : 2;
                    break;
                case SHALLOW:
                    cost = mech || type == UnitType.TRACKED ? 2 : (type == UnitType.HOVER ? 1 : (type == UnitType.INFANTRY ? 3 : IMPASSABLE));
                    break;
                case DEEP:
                    cost = mech ? 3 : (type == UnitType.HOVER ? 1 : IMPASSABLE);
                    break;
                case TOWN:
                    cost = mech ? 2 : (type == UnitType.INFANTRY ? 1 : IMPASSABLE);
                    break;
                default: cost = 1;
            }
            if (weather.snowSlow && (type == UnitType.WHEELED || type == UnitType.HOVER) && cost < IMPASSABLE) cost += 1;
        }
        if (cost >= IMPASSABLE) return IMPASSABLE;
        int up = elev[t] - elev[f];
        if (up > 0) {
            if (!mech && type != UnitType.INFANTRY && up >= 2) return IMPASSABLE;
            if (up >= 3) return IMPASSABLE;
            cost += up;
        } else if (up < -1 && !mech && type != UnitType.INFANTRY) {
            return IMPASSABLE; // vehicles can't drive off cliffs
        }
        return cost;
    }

    public boolean passable(int type, int c, int r) {
        if (type == UnitType.VTOL) return true;
        int k = idx(c, r);
        if (road[k]) return true;
        int t = terrain[k];
        boolean mech = UnitType.isMech(type);
        switch (t) {
            case LIGHT_WOODS: return mech || type == UnitType.INFANTRY || type == UnitType.TRACKED || type == UnitType.HOVER;
            case HEAVY_WOODS:
            case TOWN: return mech || type == UnitType.INFANTRY;
            case SHALLOW: return type != UnitType.WHEELED;
            case DEEP: return mech || type == UnitType.HOVER;
            default: return true;
        }
    }

    // =====================================================================
    // Generation
    // =====================================================================

    public void generate(Random rnd) {
        int n = cols * rows;
        float[] e = noise(rnd, 5, 4, 0.65f);
        float[] wet = noise(rnd, 4, 3, 0.55f);
        float[] rock = noise(rnd, 6, 4, 0.5f);
        // Elevation: valleys in the middle band are a little more likely so units meet over terrain
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int k = idx(c, r);
                float v = e[k];
                int lvl = v < 0.45f ? 0 : (v < 0.62f ? 1 : (v < 0.78f ? 2 : 3));
                elev[k] = lvl;
            }
        }
        // No cliffs taller than 1 between neighbors except on mountain tops
        for (int pass = 0; pass < 4; pass++) {
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    int k = idx(c, r);
                    for (int d = 0; d < 6; d++) {
                        int nc = ncol(c, r, d), nr = nrow(c, r, d);
                        if (!in(nc, nr)) continue;
                        int nk = idx(nc, nr);
                        if (elev[k] - elev[nk] > 1 && rnd.nextFloat() < 0.8f) elev[k] = elev[nk] + 1;
                    }
                }
            }
        }
        Arrays.fill(terrain, CLEAR);

        float wetBias = biome == BIOME_DESERT ? -0.2f : (biome == BIOME_BADLANDS ? -0.12f : (biome == BIOME_ARCTIC ? -0.03f : 0f));
        // Lakes in low, wet ground
        for (int k = 0; k < n; k++) {
            if (elev[k] == 0 && wet[k] + wetBias > 0.62f) terrain[k] = SHALLOW;
        }
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int k = idx(c, r);
                if (terrain[k] != SHALLOW || wet[k] + wetBias < 0.7f) continue;
                boolean allWater = true;
                for (int d = 0; d < 6; d++) {
                    int nc = ncol(c, r, d), nr = nrow(c, r, d);
                    if (in(nc, nr) && terrain[idx(nc, nr)] != SHALLOW && terrain[idx(nc, nr)] != DEEP) allWater = false;
                }
                if (allWater) terrain[k] = DEEP;
            }
        }
        // Rivers run downhill from one long edge toward the other
        float riverChance = biome == BIOME_TEMPERATE ? 0.85f : (biome == BIOME_ARCTIC ? 0.6f : (biome == BIOME_BADLANDS ? 0.35f : 0.25f));
        if (forceRiver || rnd.nextFloat() < riverChance) river(rnd);
        if (biome == BIOME_TEMPERATE && rnd.nextFloat() < 0.3f) river(rnd);

        // Forests where it is wet, thicker near water; none on water
        float woodT = biome == BIOME_TEMPERATE ? 0.58f : (biome == BIOME_ARCTIC ? 0.6f : (biome == BIOME_BADLANDS ? 0.7f : 0.78f));
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int k = idx(c, r);
                if (terrain[k] != CLEAR || elev[k] >= 3) continue;
                float m = wet[k] + 0.12f * nearWater(c, r);
                if (m > woodT + 0.19f) terrain[k] = HEAVY_WOODS;
                else if (m > woodT) terrain[k] = LIGHT_WOODS;
            }
        }
        // Heavy woods need woods around them; lone trees thin out
        int[] copy = terrain.clone();
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int k = idx(c, r);
                if (copy[k] != LIGHT_WOODS && copy[k] != HEAVY_WOODS) continue;
                int woods = 0;
                for (int d = 0; d < 6; d++) {
                    int nc = ncol(c, r, d), nr = nrow(c, r, d);
                    if (in(nc, nr)) {
                        int t = copy[idx(nc, nr)];
                        if (t == LIGHT_WOODS || t == HEAVY_WOODS) woods++;
                    }
                }
                if (copy[k] == HEAVY_WOODS && woods < 3) terrain[k] = LIGHT_WOODS;
                if (woods == 0 && rnd.nextFloat() < 0.5f) terrain[k] = CLEAR;
            }
        }
        // Rocks on high ground and in badlands/desert
        float rockT = biome == BIOME_BADLANDS ? 0.52f : (biome == BIOME_DESERT ? 0.58f : 0.66f);
        for (int k = 0; k < n; k++) {
            if (terrain[k] != CLEAR) continue;
            if (rock[k] + elev[k] * 0.05f > rockT) terrain[k] = ROUGH;
        }
        // A small town on flat open ground in the middle
        float townChance = biome == BIOME_TEMPERATE ? 0.55f : 0.35f;
        int townC = -1, townR = -1;
        if (forceTown || rnd.nextFloat() < townChance) {
            for (int tries = 0; tries < 40 && townC < 0; tries++) {
                int c = cols / 3 + rnd.nextInt(cols / 3), r = 2 + rnd.nextInt(rows - 4);
                int k = idx(c, r);
                if (terrain[k] == CLEAR || terrain[k] == LIGHT_WOODS) {
                    townC = c;
                    townR = r;
                }
            }
            if (townC >= 0) {
                terrain[idx(townC, townR)] = TOWN;
                int extra = 2 + rnd.nextInt(4);
                for (int i = 0; i < extra; i++) {
                    int d = rnd.nextInt(6);
                    int nc = ncol(townC, townR, d), nr = nrow(townC, townR, d);
                    if (in(nc, nr)) {
                        int nk = idx(nc, nr);
                        if (terrain[nk] != SHALLOW && terrain[nk] != DEEP && Math.abs(elev[nk] - elev[idx(townC, townR)]) <= 1) terrain[nk] = TOWN;
                    }
                }
            }
        }
        // Road from the west edge to the east edge, through the town if there is one
        int startR = 1 + rnd.nextInt(rows - 2), endR = 1 + rnd.nextInt(rows - 2);
        if (townC >= 0) {
            buildRoad(0, startR, townC, townR);
            buildRoad(townC, townR, cols - 1, endR);
        } else {
            buildRoad(0, startR, cols - 1, endR);
        }
    }

    private int nearWater(int c, int r) {
        int w = 0;
        for (int d = 0; d < 6; d++) {
            int nc = ncol(c, r, d), nr = nrow(c, r, d);
            if (in(nc, nr)) {
                int t = terrain[idx(nc, nr)];
                if (t == SHALLOW || t == DEEP) w++;
            }
        }
        return w;
    }

    private void river(Random rnd) {
        // Rivers run north to south so they lie between the two deployment zones.
        int c = cols / 4 + rnd.nextInt(cols / 2);
        int r = 0;
        int k0 = idx(c, r);
        int guard = 0;
        int prevElev = elev[k0];
        while (in(c, r) && guard++ < rows * 3) {
            int k = idx(c, r);
            if (terrain[k] == DEEP) break;
            elev[k] = Math.min(elev[k], prevElev);   // the river cuts its own valley
            prevElev = elev[k];
            terrain[k] = SHALLOW;
            if (r == rows - 1) break;
            // candidates: directions that move south or sideways, prefer downhill
            int bestD = -1;
            float best = Float.MAX_VALUE;
            for (int d = 0; d < 6; d++) {
                int nc = ncol(c, r, d), nr = nrow(c, r, d);
                if (!in(nc, nr) || nr < r) continue;
                if (d == 0 || d == 4 || d == 5) continue;    // never flow back north
                int nk = idx(nc, nr);
                if (terrain[nk] == SHALLOW && nr == r) continue;
                float score = elev[nk] * 2f + rnd.nextFloat() * 1.2f + (nr == r ? 0.8f : 0f);
                if (Math.abs(nc - cols / 2f) > cols * 0.35f) score += 1.5f;
                if (score < best) {
                    best = score;
                    bestD = d;
                }
            }
            if (bestD < 0) break;
            int nc = ncol(c, r, bestD), nr = nrow(c, r, bestD);
            c = nc;
            r = nr;
        }
    }

    private void buildRoad(int c1, int r1, int c2, int r2) {
        int n = cols * rows;
        float[] dist = new float[n];
        int[] prev = new int[n];
        Arrays.fill(dist, Float.MAX_VALUE);
        Arrays.fill(prev, -1);
        boolean[] done = new boolean[n];
        int s = idx(c1, r1), goal = idx(c2, r2);
        dist[s] = 0;
        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            float bd = Float.MAX_VALUE;
            for (int k = 0; k < n; k++) if (!done[k] && dist[k] < bd) {
                bd = dist[k];
                u = k;
            }
            if (u < 0 || u == goal) break;
            done[u] = true;
            int uc = u % cols, ur = u / cols;
            for (int d = 0; d < 6; d++) {
                int nc = ncol(uc, ur, d), nr = nrow(uc, ur, d);
                if (!in(nc, nr)) continue;
                int v = idx(nc, nr);
                float cost;
                switch (terrain[v]) {
                    case CLEAR: cost = 1; break;
                    case TOWN: cost = 0.6f; break;
                    case ROUGH: cost = 3; break;
                    case LIGHT_WOODS: cost = 3.5f; break;
                    case HEAVY_WOODS: cost = 7; break;
                    case SHALLOW: cost = 5; break;
                    default: cost = 12; break;
                }
                if (road[v]) cost = 0.5f;
                cost += Math.abs(elev[v] - elev[u]) * 2.5f;
                if (dist[u] + cost < dist[v]) {
                    dist[v] = dist[u] + cost;
                    prev[v] = u;
                }
            }
        }
        int k = goal;
        int guard = 0;
        while (k >= 0 && guard++ < n) {
            road[k] = true;
            if (terrain[k] == DEEP) terrain[k] = SHALLOW;           // shallow ford with a bridge
            if (terrain[k] == HEAVY_WOODS || terrain[k] == LIGHT_WOODS) terrain[k] = CLEAR;
            k = prev[k];
        }
        // smooth the road's climb
        k = goal;
        guard = 0;
        while (k >= 0 && prev[k] >= 0 && guard++ < n) {
            int p = prev[k];
            if (elev[k] - elev[p] > 1) elev[k] = elev[p] + 1;
            if (elev[p] - elev[k] > 1) elev[p] = elev[k] + 1;
            k = p;
        }
    }

    /** Smooth value noise in [0,1] over the grid. */
    private float[] noise(Random rnd, int cell, int octaves, float persistence) {
        int n = cols * rows;
        float[] out = new float[n];
        float amp = 1, total = 0;
        int size = cell;
        for (int o = 0; o < octaves; o++) {
            int gw = cols / size + 3, gh = rows / size + 3;
            float[] g = new float[gw * gh];
            for (int i = 0; i < g.length; i++) g[i] = rnd.nextFloat();
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    float x = px(c, r) / 1.5f / size, y = py(c, r) / 1.732f / size;
                    int ix = (int) x, iy = (int) y;
                    float fx = x - ix, fy = y - iy;
                    fx = fx * fx * (3 - 2 * fx);
                    fy = fy * fy * (3 - 2 * fy);
                    float a = g[iy * gw + ix], b = g[iy * gw + ix + 1], cc = g[(iy + 1) * gw + ix], d = g[(iy + 1) * gw + ix + 1];
                    float v = (a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + d * fx) * fy;
                    out[idx(c, r)] += v * amp;
                }
            }
            total += amp;
            amp *= persistence;
            size = Math.max(1, size / 2);
        }
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            out[i] /= total;
            lo = Math.min(lo, out[i]);
            hi = Math.max(hi, out[i]);
        }
        for (int i = 0; i < n; i++) out[i] = (out[i] - lo) / Math.max(0.001f, hi - lo);
        return out;
    }

    public String describe(int c, int r) {
        int k = idx(c, r);
        String s = TERRAIN_NAMES[terrain[k]];
        if (road[k]) s = (terrain[k] == SHALLOW ? "Bridge" : "Road");
        s += ", elevation " + elev[k];
        if (smoke[k] > 0) s += ", smoke";
        return s;
    }
}
