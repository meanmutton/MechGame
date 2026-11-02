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

    public static final int MAX_ROSTER = 9;

    /** How the rival's money compares to the player's, by difficulty. */
    public static float incomeMult(int difficulty) {
        return difficulty == 0 ? 0.75f : (difficulty == 1 ? 1.0f : 1.3f);
    }

    /** With a full roster and money to spare, trade the weakest unit for something much better. */
    private static void upgrade(Company rv, List<String> bought, Scenario next) {
        for (int guard = 0; guard < 3 && rv.roster.size() >= MAX_ROSTER; guard++) {
            // trade away what the next battle can't use first; buy only what it can
            Unit weakest = null;
            for (Unit u : rv.roster) {
                boolean better = weakest == null || (next != null && next.allows(weakest) && !next.allows(u))
                        || ((next == null || next.allows(weakest) == next.allows(u)) && u.value() < weakest.value());
                if (better) weakest = u;
            }
            if (weakest == null) return;
            int budget = rv.cash * 4 / 5 + weakest.value() / 2;
            Unit pick = null;
            for (Chassis ch : Chassis.SHOP) {
                Unit u = new Unit(ch);
                if (next != null && !next.allows(ch)) continue;
                if (u.value() <= budget && u.value() > weakest.value() * 1.6f && (pick == null || u.value() > pick.value())) pick = u;
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
     * The units the rival sends into the next battle: its strongest units still in one piece, up to six,
     * within the contract's engagement limit. A poor rival may field less than that; it never fields more.
     */
    public static List<Unit> chooseForce(Company rv, int playerValue, int difficulty) {
        int cap = Math.max(1500, Math.round(playerValue * engagementLimit(difficulty)));
        List<Unit> best = chooseForce(rv);
        List<Unit> out = new ArrayList<Unit>();
        int total = 0;
        for (Unit u : best) {
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
        int want = sc.unitLimit(rivalAttacks ? Scenario.ATTACKER : Scenario.DEFENDER, battles);
        List<Unit> best = chooseForce(rv, Company.MAX_ROSTER);
        List<Unit> out = new ArrayList<Unit>();
        int total = 0;
        for (Unit u : best) {
            if (out.size() >= want || !sc.allows(u)) continue;
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
        return chooseForce(rv, Company.MAX_DEPLOY);
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
        for (int guard = 0; next != null && guard < 2 && rv.roster.size() >= MAX_ROSTER && ready < next.maxUnits; guard++) {
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
            for (Unit u : rv.roster) if (!u.destroyed) healthy++;
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
                if (ch.type == UnitType.INFANTRY && healthy >= 4) s *= 0.5f;
                // with the next battle known, buy for it, above all when nothing it owns can fight there
                if (next != null && next.allows(ch)) s *= ready < 2 ? 2.2f : (ready < next.maxUnits ? 1.4f : 1f);
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
