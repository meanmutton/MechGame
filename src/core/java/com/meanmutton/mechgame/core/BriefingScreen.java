package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/** Mission briefing: the scenario, the map, the weather, and what you're up against. */
public final class BriefingScreen extends Screen {
    private static final int BACK = 1, GO = 2;
    private final HexMap map;
    private final List<Unit> enemy;
    private final List<Unit> allies;
    private float t;
    /** The rival had nothing left and fields a scratch militia. */
    public boolean militia;

    public BriefingScreen(Game game, HexMap map, List<Unit> enemy) {
        this(game, map, enemy, new ArrayList<Unit>());
    }

    public BriefingScreen(Game game, HexMap map, List<Unit> enemy, List<Unit> allies) {
        super(game);
        this.map = map;
        this.enemy = enemy;
        this.allies = allies;
    }

    protected void layout() {
        buttons.clear();
        add(new Btn(BACK, "Back to HQ").set(u * 3, h - u * 12, u * 32, u * 9));
        add(new Btn(GO, "BEGIN BATTLE").set(w - u * 43, h - u * 12, u * 40, u * 9)).color = 0xFF2E7D4F;
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
    }

    protected void onClick(Btn b) {
        if (b.id == BACK) game.toHq();
        else game.startBattle(map, enemy, allies);
    }

