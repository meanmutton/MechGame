package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The campaign's opposing company. Like the player it has money and a roster that carry over:
 * it is paid after every battle, repairs what it can afford, loses units it can't recover,
 * and buys reinforcements with what is left.
 */
public final class Rival {
    private Rival() {}

    public static final int MAX_ROSTER = 15;

    /** How the rival's money compares to the player's, by difficulty. */
    public static float incomeMult(int difficulty) {
        return difficulty == 0 ? 0.75f : (difficulty == 1 ? 1.0f : 1.3f);
    }

    /**
     * With a full roster and money to spare, trade a weak unit for a much better one of the same kind (mech,
     * vehicle or infantry), so the roster keeps its mix. Units the next battle can't use are traded first, and
     * only for units it can.
     */
    private static void upgrade(Company rv, List<String> bought, final Scenario next) {
        for (int guard = 0; guard < 3 && rv.roster.size() >= MAX_ROSTER; guard++) {
            List<Unit> order = new ArrayList<Unit>(rv.roster);
            Collections.sort(order, new Comparator<Unit>() {
                public int compare(Unit a, Unit b) {
                    if (next != null && next.allows(a) != next.allows(b)) return next.allows(a) ? 1 : -1;
                    return a.value() - b.value();
                }
            });
            Unit weakest = null, pick = null;
            for (Unit w : order) {
                int budget = rv.cash * 4 / 5 + w.value() / 2;
                for (Chassis ch : Chassis.SHOP) {
                    if (Limits.kind(ch.type) != Limits.kind(w.type) || (next != null && !next.allows(ch))) continue;
                    Unit u = new Unit(ch);
                    if (u.value() <= budget && u.value() > w.value() * 1.6f && (pick == null || u.value() > pick.value())) pick = u;
                }
                if (pick != null) {
                    weakest = w;
                    break;
                }
            }
            if (pick == null) return;
            rv.roster.remove(weakest);
            rv.cash += weakest.value() / 2 - pick.value();
            int same = 0;
            for (Unit o : rv.roster) if (o.ch == pick.ch) same++;
            if (same > 0) pick.name = pick.ch.name + " " + (same + 1);
            rv.roster.add(pick);
            if (bought != null) bought.add(pick.name);
        }
    }

    public static Company create(int difficulty, int playerFunds, Random r) {
        Company rv = new Company();
        rv.difficulty = difficulty;
        rv.cash = Math.round(playerFunds * incomeMult(difficulty));
        int reserve = rv.cash / 5;   // keep a fifth back for the first round of repairs
        rv.cash -= reserve;
        shop(rv, r, null, Scenario.get(Scenario.INITIAL_DROP));
        rv.cash += reserve;
        return rv;
    }

    /** Contract terms cap the rival's force at this multiple of the value you deploy. */
    public static float engagementLimit(int difficulty) {
        return difficulty == 0 ? 1.1f : (difficulty == 1 ? 1.4f : 1.8f);
    }

    /**
     * The units the rival sends into the next battle: its strongest units still in one piece, within the
     * full-size unit limits and the contract's engagement limit. A poor rival may field less; it never fields more.
     */
    public static List<Unit> chooseForce(Company rv, int playerValue, int difficulty) {
        int cap = Math.max(1500, Math.round(playerValue * engagementLimit(difficulty)));
        List<Unit> best = chooseForce(rv, Company.MAX_ROSTER);
        List<Unit> out = new ArrayList<Unit>();
        int total = 0;
        for (Unit u : best) {
            if (!Limits.FULL.fits(out, u)) continue;
            if (out.size() >= 2 && total + u.value() > cap) continue;
            out.add(u);
            total += u.value();
        }
        rv.deployed.clear();
        rv.deployed.addAll(out);
        return out;
    }

