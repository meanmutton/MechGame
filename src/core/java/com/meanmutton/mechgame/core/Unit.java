package com.meanmutton.mechgame.core;

/**
 * One owned unit: its loadout, its condition (which carries over between battles),
 * and its state during a battle.
 */
public final class Unit {
    public static final float ARMOR_MASS = 0.1f;

    public final Chassis ch;
    public final int type;
    public String name;
    /** Not part of either company: "garrison" (local troops) or "hired" (enemy stopgaps). Not saved. */
    public String tag;

    // ---- loadout ----
    public final int[] weapon;   // per mount, -1 = empty
    public final int[] bins;     // extra ammo bins per mount
    public final int[] equip;    // per equipment slot, -1 = empty
    public int armorTotal;
    public int extraSinks;

    // ---- condition (persistent) ----
    public int[] armor, armorMax, infra, infraMax;
    public boolean[] wpnBroken, eqBroken;
    public int[] ammo;
    public int sinksLost, mpLost;
    public boolean destroyed;

    // ---- battle state ----
    public int team, q, r, facing;
    public float heat;
    public boolean activated, shutdown, staggered, ionized, turboOn, fired, moved;
    public int movedHexes, mpUsed;
    public int[] eqUses;
    public float drawX, drawY, drawFacing;
    public boolean deathShown;
    public float hitFlash;
    public int kills;
    public float damageDealt;

    public Unit(Chassis ch) {
        this.ch = ch;
        this.type = ch.type;
        this.name = ch.name;
        int nm = ch.mounts.length;
        weapon = new int[nm];
        bins = new int[nm];
        for (int i = 0; i < nm; i++) {
            weapon[i] = i < ch.stockWeapons.length ? ch.stockWeapons[i] : -1;
            bins[i] = ch.stockBins[i];
        }
        equip = new int[ch.equipSlots];
        for (int i = 0; i < equip.length; i++) equip[i] = i < ch.stockEquip.length ? ch.stockEquip[i] : -1;
        armorTotal = ch.stockArmor;
        extraSinks = ch.stockSinks;
        int nl = UnitType.locCount(type);
        armor = new int[nl];
        armorMax = new int[nl];
        infra = new int[nl];
        infraMax = new int[nl];
        wpnBroken = new boolean[nm];
        ammo = new int[nm];
        eqBroken = new boolean[equip.length];
        eqUses = new int[equip.length];
        computeMaxes();
        repairFully();
    }

    // =====================================================================
    // Loadout math
    // =====================================================================

