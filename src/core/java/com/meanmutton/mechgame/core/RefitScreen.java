package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Workshop: swap weapons and equipment, adjust armor, heat sinks and ammunition.
 * Removed parts go into the company's spare-parts store and can be reinstalled for free.
 */
public final class RefitScreen extends Screen {
    private static final int DONE = 1, ARM_DN = 2, ARM_UP = 3, SINK_DN = 4, SINK_UP = 5, REPAIR = 6,
            MOUNT = 100, BIN_DN = 200, BIN_UP = 300, SLOT = 400, OPT = 500, CLOSE = 499;
    private final Unit un;
    private final Company c;
    private float t;
    private int choosing = -1;      // mount index, or 1000 + equipment slot
    private final List<Btn> overlay = new ArrayList<Btn>();
    private String note;
    private float noteT;

    public RefitScreen(Game game, Unit un) {
        super(game);
        this.un = un;
        this.c = game.company;
    }

    protected void layout() {
        buttons.clear();
        overlay.clear();
        float lx = u * 3, lw = w * 0.36f;
        if (un.isDamaged()) {
            Btn r = add(new Btn(REPAIR, "Repair first: $" + Ui.money(un.repairCost())).set(lx, h * 0.5f, lw, u * 10));
            r.enabled = c.cash >= un.repairCost();
            r.color = 0xFF8F6A2E;
            add(new Btn(DONE, "Back to HQ").set(w - u * 43, h - u * 12, u * 40, u * 9));
            return;
        }
        float rowY = h * 0.56f;
        float bs = u * 8;
        Btn ad = add(new Btn(ARM_DN, "-").set(lx + lw - bs * 2 - u, rowY, bs, bs));
        Btn au = add(new Btn(ARM_UP, "+").set(lx + lw - bs, rowY, bs, bs));
        ad.repeat = au.repeat = true;
        ad.enabled = un.armorTotal > 0;
        au.enabled = un.armorTotal < un.ch.maxArmor && fits(Math.min(5, un.ch.maxArmor - un.armorTotal) * Unit.ARMOR_MASS) && c.cash >= 5;
        Btn sd = add(new Btn(SINK_DN, "-").set(lx + lw - bs * 2 - u, rowY + bs + u * 2, bs, bs));
        Btn su = add(new Btn(SINK_UP, "+").set(lx + lw - bs, rowY + bs + u * 2, bs, bs));
        sd.enabled = un.extraSinks > 0;
        su.enabled = un.extraSinks < un.ch.maxExtraSinks && fits(1) && c.cash >= 40;
        if (un.type == UnitType.INFANTRY) sd.visible = su.visible = false;

        float rx = lx + lw + u * 4, rw = w - rx - u * 3;
        float y = h * 0.16f;
        float rh = Math.min(u * 9.5f, (h * 0.66f) / (un.weapon.length + un.equip.length + 1));
        for (int m = 0; m < un.weapon.length; m++) {
            Btn b = add(new Btn(MOUNT + m, "").set(rx, y, rw - u * 20, rh - u * 0.8f));
            b.color = 0xFF1E293C;
            Wpn wp = un.wpn(m);
            if (wp != null && wp.usesAmmo()) {
                Btn dn = add(new Btn(BIN_DN + m, "-").set(rx + rw - u * 19, y, u * 8.5f, rh - u * 0.8f));
                Btn up = add(new Btn(BIN_UP + m, "+").set(rx + rw - u * 9.5f, y, u * 8.5f, rh - u * 0.8f));
                dn.enabled = un.bins[m] > 0;
                up.enabled = fits(wp.binMass) && c.cash >= wp.binCost() && un.bins[m] < 6;
            }
            y += rh;
        }
        y += u * 3;
        for (int e = 0; e < un.equip.length; e++) {
            Btn b = add(new Btn(SLOT + e, "").set(rx, y, rw, rh - u * 0.8f));
            b.color = 0xFF1E2A30;
            y += rh;
        }
        add(new Btn(DONE, "Done").set(w - u * 33, h - u * 12, u * 30, u * 9)).color = 0xFF2E7D4F;
        if (choosing >= 0) layoutChooser();
    }

