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
    private static void upgrade(Company rv, List<String> bought) {
        for (int guard = 0; guard < 3 && rv.roster.size() >= MAX_ROSTER; guard++) {
            Unit weakest = null;
            for (Unit u : rv.roster) if (weakest == null || u.value() < weakest.value()) weakest = u;
            if (weakest == null) return;
            int budget = rv.cash * 4 / 5 + weakest.value() / 2;
            Unit pick = null;
            for (Chassis ch : Chassis.ALL) {
                Unit u = new Unit(ch);
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
        shop(rv, r, null);
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

    public static List<Unit> chooseForce(Company rv) {
        List<Unit> ready = new ArrayList<Unit>();
        for (Unit u : rv.roster) if (!u.destroyed) ready.add(u);
        Collections.sort(ready, new Comparator<Unit>() {
            public int compare(Unit a, Unit b) {
                return Float.compare(b.value() * (0.4f + 0.6f * b.condition()), a.value() * (0.4f + 0.6f * a.condition()));
            }
        });
        while (ready.size() > Company.MAX_DEPLOY) ready.remove(ready.size() - 1);
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
        shop(rv, r, bought);
        return bought;
    }

    private static void shop(Company rv, Random r, List<String> bought) {
        upgrade(rv, bought);
        for (int guard = 0; guard < 12 && rv.roster.size() < MAX_ROSTER; guard++) {
            int reserve = rv.roster.size() >= 4 ? rv.cash / 6 : 0;   // keep something back for repairs
            int budget = rv.cash - reserve;
            int[] roles = new int[Chassis.ROLES.length];
            for (Unit u : rv.roster) roles[u.ch.role]++;
            int healthy = 0;
            for (Unit u : rv.roster) if (!u.destroyed) healthy++;
            Unit best = null;
            float bestScore = -Float.MAX_VALUE;
            for (Chassis ch : Chassis.ALL) {
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
                if (s > bestScore) {
                    bestScore = s;
                    best = u;
                }
            }
            if (best == null) break;
            rv.cash -= best.value();
            int same = 0;
            for (Unit o : rv.roster) if (o.ch == best.ch) same++;
            if (same > 0) best.name = best.ch.name + " " + (same + 1);
            rv.roster.add(best);
            if (bought != null) bought.add(best.name);
        }
    }
}
