package com.meanmutton.mechgame.core;

/** Unit designs: the frame, its weapon mounts, and its factory ("stock") loadout. */
public final class Chassis {
    public static final int R_SCOUT = 0, R_LINE = 1, R_STRIKER = 2, R_SUPPORT = 3, R_ARTILLERY = 4, R_BRAWLER = 5, R_COMMAND = 6;
    public static final String[] ROLES = {"Scout", "Front-line", "Striker", "Fire Support", "Artillery", "Brawler", "Command"};

    public static final int ARC_FRONT = 0, ARC_WIDE = 1, ARC_TURRET = 2;
    public static final String[] ARC_NAMES = {"front arc", "wide arc", "turret"};

    public static final class Mount {
        public final int loc, cls, arc;

        Mount(int loc, int cls, int arc) {
            this.loc = loc;
            this.cls = cls;
            this.arc = arc;
        }
    }

    public final int id;
    public final String name;
    public final int type, role;
    public final int baseCost;
    public final float capacity;
    public final int maxArmor, infra, mp, sinks, heatCap, maxExtraSinks, equipSlots;
    public final Mount[] mounts;
    public final int[] stockWeapons, stockBins, stockEquip;
    public final int stockArmor, stockSinks;
    public final String desc;

    private Chassis(int id, String name, int type, int role, int baseCost, float capacity, int maxArmor, int infra, int mp,
                    int sinks, int heatCap, int maxExtraSinks, int equipSlots, String mounts, int stockArmor, int stockSinks,
                    int[] weapons, int[] bins, int[] equip, String desc) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.role = role;
        this.baseCost = baseCost;
        this.capacity = capacity;
        this.maxArmor = maxArmor;
        this.infra = infra;
        this.mp = mp;
        this.sinks = sinks;
        this.heatCap = heatCap;
        this.maxExtraSinks = maxExtraSinks;
        this.equipSlots = equipSlots;
        String[] m = mounts.trim().split("\\s+");
        this.mounts = new Mount[m.length];
        for (int i = 0; i < m.length; i++) this.mounts[i] = parseMount(type, m[i]);
        this.stockArmor = stockArmor;
        this.stockSinks = stockSinks;
        this.stockWeapons = weapons;
        this.stockBins = bins.length == m.length ? bins : new int[m.length];
        this.stockEquip = equip;
        this.desc = desc;
    }

    /** Mortar crews work in the open around the gun: they don't get infantry's protection from big guns. */
    public boolean exposedCrew() {
        return id == 3;
    }

    private static Mount parseMount(int type, String s) {
        char arcC = s.charAt(s.length() - 1);
        int cls = s.charAt(s.length() - 2) - '0';
        String loc = s.substring(0, s.length() - 2);
        int arc = arcC == 'W' ? ARC_WIDE : (arcC == 'T' ? ARC_TURRET : ARC_FRONT);
        int l;
        if (loc.equals("S")) l = 0;
        else if (loc.equals("C")) l = UnitType.M_CORE;
        else if (loc.equals("RA")) l = UnitType.M_RARM;
        else if (loc.equals("LA")) l = UnitType.M_LARM;
        else if (loc.equals("F")) l = UnitType.V_FRONT;
        else if (loc.equals("T")) l = UnitType.V_TOP;
        else throw new IllegalArgumentException(s);
        if (type == UnitType.INFANTRY) arc = ARC_TURRET;
        return new Mount(l, cls, arc);
    }

    private static int[] w(int... ids) {
        return ids;
    }

    private static final int[] NONE = {};

    public static final Chassis[] ALL = {
            // ---------------- Infantry ----------------
            new Chassis(0, "Rifle Platoon", UnitType.INFANTRY, Chassis.R_LINE, 90, 3f, 20, 24, 2, 0, 10, 0, 1,
                    "S1T S1T", 12, 0, w(Wpn.RIFLES, Wpn.RIFLES), NONE, NONE,
                    "Cheap riflemen. Hard to hit in woods and towns, and they hold ground."),
            new Chassis(1, "Rocket Squad", UnitType.INFANTRY, R_STRIKER, 110, 3.5f, 20, 20, 2, 0, 10, 0, 1,
                    "S1T S1T", 15, 0, w(Wpn.AT_TEAM, Wpn.RIFLES), NONE, NONE,
                    "Anti-tank missile teams. Dangerous to anything that wanders close."),
            new Chassis(2, "Recon Team", UnitType.INFANTRY, R_SCOUT, 120, 3f, 15, 16, 3, 0, 10, 0, 1,
                    "S1T", 10, 0, w(Wpn.RIFLES), NONE, w(Equip.SENSORS),
                    "Fast-moving spotters with sensor kits. Great for calling in artillery."),
            new Chassis(3, "Mortar Section", UnitType.INFANTRY, R_ARTILLERY, 140, 4f, 6, 10, 2, 0, 10, 0, 1,
                    "S1T", 4, 0, w(Wpn.MORTAR), w(1), NONE,
                    "A glass cannon: a hard-hitting portable mortar crewed by a handful of soldiers. Keep it far back; it dies fast."),
            // ---------------- Tracked tanks ----------------
            new Chassis(4, "Ironhide MBT", UnitType.TRACKED, R_LINE, 600, 32f, 200, 42, 4, 6, 20, 4, 2,
                    "T3T F2F T1T", 180, 0, w(Wpn.BATTLE_CANNON, Wpn.ROCKETS, Wpn.CHAINGUN), w(1, 0, 0), NONE,
                    "Main battle tank. Thick armor, a big turret gun, and a stubborn crew."),
            new Chassis(5, "Breaker Heavy Tank", UnitType.TRACKED, R_BRAWLER, 720, 38f, 220, 45, 3, 6, 20, 4, 2,
                    "T3T F3F F1F", 200, 0, w(Wpn.SIEGE_CANNON, Wpn.HEAVY_ROCKETS, Wpn.CHAINGUN), w(1, 0, 0), NONE,
                    "A rolling fortress built to smash through the enemy line at close range."),
            new Chassis(6, "Thunder SPG", UnitType.TRACKED, R_ARTILLERY, 650, 30f, 150, 35, 3, 5, 20, 3, 2,
                    "T3T F1F", 120, 0, w(Wpn.SIEGE_HOWITZER, Wpn.LIGHT_LASER), w(1, 0), NONE,
                    "Self-propelled siege howitzer. Reaches the whole map, given a spotter."),
            new Chassis(7, "Quiver Missile Carrier", UnitType.TRACKED, R_SUPPORT, 620, 28f, 160, 37, 4, 6, 20, 3, 2,
                    "T3T T2T F1F", 140, 0, w(Wpn.HEAVY_VOLLEY, Wpn.VOLLEY, Wpn.LIGHT_LASER), w(1, 0, 0), NONE,
                    "Twin missile turrets that rain guided missiles from long range."),
            // ---------------- Hovertanks ----------------
            new Chassis(8, "Dart Scout Hover", UnitType.HOVER, R_SCOUT, 400, 14f, 90, 21, 9, 5, 18, 2, 2,
                    "T1T F1F", 70, 0, w(Wpn.LIGHT_LASER, Wpn.CHAINGUN), NONE, w(Equip.SENSORS),
                    "Blazing fast and skims over water. Finds the enemy, then gets out."),
            new Chassis(9, "Riptide Strike Hover", UnitType.HOVER, R_STRIKER, 480, 18f, 110, 24, 8, 5, 18, 2, 2,
                    "T2T F2F F1F", 100, 0, w(Wpn.LASER, Wpn.ROCKETS, Wpn.LIGHT_ROCKETS), NONE, w(Equip.TURBO),
                    "Hit-and-run hovertank. Dashes in for a rocket salvo, then dashes out."),
            new Chassis(10, "Squall Missile Hover", UnitType.HOVER, R_SUPPORT, 470, 17f, 100, 22, 7, 5, 18, 2, 2,
                    "T2T T2T F1F", 80, 0, w(Wpn.VOLLEY, Wpn.VOLLEY, Wpn.LIGHT_LASER), w(1, 0, 0), NONE,
                    "Mobile missile platform that keeps its distance."),
            // ---------------- VTOLs ----------------
            new Chassis(11, "Mosquito Recon VTOL", UnitType.VTOL, R_SCOUT, 380, 9f, 55, 14, 10, 4, 16, 1, 2,
                    "F1F", 45, 0, w(Wpn.LIGHT_LASER), NONE, w(Equip.SENSORS, Equip.ECM),
                    "Flies over everything. Spots for artillery and jams enemy missiles."),
            new Chassis(12, "Dragonfly Attack VTOL", UnitType.VTOL, R_STRIKER, 450, 10f, 60, 16, 9, 4, 16, 1, 1,
                    "F1F T1T", 55, 0, w(Wpn.LIGHT_ROCKETS, Wpn.CHAINGUN), w(1, 0), NONE,
                    "Swoops in with rockets and a chin gun. Fragile; keep it moving."),
            new Chassis(13, "Talon Gunship", UnitType.VTOL, R_SUPPORT, 520, 11f, 65, 18, 8, 4, 16, 1, 1,
                    "F1F F1F T1T", 60, 0, w(Wpn.LIGHT_VOLLEY, Wpn.LIGHT_VOLLEY, Wpn.LIGHT_LASER), w(1, 0, 0), NONE,
                    "Missile gunship that strikes from beyond enemy reach."),
            // ---------------- Wheeled vehicles ----------------
            new Chassis(14, "Jackal Scout Car", UnitType.WHEELED, R_SCOUT, 300, 14f, 110, 24, 7, 4, 16, 2, 2,
                    "T1T F1F", 90, 0, w(Wpn.CHAINGUN, Wpn.LIGHT_LASER), NONE, w(Equip.SENSORS, Equip.SMOKE),
                    "Cheap, sturdy scout car. Flies along roads."),
            new Chassis(15, "Rhino Armored Car", UnitType.WHEELED, R_LINE, 420, 22f, 140, 29, 6, 5, 18, 2, 2,
                    "T2T F2F F1F", 130, 0, w(Wpn.BATTLE_CANNON, Wpn.ROCKETS, Wpn.CHAINGUN), NONE, NONE,
                    "Six-wheeled brawler with as much armor as a heavy mech."),
            new Chassis(16, "Mule Battery", UnitType.WHEELED, R_ARTILLERY, 380, 18f, 100, 24, 6, 5, 18, 2, 2,
                    "T2T F1F", 80, 0, w(Wpn.HOWITZER, Wpn.CHAINGUN), w(1, 0), NONE,
                    "Truck-mounted howitzer. Shoot, then scoot down the road."),
            new Chassis(17, "Lancer AT Car", UnitType.WHEELED, R_SUPPORT, 400, 18f, 120, 26, 6, 5, 18, 2, 2,
                    "T2T F2F", 100, 0, w(Wpn.VOLLEY, Wpn.LASER), w(1, 0), w(Equip.TARGETING),
                    "Tank-hunter with missiles and a computer-aimed laser."),
            // ---------------- Light mechs ----------------
            new Chassis(18, "Skitter", UnitType.LIGHT_MECH, R_SCOUT, 650, 15f, 64, 47, 8, 8, 30, 4, 2,
                    "RA1W LA1W C1W", 56, 0, w(Wpn.LIGHT_LASER, Wpn.LIGHT_LASER, Wpn.LIGHT_ROCKETS), NONE,
                    w(Equip.SENSORS, Equip.TURBO), "Fleet-footed recon mech. Spots, harasses, and outruns trouble."),
            new Chassis(19, "Needle", UnitType.LIGHT_MECH, R_STRIKER, 650, 16f, 68, 47, 8, 8, 30, 4, 2,
                    "RA1W LA1W C1W", 64, 2, w(Wpn.LIGHT_BURST, Wpn.VIBRO_BLADE, Wpn.LIGHT_BURST), NONE, w(Equip.TURBO),
                    "Knife-fighter. Burst lasers and a vibro blade for flanking attacks."),
            new Chassis(20, "Lantern", UnitType.LIGHT_MECH, R_COMMAND, 700, 15f, 60, 44, 7, 8, 30, 4, 2,
                    "RA1W C1W", 50, 0, w(Wpn.LIGHT_LASER, Wpn.LIGHT_VOLLEY), w(0, 1), w(Equip.ECM, Equip.SENSORS),
                    "Electronic-warfare mech. Jams the enemy and lights targets for your artillery."),
            // ---------------- Medium mechs ----------------
            new Chassis(21, "Halberd", UnitType.MEDIUM_MECH, R_LINE, 1050, 26f, 112, 73, 5, 10, 30, 6, 3,
                    "RA2W LA2W C2W C1W", 104, 2, w(Wpn.LASER, Wpn.LASER, Wpn.ROCKETS, Wpn.LIGHT_LASER), NONE,
                    w(Equip.TARGETING), "Dependable line mech with computer-aimed lasers."),
            new Chassis(22, "Ricochet", UnitType.MEDIUM_MECH, R_STRIKER, 1100, 24f, 100, 68, 7, 10, 30, 6, 3,
                    "RA2W LA1W C2W", 96, 2, w(Wpn.BURST_LASER, Wpn.LIGHT_BURST, Wpn.SEEKERS), w(0, 0, 1), w(Equip.TURBO),
                    "Fast skirmisher that dances around the enemy's flanks."),
            new Chassis(23, "Cinder", UnitType.MEDIUM_MECH, R_SUPPORT, 1100, 26f, 104, 70, 5, 10, 30, 6, 3,
                    "RA2W LA2W C2W", 96, 1, w(Wpn.VOLLEY, Wpn.VOLLEY, Wpn.LASER), w(1, 1, 0), w(Equip.SENSORS),
                    "Missile boat. Keeps enemies under fire from long range."),
            new Chassis(24, "Sabre Dancer", UnitType.MEDIUM_MECH, R_BRAWLER, 1150, 26f, 112, 75, 6, 10, 30, 6, 3,
                    "RA2W LA2W C1W", 108, 2, w(Wpn.ARC_SWORD, Wpn.BURST_LASER, Wpn.LIGHT_ROCKETS), NONE,
                    w(Equip.TURBO, Equip.SMOKE), "Duelist mech with an energized sword. Closes in under smoke."),
            // ---------------- Heavy mechs ----------------
            new Chassis(25, "Bastille", UnitType.HEAVY_MECH, R_LINE, 1550, 40f, 150, 99, 4, 12, 32, 6, 3,
                    "RA3W LA2W C2W C1W", 145, 2, w(Wpn.HEAVY_LASER, Wpn.BATTLE_CANNON, Wpn.ROCKETS, Wpn.LIGHT_LASER),
                    w(0, 1, 0, 0), w(Equip.TARGETING), "Heavy line-holder. Anchors any formation."),
            new Chassis(26, "Overwatch", UnitType.HEAVY_MECH, R_SUPPORT, 1650, 40f, 140, 94, 4, 12, 32, 6, 3,
                    "RA3W LA2W C2W", 130, 0, w(Wpn.RAIL, Wpn.LASER, Wpn.VOLLEY), w(1, 0, 1), w(Equip.TARGETING, Equip.SENSORS),
                    "Long-range sniper with a rail driver and guided missiles."),
            new Chassis(27, "Cleaver", UnitType.HEAVY_MECH, R_BRAWLER, 1600, 40f, 150, 104, 5, 12, 32, 6, 3,
                    "RA3W LA2W C3W C1W", 150, 2, w(Wpn.GREAT_AXE, Wpn.BURST_LASER, Wpn.HEAVY_ROCKETS, Wpn.LIGHT_BURST),
                    w(0, 0, 1, 0), w(Equip.TURBO, Equip.REACTIVE), "Charges in and swings a great axe. Terrifying up close."),
            new Chassis(28, "Rainmaker", UnitType.HEAVY_MECH, R_ARTILLERY, 1600, 38f, 130, 91, 4, 12, 32, 6, 3,
                    "RA3W LA2W C1W", 120, 0, w(Wpn.SIEGE_HOWITZER, Wpn.LASER, Wpn.LIGHT_LASER), w(1, 0, 0),
                    w(Equip.SENSORS, Equip.ECM), "Walking artillery battery that can defend itself."),
            // ---------------- Assault mechs ----------------
            new Chassis(29, "Monolith", UnitType.ASSAULT_MECH, R_LINE, 2150, 56f, 210, 125, 3, 14, 34, 8, 4,
                    "RA3W LA3W C2W C2W C1W", 200, 2,
                    w(Wpn.SIEGE_CANNON, Wpn.HEAVY_LASER, Wpn.ROCKETS, Wpn.LASER, Wpn.LIGHT_LASER), w(1, 0, 0, 0, 0),
                    w(Equip.TARGETING, Equip.REFLECTIVE), "A walking fortress. Slow, and very hard to stop."),
            new Chassis(30, "Pulverizer", UnitType.ASSAULT_MECH, R_BRAWLER, 2250, 56f, 220, 130, 4, 14, 34, 8, 4,
                    "RA3W LA3W C3W C2W", 215, 4, w(Wpn.HAMMER, Wpn.PLASMA, Wpn.HEAVY_ROCKETS, Wpn.BURST_LASER),
                    w(0, 0, 1, 0), w(Equip.TURBO, Equip.REACTIVE, Equip.SMOKE),
                    "Close-assault monster with a thunder hammer and a plasma caster."),
            new Chassis(31, "Skyfall", UnitType.ASSAULT_MECH, R_SUPPORT, 2200, 54f, 200, 122, 3, 14, 34, 8, 4,
                    "RA3W LA3W C3W C2W", 190, 3, w(Wpn.HEAVY_VOLLEY, Wpn.HEAVY_VOLLEY, Wpn.HEAVY_LASER, Wpn.LASER),
                    w(2, 1, 0, 0), w(Equip.SENSORS, Equip.COOLANT), "Missile artillery on legs. Darkens the sky."),
            new Chassis(32, "Aegis", UnitType.ASSAULT_MECH, R_COMMAND, 2400, 56f, 210, 125, 3, 14, 34, 8, 4,
                    "RA3W LA3W C2W C1W", 200, 2, w(Wpn.RAIL, Wpn.HEAVY_LASER, Wpn.ION_LANCE, Wpn.LIGHT_LASER),
                    w(1, 0, 0, 0), w(Equip.ECM, Equip.UPLINK, Equip.TARGETING, Equip.REPAIR_KIT),
                    "Command mech. Jams the enemy, sharpens your whole lance, and hits hard itself."),
    };
}
