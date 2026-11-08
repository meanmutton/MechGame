package com.meanmutton.mechgame.core;

/** Company headquarters: roster, deployment, repairs, and launching the next mission. */
public final class HqScreen extends Screen {
    private static final int MENU = 1, MARKET = 2, REPAIR_ALL = 3, LAUNCH = 4, REFIT = 5, REPAIR = 6, SELL = 7, RENAME = 8, COLORS = 9, CONTRACTS = 10,
            SORT = 11, TAB = 20, ROW = 100, DEPLOY = 200;
    static final String[] TABS = {"All", "Mechs", "Vehicles", "VTOL & Inf."};
    private static final String[] TAB_NOUNS = {"units", "mechs", "vehicles", "VTOLs or infantry"};
    static final String[] SORTS = {"Roster order", "Name", "Type", "Value", "Condition", "Deployed first"};
    // the roster's filter and order stay as you left them while the game runs
    private static int tab, sort;
    private float scroll, maxScroll, loadScroll, loadMax;
    private float listY0, listY1, loadY0, loadY1, rowsW;
    private int laidOut = -1;   // roster size the rows were laid out for
    private int dragPointer = -1, dragWhat;   // 1 = roster list, 2 = loadout
    private float downY, lastY;
    private boolean dragging;
    private final java.util.List<Integer> shown = new java.util.ArrayList<Integer>();
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
        float lx = u * 3, ly = listTop(), lw = w * 0.56f;
        laidOut = n;
        // the roster, filtered by type and put in the chosen order
        shown.clear();
        for (int i = 0; i < n; i++) if (inTab(c.roster.get(i))) shown.add(i);
        sortShown();
        // a long roster goes into two columns; rows never shrink below a readable size, and the list scrolls instead
        int m = shown.size();
        int cols = m > 8 ? 2 : 1, rows = (m + cols - 1) / cols;
        float colW = (lw - u * (cols - 1)) / cols;
        listY0 = ly;
        listY1 = h - u * 14;
        rowsW = lw;
        float avail = listY1 - ly;
        float rowH = Math.max(u * 7.5f, Math.min(u * 10, avail / Math.max(6, rows)));
        maxScroll = rows * rowH > avail ? rows * rowH - avail + u * 2.8f : 0;
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        for (int k = 0; k < m; k++) {
            int i = shown.get(k);
            Unit un = c.roster.get(i);
            float x = lx + (k / Math.max(1, rows)) * (colW + u), y = ly + (k % Math.max(1, rows)) * rowH - scroll;
            boolean vis = y + rowH * 0.5f > ly && y + rowH * 0.5f < listY1;
            Btn row = add(new Btn(ROW + i, "").set(x + rowH * 1.1f, y + u * 0.3f, colW - rowH * 1.1f, rowH - u * 0.6f));
            row.selected = sel == i;
            row.color = 0xFF1E2A3C;
            row.visible = vis;
            Btn dep = add(new Btn(DEPLOY + i, c.deployed.contains(un) ? "✓" : "").set(x, y + u * 0.3f, rowH - u * 0.6f, rowH - u * 0.6f));
            dep.color = c.deployed.contains(un) ? 0xFF2E7D4F : (c.canDeploy(un) ? 0xFF2A3346 : 0xFF171D29);
            dep.enabled = !un.destroyed;   // tapping one that can't go explains why
            dep.visible = vis;
        }
        // the filter row above the list (added after the rows, so it takes touches first)
        float cy0 = ctrlTop(), sw = u * 27, tw = (lw - sw - u * TABS.length) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int cnt = 0;
            for (Unit un : c.roster) if (tabOf(un, i)) cnt++;
            Btn tb = add(new Btn(TAB + i, TABS[i] + (i > 0 && cnt > 0 ? " " + cnt : "")).set(lx + i * (tw + u), cy0, tw, u * 6.5f));
            tb.selected = tab == i;
            tb.textSize = u * 2.3f;
        }
        Btn sb = add(new Btn(SORT, "Sort: " + SORTS[sort]).set(lx + lw - sw, cy0, sw, u * 6.5f));
        sb.textSize = u * 2.3f;
        float rx = lx + lw + u * 3, rw = w - rx - u * 3;
        float by = h - u * 25;
        float bw = (rw - u * 2) / 3;
        Unit su = selected();
        Btn refit = add(new Btn(REFIT, "CUSTOMIZE").set(rx, by - u * 11, rw, u * 10));
        refit.sub = "weapons, armor, equipment, heat sinks, ammo";
        refit.color = 0xFF8F6A2E;
        Btn rep = add(new Btn(REPAIR, "Repair").set(rx, by, bw, u * 9));
        Btn sell = add(new Btn(SELL, confirmSell ? "Confirm" : "Sell").set(rx + (bw + u), by, bw, u * 9));
        Btn ren = add(new Btn(RENAME, "Rename").set(rx + 2 * (bw + u), by, bw, u * 9));
        refit.enabled = su != null && !su.destroyed;
        rep.enabled = su != null && su.isDamaged() && c.cash >= su.repairCost();
        if (su != null && su.isDamaged()) rep.sub = "$" + Ui.money(su.repairCost());
        sell.enabled = su != null;
        if (su != null) sell.sub = "+$" + Ui.money(sellValue(su));
        if (confirmSell) sell.color = 0xFF8F3A2E;
        ren.enabled = su != null;
        rep.textSize = sell.textSize = ren.textSize = u * 3f;
        refit.textSize = u * 3.8f;

        Btn colors = add(new Btn(COLORS, "Colors").set(w - u * 62, h * 0.025f, u * 20, u * 7));
        colors.textSize = u * 2.6f;
        colors.color = Col.darker((c.scheme != null ? c.scheme : Scheme.PLAYER_DEFAULT).main, 0.3f);
        if (c.merc) {
            Btn ct = add(new Btn(CONTRACTS, "Contracts").set(w - u * 85, h * 0.025f, u * 21, u * 7));
            ct.textSize = u * 2.6f;
            ct.color = 0xFF2E6D8F;
        }
        float bbw = (w - u * 12) / 4;
        add(new Btn(MENU, c.quick ? "Quit" : "Main Menu").set(u * 3, h - u * 12, bbw, u * 9));
        add(new Btn(MARKET, "Market").set(u * 6 + bbw, h - u * 12, bbw, u * 9)).color = 0xFF5A4A2A;
        int rc = c.repairAllCost();
        Btn ra = add(new Btn(REPAIR_ALL, rc > 0 ? "Repair All $" + Ui.money(rc) : "All Repaired").set(u * 9 + bbw * 2, h - u * 12, bbw, u * 9));
        ra.enabled = rc > 0 && c.cash >= rc;
        if (c.merc && c.job() == null) {
            // a mercenary needs a job first: the button opens the contract board
            Btn ct = add(new Btn(CONTRACTS, "Contracts").set(u * 12 + bbw * 3, h - u * 12, bbw, u * 9));
            ct.color = 0xFF2E6D8F;
            ct.sub = "take a job on " + c.planet.name;
            return;
        }
        boolean forfeit = !c.quick && !c.merc && c.deployed.isEmpty() && c.mustForfeit();
        String go = c.hotseat ? (c.seatNo == 0 ? "Done" : "Fight") : (c.pvp ? (pvpSeat() == 0 ? "Done" : "Fight") : (c.quick ? "Fight" : "Launch Mission"));
        Btn launch = add(new Btn(LAUNCH, forfeit ? "Forfeit Mission" : go + " (" + c.deployed.size() + "/" + c.deployLimit() + ")")
                .set(u * 12 + bbw * 3, h - u * 12, bbw, u * 9));
        launch.color = forfeit ? 0xFF8F3A2E : 0xFF2E7D4F;
        launch.enabled = !c.deployed.isEmpty() || forfeit;
        if (!c.quick && !forfeit && c.noneAllowed()) launch.sub = c.merc ? "this job allows none of your units" : "buy an allowed unit in the Market";
        if ((c.hotseat && c.seatNo == 0) || (c.pvp && pvpSeat() == 0 && !forfeit)) launch.sub = "then pass to Player 2";
        if (c.pvp && forfeit) launch.sub = "Player " + (2 - pvpSeat()) + " wins this one";
    }

    /** Top of the filter row; the roster list starts below it and its header. */
    private float ctrlTop() {
        return c.quick ? h * 0.15f : h * 0.18f;
    }

    /** Two-player campaign: 0 when Player 1 has the HQ, 1 when Player 2 has it. */
    private int pvpSeat() {
        return game.pvpMain != null ? 1 : 0;
    }

    private float listTop() {
        return ctrlTop() + u * 11;
    }

    private static boolean tabOf(Unit un, int t) {
        switch (t) {
            case 1: return UnitType.isMech(un.type);
            case 2: return UnitType.isVehicle(un.type) && un.type != UnitType.VTOL;
            case 3: return un.type == UnitType.VTOL || un.type == UnitType.INFANTRY;
            default: return true;
        }
    }

    private boolean inTab(Unit un) {
        return tabOf(un, tab);
    }

    private void sortShown() {
        if (sort == 0) return;
        java.util.Collections.sort(shown, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                Unit x = c.roster.get(a), y = c.roster.get(b);
                int d;
                switch (sort) {
                    case 1: d = x.name.compareToIgnoreCase(y.name); break;
                    case 2: d = typeRank(x) - typeRank(y); break;
                    case 3: d = y.value() - x.value(); break;
                    case 4: d = Float.compare(x.destroyed ? -1 : x.condition(), y.destroyed ? -1 : y.condition()); break;
                    default: d = (c.deployed.contains(x) ? 0 : 1) - (c.deployed.contains(y) ? 0 : 1); break;
                }
                return d != 0 ? d : a - b;
            }
        });
    }

    /** Heaviest first: assault, heavy, medium and light mechs, then tanks, hovertanks, wheels, VTOLs, infantry. */
    private static int typeRank(Unit u) {
        int[] rank = {8, 4, 5, 7, 6, 3, 2, 1, 0, 9};
        return rank[u.type];
    }

    // drag to scroll the roster list, or the selected unit's loadout
    protected void onTouchDown(int pointer, float x, float y) {
        startDrag(pointer, x, y);
    }

    private void startDrag(int pointer, float x, float y) {
        dragPointer = pointer;
        downY = lastY = y;
        dragging = false;
        dragWhat = x < u * 3 + rowsW && y > listY0 && y < listY1 && maxScroll > 0 ? 1
                : (x > u * 3 + rowsW && y > loadY0 && y < loadY1 && loadMax > 0 ? 2 : 0);
    }

    public void touchDown(int pointer, float x, float y) {
        super.touchDown(pointer, x, y);
        if (isPressing(pointer)) startDrag(pointer, x, y);   // a press on a row can still turn into a scroll
    }

    public void touchMove(int pointer, float x, float y) {
        if (pointer == dragPointer && dragWhat != 0) {
            if (!dragging && Math.abs(y - downY) > u * 2.5f) {
                dragging = true;
                cancelPress();
            }
            if (dragging) {
                if (dragWhat == 1) {
                    scroll = Math.max(0, Math.min(maxScroll, scroll - (y - lastY)));
                    relayout();
                } else loadScroll = Math.max(0, Math.min(loadMax, loadScroll - (y - lastY)));
                lastY = y;
                return;
            }
        }
        lastY = y;
        super.touchMove(pointer, x, y);
    }

    public void touchUp(int pointer, float x, float y) {
        if (dragging && pointer == dragPointer) {
            dragging = false;
            dragPointer = -1;
            return;
        }
        dragPointer = -1;
        super.touchUp(pointer, x, y);
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
        if (laidOut != c.roster.size()) relayout();
    }

    private void toast(String s) {
        toast = s;
        toastT = 2.5f;
    }

    protected void onClick(Btn b) {
        int id = b.id;
        if (id != SELL) confirmSell = false;
        Unit su = selected();
        if (id >= TAB && id < TAB + TABS.length) {
            tab = id - TAB;
            scroll = 0;
        } else if (id == SORT) {
            sort = (sort + 1) % SORTS.length;
            scroll = 0;
        } else if (id >= ROW && id < ROW + Company.MAX_ROSTER + 10) {
            if (sel != id - ROW) loadScroll = 0;
            sel = id - ROW;
            if (doubleTap(Integer.valueOf(id)) && selected() != null) showDetail(selected(), Battle.PLAYER, null);
        } else if (id >= DEPLOY && id < DEPLOY + Company.MAX_ROSTER + 10) {
            Unit un = c.roster.get(id - DEPLOY);
            if (sel != id - DEPLOY) loadScroll = 0;
            if (c.deployed.contains(un)) c.deployed.remove(un);
            else if (c.canDeploy(un)) {
                c.deployed.add(un);
                if (un.isDamaged()) toast(un.name + " is damaged. Repair it before the fight if you can.");
            } else if (c.deployRefusal(un) != null) toast(c.deployRefusal(un));
            sel = id - DEPLOY;
            game.saveCompany();
        } else if (id == MENU) {
            game.saveCompany();
            game.toMenu();
            return;
        } else if (id == CONTRACTS) {
            game.setScreen(new ContractsScreen(game));
            return;
        } else if (id == COLORS) {
            game.setScreen(new PaintScreen(game));
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
        } else if (id == LAUNCH && c.pvp) {
            if (c.deployed.isEmpty()) game.pvpForfeit(pvpSeat());
            else if (pvpSeat() == 0) game.pvpHandToP2();
            else game.pvpFight();
            return;
        } else if (id == LAUNCH && c.hotseat) {
            if (c.seatNo == 0) game.hotseatNext();
            else game.prepareHotseat();
            return;
        } else if (id == LAUNCH) {
            game.saveCompany();
            if (c.deployed.isEmpty()) game.forfeitMission();
            else game.prepareMission();
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

    public void wheel(float x, float y, float amount) {
        if (x < u * 3 + rowsW) {
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll + amount));
                relayout();
            }
        } else if (loadMax > 0) loadScroll = Math.max(0, Math.min(loadMax, loadScroll + amount));
    }

    public boolean back() {
        game.saveCompany();
        game.toMenu();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        String title = c.hotseat ? "PLAYER " + (c.seatNo + 1) + ": BUILD YOUR FORCE"
                : c.pvp ? "PLAYER " + (pvpSeat() + 1) + " HQ"
                : (c.quick ? "QUICK BATTLE: BUILD YOUR FORCE" : (c.merc ? "MERCENARY HQ" : "COMPANY HQ"));
        int titleColor = c.hotseat ? UnitArt.teamColor(c.seatNo) : (c.pvp ? UnitArt.teamColor(pvpSeat()) : Col.ACCENT);
        g.text(title, u * 3, h * 0.055f, u * 5.2f, titleColor, Gfx.ALIGN_LEFT, true);
        String sub = c.hotseat ? "Two players, one device.  Buy units in the Market, tick up to " + Limits.FULL.text() + " to deploy, then "
                + (c.seatNo == 0 ? "Done." : "Fight.")
                : c.quick ? "Difficulty " + Company.DIFFICULTY[c.difficulty] + ".  Buy units in the Market, tick up to " + Limits.FULL.text() + " to deploy, then Fight."
                : "Contract " + c.mission + ": " + HexMap.BIOME_NAMES[c.biome] + " region, pays $" + Ui.money(c.reward)
                + "   |   Record " + c.wins + "W " + c.losses + "L   |   " + Company.DIFFICULTY[c.difficulty]
                + (c.rival != null ? "   |   " + (c.pvp ? "Player " + (2 - pvpSeat()) : "Enemy") + ": $" + Ui.money(c.rival.cash) + ", "
                + c.rival.roster.size() + " units" : "");
        if (c.merc) sub = c.planet.name + " (" + HexMap.BIOME_NAMES[c.planet.biome] + ")   |   Record " + c.wins + "W " + c.losses + "L   |   "
                + Company.DIFFICULTY[c.difficulty];
        g.text(sub, u * 3, h * 0.105f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        Scenario sc = c.next();
        if (c.merc) {
            Merc.Contract k = c.job();
            if (k == null) g.text("No contract yet. Open Contracts to take a job, then deploy for it here.", u * 3, h * 0.15f, u * 2.6f,
                    Col.ACCENT, Gfx.ALIGN_LEFT, true);
            else {
                float x = u * 3;
                x += Emblem.label(g, k.employer, x, h * 0.15f, u * 2.6f, false) + u;
                String mid = k.role == Scenario.ATTACKER ? "vs" : "defending vs";
                g.text(mid, x, h * 0.15f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                x += g.textWidth(mid, u * 2.4f, false) + u;
                x += Emblem.label(g, k.target, x, h * 0.15f, u * 2.6f, false) + u * 2;
                g.text(Ui.fit(g, sc.name + ", pays $" + Ui.money(k.reward) + ". " + sc.limits() + ".", u * 2.5f, w - x - u * 3), x, h * 0.15f,
                        u * 2.5f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            }
        } else if (sc != null) {
            String next = "You are the " + Text.lower(Scenario.SIDE_NAMES[c.side]) + ", front " + Scenario.frontName(c.front)
                    + ".   Next: " + sc.name + ". " + sc.limits(c.limits()) + ".";
            g.text(Ui.fit(g, next, u * 2.6f, w - u * 6), u * 3, h * 0.15f, u * 2.6f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        }
        g.text("$" + Ui.money(c.cash), w - u * 3, h * 0.07f, u * 7, Col.MONEY, Gfx.ALIGN_RIGHT, true);

        float lx = u * 3, ly = listTop(), lw = w * 0.56f;
        g.text("Deploy", lx, ly - u * 2.5f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("Roster (" + c.roster.size() + "/" + Company.MAX_ROSTER + ")   Deployed value $" + Ui.money(c.deployedValue()),
                lx + u * 12, ly - u * 2.5f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        // everything but the roster rows, then the rows clipped to the list area
        for (Btn b : buttons) if (b.id < ROW) Ui.button(g, b, u);
        if (c.roster.isEmpty()) {
            g.text("No units yet. Open the Market to buy some.", lx + lw / 2, h * 0.45f, u * 3.5f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        } else if (shown.isEmpty()) {
            g.text("No " + TAB_NOUNS[tab] + " on the roster.",
                    lx + lw / 2, h * 0.45f, u * 3, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        }
        g.save();
        g.clipRect(0, listY0, lx + lw + u, listY1 - listY0);
        for (Btn b : buttons) if (b.id >= ROW && b.y + b.h > listY0 && b.y < listY1) Ui.buttonAlways(g, b, u);
        for (int i = 0; i < c.roster.size(); i++) {
            Unit un = c.roster.get(i);
            Btn row = find(ROW + i);
            if (row == null || row.y + row.h < listY0 || row.y > listY1) continue;
            float cy = row.y + row.h / 2;
            UnitArt.draw(g, un, row.x + row.h * 0.6f, cy, -30, Battle.PLAYER, row.h * 0.62f, t, un.condition());
            float tx = row.x + row.h * 1.3f;
            boolean compact = row.w < u * 60;
            float ns = Math.min(u * 3f, row.h * 0.36f), ss = Math.min(u * 2.2f, row.h * 0.27f), sts = Math.min(u * 2.5f, row.h * 0.3f);
            float barW = compact ? u * 9 : u * 16;
            boolean ok = c.allowed(un);
            String state = un.destroyed ? "WRECKED" : (!ok ? "Not allowed" : (un.isDamaged() ? "Damaged" : "Ready"));
            int stc = un.destroyed ? Col.BAD : (!ok ? Col.TEXT_DIM : (un.isDamaged() ? Col.ACCENT : Col.GOOD));
            float stateW = g.textWidth(state, sts, true) + u * 3;
            g.text(Ui.fit(g, un.name, ns, row.x + row.w - stateW - tx), tx, cy - row.h * 0.18f, ns, un.destroyed ? Col.BAD : Col.TEXT, Gfx.ALIGN_LEFT, true);
            String line2 = (compact ? UnitType.SHORT[un.type] : UnitType.NAMES[un.type]) + " · " + Chassis.ROLES[un.ch.role]
                    + (un.name.equals(un.ch.name) || compact ? "" : " · " + un.ch.name);
            g.text(Ui.fit(g, line2, ss, row.x + row.w - barW - u * 3 - tx), tx, cy + row.h * 0.22f, ss, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            g.text(state, row.x + row.w - u * 2, cy - row.h * 0.18f, sts, stc, Gfx.ALIGN_RIGHT, true);
            Ui.bar(g, row.x + row.w - barW - u * 2, cy + row.h * 0.12f, barW, u * 1.4f, un.condition(), un.destroyed ? Col.BAD : (un.isDamaged() ? Col.ACCENT : Col.GOOD));
        }
        g.restore();
        if (maxScroll > 0) {
            float lh = listY1 - listY0, bh = lh * lh / (lh + maxScroll), by = listY0 + (lh - bh) * (scroll / maxScroll);
            g.fillRoundRect(lx + lw + u * 0.6f, by, u * 0.8f, bh, u * 0.4f, 0x66FFFFFF);
            if (scroll < maxScroll - 1) Ui.moreBelow(g, lx, listY1, lw, u, 0xFF17213B);
        }
        // detail
        Unit su = selected();
        float rx = lx + lw + u * 3, rw = w - rx - u * 3, ry = ctrlTop();
        float pBottom = h - u * 37.5f;
        Ui.panel(g, rx - u, ry, rw + u * 2, pBottom - ry, u * 2);
        if (su != null) {
            UnitArt.draw(g, su, rx + u * 9, ry + u * 10, -40 + (float) Math.sin(t) * 10, Battle.PLAYER, u * 11, t, 1);
            g.text(su.name, rx + u * 19, ry + u * 5, u * 3.8f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text(UnitType.NAMES[su.type] + ", " + Chassis.ROLES[su.ch.role], rx + u * 19, ry + u * 9.5f, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            g.text("Value $" + Ui.money(su.value()), rx + u * 19, ry + u * 13.5f, u * 2.6f, Col.MONEY, Gfx.ALIGN_LEFT, false);
            // compact two-column stats so the loadout fits above the Customize button
            String[] k = {"Move", "Armor", "Structure", "Heat", "Firepower", "Range"};
            String[] v = {su.ch.mp + " MP", su.totalArmor() + "/" + su.armorTotal, su.totalInfra() + "/" + su.totalInfraMax(),
                    "cools " + su.dissipation() + ", cap " + su.ch.heatCap, Info.alphaDamage(su) + " dmg", Info.maxRange(su) + " hexes"};
            float cw = (rw - u * 2) / 2;
            for (int i = 0; i < k.length; i++) {
                float cx = rx + u + (i % 2) * cw, cy = ry + u * 19.5f + (i / 2) * u * 3f;
                g.text(k[i], cx, cy, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                g.text(v[i], cx + cw * 0.42f, cy, u * 2.1f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            }
            // the loadout at a readable size; a long one scrolls inside the panel
            loadY0 = ry + u * 28.5f;
            loadY1 = pBottom - u * 0.8f;
            g.save();
            g.clipRect(rx - u, loadY0, rw + u * 2, loadY1 - loadY0);
            float end = Info.loadout(g, su, rx + u, loadY0 + u * 1.4f - loadScroll, rw - u * 2, u * 2.25f) + loadScroll;
            g.restore();
            loadMax = end > loadY1 ? end - loadY1 + u * 3.2f : 0;
            loadScroll = Math.min(loadScroll, loadMax);
            if (loadMax > 0) {
                float lh = loadY1 - loadY0, bh = lh * lh / (lh + loadMax), by = loadY0 + (lh - bh) * (loadScroll / loadMax);
                g.fillRoundRect(rx + rw + u * 0.2f, by, u * 0.7f, bh, u * 0.35f, 0x66FFFFFF);
                if (loadScroll < loadMax - 1) Ui.moreBelow(g, rx - u * 0.5f, loadY1, rw + u * 0.5f, u, Col.PANEL);
            }
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
