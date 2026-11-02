package com.meanmutton.mechgame.core;

/** Company headquarters: roster, deployment, repairs, and launching the next mission. */
public final class HqScreen extends Screen {
    private static final int MENU = 1, MARKET = 2, REPAIR_ALL = 3, LAUNCH = 4, REFIT = 5, REPAIR = 6, SELL = 7, RENAME = 8,
            ROW = 100, DEPLOY = 200;
    private final Company c;
    private int sel;
    private boolean confirmSell;
    private float t;
    private String toast;
    private float toastT;

    public HqScreen(Game game) {
        super(game);
        this.c = game.company;
    }

    protected void layout() {
        buttons.clear();
        int n = c.roster.size();
        if (sel >= n) sel = n - 1;
        if (sel < 0 && n > 0) sel = 0;
        float lx = u * 3, ly = h * 0.19f, lw = w * 0.56f;
        float rowH = Math.min(u * 10, (h * 0.68f) / Math.max(6, n));
        for (int i = 0; i < n; i++) {
            Unit un = c.roster.get(i);
            float y = ly + i * rowH;
            Btn row = add(new Btn(ROW + i, "").set(lx + rowH * 1.1f, y + u * 0.3f, lw - rowH * 1.1f, rowH - u * 0.6f));
            row.selected = sel == i;
            row.color = 0xFF1E2A3C;
            Btn dep = add(new Btn(DEPLOY + i, c.deployed.contains(un) ? "✓" : "").set(lx, y + u * 0.3f, rowH - u * 0.6f, rowH - u * 0.6f));
            dep.color = c.deployed.contains(un) ? 0xFF2E7D4F : 0xFF2A3346;
            dep.enabled = c.canDeploy(un);
        }
        float rx = lx + lw + u * 3, rw = w - rx - u * 3;
        float by = h - u * 25;
        float bw = (rw - u * 3) / 4;
        Unit su = selected();
        Btn refit = add(new Btn(REFIT, "Refit").set(rx, by, bw, u * 9));
        Btn rep = add(new Btn(REPAIR, "Repair").set(rx + (bw + u), by, bw, u * 9));
        Btn sell = add(new Btn(SELL, confirmSell ? "Confirm" : "Sell").set(rx + 2 * (bw + u), by, bw, u * 9));
        Btn ren = add(new Btn(RENAME, "Rename").set(rx + 3 * (bw + u), by, bw, u * 9));
        refit.enabled = su != null && !su.destroyed;
        rep.enabled = su != null && su.isDamaged() && c.cash >= su.repairCost();
        if (su != null && su.isDamaged()) rep.sub = "$" + Ui.money(su.repairCost());
        sell.enabled = su != null;
        if (su != null) sell.sub = "+$" + Ui.money(sellValue(su));
        if (confirmSell) sell.color = 0xFF8F3A2E;
        ren.enabled = su != null;
        rep.textSize = refit.textSize = sell.textSize = ren.textSize = u * 3f;

        float bbw = (w - u * 12) / 4;
        add(new Btn(MENU, c.quick ? "Quit" : "Main Menu").set(u * 3, h - u * 12, bbw, u * 9));
        add(new Btn(MARKET, "Market").set(u * 6 + bbw, h - u * 12, bbw, u * 9)).color = 0xFF5A4A2A;
        int rc = c.repairAllCost();
        Btn ra = add(new Btn(REPAIR_ALL, rc > 0 ? "Repair All $" + Ui.money(rc) : "All Repaired").set(u * 9 + bbw * 2, h - u * 12, bbw, u * 9));
        ra.enabled = rc > 0 && c.cash >= rc;
        Btn launch = add(new Btn(LAUNCH, (c.quick ? "Fight" : "Launch Mission") + " (" + c.deployed.size() + "/6)")
                .set(u * 12 + bbw * 3, h - u * 12, bbw, u * 9));
        launch.color = 0xFF2E7D4F;
        launch.enabled = !c.deployed.isEmpty();
    }