    public void computeMaxes() {
        int[] as = UnitType.armorShare(type), is = UnitType.infraShare(type);
        int sum = 0;
        for (int i = 0; i < as.length; i++) {
            armorMax[i] = Math.round(armorTotal * as[i] / 100f);
            sum += armorMax[i];
        }
        int main = UnitType.isMech(type) ? UnitType.M_CORE : 0;
        armorMax[main] = Math.max(0, armorMax[main] + armorTotal - sum);
        for (int i = 0; i < is.length; i++) {
            infraMax[i] = is[i] == 0 ? 0 : Math.max(3, Math.round(ch.infra * is[i] / 100f));
        }
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] > armorMax[i]) armor[i] = armorMax[i];
        }
    }

    public void repairFully() {
        for (int i = 0; i < armor.length; i++) {
            armor[i] = armorMax[i];
            infra[i] = infraMax[i];
        }
        for (int i = 0; i < weapon.length; i++) {
            wpnBroken[i] = false;
            ammo[i] = maxAmmo(i);
        }
        for (int i = 0; i < equip.length; i++) eqBroken[i] = false;
        sinksLost = 0;
        mpLost = 0;
        destroyed = false;
    }

    public Wpn wpn(int mount) {
        return weapon[mount] < 0 ? null : Wpn.ALL[weapon[mount]];
    }

    public int maxAmmo(int mount) {
        Wpn w = wpn(mount);
        if (w == null || !w.usesAmmo()) return 0;
        return w.ammo * (1 + bins[mount]);
    }

    public float mass() {
        float m = armorTotal * ARMOR_MASS + extraSinks;
        for (int i = 0; i < weapon.length; i++) {
            Wpn w = wpn(i);
            if (w != null) m += w.mass + bins[i] * w.binMass;
        }
        for (int e : equip) if (e >= 0) m += Equip.ALL[e].mass;
        return m;
    }

    public int value() {
        int v = ch.baseCost + armorTotal + extraSinks * 40;
        for (int i = 0; i < weapon.length; i++) {
            Wpn w = wpn(i);
            if (w != null) v += w.cost + bins[i] * w.binCost();
        }
        for (int e : equip) if (e >= 0) v += Equip.ALL[e].cost;
        return v;
    }

    /** Returns null if the loadout is legal, otherwise the reason. */
    public String validate() {
        if (mass() > ch.capacity + 0.001f) return "Over capacity";
        if (armorTotal > ch.maxArmor) return "Too much armor";
        if (extraSinks > ch.maxExtraSinks) return "Too many heat sinks";
        for (int i = 0; i < weapon.length; i++) {
            Wpn w = wpn(i);
            if (w != null && !Wpn.canMount(w, type, ch.mounts[i].cls)) return w.name + " can't mount there";
            if (w == null && bins[i] > 0) return "Ammo with no weapon";
            if (w != null && !w.usesAmmo() && bins[i] > 0) return "Ammo for a weapon that uses none";
        }
        for (int i = 0; i < equip.length; i++) {
            for (int j = i + 1; j < equip.length; j++)
                if (equip[i] >= 0 && equip[j] >= 0 && Equip.ALL[equip[i]].base == Equip.ALL[equip[j]].base) return "Duplicate equipment";
        }
        return null;
    }

    /** Working equipment of this standard type (any maker)? */
    public boolean hasEquip(int id) {
        return equipSlot(id) >= 0;
    }

    /** Strength of the best working equipment of this standard type (Equip.power), or 0 if none. */
    public int equipPower(int id) {
        int p = 0;
        for (int i = 0; i < equip.length; i++) if (equip[i] >= 0 && !eqBroken[i] && Equip.ALL[equip[i]].base == id) p = Math.max(p, Equip.ALL[equip[i]].power);
        return p;
    }

    public int equipSlot(int id) {
        for (int i = 0; i < equip.length; i++) if (equip[i] >= 0 && Equip.ALL[equip[i]].base == id && !eqBroken[i]) return i;
        return -1;
    }

    // =====================================================================
    // Condition
    // =====================================================================

    public int totalArmor() {
        int s = 0;
        for (int a : armor) s += a;
        return s;
    }

    public int totalInfra() {
        int s = 0;
        for (int i : infra) s += i;
        return s;
    }

    public int totalInfraMax() {
        int s = 0;
        for (int i : infraMax) s += i;
        return s;
    }

    /** 0..1 overall condition for health bars and AI. */
    public float condition() {
        if (destroyed) return 0;
        float a = armorTotal + totalInfraMax();
        return a <= 0 ? 0 : (totalArmor() + totalInfra()) / a;
    }

    public boolean isDamaged() {
        if (destroyed || sinksLost > 0 || mpLost > 0) return true;
        for (int i = 0; i < armor.length; i++) if (armor[i] < armorMax[i] || infra[i] < infraMax[i]) return true;
        for (int i = 0; i < weapon.length; i++) if (wpnBroken[i] || ammo[i] < maxAmmo(i)) return true;
        for (boolean b : eqBroken) if (b) return true;
        return false;
    }

    public int repairCost() {
        int c = 0;
        for (int i = 0; i < armor.length; i++) c += (armorMax[i] - armor[i]) * 2 + (infraMax[i] - infra[i]) * 5;
        for (int i = 0; i < weapon.length; i++) {
            Wpn w = wpn(i);
            if (w == null) continue;
            if (wpnBroken[i]) c += w.cost / 2;
            if (w.usesAmmo()) c += (maxAmmo(i) - ammo[i]) * w.binCost() / w.ammo;
        }
        for (int i = 0; i < equip.length; i++) if (eqBroken[i] && equip[i] >= 0) c += Equip.ALL[equip[i]].cost / 2;
        c += sinksLost * 20 + mpLost * 30;
        if (destroyed) c += ch.baseCost * 3 / 10;
        return c;
    }

    public boolean locDestroyed(int loc) {
        return infra[UnitType.infraLoc(type, loc)] <= 0;
    }

    public boolean legsGone(int n) {
        int c = 0;
        if (infra[UnitType.M_LLEG] <= 0) c++;
        if (infra[UnitType.M_RLEG] <= 0) c++;
        return c >= n;
    }

    // =====================================================================
    // Derived battle stats
    // =====================================================================

    public boolean alive() {
        return !destroyed;
    }

    public int baseMp() {
        int mp = ch.mp - mpLost;
        if (UnitType.isMech(type)) {
            if (legsGone(2)) return 0;
            if (legsGone(1)) mp = (mp + 1) / 2;
        }
        return Math.max(0, mp);
    }

    public int dissipation() {
        return Math.max(0, ch.sinks + extraSinks * 2 - sinksLost * 2);
    }

    public float heatFrac() {
        return ch.heatCap <= 0 ? 0 : heat / ch.heatCap;
    }

    public int heatPenaltyHit() {
        float f = heatFrac();
        return f >= 0.75f ? 20 : (f >= 0.5f ? 10 : 0);
    }

    public int heatPenaltyMp() {
        float f = heatFrac();
        return f >= 0.75f ? 2 : (f >= 0.5f ? 1 : 0);
    }

    /** Infantry damage output scales with surviving troopers. */
    public float firepowerScale() {
        if (type != UnitType.INFANTRY) return 1f;
        return Math.max(0.25f, infra[0] / (float) Math.max(1, infraMax[0]));
    }

    public boolean weaponUsable(int m) {
        Wpn w = wpn(m);
        if (w == null || wpnBroken[m]) return false;
        if (locDestroyed(ch.mounts[m].loc)) return false;
        if (w.usesAmmo() && ammo[m] <= 0) return false;
        return true;
    }

    public String statusText() {
        if (destroyed) return "DESTROYED";
        if (shutdown) return "SHUT DOWN";
        StringBuilder b = new StringBuilder();
        if (ch.fixed()) b.append("FIXED ");
        else if (baseMp() == 0) b.append("IMMOBILE ");
        if (staggered) b.append("STAGGERED ");
        if (ionized) b.append("IONIZED ");
        return b.toString().trim();
    }

    // =====================================================================
    // Persistence
    // =====================================================================

    public String serialize() {
        StringBuilder b = new StringBuilder();
        // a design's own name is stored as empty (it can be longer than a typed name may be)
        b.append(ch.id).append(',').append(name.equals(ch.name) ? "" : clean(name, 30)).append(',').append(armorTotal).append(',').append(extraSinks)
                .append(',').append(sinksLost).append(',').append(mpLost).append(',').append(destroyed ? 1 : 0);
        b.append(';');
        for (int i = 0; i < weapon.length; i++) {
            if (i > 0) b.append('/');
            b.append(weapon[i]).append(':').append(bins[i]).append(':').append(ammo[i]).append(':').append(wpnBroken[i] ? 1 : 0);
        }
        b.append(';');
        for (int i = 0; i < equip.length; i++) {
            if (i > 0) b.append('/');
            b.append(equip[i]).append(':').append(eqBroken[i] ? 1 : 0);
        }
        b.append(';');
        for (int i = 0; i < armor.length; i++) {
            if (i > 0) b.append('/');
            b.append(armor[i]).append(':').append(infra[i]);
        }
        return b.toString();
    }

    public static Unit parse(String s) {
        String[] parts = s.split(";", -1);
        String[] h = parts[0].split(",");
        Chassis ch = Chassis.ALL[Integer.parseInt(h[0])];
        Unit u = new Unit(ch);
        u.name = h[1].length() == 0 ? ch.name : h[1];
        u.armorTotal = Integer.parseInt(h[2]);
        u.extraSinks = Integer.parseInt(h[3]);
        u.sinksLost = Integer.parseInt(h[4]);
        u.mpLost = Integer.parseInt(h[5]);
        u.destroyed = Integer.parseInt(h[6]) != 0;
        String[] ws = parts[1].split("/");
        for (int i = 0; i < ws.length && i < u.weapon.length; i++) {
            String[] f = ws[i].split(":");
            u.weapon[i] = Integer.parseInt(f[0]);
            u.bins[i] = Integer.parseInt(f[1]);
            u.ammo[i] = Integer.parseInt(f[2]);
            u.wpnBroken[i] = Integer.parseInt(f[3]) != 0;
        }
        if (parts[2].length() > 0) {
            String[] es = parts[2].split("/");
            for (int i = 0; i < es.length && i < u.equip.length; i++) {
                String[] f = es[i].split(":");
                u.equip[i] = Integer.parseInt(f[0]);
                u.eqBroken[i] = Integer.parseInt(f[1]) != 0;
            }
        }
        u.computeMaxes();
        String[] ls = parts[3].split("/");
        for (int i = 0; i < ls.length && i < u.armor.length; i++) {
            String[] f = ls[i].split(":");
            u.armor[i] = Math.min(u.armorMax[i], Integer.parseInt(f[0]));
            u.infra[i] = Math.min(u.infraMax[i], Integer.parseInt(f[1]));
        }
        return u;
    }

    static String clean(String s) {
        return clean(s, 18);
    }

    /** Strips the characters saves use as separators, and limits the length. */
    static String clean(String s, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < max; i++) {
            char c = s.charAt(i);
            if (c == ',' || c == ';' || c == '/' || c == ':' || c == '|' || c < 32) continue;
            b.append(c);
        }
        return b.toString();
    }

    /** Copy with the same loadout (used for enemy forces and market previews). */
    public Unit copyLoadout() {
        Unit u = new Unit(ch);
        System.arraycopy(weapon, 0, u.weapon, 0, weapon.length);
        System.arraycopy(bins, 0, u.bins, 0, bins.length);
        System.arraycopy(equip, 0, u.equip, 0, equip.length);
        u.armorTotal = armorTotal;
        u.extraSinks = extraSinks;
        u.computeMaxes();
        u.repairFully();
        return u;
    }
}
