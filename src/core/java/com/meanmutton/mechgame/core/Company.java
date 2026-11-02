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

    // next contract
    public int biome;
    public int reward;
    public long seed;

    public void rollContract(Random r) {
        biome = r.nextInt(HexMap.BIOME_NAMES.length);
        reward = 1600 + mission * 300 + difficulty * 400;
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

    public boolean canDeploy(Unit u) {
        return !u.destroyed && (deployed.contains(u) || deployed.size() < MAX_DEPLOY);
    }

    // ---------------- persistence ----------------

    public String serialize() {
        StringBuilder b = new StringBuilder("C1|");
        b.append(quick ? 1 : 0).append(',').append(difficulty).append(',').append(cash).append(',').append(mission)
                .append(',').append(wins).append(',').append(losses).append(',').append(biome).append(',').append(reward)
                .append(',').append(seed).append('|');
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
