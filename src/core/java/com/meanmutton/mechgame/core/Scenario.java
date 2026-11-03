package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Campaign scenarios. A campaign is a fight for one planet between an attacker and a defender.
 * The front runs from -1 (the attacker is driven back to its dropships) to 6 (the attacker is at the
 * defender's base) and starts at 2, so the defender begins with the upper hand. Each battle is a
 * scenario that limits which units may fight and how many, and may lean slightly toward one side.
 * The next scenario is rolled from the result, the front, and a random roll, before you refit.
 *
 * Scenario ids are saved: never renumber them, only add new ones at the end.
 */
public final class Scenario {
    public static final int ATTACKER = 0, DEFENDER = 1;
    public static final String[] SIDE_NAMES = {"Attacker", "Defender"};
    public static final int FRONT_MIN = -1, FRONT_START = 2, FRONT_BASE = 6;
    /**
     * Campaign economics. The attacker lands with more money (ATTACKER_START times the starting funds), but its
     * income stays flat while the defender's grows by DEFENDER_GROWTH of a contract's pay with every battle fought
     * on the planet (up to DEFENDER_MAX_INCOME). Resources become force: the side with more of them may field
     * extra units beyond a scenario's limit (see extraUnits), and the enemy company's force limit follows the two
     * sides' resources. A force of top units costs about as much as a few contracts, so money alone would stop mattering;
     * the extra units are what make a long campaign overwhelming for the defender.
     */
    public static final float ATTACKER_START = 1.5f, DEFENDER_GROWTH = 0.15f, DEFENDER_MAX_INCOME = 2.5f;
    /** Starting funds measured in contract pays, for comparing the two sides' resources. */
    private static final float START_IN_PAYS = 4f;
    /** What the last battle did, as the next roll sees it. */
    public static final int OUT_NONE = 0, OUT_ATTACKER = 1, OUT_DEFENDER = 2, OUT_DRAW = 3;

    // which units may fight
    public static final int F_ALL = 0, F_DROP = 1, F_SCOUT = 2, F_LIGHT = 3, F_MECHS = 4, F_ARMOR = 5, F_AIR = 6, F_GROUND = 7;
    public static final String[] FORCE_TEXT = {
            "any units",
            "VTOLs, hovertanks, wheeled vehicles, light and medium mechs",
            "VTOLs, hovertanks, wheeled vehicles and light mechs",
            "infantry, hovertanks, VTOLs, wheeled vehicles and light mechs",
            "mechs only",
            "infantry and vehicles, no mechs",
            "VTOLs, hovertanks and infantry",
            "anything but VTOLs",
    };

    private static final int ANY = 0, AFTER_ATTACKER_WIN = 1, AFTER_DEFENDER_WIN = 2;

    public final int id;
    public final String name, desc;
    public final int force;
    /** How many mechs, mechs and vehicles, and infantry each side may field (before reserves). */
    public final Limits caps;
    private final int frontMin, frontMax, weight;
    private int cond = ANY;
    /** Chance the attacker wins initiative each round (0.5 is even). */
    public float attackerInitiative = 0.5f;
    /** Time-out lean: positive favors the defender, negative the attacker (fraction of starting value). */
    public float timeLean;
    /** Positive lets an attacking enemy company bring more (and a defending one less), by this fraction. */
    public float capLean;
    /** Free local units that fight for the defender (or the attacker) in this battle only. */
    public int[] defenderGarrison = {}, attackerGarrison = {};
    public int weather = -1, rounds = Battle.MAX_ROUNDS;
    /** Front change when the attacker wins / loses. */
    public int attackerWinShift = 1, attackerLossShift = -1;
    public float pay = 1f;
    /** The winner gets the edge (first-round initiative) in the next battle. */
    public boolean intel;
    public boolean river, town;

    private Scenario(int id, String name, int force, Limits caps, int frontMin, int frontMax, int weight, String desc) {
        this.id = id;
        this.name = name;
        this.force = force;
        this.caps = caps;
        this.frontMin = frontMin;
        this.frontMax = frontMax;
        this.weight = weight;
        this.desc = desc;
    }

    private Scenario init(float attacker) { attackerInitiative = attacker; return this; }
    private Scenario time(float lean) { timeLean = lean; return this; }
    private Scenario cap(float lean) { capLean = lean; return this; }
    private Scenario defGarrison(int... ids) { defenderGarrison = ids; return this; }
    private Scenario attGarrison(int... ids) { attackerGarrison = ids; return this; }
    private Scenario weather(int w) { weather = w; return this; }
    private Scenario rounds(int n) { rounds = n; return this; }
    private Scenario shift(int win, int loss) { attackerWinShift = win; attackerLossShift = loss; return this; }
    private Scenario pay(float p) { pay = p; return this; }
    private Scenario intel() { intel = true; return this; }
    private Scenario after(int c) { cond = c; return this; }
    private Scenario river() { river = true; return this; }
    private Scenario town() { town = true; return this; }

    public static final int INITIAL_DROP = 0, BASE_ASSAULT = 19, EVACUATION = 20;

    public static final Scenario[] ALL = {
            new Scenario(0, "Initial Drop", F_DROP, new Limits(3, 5, 0), 99, 99, 0,
                    "The attacker's dropships come down and the defender rushes to meet them. Only what lands or arrives fast is in the fight.")
                    .rounds(15),
            new Scenario(1, "Recon Sweep", F_SCOUT, new Limits(2, 4, 0), 0, 5, 10,
                    "Scouts feel out the other side's positions. The front doesn't move, but the winner has the edge in the next battle: initiative in its first round.")
                    .rounds(12).shift(0, 0).intel(),
            new Scenario(2, "Probing Attack", F_LIGHT, new Limits(2, 4, 2), 1, 5, 8,
                    "Light forces test the defender's line. The attacker picks the moment, so it wins initiative a little more often.")
                    .rounds(15).init(0.6f),
            new Scenario(3, "Skirmish", F_LIGHT, new Limits(3, 5, 3), 0, 5, 8,
                    "Light units clash over the ground between the lines.").rounds(15),
            new Scenario(4, "Pitched Battle", F_ALL, new Limits(4, 6, 4), 0, 5, 14,
                    "Both sides commit their main forces."),
            new Scenario(5, "Decisive Engagement", F_ALL, new Limits(4, 6, 4), 1, 4, 7,
                    "Everything rides on this one. The front moves two steps either way, and the pay is higher.")
                    .shift(2, -2).pay(1.5f),
            new Scenario(6, "Hold the Ridge", F_ALL, new Limits(4, 6, 4), 1, 5, 7,
                    "The defender is dug in on high ground with a garrison rifle platoon. If time runs out, the defender only has to be close.")
                    .defGarrison(0).time(0.10f),
            new Scenario(7, "Breakthrough", F_ALL, new Limits(4, 6, 4), 2, 5, 13,
                    "The attacker presses its last win. It wins initiative more often, and the enemy's force limit shifts 15% in the attacker's favor. A win moves the front two steps.")
                    .after(AFTER_ATTACKER_WIN).init(0.6f).cap(0.15f).shift(2, -1),
            new Scenario(8, "Counterattack", F_ALL, new Limits(4, 6, 4), 1, 4, 13,
                    "The defender strikes back while the attacker is off balance, winning initiative more often. A defender win moves the front two steps.")
                    .after(AFTER_DEFENDER_WIN).init(0.4f).shift(1, -2),
            new Scenario(9, "Supply Raid", F_LIGHT, new Limits(2, 4, 2), 1, 5, 6,
                    "Raiders hit the defender's supply lines. If time runs out, the attacker only has to be close.")
                    .rounds(12).init(0.55f).time(-0.10f),
            new Scenario(10, "Night Raid", F_ALL, new Limits(2, 4, 2), 1, 5, 5,
                    "A strike under cover of darkness. The attacker wins initiative more often.")
                    .weather(Weather.NIGHT).init(0.6f).rounds(15),
            new Scenario(11, "River Crossing", F_GROUND, new Limits(4, 6, 4), 1, 5, 6,
                    "The defender holds a river line. It wins initiative a little more often, and if time runs out it only has to be close.")
                    .river().init(0.45f).time(0.05f),
            new Scenario(12, "Lance Clash", F_MECHS, new Limits(4, 4, 0), 1, 5, 5,
                    "Mech against mech, nothing else on the field.").rounds(15),
            new Scenario(13, "Armored Thrust", F_ARMOR, new Limits(0, 6, 4), 1, 5, 6,
                    "Tanks, cars and infantry, no mechs. The enemy's force limit shifts 10% in the attacker's favor.")
                    .cap(0.10f),
            new Scenario(14, "Air Assault", F_AIR, new Limits(0, 4, 3), 2, 5, 5,
                    "The attacker leaps ahead by air. It wins initiative more often.")
                    .init(0.6f).rounds(12),
            new Scenario(15, "Street Fight", F_GROUND, new Limits(3, 5, 4), 3, 5, 6,
                    "A fight for a town. The defender has two garrison squads, and if time runs out it only has to be close.")
                    .town().defGarrison(0, 1).time(0.05f).rounds(15),
            new Scenario(16, "Siege Lines", F_ALL, new Limits(4, 6, 4), 4, 5, 6,
                    "The defender's outer works, covered by a gun emplacement.")
                    .defGarrison(33),
            new Scenario(17, "Hold the Landing Zone", F_ALL, new Limits(3, 5, 3), 0, 1, 9,
                    "Backed up against its dropships, the attacker digs in with a garrison rifle platoon. If time runs out, the attacker only has to be close.")
                    .attGarrison(0).time(-0.10f).rounds(15),
            new Scenario(18, "Storm Front", F_GROUND, new Limits(4, 6, 4), 0, 5, 4,
                    "A thunderstorm grounds the VTOLs. Everything else fights.")
                    .weather(Weather.STORM),
            new Scenario(19, "Base Assault", F_ALL, new Limits(4, 6, 4), 99, 99, 0,
                    "The attacker reaches the defender's base: two garrison squads, an armored car, a gun emplacement and a laser turret stand with the defender. If the attacker wins, the planet falls. If not, it is thrown back a step.")
                    .defGarrison(0, 1, 15, 33, 35).shift(0, -1).pay(2f),
            new Scenario(20, "Evacuation", F_ALL, new Limits(4, 6, 4), 99, 99, 0,
                    "The attacker is pushed back to its dropships. If the defender wins, the attacker leaves the planet. If the attacker holds (and if time runs out it only has to be close), reinforcements land and it can try again from the landing zone.")
                    .rounds(15).time(-0.10f).shift(1, 0).pay(1.5f),
    };

    public static Scenario get(int id) {
        return id >= 0 && id < ALL.length ? ALL[id] : ALL[4];
    }

    /** Can this design fight in this scenario? */
    public boolean allows(Chassis ch) {
        int t = ch.type;
        if (t == UnitType.EMPLACEMENT) return false;
        switch (force) {
            case F_DROP: return t == UnitType.VTOL || t == UnitType.HOVER || t == UnitType.WHEELED || t == UnitType.LIGHT_MECH || t == UnitType.MEDIUM_MECH;
            case F_SCOUT: return t == UnitType.VTOL || t == UnitType.HOVER || t == UnitType.WHEELED || t == UnitType.LIGHT_MECH;
            case F_LIGHT: return t == UnitType.INFANTRY || t == UnitType.HOVER || t == UnitType.VTOL || t == UnitType.WHEELED || t == UnitType.LIGHT_MECH;
            case F_MECHS: return UnitType.isMech(t);
            case F_ARMOR: return !UnitType.isMech(t);
            case F_AIR: return t == UnitType.VTOL || t == UnitType.HOVER || t == UnitType.INFANTRY;
            case F_GROUND: return t != UnitType.VTOL;
            default: return true;
        }
    }

    public boolean allows(Unit u) {
        return allows(u.ch);
    }

    /** One-line summary of the restrictions. */
    public String limits() {
        return limits(caps);
    }

    /** Restrictions with a side's own limits (reserves included). */
    public String limits(Limits l) {
        boolean m = false, v = false, inf = false;
        for (Chassis ch : Chassis.SHOP) {
            if (!allows(ch)) continue;
            int k = Limits.kind(ch.type);
            if (k == Limits.MECH) m = true;
            else if (k == Limits.VEHICLE) v = true;
            else if (k == Limits.INFANTRY) inf = true;
        }
        return "Up to " + l.text(m, v, inf) + ": " + FORCE_TEXT[force] + (rounds != Battle.MAX_ROUNDS ? ", " + rounds + " rounds" : "");
    }

    /** Which way the scenario leans, from the player's point of view (null if it's even). */
    public String leanText(int playerSide) {
        int score = 0;
        if (attackerInitiative > 0.5f) score++;
        if (attackerInitiative < 0.5f) score--;
        if (timeLean < 0) score++;
        if (timeLean > 0) score--;
        if (capLean > 0) score++;
        if (attackerGarrison.length > 0) score++;
        if (defenderGarrison.length > 0) score--;
        if (attackerWinShift + attackerLossShift > 0) score++;
        if (attackerWinShift + attackerLossShift < 0) score--;
        if (score == 0) return null;
        boolean forAttacker = score > 0;
        boolean forMe = forAttacker == (playerSide == ATTACKER);
        return "Leans toward the " + (forAttacker ? "attacker" : "defender") + (forMe ? " (you)" : " (the enemy)");
    }

    /** Front after this battle, given who won. */
    public int frontAfter(int front, int outcome) {
        int f = front;
        if (outcome == OUT_ATTACKER) f += attackerWinShift;
        else if (outcome == OUT_DEFENDER) f += attackerLossShift;
        return Math.max(FRONT_MIN, Math.min(FRONT_BASE, f));
    }

    /** What a given outcome of this battle does to the campaign, in a few words. */
    public String resultText(int front, int outcome) {
        if (id == BASE_ASSAULT && outcome == OUT_ATTACKER) return "the planet falls to the attacker";
        if (id == EVACUATION && outcome == OUT_DEFENDER) return "the attacker leaves the planet";
        int f = frontAfter(front, outcome);
        if (f == front) return "the front holds";
        int steps = Math.abs(f - front);
        return (f > front ? "the attacker" : "the defender") + " gains " + (steps > 1 ? steps + " steps" : "ground") + " (front " + f + ")";
    }

    /** Stakes from the player's point of view. */
    public String stakes(int front, int playerSide) {
        int win = playerSide == ATTACKER ? OUT_ATTACKER : OUT_DEFENDER;
        int lose = playerSide == ATTACKER ? OUT_DEFENDER : OUT_ATTACKER;
        return "Win: " + resultText(front, win) + ".  Lose: " + resultText(front, lose) + ".";
    }

    public static float startFunds(int side) {
        return side == ATTACKER ? ATTACKER_START : 1f;
    }

    /** Pay multiplier for a side in a planet's n-th battle (counting from 0). */
    public static float income(int side, int battle) {
        return side == ATTACKER ? 1f : Math.min(DEFENDER_MAX_INCOME, 1f + DEFENDER_GROWTH * battle);
    }

    /** A side's resources after this many battles: starting funds plus income so far, in contract pays. */
    public static float resources(int side, int battles) {
        float r = START_IN_PAYS * startFunds(side);
        for (int i = 0; i < battles; i++) r += income(side, i);
        return r;
    }

    /** Attacker's resources divided by the defender's after this many battles. */
    public static float attackerRatio(int battles) {
        return resources(ATTACKER, battles) / resources(DEFENDER, battles);
    }

    /** Reserves: extra units a side may field beyond a scenario's limit, from its share of the resources. */
    public static int extraUnits(int side, int battles) {
        float r = attackerRatio(battles);
        if (side == ATTACKER) return r >= 1.4f ? 1 : 0;
        return r < 0.65f ? 3 : (r < 0.8f ? 2 : (r < 1f ? 1 : 0));
    }

    /** What a side may field in this scenario, reserves included. */
    public Limits limitsFor(int side, int battles) {
        return caps.plus(extraUnits(side, battles));
    }

    /** Multiplier on the enemy company's force limit: its side's resources relative to yours. */
    public static float forceFactor(int rivalSide, int battles) {
        float ratio = attackerRatio(battles);
        float f = rivalSide == ATTACKER ? ratio : 1f / ratio;
        return Math.max(0.6f, Math.min(1.6f, f));
    }

    /** Rolls the next scenario from the front, what just happened, and a random roll. */
    public static int roll(int front, int lastOutcome, int lastId, Random r) {
        if (front >= FRONT_BASE) return BASE_ASSAULT;
        if (front < 0) return EVACUATION;
        List<Scenario> pool = new ArrayList<Scenario>();
        List<Integer> weights = new ArrayList<Integer>();
        int total = 0;
        for (Scenario s : ALL) {
            if (s.weight <= 0 || front < s.frontMin || front > s.frontMax) continue;
            if (s.cond == AFTER_ATTACKER_WIN && lastOutcome != OUT_ATTACKER) continue;
            if (s.cond == AFTER_DEFENDER_WIN && lastOutcome != OUT_DEFENDER) continue;
            if (s.id == lastId && s.id != 4) continue;   // no repeats, except plain pitched battles
            int wgt = s.weight;
            // a stalemate calls for scouting; momentum calls for exploiting it
            if (lastOutcome == OUT_DRAW && s.intel) wgt *= 2;
            if (s.cond != ANY) wgt *= 2;
            pool.add(s);
            weights.add(wgt);
            total += wgt;
        }
        if (pool.isEmpty()) return 4;
        int k = r.nextInt(total);
        for (int i = 0; i < pool.size(); i++) {
            k -= weights.get(i);
            if (k < 0) return pool.get(i).id;
        }
        return pool.get(pool.size() - 1).id;
    }

    /** Description of where the front stands. */
    public static String frontName(int front) {
        switch (front) {
            case -1: return "at the attacker's dropships";
            case 0: return "at the landing zone";
            case 1: return "near the landing zone";
            case 2: return "on the attacker's side of the line";
            case 3: return "contested";
            case 4: return "on the defender's side of the line";
            case 5: return "near the defender's base";
            default: return "at the defender's base";
        }
    }

    /** Free garrison units for one side, built fresh for this battle. */
    public static List<Unit> garrison(int[] ids) {
        List<Unit> out = new ArrayList<Unit>();
        for (int id : ids) {
            Unit u = new Unit(Chassis.ALL[id]);
            u.tag = "garrison";
            int same = 0;
            for (Unit o : out) if (o.ch == u.ch) same++;
            if (same > 0) u.name = u.ch.name + " " + (same + 1);
            out.add(u);
        }
        return out;
    }
}