    private boolean fits(float extraMass) {
        return un.mass() + extraMass <= un.ch.capacity + 0.001f;
    }

    private void layoutChooser() {
        float pw = w * 0.9f, ph = h * 0.9f, px = (w - pw) / 2, py = (h - ph) / 2;
        List<int[]> opts = new ArrayList<int[]>(); // {id, enabled}
        boolean equipMode = choosing >= 1000;
        if (equipMode) {
            int slot = choosing - 1000;
            int cur = un.equip[slot];
            float freeMass = un.ch.capacity - un.mass() + (cur >= 0 ? Equip.ALL[cur].mass : 0);
            for (Equip e : Equip.ALL) {
                boolean dup = false;
                for (int s = 0; s < un.equip.length; s++) if (s != slot && un.equip[s] == e.id) dup = true;
                boolean ok = !dup && e.mass <= freeMass + 0.001f && (c.armoryE[e.id] > 0 || c.cash >= e.cost || e.id == cur);
                if (un.type == UnitType.INFANTRY && e.id != Equip.SENSORS && e.id != Equip.SMOKE) continue;
                opts.add(new int[]{e.id, ok ? 1 : 0});
            }
        } else {
            int m = choosing;
            Wpn cur = un.wpn(m);
            float freeMass = un.ch.capacity - un.mass() + (cur != null ? cur.mass + un.bins[m] * cur.binMass : 0);
            for (Wpn wp : Wpn.ALL) {
                if (!Wpn.canMount(wp, un.type, un.ch.mounts[m].cls)) continue;
                boolean ok = wp.mass <= freeMass + 0.001f && (c.armoryW[wp.id] > 0 || c.cash >= wp.cost || (cur != null && cur.id == wp.id));
                opts.add(new int[]{wp.id, ok ? 1 : 0});
            }
        }
        int cols = 2;
        int rows = (opts.size() + 1 + cols - 1) / cols;
        float cw = (pw - u * 6) / cols, ch = Math.min(u * 9, (ph - u * 14) / rows);
        Btn empty = new Btn(OPT + 999, "Empty this " + (equipMode ? "slot" : "mount"));
        empty.set(px + u * 2, py + u * 11, cw - u, ch - u * 0.6f);
        empty.color = 0xFF3A2E2E;
        empty.sub = "Part goes to your spares";
        overlay.add(empty);
        for (int i = 0; i < opts.size(); i++) {
            int k = i + 1;
            int[] o = opts.get(i);
            Btn b = new Btn(OPT + o[0], "");
            b.set(px + u * 2 + (k % cols) * (cw + u * 2), py + u * 11 + (k / cols) * ch, cw - u, ch - u * 0.6f);
            b.enabled = o[1] == 1;
            b.color = 0xFF1E293C;
            b.selected = equipMode ? un.equip[choosing - 1000] == o[0] : un.weapon[choosing] == o[0];
            overlay.add(b);
        }
        overlay.add(new Btn(CLOSE, "Cancel").set(px + pw - u * 26, py + u * 1.5f, u * 23, u * 8));
    }

    protected List<Btn> activeButtons() {
        return choosing >= 0 ? overlay : buttons;
    }

    private void relayout() {
        w = -1;
        game.setScreen(this);
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
        if (noteT > 0) noteT -= dt;
    }

    private void note(String s) {
        note = s;
        noteT = 2.5f;
    }

