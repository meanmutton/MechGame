package com.meanmutton.mechgame.core;

/** Electronic and special equipment. */
public final class Equip {
    public static final int TARGETING = 0, ECM = 1, SENSORS = 2, TURBO = 3, COOLANT = 4, SMOKE = 5, REFLECTIVE = 6,
            REACTIVE = 7, UPLINK = 8, REPAIR_KIT = 9;

    public final int id;
    public final String name, shortName;
    public final float mass;
    public final int cost;
    public final int uses;     // per battle, 0 = passive
    public final String desc;
    /** The standard equipment this is a version of (its own id for standard items); rules check this, not the id. */
    public final int base;
    /** Faction that makes this version (-1 for the standard make). */
    public final int maker;
    public final boolean unique;
    /**
     * How strong it is: +% to hit (targeting computer, uplink), jamming radius in hexes (ECM), woods points seen
     * through (sensors), extra MP (turbo), heat dumped (coolant), rounds of smoke, % damage stopped (plating),
     * armor restored (repair kit).
     */
    public final int power;
    public final String bonus;

    private static final int[] STANDARD_POWER = {10, 2, 1, 3, 15, 2, 25, 25, 5, 15};

    private Equip(int id, String name, String shortName, float mass, int cost, int uses, String desc) {
        this(id, name, shortName, mass, cost, uses, desc, id, -1, false, STANDARD_POWER[id], null);
    }

    private Equip(int id, String name, String shortName, float mass, int cost, int uses, String desc, int base, int maker,
                  boolean unique, int power, String bonus) {
        this.base = base;
        this.maker = maker;
        this.unique = unique;
        this.power = power;
        this.bonus = bonus;
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.mass = mass;
        this.cost = cost;
        this.uses = uses;
        this.desc = desc;
    }

    private static final Equip[] BASE = {
            new Equip(TARGETING, "Targeting Computer", "TARGET CPU", 2f, 200, 0,
                    "+10% to hit with energy and ballistic weapons."),
            new Equip(ECM, "ECM Suite", "ECM", 1.5f, 220, 0,
                    "Jamming field 2 hexes around this unit: enemy missiles -15% against friendly units inside it, "
                            + "enemy seekers and targeting computers lose their bonus, and enemy artillery can't use spotters to target them."),
            new Equip(SENSORS, "Sensor Suite", "SENSORS", 1f, 120, 0,
                    "Ignores the first woods penalty, sees through ECM for spotting, and ignores the night penalty."),
            new Equip(TURBO, "Turbo Booster", "TURBO", 1.5f, 150, 3,
                    "+3 movement for one activation. 3 uses per battle; mechs also take +5 heat."),
            new Equip(COOLANT, "Coolant Pod", "COOLANT", 1f, 90, 1,
                    "Once per battle: instantly dumps 15 heat."),
            new Equip(SMOKE, "Smoke Launcher", "SMOKE", 0.5f, 60, 2,
                    "Twice per battle: fills your hex with smoke until the end of next round. Blocks sight, -25% to hit you."),
            new Equip(REFLECTIVE, "Reflective Plating", "REFLECT", 2f, 250, 0,
                    "Energy weapon damage against this unit -25%."),
            new Equip(REACTIVE, "Reactive Plating", "REACTIVE", 2.5f, 250, 0,
                    "Missile and artillery damage against this unit -25%."),
            new Equip(UPLINK, "Command Uplink", "UPLINK", 1.5f, 200, 0,
                    "Friendly units within 3 hexes get +5% to hit."),
            new Equip(REPAIR_KIT, "Field Repair Kit", "REPAIR", 1.5f, 180, 1,
                    "Once per battle: restores 15 armor to the most damaged location."),
    };

    /**
     * Every piece of equipment, indexed by id: the standard items (ids 0-9), then the factions' versions, then the
     * unique items. Ids are saved: only ever add at the end.
     */
    public static final Equip[] ALL = build();

    private static java.util.List<Equip> list;

    private static Equip[] build() {
        list = new java.util.ArrayList<Equip>(java.util.Arrays.asList(BASE));
        make(Faction.HELION, COOLANT, 22, 0, "dumps 22 heat");
        make(Faction.HELION, REFLECTIVE, 33, 0, "stops a third of energy damage");
        make(Faction.VARDAN, REACTIVE, 33, 0, "stops a third of missile and artillery damage");
        make(Faction.KESTREL, TURBO, 4, 0, "+4 movement instead of +3");
        make(Faction.MERIDIAN, TARGETING, 15, 0, "+15% instead of +10%");
        make(Faction.MERIDIAN, ECM, 3, 0, "jams 3 hexes around instead of 2");
        make(Faction.MERIDIAN, SENSORS, 2, 0, "sees through 2 points of woods");
        make(Faction.MERIDIAN, UPLINK, 8, 0, "+8% instead of +5%");
        make(Faction.THORNWALL, REPAIR_KIT, 25, 0, "restores 25 armor");
        make(Faction.IRONROOT, REPAIR_KIT, 15, 2, "two uses per battle");
        make(Faction.OBSIDIAN, SMOKE, 3, 3, "three uses; smoke lasts a round longer");
        unique(Faction.KESTREL, "Afterburner", "AFTERBURN", TURBO, 1f, 400, 4, 5,
                "+5 movement for one activation, four times a battle.");
        unique(Faction.MERIDIAN, "Omni-Link", "OMNI-LINK", UPLINK, 1.5f, 600, 0, 10,
                "A prototype command link: friendly units within 3 hexes get +10% to hit.");
        unique(Faction.IRONROOT, "Field Workshop", "WORKSHOP", REPAIR_KIT, 2f, 450, 3, 15,
                "A compact repair rig: restores 15 armor, three times a battle.");
        unique(Faction.OBSIDIAN, "Shroud", "SHROUD", ECM, 1.5f, 600, 0, 3,
                "A prototype jammer that blankets 3 hexes around its carrier.");
        Equip[] out = list.toArray(new Equip[0]);
        list = null;
        return out;
    }

    private static void make(int maker, int b, int power, int uses, String bonus) {
        Equip e = BASE[b];
        Faction f = Faction.ALL[maker];
        int cost = Math.round(e.cost * 1.2f / 5f) * 5;
        list.add(new Equip(list.size(), f.shortName + " " + e.name, e.shortName, e.mass, cost, uses > 0 ? uses : e.uses,
                e.desc + " " + f.name + " make: " + bonus + ".", b, maker, false, power, bonus));
    }

    private static void unique(int maker, String name, String shortName, int b, float mass, int cost, int uses, int power, String desc) {
        list.add(new Equip(list.size(), name, shortName, mass, cost, uses, desc + " Unique (" + Faction.ALL[maker].name + ").",
                b, maker, true, power, "unique"));
    }

    /** The id of a faction's version of a standard item, or the standard item if that faction doesn't make one. */
    public static int made(int maker, int base) {
        for (Equip e : ALL) if (e.base == base && e.maker == maker && !e.unique) return e.id;
        return base;
    }

    public static int named(String name) {
        for (Equip e : ALL) if (e.name.equals(name)) return e.id;
        return -1;
    }
}
