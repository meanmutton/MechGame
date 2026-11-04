package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/** Buy new units (factory loadouts). */
public final class MarketScreen extends Screen {
    private static final int BACK = 1, BUY = 2, FACTION = 3, SORT = 4, ROLE = 5, AFFORD = 6, ALLOWED = 7, TAB = 10, CARD = 100;
    private static final String[] TABS = {"All", "Mechs", "Vehicles", "VTOL & Inf."};
    static final String[] SORTS = {"Catalog", "Price: low", "Price: high", "Name", "Fastest", "Firepower", "Armor", "Range"};
    // sorting and filters stay as you left them while the game runs
    private static int sort, role = -1;
    private static boolean onlyAffordable, onlyAllowed;
    private int shown, total;
    private int tab;
    /** Show only designs this faction builds, or -1 for all. */
    private int faction = -1;
    private int sel = 21;
    private float scroll, maxScroll;
    private float downX, downY, lastY;
    private boolean scrolling;
    private int scrollPointer = -1;
    private float t;
    private String note;
    private float noteT;
    /** A factory unit for each design, indexed by design id. */
    private final Unit[] previews = new Unit[Chassis.ALL.length];
    private float gridX, gridY, gridW, gridH;

    public MarketScreen(Game game) {
        super(game);
        for (Chassis ch : Chassis.ALL) if (ch.type != UnitType.EMPLACEMENT) previews[ch.id] = new Unit(ch);
        if (!game.company.sells(Chassis.ALL[sel])) {
            for (Chassis ch : Chassis.ALL) if (game.company.sells(ch)) { sel = ch.id; break; }
        }
    }

    private boolean inTab(Chassis ch) {
        if (faction >= 0 && (ch.factions & Faction.bit(faction)) == 0) return false;
        if (role >= 0 && ch.role != role) return false;
        if (onlyAffordable && previews[ch.id].value() > game.company.cash) return false;
        Scenario next = game.company.next();
        if (onlyAllowed && next != null && !next.allows(ch)) return false;
        switch (tab) {
            case 1: return UnitType.isMech(ch.type);
            case 2: return UnitType.isVehicle(ch.type) && ch.type != UnitType.VTOL;
            case 3: return ch.type == UnitType.VTOL || ch.type == UnitType.INFANTRY;
            default: return true;
        }
    }

    protected void layout() {
        buttons.clear();
        float tw = Math.min(u * 21, (w * 0.6f - u * 28) / TABS.length - u);
        for (int i = 0; i < TABS.length; i++) {
            Btn b = add(new Btn(TAB + i, TABS[i]).set(u * 3 + i * (tw + u), h * 0.15f, tw, u * 8));
            b.selected = tab == i;
            b.textSize = u * 2.6f;
        }
        Btn fb = add(new Btn(FACTION, faction < 0 ? "Any faction" : Faction.ALL[faction].shortName)
                .set(u * 3 + TABS.length * (tw + u) + u, h * 0.15f, u * 24, u * 8));
        fb.textSize = u * 2.6f;
        if (faction >= 0) fb.textColor = Faction.ALL[faction].color;
        // second row: sort and the other filters
        float r2 = h * 0.15f + u * 9, bw2 = (w * 0.6f - u * 3) / 4;
        Btn sb = add(new Btn(SORT, "Sort: " + SORTS[sort]).set(u * 3, r2, bw2, u * 7));
        Btn rb = add(new Btn(ROLE, role < 0 ? "Any role" : Chassis.ROLES[role]).set(u * 3 + (bw2 + u), r2, bw2, u * 7));
        rb.selected = role >= 0;
        Btn ab = add(new Btn(AFFORD, "Affordable").set(u * 3 + 2 * (bw2 + u), r2, bw2, u * 7));
        ab.selected = onlyAffordable;
        Btn lb = add(new Btn(ALLOWED, "Allowed next").set(u * 3 + 3 * (bw2 + u), r2, bw2, u * 7));
        lb.selected = onlyAllowed;
        lb.enabled = game.company.next() != null;
        sb.textSize = rb.textSize = ab.textSize = lb.textSize = u * 2.4f;
        gridX = u * 3;
        gridY = r2 + u * 9;
        gridW = w * 0.6f;
        gridH = h - gridY - u * 2;
        int cols = 3;
        float cw = gridW / cols, ch = u * 17;
        int k = 0;
        total = 0;
        for (Chassis c : sorted()) {
            if (!game.company.sells(c)) continue;
            total++;
            if (!inTab(c)) continue;
            float x = gridX + (k % cols) * cw, y = gridY + (k / cols) * ch - scroll;
            Btn b = add(new Btn(CARD + c.id, "").set(x + u * 0.5f, y + u * 0.5f, cw - u, ch - u));
            b.selected = sel == c.id;
            b.color = 0xFF1E293C;
            b.visible = y + ch > gridY - 1 && y < gridY + gridH;
            k++;
        }
        shown = k;
        if (shown < total) sb.sub = "showing " + shown + " of " + total;
        int rows = (k + cols - 1) / cols;
        maxScroll = Math.max(0, rows * ch - gridH);
        float rx = gridX + gridW + u * 3, rw = w - rx - u * 3;
        Btn buy = add(new Btn(BUY, "Buy").set(rx, h - u * 25, rw, u * 10));
        Unit p = previews[sel];
        buy.label = "Buy for $" + Ui.money(p.value());
        buy.enabled = game.company.sells(p.ch) && game.company.cash >= p.value() && game.company.roster.size() < Company.MAX_ROSTER;
        buy.color = 0xFF2E7D4F;
        add(new Btn(BACK, "Back to HQ").set(rx, h - u * 13, rw, u * 9));
    }