    protected void onClick(Btn b) {
        int id = b.id;
        if (id == DONE) {
            game.saveCompany();
            game.toHq();
            return;
        }
        if (id == REPAIR) {
            if (c.cash >= un.repairCost()) {
                c.cash -= un.repairCost();
                un.repairFully();
                game.sfx(Platform.SND_BUY, 0.7f);
            }
        } else if (id == ARM_UP) {
            int add = Math.min(5, un.ch.maxArmor - un.armorTotal);
            if (add > 0 && fits(add * Unit.ARMOR_MASS) && c.cash >= add) {
                c.cash -= add;
                un.armorTotal += add;
                changed();
            }
        } else if (id == ARM_DN) {
            int sub = Math.min(5, un.armorTotal);
            un.armorTotal -= sub;
            c.cash += sub / 2;
            changed();
        } else if (id == SINK_UP) {
            if (fits(1) && c.cash >= 40 && un.extraSinks < un.ch.maxExtraSinks) {
                c.cash -= 40;
                un.extraSinks++;
                changed();
            }
        } else if (id == SINK_DN) {
            if (un.extraSinks > 0) {
                un.extraSinks--;
                c.cash += 20;
                changed();
            }
        } else if (id >= BIN_UP && id < BIN_UP + 50) {
            int m = id - BIN_UP;
            Wpn wp = un.wpn(m);
            if (wp != null && fits(wp.binMass) && c.cash >= wp.binCost()) {
                c.cash -= wp.binCost();
                un.bins[m]++;
                changed();
            }
        } else if (id >= BIN_DN && id < BIN_DN + 50) {
            int m = id - BIN_DN;
            Wpn wp = un.wpn(m);
            if (wp != null && un.bins[m] > 0) {
                un.bins[m]--;
                c.cash += wp.binCost() / 2;
                changed();
            }
        } else if (id >= MOUNT && id < MOUNT + 50) {
            choosing = id - MOUNT;
        } else if (id >= SLOT && id < SLOT + 50) {
            choosing = 1000 + id - SLOT;
        } else if (id == CLOSE) {
            choosing = -1;
        } else if (id >= OPT) {
            int pick = id - OPT;
            if (choosing >= 1000) installEquip(choosing - 1000, pick == 999 ? -1 : pick);
            else installWeapon(choosing, pick == 999 ? -1 : pick);
            choosing = -1;
        }
        relayout();
    }

    private void changed() {
        un.computeMaxes();
        un.repairFully();
        game.sfx(Platform.SND_CLICK, 0.4f);
    }

    private void installWeapon(int m, int wid) {
        int old = un.weapon[m];
        if (old == wid) return;
        if (wid >= 0) {
            Wpn nw = Wpn.ALL[wid];
            boolean owned = c.armoryW[wid] > 0;
            if (!owned && c.cash < nw.cost) {
                note("Not enough money");
                return;
            }
            if (owned) c.armoryW[wid]--;
            else c.cash -= nw.cost;
            note(owned ? nw.name + " taken from spares" : nw.name + " bought for $" + Ui.money(nw.cost));
        }
        if (old >= 0) {
            c.armoryW[old]++;
            Wpn ow = Wpn.ALL[old];
            c.cash += un.bins[m] * ow.binCost() / 2;
        }
        un.weapon[m] = wid;
        un.bins[m] = 0;
        changed();
        game.sfx(Platform.SND_BUY, 0.6f);
    }

    private void installEquip(int s, int eid) {
        int old = un.equip[s];
        if (old == eid) return;
        if (eid >= 0) {
            Equip ne = Equip.ALL[eid];
            boolean owned = c.armoryE[eid] > 0;
            if (!owned && c.cash < ne.cost) {
                note("Not enough money");
                return;
            }
            if (owned) c.armoryE[eid]--;
            else c.cash -= ne.cost;
        }
        if (old >= 0) c.armoryE[old]++;
        un.equip[s] = eid;
        changed();
        game.sfx(Platform.SND_BUY, 0.6f);
    }

    public boolean back() {
        if (choosing >= 0) {
            choosing = -1;
            relayout();
        } else {
            game.saveCompany();
            game.toHq();
        }
        return true;
    }

