package com.meanmutton.mechgame.core;

/** Weapon catalog. Classes: 1 = light (any unit), 2 = medium, 3 = heavy (heaviest units only). */
public final class Wpn {
    public static final int F_ENERGY = 0, F_BALLISTIC = 1, F_MISSILE = 2, F_ARTILLERY = 3, F_MELEE = 4, F_INFANTRY = 5;
    public static final String[] FAMILY = {"Energy", "Ballistic", "Missile", "Artillery", "Melee", "Infantry"};

    public static final int VS_INF = 1, VS_AIR = 2, AP = 4, HEAT_TARGET = 8, ION = 16, STAGGER = 32, SEEKER = 64,
            INDIRECT = 128, MECH_ONLY = 256, INF_ONLY = 512;

    public final int id;
    public final String name;
    public final int cls, family;
    public final int dmg;       // total damage of a full hit
    public final int group;     // damage per location roll (smaller = spread over more locations)
    public final int heat;
    public final int ammo;      // shots per bin (0 = no ammunition)
    public final float binMass;
    public final int rMin, rShort, rMed, rLong;
    public final float mass;
    public final int cost;
    public final int acc;       // accuracy modifier in percent
    public final int flags;
    public final int splash;    // artillery splash damage to adjacent hexes
    public final String desc;

