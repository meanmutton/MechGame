package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mercenary play. Your company lands on a planet where several factions are fighting and takes contracts from
 * them: single battles using the campaign scenarios, for pay and reputation. Working for a faction raises your
 * standing with it (and a little with its allies); fighting a faction lowers it (and a little with its allies).
 * Standing decides who will hire you, whose markets sell to you, and who offers you their unique prototypes.
 * When a planet runs dry, travel to another.
 */
public final class Merc {
    private Merc() {}

    // ---------------- reputation ----------------

    public static final int REP_MIN = -100, REP_MAX = 100;
    public static final String[] TIERS = {"Hostile", "Unfriendly", "Neutral", "Friendly", "Allied"};
    public static final int[] TIER_COLORS = {0xFFFF5A4E, 0xFFFF9A4E, 0xFFC8CCD4, 0xFF8CF06A, 0xFF5BD46A};

    public static int tier(int rep) {
        if (rep <= -50) return 0;
        if (rep < -10) return 1;
        if (rep < 20) return 2;
        if (rep < 60) return 3;
        return 4;
    }

    /** Will hire you: anything better than Hostile. */
    public static boolean hires(int rep) {
        return rep > -50;
    }

    /** Will sell to you: Neutral or better. */
    public static boolean trades(int rep) {
        return rep >= -10;
    }

    /** Offers you its unique prototypes: Allied. */
    public static boolean trusts(int rep) {
        return rep >= 60;
    }

    /** Two factions on the same side: the same bloc. Independents have no allies. */
    public static boolean allied(int a, int b) {
        int ba = Faction.ALL[a].bloc, bb = Faction.ALL[b].bloc;
        return a == b || (ba >= 0 && ba == bb);
    }

    // ---------------- planets ----------------

    private static final String[] P1 = {"Kor", "Vel", "Ash", "Thal", "Nyx", "Bran", "Ori", "Sel", "Mar", "Dun", "Cal", "Zer", "Hal", "Ves", "Tor", "Ira"};
    private static final String[] P2 = {"is", "ara", "on", "ex", "ion", "heim", "ia", "or", "us", "ade", "ith", "ova"};
    private static final String[] P3 = {"", "", "", " Prime", " II", " IV", " Minor", " Reach"};

    public static final class Planet {
        public final long seed;
        public final String name;
        public final int biome;
        /** Factions fighting over this planet (a Faction mask). */
        public final int factions;

        Planet(long seed, String name, int biome, int factions) {
            this.seed = seed;
            this.name = name;
            this.biome = biome;
            this.factions = factions;
        }

        public boolean has(int faction) {
            return (factions & Faction.bit(faction)) != 0;
        }

        String serialize() {
            return seed + ":" + name + ":" + biome + ":" + factions;
        }

        static Planet parse(String s) {
            String[] p = s.split(":");
            return new Planet(Long.parseLong(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        }
    }

    /** A new planet: two to four factions, at least two of which are not allies (so there is a war to take part in). */
    public static Planet planet(Random r) {
        long seed = r.nextLong();
        String name = P1[r.nextInt(P1.length)] + P2[r.nextInt(P2.length)] + P3[r.nextInt(P3.length)];
        int n = 2 + r.nextInt(3);
        int mask;
        do {
            mask = 0;
            while (Integer.bitCount(mask) < n) mask |= Faction.bit(r.nextInt(Faction.COUNT));
        } while (!contested(mask));
        return new Planet(seed, name, r.nextInt(HexMap.BIOME_NAMES.length), mask);
    }

    private static boolean contested(int mask) {
        for (int a = 0; a < Faction.COUNT; a++)
            for (int b = a + 1; b < Faction.COUNT; b++)
                if ((mask & Faction.bit(a)) != 0 && (mask & Faction.bit(b)) != 0 && !allied(a, b)) return true;
        return false;
    }

    /** What it costs to travel to another planet. */
    public static int travelCost(Company c) {
        return 600 + c.difficulty * 200 + Math.min(c.mission, 20) * 40;
    }

    // ---------------- contracts ----------------

    public static final class Contract {
        public final int employer, target, scenario, role, threat, reward, enemyBudget;
        public final long seed;

        Contract(int employer, int target, int scenario, int role, int threat, int reward, int enemyBudget, long seed) {
            this.employer = employer;
            this.target = target;
            this.scenario = scenario;
            this.role = role;
            this.threat = threat;
            this.reward = reward;
            this.enemyBudget = enemyBudget;
            this.seed = seed;
        }

        String serialize() {
            return employer + ";" + target + ";" + scenario + ";" + role + ";" + threat + ";" + reward + ";" + enemyBudget + ";" + seed;
        }

        static Contract parse(String s) {
            String[] p = s.split(";");
            return new Contract(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), Long.parseLong(p[7]));
        }

        public String job() {
            return (role == Scenario.ATTACKER ? "Attack " : "Defend against ") + Faction.ALL[target].name;
        }
    }