    private Unit selected() {
        return sel >= 0 && sel < c.roster.size() ? c.roster.get(sel) : null;
    }

    private int sellValue(Unit u) {
        return u.destroyed ? u.value() / 5 : u.value() / 2;
    }

    private void relayout() {
        w = -1;
        game.setScreen(this);
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
        if (toastT > 0) toastT -= dt;
        int rows = 0;
        for (Btn b : buttons) if (b.id >= ROW && b.id < DEPLOY) rows++;
        if (rows != c.roster.size()) relayout();
    }

    private void toast(String s) {
        toast = s;
        toastT = 2.5f;
    }

    protected void onClick(Btn b) {
        int id = b.id;
        if (id != SELL) confirmSell = false;
        Unit su = selected();
        if (id >= ROW && id < ROW + Company.MAX_ROSTER + 10) {
            sel = id - ROW;
            if (doubleTap(Integer.valueOf(id)) && selected() != null) showDetail(selected(), Battle.PLAYER, null);
        } else if (id >= DEPLOY && id < DEPLOY + Company.MAX_ROSTER + 10) {
            Unit un = c.roster.get(id - DEPLOY);
            if (c.deployed.contains(un)) c.deployed.remove(un);
            else if (c.canDeploy(un)) {
                c.deployed.add(un);
                if (un.isDamaged()) toast(un.name + " is damaged. Repair it before the fight if you can.");
            }
            sel = id - DEPLOY;
            game.saveCompany();
        } else if (id == MENU) {
            game.saveCompany();
            game.toMenu();
            return;
        } else if (id == MARKET) {
            game.setScreen(new MarketScreen(game));
            return;
        } else if (id == REPAIR_ALL) {
            int cost = c.repairAllCost();
            if (c.cash >= cost) {
                c.cash -= cost;
                for (Unit un : c.roster) un.repairFully();
                game.sfx(Platform.SND_BUY, 0.7f);
                game.saveCompany();
            }
        } else if (id == LAUNCH) {
            game.saveCompany();
            game.prepareMission();
            return;
        } else if (id == REFIT && su != null) {
            game.setScreen(new RefitScreen(game, su));
            return;
        } else if (id == REPAIR && su != null) {
            int cost = su.repairCost();
            if (c.cash >= cost) {
                c.cash -= cost;
                su.repairFully();
                game.sfx(Platform.SND_BUY, 0.7f);
                game.saveCompany();
            }
        } else if (id == SELL && su != null) {
            if (!confirmSell) confirmSell = true;
            else {
                confirmSell = false;
                c.cash += sellValue(su);
                c.roster.remove(su);
                c.deployed.remove(su);
                game.sfx(Platform.SND_BUY, 0.6f);
                game.saveCompany();
            }
        } else if (id == RENAME && su != null) {
            final Unit target = su;
            game.platform.promptText("Unit name", su.name, new Platform.TextCallback() {
                public void done(final String text) {
                    game.post(new Runnable() {
                        public void run() {
                            String s = Unit.clean(text).trim();
                            if (s.length() > 0) target.name = s;
                            game.saveCompany();
                            relayout();
                        }
                    });
                }
            });
        }
        relayout();
    }

