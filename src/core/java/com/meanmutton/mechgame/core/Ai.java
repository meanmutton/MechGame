package com.meanmutton.mechgame.core;

import java.util.List;

/**
 * Computer opponent. Each activation it picks the unit with the best plan, where a plan is a
 * destination, a facing, a target and a weapon selection. Plans are scored by expected damage dealt
 * minus the threat the unit would face there, adjusted for cover, heat, and the unit's role.
 */
final class Ai {
    private Ai() {}

    static final class Turn {
        Unit unit;
        int stage;
        int dest = -1;
        int facing;
        Unit target;
        boolean turbo, smoke;
        int[] path;
    }

    static final class Planner implements Runnable {
        final Battle b;
        volatile boolean done;
        Plan best;
        Unit bestU;

        Planner(Battle b) {
            this.b = b;
        }

        public void run() {
            try {
                for (Unit u : b.units) {
                    if (u.team != b.side || u.destroyed || u.activated || u.shutdown) continue;
                    Plan p = plan(b, u);
                    // artillery and support prefer to shoot before the enemy moves out of sight
                    if (p.target != null && (u.ch.role == Chassis.R_ARTILLERY || u.ch.role == Chassis.R_SUPPORT)) p.score += 4;
                    if (best == null || p.score > best.score) {
                        best = p;
                        bestU = u;
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                if (bestU == null) {
                    for (Unit u : b.units) {
                        if (u.team == b.side && !u.destroyed && !u.activated) {
                            bestU = u;
                            best = new Plan();
                            best.dest = b.map.idx(u.q, u.r);
                            best.facing = u.facing;
                            break;
                        }
                    }
                }
            }
            done = true;
        }
    }

    static final class Plan {
        float score;
        int dest;
        int facing;
        Unit target;
        boolean turbo;
    }

    static void step(Battle b) {
        Turn t = b.aiTurn;
        if (t == null) {
            if (b.aiPlanner == null) {
                // Shut-down units just spend their activation restarting.
                for (Unit u : b.units) {
                    if (u.team == b.side && !u.destroyed && !u.activated && u.shutdown && onlyShutdown(b)) {
                        b.beginActivation(u);
                        b.endActivation(u);
                        return;
                    }
                }
                Planner p = new Planner(b);
                b.aiPlanner = p;
                if (b.game.headless) p.run();
                else {
                    // Planning simulates every reachable hex for every unit: keep it off the frame thread.
                    // It only moves units' logical positions temporarily; drawing uses drawX/drawY.
                    Thread th = new Thread(p, "ai-plan");
                    th.setDaemon(true);
                    th.start();
                }
                return;
            }
            if (!b.aiPlanner.done) return;
            Planner pl = b.aiPlanner;
            b.aiPlanner = null;
            Plan best = pl.best;
            Unit bestU = pl.bestU;
            if (bestU == null) return;
            t = new Turn();
            b.aiTurn = t;
            t.unit = bestU;
            t.dest = best.dest;
            t.facing = best.facing;
            t.target = best.target;
            t.turbo = best.turbo;
            b.beginActivation(bestU);
            b.aiDelay = 0.35f;
            return;
        }
        Unit u = t.unit;
        switch (t.stage) {
            case 0: { // movement
                t.stage = 1;
                if (t.turbo) b.turbo(u);
                if (u.equipSlot(Equip.COOLANT) >= 0 && u.heatFrac() > 0.55f) b.useEquip(u, Equip.COOLANT);
                int start = b.map.idx(u.q, u.r);
                if (t.dest >= 0 && t.dest != start) {
                    int[] prev = new int[b.map.cols * b.map.rows];
                    float[] dist = b.reach(u, prev, b.mpAvailable(u));
                    if (dist[t.dest] < Float.MAX_VALUE) b.move(u, b.pathTo(prev, start, t.dest));
                }
                b.aiDelay = 0.25f;
                return;
            }
            case 1: { // face and fire
                t.stage = 2;
                b.face(u, t.facing);
                Unit target = t.target;
                if (target == null || target.destroyed) target = bestTargetNow(b, u);
                if (target != null) {
                    // re-face to the actual target so weapons are in arc
                    b.face(u, HexMap.dirFromAngle(HexMap.angleBetween(u.q, u.r, target.q, target.r)));
                    boolean[] use = chooseWeapons(b, u, target);
                    boolean any = false;
                    for (boolean x : use) any |= x;
                    if (any) b.fire(u, target, use);
                }
                b.aiDelay = 0.3f;
                return;
            }
            default: {
                if (!u.destroyed) {
                    if (u.equipSlot(Equip.REPAIR_KIT) >= 0) {
                        for (int i = 0; i < u.armor.length; i++) {
                            if (u.armorMax[i] - u.armor[i] >= 12) {
                                b.useEquip(u, Equip.REPAIR_KIT);
                                break;
                            }
                        }
                    }
                    if (u.equipSlot(Equip.SMOKE) >= 0 && u.condition() < 0.55f && threatAt(b, u, u.q, u.r) > 8) {
                        b.useEquip(u, Equip.SMOKE);
                    }
                }
                b.aiTurn = null;
                b.endActivation(u);
                b.aiDelay = 0.45f;
            }
        }
    }

    private static boolean onlyShutdown(Battle b) {
        for (Unit u : b.units) if (u.team == b.side && !u.destroyed && !u.activated && !u.shutdown) return false;
        return true;
    }

    // =====================================================================

    static Plan plan(Battle b, Unit u) {
        Plan best = evaluate(b, u, b.mpAvailable(u), false);
        if (b.canTurboAi(u)) {
            u.turboOn = true;
            Plan t = evaluate(b, u, b.mpAvailable(u), true);
            u.turboOn = false;
            if (t.score > best.score + 6) best = t;
        }
        return best;
    }

    private static Plan evaluate(Battle b, Unit u, int mp, boolean turbo) {
        int n = b.map.cols * b.map.rows;
        int[] prev = new int[n];
        float[] dist = b.reach(u, prev, mp);
        int start = b.map.idx(u.q, u.r);
        int oq = u.q, or = u.r, of = u.facing;
        boolean omoved = u.moved, oturbo = u.turboOn;
        int omh = u.movedHexes;
        Plan best = new Plan();
        best.score = -Float.MAX_VALUE;
        float noise = b.difficulty == 0 ? 0.3f : (b.difficulty == 1 ? 0.12f : 0.03f);
        List<Unit> enemies = b.team(1 - u.team);
        for (int k = 0; k < n; k++) {
            if (k != start && dist[k] == Float.MAX_VALUE) continue;
            int c = k % b.map.cols, r = k / b.map.cols;
            u.q = c;
            u.r = r;
            u.moved = k != start;
            u.turboOn = turbo;
            u.movedHexes = k == start ? 0 : Math.max(1, Math.round(dist[k]));
            float bestOff = 0;
            Unit bestT = null;
            int bestF = of;
            for (Unit e : enemies) {
                int f = HexMap.dirFromAngle(HexMap.angleBetween(c, r, e.q, e.r));
                u.facing = f;
                float off = offense(b, u, e);
                if (off > bestOff) {
                    bestOff = off;
                    bestT = e;
                    bestF = f;
                }
            }
            if (bestT == null) {
                // face the nearest enemy
                Unit near = nearest(enemies, c, r);
                if (near != null) bestF = HexMap.dirFromAngle(HexMap.angleBetween(c, r, near.q, near.r));
            }
            float threat = threatAt(b, u, c, r);
            float caution = (b.difficulty == 0 ? 0.3f : (b.difficulty == 1 ? 0.55f : 0.7f)) * (1.4f - u.condition() * 0.6f);
            float score = bestOff - threat * caution + roleScore(b, u, c, r, enemies, bestT);
            int ter = b.map.terrain[k];
            if (u.heatFrac() > 0.4f && u.type != UnitType.VTOL && (ter == HexMap.SHALLOW || ter == HexMap.DEEP) && !b.map.road[k]) {
                score += u.heat * 0.35f;
            }
            if (turbo) score -= 2;
            score *= 1f + (float) (b.aiDice().nextGaussian() * noise);
            if (score > best.score) {
                best.score = score;
                best.dest = k;
                best.target = bestT;
                best.facing = bestF;
                best.turbo = turbo;
            }
        }
        u.q = oq;
        u.r = or;
        u.facing = of;
        u.moved = omoved;
        u.turboOn = oturbo;
        u.movedHexes = omh;
        return best;
    }

    private static Unit nearest(List<Unit> list, int c, int r) {
        Unit best = null;
        int bd = 999;
        for (Unit e : list) {
            int d = HexMap.distance(c, r, e.q, e.r);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Expected damage this unit can do to a target from where it stands now, within a heat budget. */
    static float offense(Battle b, Unit u, Unit t) {
        int nm = u.weapon.length;
        float[] exp = new float[nm];
        float total = 0;
        float heatBudget = u.ch.heatCap * 0.8f - u.heat + (u.type == UnitType.INFANTRY ? 999 : 0);
        boolean melee = false;
        // greedy by damage per heat
        boolean[] used = new boolean[nm];
        for (int pass = 0; pass < nm; pass++) {
            int bi = -1;
            float bv = 0;
            for (int m = 0; m < nm; m++) {
                if (used[m]) continue;
                Wpn w = u.wpn(m);
                if (w == null) continue;
                if (w.isMelee() && melee) continue;
                if (exp[m] == 0) exp[m] = b.expected(u, m, t) + 0.0001f;
                if (exp[m] < 0.01f) continue;
                float v = exp[m] / (1f + Math.max(0, w.heat));
                if (v > bv) {
                    bv = v;
                    bi = m;
                }
            }
            if (bi < 0) break;
            used[bi] = true;
            Wpn w = u.wpn(bi);
            if (u.type != UnitType.INFANTRY && w.heat > heatBudget) continue;
            heatBudget -= w.heat;
            if (w.isMelee()) melee = true;
            total += exp[bi];
        }
        int arc = b.attackArc(u, t);
        if (arc == UnitType.ARC_REAR) total *= 1.3f;
        else if (arc != UnitType.ARC_FRONT) total *= 1.1f;
        float focus = b.difficulty == 2 ? 2.2f : 1.4f;
        float worth = 1f + focus * (1f - t.condition());
        worth *= 0.7f + Math.min(1.5f, t.value() / 3000f) * 0.4f;
        // finishing a fragile target this turn is worth a lot (glass cannons, crippled units)
        if (total >= (t.totalArmor() + t.totalInfra()) * 0.6f) worth *= 1.6f;
        return total * worth;
    }

    /** Rough damage the enemy could pour onto this hex next turn. */
    static float threatAt(Battle b, Unit u, int c, int r) {
        float threat = 0;
        int k = b.map.idx(c, r);
        float cover = 1f;
        if (u.type != UnitType.VTOL) {
            int ter = b.map.terrain[k];
            if (ter == HexMap.LIGHT_WOODS) cover = 0.8f;
            if (ter == HexMap.HEAVY_WOODS) cover = 0.65f;
            if (ter == HexMap.TOWN) cover = 0.72f;
        }
        if (b.map.smoke[k] > 0) cover *= 0.6f;
        int oq = u.q, or = u.r;
        u.q = c;
        u.r = r;
        for (Unit e : b.units) {
            if (e.destroyed || e.team == u.team) continue;
            int d = HexMap.distance(c, r, e.q, e.r);
            float fire = 0;
            int maxRange = 0;
            for (int m = 0; m < e.weapon.length; m++) {
                if (!e.weaponUsable(m)) continue;
                Wpn w = e.wpn(m);
                fire += w.dmg * e.firepowerScale();
                maxRange = Math.max(maxRange, w.rLong);
            }
            int reach = maxRange + Math.max(1, e.baseMp() / 2);
            if (d > reach) continue;
            float rf = d <= 3 ? 1f : (d <= 7 ? 0.75f : (d <= 12 ? 0.45f : 0.25f));
            boolean sees = b.losPoints(e, u) >= 0;
            threat += fire * 0.5f * rf * (sees ? 1f : 0.55f) * cover;
        }
        u.q = oq;
        u.r = or;
        return threat;
    }

    private static float roleScore(Battle b, Unit u, int c, int r, List<Unit> enemies, Unit target) {
        Unit near = nearest(enemies, c, r);
        if (near == null) return 0;
        int d = HexMap.distance(c, r, near.q, near.r);
        float s = 0;
        switch (u.ch.role) {
            case Chassis.R_SCOUT: {
                int seen = 0;
                int oq = u.q, or = u.r;
                u.q = c;
                u.r = r;
                for (Unit e : enemies) if (b.losPoints(u, e) >= 0 && HexMap.distance(c, r, e.q, e.r) <= 12) seen++;
                u.q = oq;
                u.r = or;
                s += seen * 3;
                if (d < 4) s -= (4 - d) * 4;
                break;
            }
            case Chassis.R_ARTILLERY:
                s += Math.min(d, 10) * 1.2f;
                if (d < 6) s -= (6 - d) * 4;
                break;
            case Chassis.R_SUPPORT:
                if (d < 5) s -= (5 - d) * 3;
                else if (d > 11) s -= (d - 11) * 1.5f;
                break;
            case Chassis.R_BRAWLER:
                s -= d * 1.5f;
                break;
            case Chassis.R_STRIKER:
                s -= Math.max(0, d - 4) * 0.8f;
                break;
            default:
                s -= Math.max(0, d - 7) * 1.0f;
        }
        // nobody should wander off toward the map edge with no target in sight
        if (target == null) s -= d * 0.6f;
        return s;
    }

    static Unit bestTargetNow(Battle b, Unit u) {
        Unit best = null;
        float bv = 0;
        int of = u.facing;
        for (Unit e : b.team(1 - u.team)) {
            u.facing = HexMap.dirFromAngle(HexMap.angleBetween(u.q, u.r, e.q, e.r));
            float v = offense(b, u, e);
            if (v > bv) {
                bv = v;
                best = e;
            }
        }
        u.facing = of;
        return best;
    }

    static boolean[] chooseWeapons(Battle b, Unit u, Unit t) {
        int nm = u.weapon.length;
        boolean[] use = new boolean[nm];
        float budget = u.ch.heatCap * (b.difficulty == 0 ? 0.95f : 0.8f) - u.heat;
        int minChance = b.difficulty == 0 ? 25 : 12;
        boolean melee = false;
        // order by expected damage
        Integer[] order = new Integer[nm];
        final float[] e = new float[nm];
        for (int m = 0; m < nm; m++) {
            order[m] = m;
            e[m] = b.expected(u, m, t);
        }
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer c) {
                return Float.compare(e[c], e[a]);
            }
        });
        for (int i : order) {
            Wpn w = u.wpn(i);
            if (w == null) continue;
            int ch = b.hitChance(u, i, t, null);
            if (ch < minChance) continue;
            if (w.isMelee()) {
                if (melee) continue;
                melee = true;
            }
            if (u.type != UnitType.INFANTRY && w.heat > budget) continue;
            budget -= u.type == UnitType.INFANTRY ? 0 : w.heat;
            // don't shell our own units with artillery splash
            if (w.family == Wpn.F_ARTILLERY && friendlyNear(b, u, t)) continue;
            use[i] = true;
        }
        return use;
    }

    private static boolean friendlyNear(Battle b, Unit u, Unit t) {
        for (Unit o : b.units) {
            if (o.destroyed || o.team != u.team) continue;
            if (HexMap.distance(o.q, o.r, t.q, t.r) <= 1) return true;
        }
        return false;
    }
}