    /** The catalog in the chosen order. */
    private List<Chassis> sorted() {
        List<Chassis> l = new ArrayList<Chassis>();
        for (Chassis c : Chassis.ALL) if (previews[c.id] != null) l.add(c);
        if (sort == 0) return l;
        java.util.Collections.sort(l, new java.util.Comparator<Chassis>() {
            public int compare(Chassis a, Chassis b) {
                Unit ua = previews[a.id], ub = previews[b.id];
                int d;
                switch (sort) {
                    case 1: d = ua.value() - ub.value(); break;
                    case 2: d = ub.value() - ua.value(); break;
                    case 3: d = a.name.compareTo(b.name); break;
                    case 4: d = b.mp - a.mp; break;
                    case 5: d = Info.alphaDamage(ub) - Info.alphaDamage(ua); break;
                    case 6: d = ub.armorTotal - ua.armorTotal; break;
                    default: d = Info.maxRange(ub) - Info.maxRange(ua); break;
                }
                return d != 0 ? d : ua.value() - ub.value();
            }
        });
        return l;
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

    protected void onClick(Btn b) {
        if (b.id == BACK) {
            game.toHq();
            return;
        }
        if (b.id == BUY) {
            Unit p = previews[sel];
            Company c = game.company;
            if (c.sells(p.ch) && c.cash >= p.value() && c.roster.size() < Company.MAX_ROSTER) {
                if (p.ch.unique) c.offerChassis = -1;   // a prototype is one of a kind
                c.cash -= p.value();
                Unit u2 = new Unit(p.ch);
                int same = 0;
                for (Unit o : c.roster) if (o.ch == p.ch) same++;
                if (same > 0) u2.name = p.ch.name + " " + (same + 1);
                c.roster.add(u2);
                if (c.canDeploy(u2)) c.deployed.add(u2);
                note = "Bought " + u2.name + ". Customize its weapons and armor from HQ (select it, then CUSTOMIZE).";
                noteT = 4f;
                game.sfx(Platform.SND_BUY, 0.8f);
                game.saveCompany();
            }
        } else if (b.id >= TAB && b.id < TAB + TABS.length) {
            tab = b.id - TAB;
            scroll = 0;
        } else if (b.id == SORT) {
            sort = (sort + 1) % SORTS.length;
            scroll = 0;
        } else if (b.id == ROLE) {
            role = role + 1 >= Chassis.ROLES.length ? -1 : role + 1;
            scroll = 0;
        } else if (b.id == AFFORD) {
            onlyAffordable = !onlyAffordable;
            scroll = 0;
        } else if (b.id == ALLOWED) {
            onlyAllowed = !onlyAllowed;
            scroll = 0;
        } else if (b.id == FACTION) {
            faction = faction + 1 >= Faction.COUNT ? -1 : faction + 1;   // cycles through the factions, then back to all
            scroll = 0;
        } else if (b.id >= CARD) {
            sel = b.id - CARD;
            if (doubleTap(Integer.valueOf(b.id))) showDetail(previews[sel], Battle.PLAYER, null);
        }
        relayout();
    }

    // drag-to-scroll the grid (a drag that starts on a card cancels the card press)
    /** Cards scrolled under the tab row (or below the grid) must not take touches meant for other buttons. */
    private boolean touchInGrid = true;

    protected java.util.List<Btn> activeButtons() {
        if (touchInGrid) return buttons;
        java.util.List<Btn> l = new java.util.ArrayList<Btn>();
        for (Btn b : buttons) if (b.id < CARD) l.add(b);
        return l;
    }

    public void touchDown(int pointer, float x, float y) {
        touchInGrid = !(x < gridX + gridW + u && (y < gridY || y > gridY + gridH));
        downX = x;
        downY = y;
        lastY = y;
        scrolling = false;
        scrollPointer = pointer;
        super.touchDown(pointer, x, y);
    }

    public void touchMove(int pointer, float x, float y) {
        if (pointer == scrollPointer && downX < gridX + gridW && downY > gridY) {
            if (!scrolling && Math.abs(y - downY) > u * 2.5f) {
                scrolling = true;
                cancelPress();
            }
            if (scrolling) {
                scroll = Math.max(0, Math.min(maxScroll, scroll - (y - lastY)));
                lastY = y;
                relayout();
                return;
            }
        }
        lastY = y;
        super.touchMove(pointer, x, y);
    }

    public void touchUp(int pointer, float x, float y) {
        if (scrolling && pointer == scrollPointer) {
            scrolling = false;
            touchInGrid = true;
            return;
        }
        super.touchUp(pointer, x, y);
        touchInGrid = true;
    }

    public void wheel(float x, float y, float amount) {
        if (x < gridX + gridW && maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll + amount));
            relayout();
        }
    }

    public boolean back() {
        game.toHq();
        return true;
    }

    /** Short label for who builds a design: the faction, the bloc, or how many share it. */
    static String factionTag(Chassis ch) {
        int ex = Faction.exclusive(ch.factions);
        if (ex >= 0) return Faction.ALL[ex].shortName;
        if (ch.factions == Faction.ALL_MASK) return "All";
        for (int bloc = 0; bloc < Faction.BLOC_NAMES.length; bloc++)
            if (ch.factions == Faction.blocMask(bloc)) return bloc == Faction.BLOC_ACCORD ? "Accord" : "Pact";
        return Integer.bitCount(ch.factions) + " factions";
    }

    static int factionColor(Chassis ch) {
        int ex = Faction.exclusive(ch.factions);
        return ex >= 0 ? Faction.ALL[ex].color : Col.TEXT_DIM;
    }

    public void render(Gfx g) {
        background(g);
        g.text("MARKET", u * 3, h * 0.06f, u * 5.5f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        Scenario next = game.company.next();
        if (next == null) g.text("Factory-fresh units with standard loadouts. Refit them at HQ after buying.", u * 3, h * 0.11f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        else g.text(Ui.fit(g, "Next: " + next.name + ". " + next.limits(game.company.limits()) + (next.force != Scenario.F_ALL ? ". \u2713 = allowed." : "."), u * 2.5f, gridW),
                u * 3, h * 0.11f, u * 2.5f, Col.ACCENT, Gfx.ALIGN_LEFT, false);
        g.text("$" + Ui.money(game.company.cash), w - u * 3, h * 0.07f, u * 6.5f, Col.MONEY, Gfx.ALIGN_RIGHT, true);
        g.save();
        g.clipRect(0, gridY, w * 0.66f, gridH + u);
        for (Btn b : buttons) {
            if (b.id < CARD || !b.visible) continue;
            Ui.button(g, b, u);
            Unit p = previews[b.id - CARD];
            float cy = b.y + b.h / 2;
            UnitArt.draw(g, p, b.x + b.h * 0.5f, cy, -30, Battle.PLAYER, b.h * 0.5f, t, 1);
            boolean mark = next != null && next.force != Scenario.F_ALL && next.allows(p);
            float tx = b.x + b.h * 1.0f, maxW = b.x + b.w - tx - u * (mark ? 4.5f : 1);
            g.text(Ui.fit(g, (p.ch.unique ? "\u2605 " : "") + p.ch.name, u * 2.8f, maxW), tx, b.y + b.h * 0.24f, u * 2.8f,
                    p.ch.unique ? Col.ACCENT : Col.TEXT, Gfx.ALIGN_LEFT, true);
            String tag = factionTag(p.ch);
            int ex = Faction.exclusive(p.ch.factions);
            float tagW = g.textWidth(tag, u * 2f, true) + u * (ex >= 0 ? 5.2f : 2.5f);
            if (ex >= 0) Emblem.draw(g, ex, b.x + b.w - u * 1.5f - g.textWidth(tag, u * 2f, true) - u * 1.6f, b.y + b.h * 0.5f, u * 1.25f);
            g.text(Ui.fit(g, UnitType.SHORT[p.type] + " · " + Chassis.ROLES[p.ch.role], u * 2.2f, b.x + b.w - tx - tagW), tx, b.y + b.h * 0.5f,
                    u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            g.text(tag, b.x + b.w - u * 1.5f, b.y + b.h * 0.5f, u * 2f, factionColor(p.ch), Gfx.ALIGN_RIGHT, true);
            g.text("$" + Ui.money(p.value()), tx, b.y + b.h * 0.76f, u * 2.5f, game.company.cash >= p.value() ? Col.MONEY : Col.BAD, Gfx.ALIGN_LEFT, true);
            g.text(p.ch.mp + " MP", b.x + b.w - u * 1.5f, b.y + b.h * 0.76f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            if (mark) g.text("\u2713", b.x + b.w - u * 1.8f, b.y + b.h * 0.26f, u * 3f, Col.GOOD, Gfx.ALIGN_RIGHT, true);
        }
        g.restore();
        boolean any = false;
        for (Btn b : buttons) if (b.id >= CARD) any = true;
        if (!any && total > 0) Ui.wrap(g, "Nothing matches these filters. Tap a filter again to change it.",
                gridX + u * 2, gridY + u * 6, u * 3, gridW - u * 4, Col.ACCENT);
        else if (!any && game.company.merc) Ui.wrap(g, "No faction on " + game.company.planet.name + " will sell to you. Earn some goodwill, or travel.",
                gridX + u * 2, gridY + u * 6, u * 3, gridW - u * 4, Col.ACCENT);
        if (maxScroll > 0) {
            float bh = gridH * gridH / (gridH + maxScroll);
            float by = gridY + (gridH - bh) * (scroll / maxScroll);
            g.fillRoundRect(gridX + gridW + u * 0.5f, by, u * 0.8f, bh, u * 0.4f, 0x66FFFFFF);
        }
        for (Btn b : buttons) if (b.id < CARD) Ui.button(g, b, u);
        if (faction >= 0) {
            for (Btn b : buttons) if (b.id == FACTION) Emblem.draw(g, faction, b.x + u * 3, b.y + b.h / 2, u * 2.2f);
        }

        Unit p = previews[sel];
        float rx = gridX + gridW + u * 3, rw = w - rx - u * 3, ry = h * 0.15f;
        Ui.panel(g, rx - u, ry, rw + u * 2, h - ry - u * 27, u * 2);
        UnitArt.draw(g, p, rx + u * 9, ry + u * 10, -35 + (float) Math.sin(t) * 12, Battle.PLAYER, u * 11, t, 1);
        g.text(p.ch.name, rx + u * 19, ry + u * 5, u * 3.8f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text(UnitType.NAMES[p.type] + ", " + Chassis.ROLES[p.ch.role], rx + u * 19, ry + u * 9.5f, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        g.text("Weapon class up to " + Info.classTag(UnitType.MAX_CLASS[p.type]) + ", " + p.ch.mounts.length + " mounts, "
                + p.ch.equipSlots + " equipment slots", rx + u * 19, ry + u * 13.5f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        // who builds it: each faction's emblem, then the names
        float bx = rx + u * 19;
        g.text("Built by:", bx, ry + u * 17, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        bx += g.textWidth("Built by:", u * 2.2f, false) + u * 1.2f;
        int ex = Faction.exclusive(p.ch.factions);
        if (ex >= 0) Emblem.label(g, ex, bx, ry + u * 17, u * 2.2f, true);
        else {
            for (Faction f : Faction.ALL) {
                if ((p.ch.factions & Faction.bit(f.id)) == 0) continue;
                Emblem.draw(g, f.id, bx + u * 1.3f, ry + u * 17, u * 1.3f);
                bx += u * 3.1f;
            }
            String bloc = factionTag(p.ch);
            g.text(bloc.equals("Accord") ? "Northern Accord" : (bloc.equals("Pact") ? "Iron Pact" : (bloc.equals("All") ? "all factions" : "")),
                    bx + u * 0.6f, ry + u * 17, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        }
        float y = Ui.wrap(g, p.ch.desc, rx + u, ry + u * 21, u * 2.3f, rw - u * 2, Col.TEXT) + ry + u * 21.5f;
        y = Info.stats(g, p, rx + u, y, rw - u * 2, u * 2.05f);
        Info.loadout(g, p, rx + u, y + u * 0.5f, rw - u * 2, u * 2.0f);
        g.text("Double-tap a unit for full details", gridX + gridW, h * 0.06f, u * 2.4f, Col.ACCENT, Gfx.ALIGN_RIGHT, false);

        if (noteT > 0 && note != null) {
            float tw = g.textWidth(note, u * 2.6f, false) + u * 6;
            g.fillRoundRect(w / 2f - tw / 2, h - u * 11, tw, u * 8, u * 2, 0xEE000000);
            g.text(note, w / 2f, h - u * 7, u * 2.6f, Col.ACCENT, Gfx.ALIGN_CENTER, false);
        }
        renderDetail(g);
    }
}
