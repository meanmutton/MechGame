package com.meanmutton.mechgame.core;

import java.util.List;

/**
 * How many units of each kind one side may field: mechs, mechs and vehicles together (VTOLs count as vehicles),
 * and infantry. A full-size battle allows 4 mechs, 6 mechs and vehicles, and 4 infantry. Emplacements and
 * garrisons don't count.
 */
public final class Limits {
    public static final int MECH = 0, VEHICLE = 1, INFANTRY = 2, OTHER = 3;
    public static final Limits FULL = new Limits(4, 6, 4);

    public final int mechs, heavy, infantry;

    public Limits(int mechs, int heavy, int infantry) {
        this.mechs = Math.min(mechs, heavy);
        this.heavy = heavy;
        this.infantry = infantry;
    }

    public static int kind(int type) {
        if (UnitType.isMech(type)) return MECH;
        if (UnitType.isVehicle(type)) return VEHICLE;
        if (type == UnitType.INFANTRY) return INFANTRY;
        return OTHER;
    }

    public int total() {
        return heavy + infantry;
    }

    /** Reserves: k extra units, as room for mechs and vehicles (half of it may be mechs, if any are allowed). */
    public Limits plus(int k) {
        if (k <= 0) return this;
        return new Limits(mechs == 0 ? 0 : mechs + (k + 1) / 2, heavy + k, infantry);
    }

    /** Null if the unit can join these units within the limits, otherwise why not. */
    public String refuse(List<Unit> units, Unit add) {
        int m = 0, h = 0, inf = 0;
        for (Unit u : units) {
            if (u == add || "garrison".equals(u.tag)) continue;   // garrisons don't count; hired units do
            int k = kind(u.type);
            if (k == MECH) { m++; h++; }
            else if (k == VEHICLE) h++;
            else if (k == INFANTRY) inf++;
        }
        int k = kind(add.type);
        if (k == MECH && m >= mechs) return mechs == 0 ? "no mechs allowed" : "at most " + mechs + " mech" + (mechs == 1 ? "" : "s");
        if ((k == MECH || k == VEHICLE) && h >= heavy)
            return heavy == 0 ? "no vehicles allowed" : "at most " + heavy + " mechs and vehicles in all";
        if (k == INFANTRY && inf >= infantry) return infantry == 0 ? "no infantry allowed" : "at most " + infantry + " infantry";
        if (k == OTHER) return "can't be deployed";
        return null;
    }

    public boolean fits(List<Unit> units, Unit add) {
        return refuse(units, add) == null;
    }

    /** True if every unit in the list (garrisons aside) fits within the limits together. */
    public boolean holds(List<Unit> units) {
        java.util.ArrayList<Unit> so = new java.util.ArrayList<Unit>();
        for (Unit u : units) {
            if (u.tag != null && u.tag.equals("garrison")) continue;
            if (!fits(so, u)) return false;
            so.add(u);
        }
        return true;
    }

    /** "4 mechs, 6 mechs and vehicles in all, 4 infantry", shortened to what the allowed units can use. */
    public String text(boolean mechsOk, boolean vehiclesOk, boolean infantryOk) {
        StringBuilder b = new StringBuilder();
        if (mechsOk && vehiclesOk && mechs > 0) {
            if (mechs < heavy) b.append(mechs).append(" mech").append(mechs == 1 ? "" : "s").append(", ").append(heavy).append(" mechs and vehicles in all");
            else b.append(heavy).append(" mechs or vehicles");
        } else if (mechsOk && mechs > 0) b.append(mechs).append(" mech").append(mechs == 1 ? "" : "s");
        else if (vehiclesOk && heavy > 0) b.append(heavy).append(" vehicle").append(heavy == 1 ? "" : "s");
        if (infantryOk && infantry > 0) b.append(b.length() > 0 ? ", " : "").append(infantry).append(" infantry");
        return b.toString();
    }

    public String text() {
        return text(true, true, true);
    }
}
