package com.meanmutton.mechgame.core;

/**
 * The great powers that build mechs and vehicles. Each has a specialty its designs lean into; two blocs of three
 * share some designs with their allies, and two independents go their own way. Designs carry a mask of the
 * factions that build them (Chassis.factions). Factions matter in mercenary play; the campaign and quick battles
 * ignore them (every design except uniques is for sale there).
 */
public final class Faction {
    public static final int HELION = 0, VARDAN = 1, KESTREL = 2, ASHFALL = 3, MERIDIAN = 4, THORNWALL = 5, IRONROOT = 6, OBSIDIAN = 7,
            COUNT = 8;
    public static final int BLOC_NONE = -1, BLOC_ACCORD = 0, BLOC_PACT = 1;
    public static final String[] BLOC_NAMES = {"Northern Accord", "Iron Pact"};

    /** The faction's own color: its name, tags and emblem symbol are drawn in it, and it is its units' accent. */
    public final int id, bloc, color;
    public final String name, shortName, specialty, desc;
    /** What the faction's units wear: main and trim chosen around its color, its color as the accent. */
    public final Scheme scheme;

    private Faction(int id, String name, String shortName, int bloc, int color, int main, int trim, String specialty, String desc) {
        this.scheme = new Scheme(main, trim, color);
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.bloc = bloc;
        this.color = color;
        this.specialty = specialty;
        this.desc = desc;
    }

    public static final Faction[] ALL = {
            new Faction(HELION, "Helion Concord", "Helion", BLOC_ACCORD, 0xFFFFD447, 0xFFD9B24A, 0xFFF2EEDC, "Energy weapons",
                    "A bright, orderly republic of engineers. Lasers, plasma and heat sinks; mechs that never run dry."),
            new Faction(VARDAN, "Vardan Hegemony", "Vardan", BLOC_PACT, 0xFFD2463F, 0xFF9A2E2A, 0xFF5A5E66, "Armor and cannons",
                    "An old military power. Thick armor, big guns, tracked tanks and assault mechs."),
            new Faction(KESTREL, "Kestrel League", "Kestrel", BLOC_ACCORD, 0xFF4FD1C5, 0xFF2E9A92, 0xFFE8F2F2, "Speed",
                    "Frontier worlds that live by moving fast. Hovertanks, VTOLs and light mechs that strike and vanish."),
            new Faction(ASHFALL, "Ashfall Collective", "Ashfall", BLOC_PACT, 0xFFFF8A3D, 0xFF4A4A4E, 0xFFB8602A, "Missiles and artillery",
                    "Volcanic foundry worlds. Missile carriers, fire-support mechs and guns that reach across the map."),
            new Faction(MERIDIAN, "Meridian Syndicate", "Meridian", BLOC_ACCORD, 0xFF7C93FF, 0xFF3E4FA8, 0xFFB8C2D8, "Electronics and command",
                    "Corporate states of sensors and signals. Command mechs, scouts and electronic warfare."),
            new Faction(THORNWALL, "Thornwall Compact", "Thornwall", BLOC_NONE, 0xFF83C25E, 0xFF4E7A3A, 0xFF8A7A5A, "Close assault and infantry",
                    "Fortress worlds that fight up close. Brawler mechs, tough infantry and dug-in defenses."),
            new Faction(IRONROOT, "Ironroot Freeholds", "Ironroot", BLOC_PACT, 0xFFC09460, 0xFF8A6A44, 0xFF6A6E58, "Rugged vehicles",
                    "Homesteads and mining towns. Cheap, tough wheeled vehicles that are easy to keep running."),
            new Faction(OBSIDIAN, "Obsidian Veil", "Obsidian", BLOC_NONE, 0xFFA97BE0, 0xFF2A2430, 0xFF5A4A6E, "Raiders and stealth",
                    "A shadowy network of raiders. Jammers, smoke and fast strikers that hit from the flanks."),
    };

    public static int bit(int faction) {
        return 1 << faction;
    }

    public static final int ALL_MASK = (1 << COUNT) - 1;

    /** Mask of a bloc's members. */
    public static int blocMask(int bloc) {
        int m = 0;
        for (Faction f : ALL) if (f.bloc == bloc) m |= bit(f.id);
        return m;
    }

    public static int mask(int... factions) {
        int m = 0;
        for (int f : factions) m |= bit(f);
        return m;
    }

    /** "Helion, Meridian" or "All factions" for a design's mask. */
    public static String names(int mask) {
        if (mask == ALL_MASK) return "All factions";
        StringBuilder b = new StringBuilder();
        for (Faction f : ALL) if ((mask & bit(f.id)) != 0) b.append(b.length() > 0 ? ", " : "").append(f.shortName);
        return b.length() == 0 ? "-" : b.toString();
    }

    /** The one faction that builds a design alone, or -1 if it is shared. */
    public static int exclusive(int mask) {
        return Integer.bitCount(mask) == 1 ? Integer.numberOfTrailingZeros(mask) : -1;
    }
}