    public void render(Gfx g) {
        background(g);
        g.text("WORKSHOP: " + un.name, u * 3, h * 0.055f, u * 5, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text(UnitType.NAMES[un.type] + " · " + Chassis.ROLES[un.ch.role] + "   |   value $" + Ui.money(un.value())
                + "   |   weapon class up to " + Info.classTag(UnitType.MAX_CLASS[un.type]), u * 3, h * 0.105f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("$" + Ui.money(c.cash), w - u * 3, h * 0.07f, u * 6.5f, Col.MONEY, Gfx.ALIGN_RIGHT, true);
        float lx = u * 3, lw = w * 0.36f;
        Ui.panel(g, lx - u, h * 0.15f, lw + u * 2, h * 0.85f - u * 15, u * 2);
        UnitArt.draw(g, un, lx + lw / 2, h * 0.3f, -90 + (float) Math.sin(t * 0.7f) * 25, Battle.PLAYER, u * 22, t, 1);
        if (un.isDamaged()) {
            g.text("This unit must be repaired before it can be refitted.", lx + lw / 2, h * 0.45f, u * 2.6f, Col.ACCENT, Gfx.ALIGN_CENTER, false);
            renderButtons(g);
            return;
        }
        float mass = un.mass();
        boolean over = mass > un.ch.capacity + 0.001f;
        g.text("Capacity used: " + fmt(mass) + " / " + fmt(un.ch.capacity) + " t", lx, h * 0.47f, u * 2.8f, over ? Col.BAD : Col.TEXT, Gfx.ALIGN_LEFT, true);
        Ui.bar(g, lx, h * 0.5f, lw, u * 2, mass / un.ch.capacity, over ? Col.BAD : (mass / un.ch.capacity > 0.95f ? Col.ACCENT : Col.GOOD));
        float rowY = h * 0.56f, bs = u * 8;
        g.text("Armor " + un.armorTotal + " / " + un.ch.maxArmor, lx, rowY + bs * 0.35f, u * 2.8f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("$1 per point, " + fmt(Unit.ARMOR_MASS * 10) + " t per 10", lx, rowY + bs * 0.8f, u * 2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        if (un.type != UnitType.INFANTRY) {
            g.text("Extra heat sinks " + un.extraSinks + " / " + un.ch.maxExtraSinks, lx, rowY + bs + u * 2 + bs * 0.35f, u * 2.8f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            g.text("+2 cooling each, 1 t, $40", lx, rowY + bs + u * 2 + bs * 0.8f, u * 2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        }
        float sy = rowY + bs * 2 + u * 6;
        int heat = Info.alphaHeat(un), diss = un.dissipation();
        g.text("Firing everything: " + Info.alphaDamage(un) + " damage, " + heat + " heat", lx, sy, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        if (un.type != UnitType.INFANTRY) {
            g.text("Cooling " + diss + " per round, overheats at " + un.ch.heatCap, lx, sy + u * 3.5f, u * 2.6f,
                    heat > diss + un.ch.heatCap / 2 ? Col.ACCENT : Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        }
        g.text("Tap a mount to change its weapon; -/+ changes ammo bins.", lx, sy + u * 8, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);

        renderButtons(g);
        float rx = lx + lw + u * 4;
        g.text("Weapon mounts", rx, h * 0.14f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        for (Btn b : buttons) {
            if (b.id >= MOUNT && b.id < MOUNT + 50) {
                int m = b.id - MOUNT;
                Chassis.Mount mt = un.ch.mounts[m];
                Wpn wp = un.wpn(m);
                String head = UnitType.locNames(un.type)[mt.loc] + " · " + Info.classTag(mt.cls) + " · " + Chassis.ARC_NAMES[mt.arc];
                g.text(head, b.x + u * 2, b.y + b.h * 0.3f, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                if (wp == null) g.text("(empty)", b.x + u * 2, b.y + b.h * 0.68f, u * 2.8f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, true);
                else {
                    g.text(wp.name, b.x + u * 2, b.y + b.h * 0.68f, u * 2.8f, Col.TEXT, Gfx.ALIGN_LEFT, true);
                    String st = wp.dmg + " dmg  " + wp.heat + " heat  " + wp.rangeText() + "  " + fmt(wp.mass) + " t"
                            + (wp.usesAmmo() ? "  |  " + un.maxAmmo(m) + " shots (" + (1 + un.bins[m]) + " bins)" : "");
                    g.text(st, b.x + b.w - u * 1.5f, b.y + b.h * 0.68f, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
                }
            } else if (b.id >= SLOT && b.id < SLOT + 50) {
                int s = b.id - SLOT;
                int e = un.equip[s];
                g.text("Equipment slot " + (s + 1), b.x + u * 2, b.y + b.h * 0.3f, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                if (e < 0) g.text("(empty)", b.x + u * 2, b.y + b.h * 0.68f, u * 2.8f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, true);
                else {
                    g.text(Equip.ALL[e].name, b.x + u * 2, b.y + b.h * 0.68f, u * 2.8f, Col.SHIELD, Gfx.ALIGN_LEFT, true);
                    g.text(fmt(Equip.ALL[e].mass) + " t", b.x + b.w - u * 1.5f, b.y + b.h * 0.68f, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
                }
            }
        }
        if (choosing >= 0) renderChooser(g);
        if (noteT > 0 && note != null) {
            g.fillRoundRect(w / 2f - u * 40, h - u * 24, u * 80, u * 8, u * 2, 0xEE000000);
            g.text(note, w / 2f, h - u * 20, u * 2.8f, Col.ACCENT, Gfx.ALIGN_CENTER, false);
        }
    }

    private void renderChooser(Gfx g) {
        g.fillRect(0, 0, w, h, 0xC0000000);
        float pw = w * 0.9f, ph = h * 0.9f, px = (w - pw) / 2, py = (h - ph) / 2;
        Ui.panel(g, px, py, pw, ph, u * 2.5f);
        boolean eq = choosing >= 1000;
        String title = eq ? "Choose equipment" : "Choose a weapon for the " + UnitType.locNames(un.type)[un.ch.mounts[choosing].loc]
                + " (" + Info.classTag(un.ch.mounts[choosing].cls) + " mount)";
        g.text(title, px + u * 3, py + u * 5.5f, u * 3.8f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text("Free capacity " + fmt(un.ch.capacity - un.mass()) + " t (plus whatever you remove)", px + u * 3, py + u * 9, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        for (Btn b : overlay) {
            Ui.button(g, b, u);
            if (b.id < OPT || b.id == OPT + 999) continue;
            int id = b.id - OPT;
            float tx = b.x + u * 2;
            int tc = b.enabled ? Col.TEXT : Col.TEXT_DIM;
            if (eq) {
                Equip e = Equip.ALL[id];
                g.text(e.name, tx, b.y + b.h * 0.32f, u * 2.6f, tc, Gfx.ALIGN_LEFT, true);
                g.text(Ui.fit(g, e.desc, u * 1.9f, b.w * 0.72f), tx, b.y + b.h * 0.7f, u * 1.9f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                String price = c.armoryE[id] > 0 ? "spare x" + c.armoryE[id] : "$" + Ui.money(e.cost);
                g.text(fmt(e.mass) + " t  " + price, b.x + b.w - u * 1.5f, b.y + b.h * 0.32f, u * 2.2f,
                        c.armoryE[id] > 0 ? Col.GOOD : Col.MONEY, Gfx.ALIGN_RIGHT, false);
            } else {
                Wpn wp = Wpn.ALL[id];
                g.text(wp.name + "  [" + Info.classTag(wp.cls) + "]", tx, b.y + b.h * 0.32f, u * 2.6f, tc, Gfx.ALIGN_LEFT, true);
                String st = Wpn.FAMILY[wp.family] + ": " + wp.dmg + " dmg, " + wp.heat + " heat, range " + wp.rangeText()
                        + (wp.usesAmmo() ? ", " + wp.ammo + " shots/bin" : "");
                g.text(Ui.fit(g, st, u * 1.9f, b.w * 0.74f), tx, b.y + b.h * 0.7f, u * 1.9f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                String price = c.armoryW[id] > 0 ? "spare x" + c.armoryW[id] : "$" + Ui.money(wp.cost);
                g.text(fmt(wp.mass) + " t  " + price, b.x + b.w - u * 1.5f, b.y + b.h * 0.32f, u * 2.2f,
                        c.armoryW[id] > 0 ? Col.GOOD : Col.MONEY, Gfx.ALIGN_RIGHT, false);
            }
        }
    }

    static String fmt(float f) {
        int tenths = Math.round(f * 10);
        return (tenths / 10) + (tenths % 10 != 0 ? "." + Math.abs(tenths % 10) : "");
    }
}