    public boolean back() {
        game.saveCompany();
        game.toMenu();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        String title = c.quick ? "QUICK BATTLE: BUILD YOUR FORCE" : "COMPANY HQ";
        g.text(title, u * 3, h * 0.055f, u * 5.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        String sub = c.quick ? "Difficulty " + Company.DIFFICULTY[c.difficulty] + ".  Buy units in the Market, tick up to 6 to deploy, then Fight."
                : "Contract " + c.mission + ": " + HexMap.BIOME_NAMES[c.biome] + " region, pays $" + Ui.money(c.reward)
                + "   |   Record " + c.wins + "W " + c.losses + "L   |   " + Company.DIFFICULTY[c.difficulty];
        g.text(sub, u * 3, h * 0.11f, u * 2.7f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("$" + Ui.money(c.cash), w - u * 3, h * 0.07f, u * 7, Col.MONEY, Gfx.ALIGN_RIGHT, true);

        float lx = u * 3, ly = h * 0.19f, lw = w * 0.56f;
        g.text("Deploy", lx, ly - u * 2.5f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("Roster (" + c.roster.size() + "/" + Company.MAX_ROSTER + ")   Deployed value $" + Ui.money(c.deployedValue()),
                lx + u * 12, ly - u * 2.5f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        renderButtons(g);
        if (c.roster.isEmpty()) {
            g.text("No units yet. Open the Market to buy some.", lx + lw / 2, h * 0.45f, u * 3.5f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        }
        for (int i = 0; i < c.roster.size(); i++) {
            Unit un = c.roster.get(i);
            Btn row = find(ROW + i);
            if (row == null) continue;
            float cy = row.y + row.h / 2;
            UnitArt.draw(g, un, row.x + row.h * 0.6f, cy, -30, Battle.PLAYER, row.h * 0.62f, t, un.condition());
            float tx = row.x + row.h * 1.3f;
            g.text(un.name, tx, cy - row.h * 0.18f, u * 3f, un.destroyed ? Col.BAD : Col.TEXT, Gfx.ALIGN_LEFT, true);
            g.text(UnitType.NAMES[un.type] + " · " + Chassis.ROLES[un.ch.role] + (un.name.equals(un.ch.name) ? "" : " · " + un.ch.name),
                    tx, cy + row.h * 0.22f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            String state = un.destroyed ? "WRECKED" : (un.isDamaged() ? "Damaged" : "Ready");
            int sc = un.destroyed ? Col.BAD : (un.isDamaged() ? Col.ACCENT : Col.GOOD);
            g.text(state, row.x + row.w - u * 2, cy - row.h * 0.18f, u * 2.5f, sc, Gfx.ALIGN_RIGHT, true);
            Ui.bar(g, row.x + row.w - u * 18, cy + row.h * 0.12f, u * 16, u * 1.4f, un.condition(), sc);
        }
        // detail
        Unit su = selected();
        float rx = lx + lw + u * 3, rw = w - rx - u * 3, ry = h * 0.17f;
        Ui.panel(g, rx - u, ry, rw + u * 2, h - ry - u * 27, u * 2);
        if (su != null) {
            UnitArt.draw(g, su, rx + u * 9, ry + u * 10, -40 + (float) Math.sin(t) * 10, Battle.PLAYER, u * 11, t, 1);
            g.text(su.name, rx + u * 19, ry + u * 5, u * 3.8f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text(UnitType.NAMES[su.type] + ", " + Chassis.ROLES[su.ch.role], rx + u * 19, ry + u * 9.5f, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            g.text("Value $" + Ui.money(su.value()), rx + u * 19, ry + u * 13.5f, u * 2.6f, Col.MONEY, Gfx.ALIGN_LEFT, false);
            float y = Info.stats(g, su, rx + u, ry + u * 22, rw - u * 2, u * 2.4f);
            Info.loadout(g, su, rx + u, y + u * 1.5f, rw - u * 2, u * 2.3f);
        } else {
            g.text("Select a unit", rx + rw / 2, h * 0.4f, u * 3, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        }
        g.text("Double-tap a unit for full details", lx + lw, ly - u * 2.5f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        if (toastT > 0 && toast != null) {
            g.fillRoundRect(w / 2f - u * 55, h * 0.5f - u * 5, u * 110, u * 10, u * 2, 0xEE000000);
            g.text(toast, w / 2f, h * 0.5f, u * 3, Col.ACCENT, Gfx.ALIGN_CENTER, false);
        }
        renderDetail(g);
    }

    private Btn find(int id) {
        for (Btn b : buttons) if (b.id == id) return b;
        return null;
    }
}
