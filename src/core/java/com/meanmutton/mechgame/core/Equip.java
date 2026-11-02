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

    private Equip(int id, String name, String shortName, float mass, int cost, int uses, String desc) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.mass = mass;
        this.cost = cost;
        this.uses = uses;
        this.desc = desc;
    }

    public static final Equip[] ALL = {
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
}