    /**
     * The rival's force for a campaign scenario: only units the scenario allows, no more than its unit limit,
     * within the engagement limit (shifted by the scenario's lean and by the two sides' resources). If it owns fewer than two units that may
     * fight, it hires stock units for this battle only, within the same limit.
     */
    public static List<Unit> chooseForce(Company rv, int playerValue, int difficulty, Scenario sc, boolean rivalAttacks, int battles, Random r) {
        float lean = (rivalAttacks ? 1f + sc.capLean : 1f - sc.capLean)
                * Scenario.forceFactor(rivalAttacks ? Scenario.ATTACKER : Scenario.DEFENDER, battles);
        int cap = Math.max(1200, Math.round(playerValue * engagementLimit(difficulty) * lean));
        Limits lim = sc.limitsFor(rivalAttacks ? Scenario.ATTACKER : Scenario.DEFENDER, battles);
        int want = lim.total();
        List<Unit> best = chooseForce(rv, Company.MAX_ROSTER);
        List<Unit> out = new ArrayList<Unit>();
        int total = 0;
        for (Unit u : best) {
            if (!sc.allows(u) || !lim.fits(out, u)) continue;
            if (out.size() >= 2 && total + u.value() > cap) continue;
            out.add(u);
            total += u.value();
        }
        rv.deployed.clear();
        rv.deployed.addAll(out);
        // Fewer than two units that may fight: hire stock units for free. With money to spare, it also hires
        // (for a third of their value) to fill out its limit, as long as the force limit leaves room.
        int minimum = Math.min(2, want);
        for (int guard = 0; guard < 40 && out.size() < want; guard++) {
            boolean free = out.size() < minimum;
            if (!free && total >= cap * 0.9f) break;
            int room = Math.max(0, cap - total) / Math.max(1, (free ? minimum : want) - out.size());
            Unit pick = null;
            for (int tries = 0; tries < 30; tries++) {
                Chassis ch = Chassis.SHOP[r.nextInt(Chassis.SHOP.length)];
                if (!sc.allows(ch)) continue;
                Unit u = new Unit(ch);
                if (!lim.fits(out, u)) continue;
                // the biggest unit that fits the room left; the cheapest allowed one if nothing fits
                boolean fits = u.value() <= room, pickFits = pick != null && pick.value() <= room;
                if (pick == null || (fits && !pickFits) || (fits && u.value() > pick.value())
                        || (!fits && !pickFits && u.value() < pick.value())) pick = u;
            }
            if (pick == null) break;
            if (!free) {
                if (pick.value() > room * 1.2f || rv.cash < pick.value() / 3) break;
                rv.cash -= pick.value() / 3;
            }
            pick.tag = "hired";
            out.add(pick);
            total += pick.value();
        }
        return out;
    }

    public static List<Unit> chooseForce(Company rv) {
        List<Unit> out = new ArrayList<Unit>();
        for (Unit u : chooseForce(rv, Company.MAX_ROSTER)) if (Limits.FULL.fits(out, u)) out.add(u);
        rv.deployed.clear();
        rv.deployed.addAll(out);
        return out;
    }

    private static List<Unit> chooseForce(Company rv, int limit) {
        List<Unit> ready = new ArrayList<Unit>();
        for (Unit u : rv.roster) if (!u.destroyed) ready.add(u);
        Collections.sort(ready, new Comparator<Unit>() {
            public int compare(Unit a, Unit b) {
                return Float.compare(b.value() * (0.4f + 0.6f * b.condition()), a.value() * (0.4f + 0.6f * a.condition()));
            }
        });
        while (ready.size() > limit) ready.remove(ready.size() - 1);
        rv.deployed.clear();
        rv.deployed.addAll(ready);
        return ready;
    }

    /** If the rival has nothing left to field and no money, it scrapes together a militia. */
    public static boolean ensureForce(Company rv) {
        for (Unit u : rv.roster) if (!u.destroyed) return false;
        rv.roster.add(new Unit(Chassis.ALL[0]));
        rv.roster.add(new Unit(Chassis.ALL[1]));
        return true;
    }

    /** Repairs (most valuable first), then buys reinforcements. Returns names of new units. */
    public static List<String> upkeep(Company rv, Random r) {
        return upkeep(rv, r, null);
    }