    public boolean back() {
        game.toHq();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Company c = game.company;
        Scenario sc = c.next();
        int rounds = sc != null ? sc.rounds : Battle.MAX_ROUNDS;
        float mx = u * 3, mw = w * 0.55f;
        float my;
        if (sc == null) {
            g.text("BATTLE BRIEFING", u * 3, h * 0.055f, u * 5, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text(HexMap.BIOME_NAMES[map.biome] + " battlefield.  Destroy the enemy force. " + rounds
                            + " rounds; if time runs out, the side that kept more of its value wins.",
                    u * 3, h * 0.105f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            my = h * 0.15f;
        } else {
            g.text("CONTRACT " + c.mission + ": " + sc.name.toUpperCase(), u * 3, h * 0.05f, u * 5, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text("You are the " + Scenario.SIDE_NAMES[c.side].toLowerCase(), w - u * 3, h * 0.05f, u * 3,
                    UnitArt.PLAYER_COLOR, Gfx.ALIGN_RIGHT, true);
            float dy = Ui.wrap(g, HexMap.BIOME_NAMES[map.biome] + " region. " + sc.desc, u * 3, h * 0.095f, u * 2.4f, w - u * 6, Col.TEXT);
            my = h * 0.095f + dy + u;
        }
        float mh = h - my - u * (sc != null ? 30 : 24);
        // map thumbnail
        float s = Math.min(mw / (1.5f * (map.cols - 1) + 2), mh / (1.732f * (map.rows + 0.5f)));
        g.save();
        g.clipRect(mx, my, mw, mh);
        g.fillRect(mx, my, mw, mh, 0xFF0A0F18);
        float drawnW = (1.5f * (map.cols - 1) + 2) * s;
        g.translate(mx + (mw - drawnW) / 2 + s, my + s * 0.9f);
        g.scale(s, s);
        HexRender.draw(g, map, t, false);
        for (int r = 0; r < map.rows; r++) {
            for (int col = 0; col < 3; col++) HexRender.fillHex(g, col, r, 0.98f, 0x223F8EF0);
            for (int col = map.cols - 3; col < map.cols; col++) HexRender.fillHex(g, col, r, 0.98f, 0x22E0503C);
        }
        g.restore();
        g.strokeRoundRect(mx, my, mw, mh, u, u * 0.3f, Col.BORDER);
        float by = my + mh + u * 3;
        g.text("You deploy on the west (blue), the enemy on the east (red).", mx, by, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        if (sc != null) {
            g.text("Front: " + Scenario.frontName(c.front), mx, by + u * 3.5f, u * 2.4f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            g.text("Attacker's supplies: battle " + Math.min(Scenario.SUPPLY, c.planetBattles + 1) + " of " + Scenario.SUPPLY,
                    mx + mw, by + u * 3.5f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            Ui.frontTrack(g, mx, by + u * 5.5f, mw, u, c.front, c.side);
        }

        float rx = mx + mw + u * 4, rw = w - rx - u * 3, top = sc == null ? h * 0.15f : my;
        Ui.panel(g, rx - u, top, rw + u * 2, h - top - u * 14, u * 2);
        float y = top + u * 4;
        g.text("Weather: " + Weather.NAMES[map.weather.id], rx + u, y, u * 3, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        y += u * 2.5f;
        y += Ui.wrap(g, map.weather.desc, rx + u, y + u, u * 2.2f, rw - u * 2, Col.TEXT) + u;
        if (sc != null) {
            g.text(sc.limits(), rx + u, y + u * 0.5f, u * 2.2f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            y += u * 3;
            String lean = sc.leanText(c.side);
            if (lean != null) {
                g.text(lean, rx + u, y + u * 0.5f, u * 2.2f, Col.ACCENT, Gfx.ALIGN_LEFT, false);
                y += u * 3;
            }
            if (c.edge >= 0) {
                g.text((c.edge == c.side ? "You have" : "The enemy has") + " the edge: initiative in round 1", rx + u, y + u * 0.5f,
                        u * 2.2f, c.edge == c.side ? Col.GOOD : Col.BAD, Gfx.ALIGN_LEFT, false);
                y += u * 3;
            }
            y += Ui.wrap(g, sc.stakes(c.front, c.side, sc.isLast(c.planetBattles)), rx + u, y + u * 0.5f, u * 2.2f, rw - u * 2, Col.TEXT) + u;
        }
        g.text("Enemy force (" + enemy.size() + ")", rx + u, y + u, u * 2.8f, Col.BAD, Gfx.ALIGN_LEFT, true);
        y += u * 4.5f;
        int ev = 0;
        boolean twoCols = enemy.size() > 5;
        float colW = twoCols ? (rw - u) / 2 : rw;
        float rowH = twoCols ? u * 3.6f : u * 4.2f;
        for (int i = 0; i < enemy.size(); i++) {
            Unit e = enemy.get(i);
            float ex = rx + (twoCols ? (i % 2) * colW : 0), ey = y + (twoCols ? i / 2 : i) * rowH;
            UnitArt.draw(g, e, ex + u * 3, ey, 180, Battle.ENEMY, u * 1.9f, t, 1);
            String label = e.tag != null ? e.name + " (" + e.tag + ")" : e.name;
            g.text(Ui.fit(g, label, u * 2.2f, colW - u * 7), ex + u * 6, ey, u * 2.2f, e.tag != null ? Col.ACCENT : Col.TEXT, Gfx.ALIGN_LEFT, true);
            if (!twoCols) g.text(UnitType.SHORT[e.type] + " · " + Chassis.ROLES[e.ch.role], rx + rw - u, ey, u * 2f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            if (e.tag == null) ev += e.value();
        }
        y += ((twoCols ? (enemy.size() + 1) / 2 : enemy.size())) * rowH + u * 0.5f;
        g.text("Your force (" + (c.deployed.size() + allies.size()) + ")", rx + u, y, u * 2.8f, UnitArt.PLAYER_COLOR, Gfx.ALIGN_LEFT, true);
        y += u * 3.5f;
        StringBuilder names = new StringBuilder();
        for (Unit un : c.deployed) names.append(names.length() > 0 ? ", " : "").append(un.name);
        y += Ui.wrap(g, names.toString(), rx + u, y, u * 2.1f, rw - u * 2, Col.TEXT);
        if (!allies.isEmpty()) {
            StringBuilder an = new StringBuilder("With you, garrison: ");
            for (int i = 0; i < allies.size(); i++) an.append(i > 0 ? ", " : "").append(allies.get(i).name);
            y += Ui.wrap(g, an.toString(), rx + u, y, u * 2.1f, rw - u * 2, Col.ACCENT);
        }
        g.text("Value: yours $" + Ui.money(c.deployedValue()) + "  vs  theirs $" + Ui.money(ev)
                + (c.quick ? "" : "  (pay $" + Ui.money(c.reward) + " for a win)"), rx + u, y + u, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        y += u * 3.5f;
        if (!c.quick && c.rival != null) {
            int damaged = 0, hired = 0;
            for (Unit e : enemy) {
                if (e.isDamaged()) damaged++;
                if ("hired".equals(e.tag)) hired++;
            }
            String rs = "Enemy company: $" + Ui.money(c.rival.cash) + ", " + c.rival.roster.size() + " units"
                    + (damaged > 0 ? " (" + damaged + " fielded damaged)" : "")
                    + (hired > 0 ? "; it hired " + hired + " for this fight" : "")
                    + ". Its force is capped at $" + Ui.money(rivalCap(c, sc)) + ".";
            y += Ui.wrap(g, rs, rx + u, y, u * 2.1f, rw - u * 2, Col.TEXT_DIM);
            if (militia) g.text("Broke and beaten, the enemy has scraped together a militia.", rx + u, y, u * 2.1f, Col.ACCENT, Gfx.ALIGN_LEFT, false);
        }
        renderButtons(g);
    }

    private static int rivalCap(Company c, Scenario sc) {
        float lean = sc == null ? 1f : (c.side == Scenario.DEFENDER ? 1f + sc.capLean : 1f - sc.capLean);
        return Math.round(c.deployedValue() * Rival.engagementLimit(c.difficulty) * lean);
    }
}
