package com.meanmutton.mechgame.core;

/**
 * The unit types (nine you can buy, plus fixed emplacements that defend bases): what each can mount, where it can be hit, and how it moves.
 * Hit tables give percentage weights per location for an attack from each arc.
 */
public final class UnitType {
    public static final int INFANTRY = 0, TRACKED = 1, HOVER = 2, VTOL = 3, WHEELED = 4,
            LIGHT_MECH = 5, MEDIUM_MECH = 6, HEAVY_MECH = 7, ASSAULT_MECH = 8, EMPLACEMENT = 9, COUNT = 10;

    public static final int ARC_FRONT = 0, ARC_LEFT = 1, ARC_RIGHT = 2, ARC_REAR = 3;

    // Mech locations. BACK is rear armor over the core and shares the core's infrastructure.
    public static final int M_COCKPIT = 0, M_CORE = 1, M_BACK = 2, M_LARM = 3, M_RARM = 4, M_LLEG = 5, M_RLEG = 6;
    // Vehicle locations. Slot 4 is the turret (or the rotor on a VTOL).
    public static final int V_FRONT = 0, V_LEFT = 1, V_RIGHT = 2, V_REAR = 3, V_TOP = 4;

    public static final String[] NAMES = {"Infantry", "Tracked Tank", "Hovertank", "VTOL", "Wheeled Vehicle",
            "Light Mech", "Medium Mech", "Heavy Mech", "Assault Mech", "Emplacement"};
    public static final String[] SHORT = {"INF", "TANK", "HOVER", "VTOL", "WHEEL", "LIGHT", "MEDIUM", "HEAVY", "ASSAULT", "FIXED"};

    /** Heaviest weapon class each type can mount. */
    public static final int[] MAX_CLASS = {1, 3, 2, 1, 2, 1, 2, 3, 3, 3};

    /** Height for line of sight (in elevation levels above the ground it stands on). */
    public static final int[] HEIGHT = {1, 1, 1, 4, 1, 2, 2, 2, 2, 1};

    /** Chance per hull hit of motive damage (vehicles only). */
    public static final float[] MOTIVE = {0f, 0.06f, 0.12f, 0.06f, 0.10f, 0f, 0f, 0f, 0f, 0f};

    public static boolean isMech(int t) {
        return t >= LIGHT_MECH && t <= ASSAULT_MECH;
    }

    public static boolean isVehicle(int t) {
        return t >= TRACKED && t <= WHEELED;
    }

    static final String[] MECH_LOCS = {"Cockpit", "Core", "Back", "Left Arm", "Right Arm", "Left Leg", "Right Leg"};
    static final String[] MECH_SHORT = {"CP", "CORE", "BACK", "LA", "RA", "LL", "RL"};
    static final String[] VEH_LOCS = {"Front", "Left Side", "Right Side", "Rear", "Turret"};
    static final String[] VEH_SHORT = {"FRONT", "LEFT", "RIGHT", "REAR", "TURRET"};
    static final String[] VTOL_LOCS = {"Nose", "Left Side", "Right Side", "Tail", "Rotor"};
    static final String[] VTOL_SHORT = {"NOSE", "LEFT", "RIGHT", "TAIL", "ROTOR"};
    static final String[] INF_LOCS = {"Platoon"};

    public static String[] locNames(int t) {
        if (t == INFANTRY) return INF_LOCS;
        if (isMech(t)) return MECH_LOCS;
        if (t == VTOL) return VTOL_LOCS;
        return VEH_LOCS;
    }

    public static String[] locShort(int t) {
        if (t == INFANTRY) return INF_LOCS;
        if (isMech(t)) return MECH_SHORT;
        if (t == VTOL) return VTOL_SHORT;
        return VEH_SHORT;
    }

    public static int locCount(int t) {
        return locNames(t).length;
    }

    /** Where a location's infrastructure lives (BACK uses the core's). */
    public static int infraLoc(int t, int loc) {
        return isMech(t) && loc == M_BACK ? M_CORE : loc;
    }

    /** Share of the total armor at each location (percent). */
    static int[] armorShare(int t) {
        if (t == INFANTRY) return new int[]{100};
        if (isMech(t)) return new int[]{6, 26, 10, 13, 13, 16, 16};
        if (t == VTOL) return new int[]{30, 20, 20, 15, 15};
        return new int[]{32, 20, 20, 12, 16};
    }

    /** Share of the total infrastructure at each location (percent; mech BACK has none of its own). */
    static int[] infraShare(int t) {
        if (t == INFANTRY) return new int[]{100};
        if (isMech(t)) return new int[]{6, 30, 0, 14, 14, 18, 18};
        if (t == VTOL) return new int[]{26, 20, 20, 16, 18};
        return new int[]{26, 20, 20, 14, 20};
    }

    private static final int[][] MECH_HIT = {
            {3, 33, 0, 14, 14, 18, 18},   // front
            {3, 22, 5, 30, 5, 30, 5},     // left
            {3, 22, 5, 5, 30, 5, 30},     // right
            {2, 8, 40, 10, 10, 15, 15},   // rear
    };
    private static final int[][] VEH_HIT = {
            {60, 8, 8, 0, 24},
            {10, 62, 0, 8, 20},
            {10, 0, 62, 8, 20},
            {0, 10, 10, 60, 20},
    };
    private static final int[][] VTOL_HIT = {
            {55, 10, 10, 0, 25},
            {10, 55, 0, 10, 25},
            {10, 0, 55, 10, 25},
            {0, 10, 10, 55, 25},
    };

    public static int[] hitTable(int t, int arc) {
        if (t == INFANTRY) return new int[]{100};
        if (isMech(t)) return MECH_HIT[arc];
        if (t == VTOL) return VTOL_HIT[arc];
        return VEH_HIT[arc];
    }

    public static final String[] ARC_NAMES = {"front", "left side", "right side", "rear"};
}