    /** Scenarios that make sense as a single job (not the opening drop or a last-ditch evacuation). */
    private static final int[] JOBS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19};

    /** The value of the strongest force the company could field in a full-size battle. */
    public static int fieldValue(Company c) {
        List<Unit> best = new ArrayList<Unit>(c.roster);
        java.util.Collections.sort(best, new java.util.Comparator<Unit>() {
            public int compare(Unit a, Unit b) {
                return b.value() - a.value();
            }
        });
        List<Unit> force = new ArrayList<Unit>();
        int v = 0;
        for (Unit u : best) {
            if (u.destroyed || !Limits.FULL.fits(force, u)) continue;
            force.add(u);
            v += u.value();
        }
        return Math.max(4000, v);
    }

    /** A new contract on this planet, or null if nobody here will hire the company. */
    public static Contract contract(Company c, Random r) {
        List<int[]> pairs = new ArrayList<int[]>();
        for (int e = 0; e < Faction.COUNT; e++) {
            if (!c.planet.has(e) || !hires(c.rep[e])) continue;
            for (int t = 0; t < Faction.COUNT; t++) if (c.planet.has(t) && !allied(e, t)) pairs.add(new int[]{e, t});
        }
        if (pairs.isEmpty()) return null;
        int[] p = pairs.get(r.nextInt(pairs.size()));
        int threat = 1 + r.nextInt(3);
        int scenario = JOBS[r.nextInt(JOBS.length)];
        if (scenario == Scenario.BASE_ASSAULT && threat < 3) scenario = 4;   // base assaults are only big jobs
        int role = r.nextBoolean() ? Scenario.ATTACKER : Scenario.DEFENDER;
        if (scenario == Scenario.BASE_ASSAULT) role = Scenario.ATTACKER;
        float mult = (threat == 1 ? 0.8f : (threat == 2 ? 1.1f : 1.45f)) * (c.difficulty == 0 ? 0.85f : (c.difficulty == 1 ? 1f : 1.2f));
        int budget = Math.round(fieldValue(c) * mult / 50f) * 50;
        Scenario sc = Scenario.get(scenario);
        int reward = Math.round((1400 + threat * 500 + c.difficulty * 300 + budget * 0.22f) * sc.pay / 50f) * 50;
        return new Contract(p[0], p[1], scenario, role, threat, reward, budget, r.nextLong());
    }

    /** Fills the board up to four contracts. */
    public static void fillBoard(Company c, Random r) {
        for (int guard = 0; guard < 12 && c.contracts.size() < 4; guard++) {
            Contract k = contract(c, r);
            if (k == null) break;
            c.contracts.add(k);
        }
    }

    /** Reputation change for every faction when a contract ends with this outcome for the company. */
    public static int[] repChanges(Contract k, int result) {
        int[] d = new int[Faction.COUNT];
        int emp, tgt, empAllies, tgtAllies;
        switch (result) {
            case Battle.RESULT_WIN: emp = 6 + 2 * k.threat; tgt = -(8 + 2 * k.threat); empAllies = 2; tgtAllies = -3; break;
            case Battle.RESULT_DRAW: emp = 2; tgt = -(6 + k.threat); empAllies = 0; tgtAllies = -2; break;
            default: emp = -3; tgt = -(4 + k.threat); empAllies = 0; tgtAllies = -1; break;
        }
        for (int f = 0; f < Faction.COUNT; f++) {
            if (f == k.employer) d[f] = emp;
            else if (f == k.target) d[f] = tgt;
            else if (allied(f, k.employer)) d[f] = empAllies;
            else if (allied(f, k.target)) d[f] = tgtAllies;
        }
        return d;
    }

    // ---------------- forces ----------------

