package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The player's company: money, units, spare parts, and the next contract. Saved between sessions. */
public final class Company {
    public static final int MAX_ROSTER = 12, MAX_DEPLOY = 6;
    public static final String[] DIFFICULTY = {"Easy", "Normal", "Hard"};

    public boolean quick;
    /** Campaign only: the opposing company (saved separately). */
    public Company rival;
    public int difficulty = 1;
    public int cash;
    public int mission = 1, wins, losses;
    public final List<Unit> roster = new ArrayList<Unit>();
    public final List<Unit> deployed = new ArrayList<Unit>();
    public final int[] armoryW = new int[Wpn.ALL.length];
    public final int[] armoryE = new int[Equip.ALL.length];

    // campaign: the fight for one planet (see Scenario)
    public int side = Scenario.ATTACKER;
    public int front = Scenario.FRONT_START;
    public int scenario = Scenario.INITIAL_DROP;
    public int lastScenario = -1, lastOutcome = Scenario.OUT_NONE;
    /** Side with the edge (first-round initiative) in the next battle, or -1. */
    public int edge = -1;
    /** 0 while the campaign runs; otherwise OUT_ATTACKER or OUT_DEFENDER, whoever took the planet. */
    public int over;
    public int planets;
    /** Battles fought on the current planet (the defender's income grows with it). */
    public int planetBattles;

    // next contract
    public int biome;
    public int reward;
    public long seed;

    public void rollContract(Random r) {
        biome = r.nextInt(HexMap.BIOME_NAMES.length);
        reward = 1600 + Math.min(mission, 20) * 300 + difficulty * 400;
        if (!quick) reward = Math.round(reward * Scenario.get(scenario).pay * Scenario.income(side, planetBattles) / 50f) * 50;
        seed = r.nextLong();
    }

    public int deployedValue() {
        int v = 0;
        for (Unit u : deployed) v += u.value();
        return v;
    }

    public int repairAllCost() {
        int c = 0;
        for (Unit u : roster) c += u.repairCost();
        return c;
    }

    /** The next campaign scenario (null in a quick battle). */
    public Scenario next() {
        return quick ? null : Scenario.get(scenario);
    }

    public int deployLimit() {
        Scenario s = next();
        return s == null ? MAX_DEPLOY : s.unitLimit(side, planetBattles);
    }

    /** May this unit fight in the next battle? */
    public boolean allowed(Unit u) {
        Scenario s = next();
        return s == null || s.allows(u);
    }

    public boolean canDeploy(Unit u) {
        return !u.destroyed && allowed(u) && (deployed.contains(u) || deployed.size() < deployLimit());
    }

    /** Drops deployed units the next scenario doesn't allow, and any over its unit limit. */
    public void fitDeployment() {
        for (Unit u : new ArrayList<Unit>(deployed)) if (u.destroyed || !allowed(u)) deployed.remove(u);
        while (deployed.size() > deployLimit()) deployed.remove(deployed.size() - 1);
    }

    /** True if no unit on the roster may fight in the next scenario and none the Market sells is affordable: only a forfeit is left. */
    public boolean mustForfeit() {
        if (!noneAllowed()) return false;
        Scenario s = next();
        if (roster.size() < MAX_ROSTER) for (Chassis ch : Chassis.SHOP) if (s.allows(ch) && new Unit(ch).value() <= cash) return false;
        return true;
    }

    /** True if no unit on the roster can fight in the next scenario. */
    public boolean noneAllowed() {
        for (Unit u : roster) if (!u.destroyed && allowed(u)) return false;
        return true;
    }

    /** The player's side as the attacker/defender outcome of a battle result. */
    public int outcome(int result) {
        if (result == Battle.RESULT_WIN) return side == Scenario.ATTACKER ? Scenario.OUT_ATTACKER : Scenario.OUT_DEFENDER;
        if (result == Battle.RESULT_DRAW) return Scenario.OUT_DRAW;
        return side == Scenario.ATTACKER ? Scenario.OUT_DEFENDER : Scenario.OUT_ATTACKER;
    }

    // ---------------- persistence ----------------

    public String serialize() {
        StringBuilder b = new StringBuilder("C1|");
        b.append(quick ? 1 : 0).append(',').append(difficulty).append(',').append(cash).append(',').append(mission)
                .append(',').append(wins).append(',').append(losses).append(',').append(biome).append(',').append(reward)
                .append(',').append(seed).append(',').append(side).append(',').append(front).append(',').append(scenario)
                .append(',').append(lastScenario).append(',').append(lastOutcome).append(',').append(edge).append(',').append(over)
                .append(',').append(planets).append(',').append(planetBattles).append('|');
        for (int i = 0; i < armoryW.length; i++) b.append(i > 0 ? "," : "").append(armoryW[i]);
        b.append('|');
        for (int i = 0; i < armoryE.length; i++) b.append(i > 0 ? "," : "").append(armoryE[i]);
        b.append('|');
        for (int i = 0; i < deployed.size(); i++) b.append(i > 0 ? "," : "").append(roster.indexOf(deployed.get(i)));
        for (Unit u : roster) b.append('|').append(u.serialize());
        return b.toString();
    }

    public static Company parse(String s) {
        if (s == null || !s.startsWith("C1|")) return null;
        try {
            String[] p = s.split("\\|", -1);
            Company c = new Company();
            String[] h = p[1].split(",");
            c.quick = Integer.parseInt(h[0]) != 0;
            c.difficulty = Integer.parseInt(h[1]);
            c.cash = Integer.parseInt(h[2]);
            c.mission = Integer.parseInt(h[3]);
            c.wins = Integer.parseInt(h[4]);
            c.losses = Integer.parseInt(h[5]);
            c.biome = Integer.parseInt(h[6]);
            c.reward = Integer.parseInt(h[7]);
            c.seed = Long.parseLong(h[8]);
            if (h.length >= 17) {   // saves from before campaign scenarios start a fresh planet
                c.side = Integer.parseInt(h[9]);
                c.front = Integer.parseInt(h[10]);
                c.scenario = Integer.parseInt(h[11]);
                c.lastScenario = Integer.parseInt(h[12]);
                c.lastOutcome = Integer.parseInt(h[13]);
                c.edge = Integer.parseInt(h[14]);
                c.over = Integer.parseInt(h[15]);
                c.planets = Integer.parseInt(h[16]);
                if (h.length >= 18) c.planetBattles = Integer.parseInt(h[17]);
            }
            String[] aw = p[2].split(",");
            for (int i = 0; i < aw.length && i < c.armoryW.length; i++) c.armoryW[i] = Integer.parseInt(aw[i]);
            String[] ae = p[3].split(",");
            for (int i = 0; i < ae.length && i < c.armoryE.length; i++) c.armoryE[i] = Integer.parseInt(ae[i]);
            for (int i = 5; i < p.length; i++) c.roster.add(Unit.parse(p[i]));
            if (p[4].length() > 0) {
                for (String d : p[4].split(",")) {
                    int k = Integer.parseInt(d);
                    if (k >= 0 && k < c.roster.size()) c.deployed.add(c.roster.get(k));
                }
            }
            return c;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