    /** Repairs and shopping, preferring units the next scenario allows. */
    public static List<String> upkeep(Company rv, Random r, Scenario next) {
        List<Unit> byValue = new ArrayList<Unit>(rv.roster);
        Collections.sort(byValue, new Comparator<Unit>() {
            public int compare(Unit a, Unit b) {
                return b.value() - a.value();
            }
        });
        for (Unit u : byValue) {
            if (u.isDamaged() && rv.cash >= u.repairCost()) {
                rv.cash -= u.repairCost();
                u.repairFully();
            }
        }
        // wrecks it can't afford to fix are scrapped for a little money
        for (Unit u : new ArrayList<Unit>(rv.roster)) {
            if (u.destroyed) {
                rv.cash += u.value() / 5;
                rv.roster.remove(u);
            }
        }
        List<String> bought = new ArrayList<String>();
        shop(rv, r, bought, next);
        return bought;
    }

    private static void shop(Company rv, Random r, List<String> bought, Scenario next) {
        int ready = 0;
        if (next != null) for (Unit u : rv.roster) if (!u.destroyed && next.allows(u)) ready++;
        // A full roster with too few units the next battle allows: sell the cheapest ones it doesn't, to make room.
        for (int guard = 0; next != null && guard < 2 && rv.roster.size() >= MAX_ROSTER && ready < next.caps.total(); guard++) {
            Unit sell = null;
            for (Unit u : rv.roster) if (!next.allows(u) && (sell == null || u.value() < sell.value())) sell = u;
            if (sell == null) break;
            rv.roster.remove(sell);
            rv.cash += sell.destroyed ? sell.value() / 5 : sell.value() / 2;
        }
        upgrade(rv, bought, next);
        for (int guard = 0; guard < 12 && rv.roster.size() < MAX_ROSTER; guard++) {
            int reserve = rv.roster.size() >= 4 ? rv.cash / 6 : 0;   // keep something back for repairs
            int budget = rv.cash - reserve;
            int[] roles = new int[Chassis.ROLES.length];
            for (Unit u : rv.roster) roles[u.ch.role]++;
            int healthy = 0;
            int[] kinds = new int[4];
            for (Unit u : rv.roster) if (!u.destroyed) {
                healthy++;
                kinds[Limits.kind(u.type)]++;
            }
            // keep a roster that can fill a full-size battle: mechs, vehicles and infantry
            int[] target = {Limits.FULL.mechs + 1, Limits.FULL.heavy - Limits.FULL.mechs + 2, Limits.FULL.infantry + 1, 0};
            Unit best = null;
            float bestScore = -Float.MAX_VALUE;
            for (Chassis ch : Chassis.SHOP) {
                Unit u = new Unit(ch);
                int v = u.value();
                if (v > budget) continue;
                // until it has a full lance, don't blow everything on one unit
                if (healthy < 4 && v > budget * 0.55f && budget > 2000) continue;
                float s = v * (0.75f + r.nextFloat() * 0.5f);
                if (roles[ch.role] == 0) s *= 1.35f;
                if (roles[ch.role] >= 2) s *= 0.6f;
                if ((ch.role == Chassis.R_LINE || ch.role == Chassis.R_BRAWLER) && roles[Chassis.R_LINE] + roles[Chassis.R_BRAWLER] < 2) s *= 1.3f;
                s *= kinds[Limits.kind(ch.type)] < target[Limits.kind(ch.type)] ? 1.3f : 0.5f;
                if (ch.type == UnitType.INFANTRY) s *= 3f;   // cheap: score by value would never pick them
                // with the next battle known, buy for it, above all when nothing it owns can fight there
                if (next != null && next.allows(ch)) s *= ready < 2 ? 2.2f : (ready < next.caps.total() ? 1.4f : 1f);
                if (s > bestScore) {
                    bestScore = s;
                    best = u;
                }
            }
            if (best == null) break;
            if (next != null && next.allows(best)) ready++;
            rv.cash -= best.value();
            int same = 0;
            for (Unit o : rv.roster) if (o.ch == best.ch) same++;
            if (same > 0) best.name = best.ch.name + " " + (same + 1);
            rv.roster.add(best);
            if (bought != null) bought.add(best.name);
        }
    }
}