    /**
     * The target faction's force for a contract: its designs (and its makes of weapons and equipment), within the
     * scenario's limits and the contract's budget, painted in its colors. Big jobs sometimes field its prototype.
     */
    public static List<Unit> enemyForce(Contract k, Scenario sc) {
        Random r = new Random(k.seed);
        Limits lim = sc.caps;
        List<Chassis> pool = new ArrayList<Chassis>();
        Chassis proto = null;
        for (Chassis ch : Chassis.ALL) {
            if ((ch.factions & Faction.bit(k.target)) == 0 || !sc.allows(ch) || ch.type == UnitType.EMPLACEMENT) continue;
            if (ch.unique) proto = ch;
            else pool.add(ch);
        }
        List<Unit> out = new ArrayList<Unit>();
        int remaining = k.enemyBudget;
        if (proto != null && k.threat == 3 && r.nextFloat() < 0.3f) {
            Unit u = faction(new Unit(proto), k.target);
            if (lim.fits(out, u) && u.value() <= remaining) {
                out.add(u);
                remaining -= u.value();
            }
        }
        int want = Math.min(lim.total(), 3 + k.threat * 2);
        for (int slot = out.size(); slot < want && !pool.isEmpty(); slot++) {
            int per = remaining / Math.max(1, want - slot);
            Unit pick = null;
            float best = -Float.MAX_VALUE;
            for (int tries = 0; tries < 40; tries++) {
                Unit u = faction(new Unit(pool.get(r.nextInt(pool.size()))), k.target);
                if (u.value() > remaining || !lim.fits(out, u)) continue;
                float fit = 1f - Math.abs(u.value() - per) / (float) Math.max(1, per) + r.nextFloat() * 0.3f;
                if (fit > best) {
                    best = fit;
                    pick = u;
                }
            }
            if (pick == null) break;
            int same = 0;
            for (Unit o : out) if (o.ch == pick.ch) same++;
            if (same > 0) pick.name = pick.ch.name + " " + (same + 1);
            out.add(pick);
            remaining -= pick.value();
        }
        if (out.isEmpty() && !pool.isEmpty()) out.add(faction(new Unit(pool.get(0)), k.target));
        return out;
    }

    /** A unit refitted with a faction's makes of its weapons and equipment, and painted in its colors. */
    public static Unit faction(Unit u, int f) {
        for (int m = 0; m < u.weapon.length; m++) if (u.weapon[m] >= 0 && !Wpn.ALL[u.weapon[m]].unique) u.weapon[m] = Wpn.made(f, Wpn.ALL[u.weapon[m]].base);
        for (int e = 0; e < u.equip.length; e++) if (u.equip[e] >= 0 && !Equip.ALL[u.equip[e]].unique) u.equip[e] = Equip.made(f, Equip.ALL[u.equip[e]].base);
        u.computeMaxes();
        u.repairFully();
        u.paint = Faction.ALL[f].scheme;
        return u;
    }

    // ---------------- markets ----------------

    /** Is a design for sale to this company here: built by a faction on the planet that trades with you? */
    public static boolean sells(Company c, Chassis ch) {
        if (ch.type == UnitType.EMPLACEMENT) return false;
        if (ch.unique) return ch.id == c.offerChassis;
        for (int f = 0; f < Faction.COUNT; f++)
            if (c.planet.has(f) && trades(c.rep[f]) && (ch.factions & Faction.bit(f)) != 0) return true;
        return false;
    }

    /** Standard items need any trading partner here; a faction's makes need that faction; uniques need an offer. */
    public static boolean sellsItem(Company c, int maker, boolean unique, int offerCode) {
        if (unique) return c.offerItem == offerCode;
        for (int f = 0; f < Faction.COUNT; f++) {
            if (!c.planet.has(f) || !trades(c.rep[f])) continue;
            if (maker < 0 || maker == f) return true;
        }
        return false;
    }

    /**
     * Rolls this planet's special offers: an allied faction here may offer its unique prototype or unique item.
     * Called on arrival and after each contract.
     */
    public static void rollOffers(Company c, Random r) {
        c.offerChassis = -1;
        c.offerItem = -1;
        for (int f = 0; f < Faction.COUNT; f++) {
            if (!c.planet.has(f) || !trusts(c.rep[f]) || r.nextFloat() > 0.45f) continue;
            if (r.nextBoolean()) {
                for (Chassis ch : Chassis.ALL) if (ch.unique && (ch.factions & Faction.bit(f)) != 0) c.offerChassis = ch.id;
            } else {
                for (Wpn w : Wpn.ALL) if (w.unique && w.maker == f) c.offerItem = w.id;
                for (Equip e : Equip.ALL) if (e.unique && e.maker == f) c.offerItem = 1000 + e.id;
            }
        }
    }
}