    private Wpn(int id, String name, int cls, int family, int dmg, int group, int heat, int ammo, float binMass,
                int rMin, int rShort, int rMed, int rLong, float mass, int cost, int acc, int flags, int splash, String desc) {
        this.id = id;
        this.name = name;
        this.cls = cls;
        this.family = family;
        this.dmg = dmg;
        this.group = group;
        this.heat = heat;
        this.ammo = ammo;
        this.binMass = binMass;
        this.rMin = rMin;
        this.rShort = rShort;
        this.rMed = rMed;
        this.rLong = rLong;
        this.mass = mass;
        this.cost = cost;
        this.acc = acc;
        this.flags = flags;
        this.splash = splash;
        this.desc = desc;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    public boolean usesAmmo() {
        return ammo > 0;
    }

    public boolean isMelee() {
        return family == F_MELEE;
    }

    public boolean direct() {
        return family == F_ENERGY || family == F_BALLISTIC;
    }

    public int binCost() {
        return Math.max(5, cost / 10);
    }

    public static final int LIGHT_LASER = 0, LIGHT_BURST = 1, LASER = 2, BURST_LASER = 3, HEAVY_LASER = 4, PLASMA = 5,
            ION_LANCE = 6, CHAINGUN = 7, LIGHT_CANNON = 8, BATTLE_CANNON = 9, FLAK = 10, SIEGE_CANNON = 11, RAIL = 12,
            LIGHT_ROCKETS = 13, ROCKETS = 14, HEAVY_ROCKETS = 15, LIGHT_VOLLEY = 16, VOLLEY = 17, HEAVY_VOLLEY = 18,
            SEEKERS = 19, MORTAR = 20, HOWITZER = 21, SIEGE_HOWITZER = 22, VIBRO_BLADE = 23, ARC_SWORD = 24,
            BATTLE_AXE = 25, GREAT_AXE = 26, HAMMER = 27, RIFLES = 28, SATCHELS = 29, AT_TEAM = 30;

    public static final Wpn[] ALL = {
            new Wpn(LIGHT_LASER, "Light Laser", 1, F_ENERGY, 4, 4, 2, 0, 0, 0, 2, 4, 6, 1f, 40, 0, 0, 0,
                    "Cheap, light, reliable. Never runs dry."),
            new Wpn(LIGHT_BURST, "Light Burst Laser", 1, F_ENERGY, 4, 4, 3, 0, 0, 0, 1, 2, 3, 1f, 55, 15, 0, 0,
                    "Rapid pulses: very accurate, but short-ranged and hotter."),
            new Wpn(LASER, "Laser", 2, F_ENERGY, 7, 7, 4, 0, 0, 0, 3, 6, 9, 2f, 110, 0, 0, 0,
                    "The standard line weapon of the battlefield."),
            new Wpn(BURST_LASER, "Burst Laser", 2, F_ENERGY, 7, 7, 5, 0, 0, 0, 2, 4, 6, 2.5f, 150, 15, 0, 0,
                    "Hits where you aim it. Runs hot."),
            new Wpn(HEAVY_LASER, "Heavy Laser", 3, F_ENERGY, 11, 11, 8, 0, 0, 0, 3, 7, 11, 5f, 250, 0, 0, 0,
                    "Long-reaching beam that burns through armor. Needs serious cooling."),
            new Wpn(PLASMA, "Plasma Caster", 3, F_ENERGY, 15, 15, 12, 0, 0, 0, 2, 5, 7, 6f, 320, 0, HEAT_TARGET, 0,
                    "Hurls superheated plasma. Huge damage and it heats up the target too."),
            new Wpn(ION_LANCE, "Ion Lance", 2, F_ENERGY, 8, 8, 7, 0, 0, 0, 3, 6, 10, 4f, 220, 0, ION, 0,
                    "Charged particle beam. Scrambles the target's targeting systems for a round."),
            new Wpn(CHAINGUN, "Chaingun", 1, F_BALLISTIC, 6, 2, 0, 12, 0.5f, 0, 1, 2, 3, 1f, 50, 5, VS_INF, 0,
                    "Spray of bullets. Shreds infantry; makes no heat."),
            new Wpn(LIGHT_CANNON, "Light Cannon", 1, F_BALLISTIC, 5, 5, 1, 10, 0.5f, 0, 3, 6, 9, 2f, 70, 0, 0, 0,
                    "Light autoloading cannon with good reach."),
            new Wpn(BATTLE_CANNON, "Battle Cannon", 2, F_BALLISTIC, 10, 10, 2, 6, 1f, 0, 3, 6, 9, 5f, 150, 0, 0, 0,
                    "Hard-hitting main gun with modest heat."),
            new Wpn(FLAK, "Flak Repeater", 2, F_BALLISTIC, 6, 3, 1, 10, 1f, 0, 2, 5, 8, 3f, 120, 0, VS_AIR, 0,
                    "Airburst shells. +25% to hit and double damage against VTOLs."),
            new Wpn(SIEGE_CANNON, "Siege Cannon", 3, F_BALLISTIC, 18, 18, 4, 4, 1.5f, 0, 2, 4, 6, 9f, 280, 0, 0, 0,
                    "Enormous short-range gun. One hit can take off a limb."),
            new Wpn(RAIL, "Rail Driver", 3, F_BALLISTIC, 14, 14, 1, 6, 1.5f, 0, 4, 9, 14, 10f, 400, 5, AP, 0,
                    "Magnetic accelerator. Extreme range, armor-piercing (critical hits through armor)."),
            new Wpn(LIGHT_ROCKETS, "Light Rocket Pod", 1, F_MISSILE, 8, 2, 2, 6, 0.5f, 0, 1, 3, 5, 1f, 60, 0, 0, 0,
                    "Four unguided rockets that scatter across the target."),
            new Wpn(ROCKETS, "Rocket Pod", 2, F_MISSILE, 12, 2, 3, 5, 0.5f, 0, 1, 3, 5, 2f, 100, 0, 0, 0,
                    "Six rockets. Light for the damage it can do up close."),
            new Wpn(HEAVY_ROCKETS, "Heavy Rocket Pod", 3, F_MISSILE, 20, 2, 5, 4, 1f, 0, 1, 3, 5, 3.5f, 170, 0, 0, 0,
                    "Ten-rocket salvo. Devastating at short range."),
            new Wpn(LIGHT_VOLLEY, "Light Volley Missiles", 1, F_MISSILE, 5, 5, 2, 8, 0.5f, 3, 5, 10, 15, 1.5f, 80, 0, 0, 0,
                    "Long-range guided missiles. Poor inside 3 hexes."),
            new Wpn(VOLLEY, "Volley Missiles", 2, F_MISSILE, 10, 5, 4, 6, 1f, 3, 5, 10, 15, 3f, 150, 0, 0, 0,
                    "Ten long-range missiles. The fire-support standard."),
            new Wpn(HEAVY_VOLLEY, "Heavy Volley Missiles", 3, F_MISSILE, 15, 5, 6, 4, 1f, 3, 5, 10, 15, 5f, 240, 0, 0, 0,
                    "Fifteen missiles from across the map."),
            new Wpn(SEEKERS, "Seeker Rockets", 2, F_MISSILE, 8, 2, 3, 6, 0.5f, 0, 1, 3, 5, 2f, 160, 20, SEEKER, 0,
                    "Lock-on rockets: very accurate and all of them hit. ECM spoils the lock."),
            new Wpn(MORTAR, "Field Mortar", 1, F_ARTILLERY, 6, 3, 1, 6, 0.5f, 2, 3, 6, 9, 2f, 90, 0, INDIRECT, 3,
                    "Lobs shells over obstacles at anything a friendly unit can see. Accurate at long range, wild up close."),
            new Wpn(HOWITZER, "Howitzer", 2, F_ARTILLERY, 10, 5, 2, 5, 1f, 3, 4, 9, 14, 6f, 220, 0, INDIRECT, 4,
                    "Indirect artillery with splash. Most accurate at long range, very poor at short range."),
            new Wpn(SIEGE_HOWITZER, "Siege Howitzer", 3, F_ARTILLERY, 14, 5, 3, 4, 1.5f, 4, 5, 11, 18, 11f, 380, 0, INDIRECT, 6,
                    "Heavy artillery that reaches the whole battlefield. Deadly at long range, nearly useless up close."),
            new Wpn(VIBRO_BLADE, "Vibro Blade", 1, F_MELEE, 6, 6, 0, 0, 0, 0, 1, 1, 1, 1f, 60, 10, MECH_ONLY, 0,
                    "Humming combat knife. Mechs only; adjacent targets."),
            new Wpn(ARC_SWORD, "Arc Sword", 2, F_MELEE, 11, 11, 1, 0, 0, 0, 1, 1, 1, 3f, 140, 5, MECH_ONLY, 0,
                    "Energized blade. Mechs only; adjacent targets."),
            new Wpn(BATTLE_AXE, "Battle Axe", 2, F_MELEE, 13, 13, 0, 0, 0, 0, 1, 1, 1, 4f, 130, -5, MECH_ONLY, 0,
                    "Heavy chopping blade. Mechs only; adjacent targets."),
            new Wpn(GREAT_AXE, "Great Axe", 3, F_MELEE, 20, 20, 0, 0, 0, 0, 1, 1, 1, 7f, 240, -5, MECH_ONLY, 0,
                    "A building-sized axe. Mechs only; adjacent targets."),
            new Wpn(HAMMER, "Thunder Hammer", 3, F_MELEE, 16, 16, 1, 0, 0, 0, 1, 1, 1, 6f, 260, 0, MECH_ONLY | STAGGER, 0,
                    "Shockwave hammer. Staggers the target: half movement and -15% to hit on its next activation."),
            new Wpn(RIFLES, "Assault Rifles", 1, F_INFANTRY, 4, 2, 0, 0, 0, 0, 1, 2, 3, 0.5f, 15, 0, VS_INF | INF_ONLY, 0,
                    "Infantry small arms. Infantry only."),
            new Wpn(SATCHELS, "Satchel Charges", 1, F_INFANTRY, 10, 5, 0, 2, 0.25f, 0, 1, 1, 1, 0.5f, 40, 10, INF_ONLY, 0,
                    "Demolition charges planted on adjacent vehicles. Infantry only."),
            new Wpn(AT_TEAM, "AT Missile Team", 1, F_INFANTRY, 7, 7, 0, 4, 0.25f, 0, 2, 4, 6, 0.5f, 50, 0, INF_ONLY, 0,
                    "Shoulder-launched anti-tank missiles. Infantry only."),
    };

    public static boolean canMount(Wpn w, int unitType, int mountClass) {
        if (w.cls > mountClass) return false;
        if (w.has(MECH_ONLY) && !UnitType.isMech(unitType)) return false;
        if (w.has(INF_ONLY) && unitType != UnitType.INFANTRY) return false;
        // infantry carry their own weapons, plus the man-portable field mortar
        if (unitType == UnitType.INFANTRY && !w.has(INF_ONLY) && w.id != MORTAR) return false;
        if (unitType == UnitType.VTOL && w.family == F_ARTILLERY) return false;
        return true;
    }

    public String rangeText() {
        if (isMelee()) return "Adjacent";
        String s = rShort + "/" + rMed + "/" + rLong;
        if (rMin > 0) s += " (min " + rMin + ")";
        return s;
    }
}
