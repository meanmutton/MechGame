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
        if (b.id == BACK) backToHq();
        else game.startBattle(map, enemy, allies);
    }

    public boolean back() {
        backToHq();
        return true;
    }

    /** Hotseat: Player 2 pressed Fight and is still holding the device, so Back returns to Player 2's force. */
    private void backToHq() {
        if (game.company != null && game.company.pvp) {
            game.pvpBackToP2();
            return;
        }
        if (game.company != null && game.company.hotseat && game.seats != null) game.company = game.seats[1];
        game.toHq();
    }

    public void render(Gfx g) {
        background(g);
        Company c = game.company;
        Scenario sc = c.next();
        int rounds = sc != null ? sc.rounds : Battle.MAX_ROUNDS;
        float mx = u * 3, mw = w * 0.55f;
        float my;
        if (sc == null) {
            g.text(c.twoPlayer() ? "TWO PLAYERS: BRIEFING" : "BATTLE BRIEFING", u * 3, h * 0.055f, u * 5, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text(HexMap.BIOME_NAMES[map.biome] + " battlefield.  Destroy the enemy force. " + rounds
                            + " rounds; if time runs out, the side that kept more of its value wins.",
                    u * 3, h * 0.105f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            my = h * 0.15f;
        } else {
            g.text((c.merc ? "JOB: " : "CONTRACT " + c.mission + ": ") + Text.upper(sc.name), u * 3, h * 0.05f, u * 5, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text((c.pvp ? "Player 1 is the " : "You are the ") + Text.lower(Scenario.SIDE_NAMES[c.side]), w - u * 3, h * 0.05f, u * 3,
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
        g.text(c.twoPlayer() ? "Player 1 deploys on the west (blue), Player 2 on the east (red). Neither sees the other's setup."
                : "You deploy on the west (blue), the enemy on the east (red).", mx, by, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        Merc.Contract job = c.job();
        if (job != null) {
            // who hired you, and who you are about to make an enemy of
            float x = mx;
            x += Emblem.label(g, job.employer, x, by + u * 4, u * 2.8f, true) + u * 1.5f;
            String verb = c.side == Scenario.ATTACKER ? "hires you to attack" : "hires you to defend against";
            g.text(verb, x, by + u * 4, u * 2.4f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            x += g.textWidth(verb, u * 2.4f, false) + u * 1.5f;
            Emblem.label(g, job.target, x, by + u * 4, u * 2.8f, true);
            int[] win = Merc.repChanges(job, Battle.RESULT_WIN), lose = Merc.repChanges(job, Battle.RESULT_LOSS);
            String st = "Standing if you win: " + Faction.ALL[job.employer].shortName + " " + ContractsScreen.signed(win[job.employer]) + ", "
                    + Faction.ALL[job.target].shortName + " " + ContractsScreen.signed(win[job.target]) + ".  If you lose: "
                    + Faction.ALL[job.employer].shortName + " " + ContractsScreen.signed(lose[job.employer]) + ", "
                    + Faction.ALL[job.target].shortName + " " + ContractsScreen.signed(lose[job.target]) + ".";
            g.text(Ui.fit(g, st, u * 2.1f, mw), mx, by + u * 8, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        } else if (sc != null) {
            g.text("Front: " + Scenario.frontName(c.front), mx, by + u * 3.5f, u * 2.4f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            g.text("Battle " + (c.planetBattles + 1) + " here. Income: attacker x" + fmt(Scenario.income(Scenario.ATTACKER, c.planetBattles))
                            + ", defender x" + fmt(Scenario.income(Scenario.DEFENDER, c.planetBattles)),
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
            g.text(Ui.fit(g, sc.limits(c.limits()), u * 2.2f, rw - u * 2), rx + u, y + u * 0.5f, u * 2.2f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            y += u * 3;
            // reserves come from a campaign's long fight; a mercenary job is fought within the scenario's own limits
            int mine = c.merc ? 0 : Scenario.extraUnits(c.side, c.planetBattles), theirs = c.merc ? 0 : Scenario.extraUnits(1 - c.side, c.planetBattles);
            if (mine > 0 || theirs > 0) {
                String res = mine > 0 ? (c.pvp ? "Player 1's" : "Your") + " resources bring " + mine + " reserve unit" + (mine > 1 ? "s" : "") + " beyond the scenario limit"
                        : (c.pvp ? "Player 2's" : "The enemy's") + " resources let it field " + theirs + " extra unit" + (theirs > 1 ? "s" : "") + " (up to "
                        + sc.limitsFor(1 - c.side, c.planetBattles).total() + ")";
                g.text(res, rx + u, y + u * 0.5f, u * 2.2f, mine > 0 ? Col.GOOD : Col.BAD, Gfx.ALIGN_LEFT, false);
                y += u * 3;
            }
            String lean = c.pvp ? sc.leanText(c.side, "Player 1", "Player 2") : sc.leanText(c.side);
            if (lean != null) {
                g.text(lean, rx + u, y + u * 0.5f, u * 2.2f, Col.ACCENT, Gfx.ALIGN_LEFT, false);
                y += u * 3;
            }
            if (c.edge >= 0) {
                g.text((c.edge == c.side ? (c.pvp ? "Player 1 has" : "You have") : (c.pvp ? "Player 2 has" : "The enemy has")) + " the edge: initiative in round 1", rx + u, y + u * 0.5f,
                        u * 2.2f, c.edge == c.side ? Col.GOOD : Col.BAD, Gfx.ALIGN_LEFT, false);
                y += u * 3;
            }
            if (job == null) y += Ui.wrap(g, (c.pvp ? "For Player 1: " : "") + sc.stakes(c.front, c.side), rx + u, y + u * 0.5f, u * 2.2f, rw - u * 2, Col.TEXT) + u;
        }
        g.text((c.twoPlayer() ? "Player 2 force (" : "Enemy force (") + enemy.size() + ")", rx + u, y + u, u * 2.8f, c.twoPlayer() ? UnitArt.ENEMY_COLOR : Col.BAD,
                Gfx.ALIGN_LEFT, true);
        if (job != null) {
            Faction tf = Faction.ALL[job.target];
            float lw = g.textWidth(tf.shortName, u * 2.2f, true) + u * 2.8f;
            Emblem.label(g, job.target, rx + rw - u - lw, y + u, u * 2.2f, false);
        }
        y += u * 4.5f;
        int ev = 0;
        boolean twoCols = enemy.size() > 5;
        float colW = twoCols ? (rw - u) / 2 : rw;
        float rowH = twoCols ? (enemy.size() > 12 ? u * 2.8f : u * 3.6f) : u * 4.2f;
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
        g.text((c.twoPlayer() ? "Player 1 force (" : "Your force (") + (c.deployed.size() + allies.size()) + ")", rx + u, y, u * 2.8f, UnitArt.PLAYER_COLOR,
                Gfx.ALIGN_LEFT, true);
        y += u * 3.5f;
        StringBuilder names = new StringBuilder();
        for (Unit un : c.deployed) names.append(names.length() > 0 ? ", " : "").append(un.name);
        y += Ui.wrap(g, names.toString(), rx + u, y, u * 2.1f, rw - u * 2, Col.TEXT);
        if (!allies.isEmpty()) {
            StringBuilder an = new StringBuilder(c.pvp ? "With Player 1, garrison: " : "With you, garrison: ");
            for (int i = 0; i < allies.size(); i++) an.append(i > 0 ? ", " : "").append(allies.get(i).name);
            y += Ui.wrap(g, an.toString(), rx + u, y, u * 2.1f, rw - u * 2, Col.ACCENT);
        }
        g.text((c.twoPlayer() ? "Value: Player 1 $" + Ui.money(c.deployedValue()) + "  vs  Player 2 $" : "Value: yours $" + Ui.money(c.deployedValue()) + "  vs  theirs $") + Ui.money(ev)
                + (c.quick || c.rival == null || c.pvp ? "" : " (capped at $" + Ui.money(rivalCap(c, sc)) + ")")
                + (c.quick || c.pvp ? "" : ".  Pay $" + Ui.money(c.reward) + " for a win"), rx + u, y + u, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        y += u * 3.5f;
        if (!c.quick && c.rival != null && !c.pvp) {
            int damaged = 0, hired = 0;
            for (Unit e : enemy) {
                if (e.isDamaged()) damaged++;
                if ("hired".equals(e.tag)) hired++;
            }
            String rs = "Enemy company: $" + Ui.money(c.rival.cash) + ", " + c.rival.roster.size() + " units"
                    + (damaged > 0 ? " (" + damaged + " fielded damaged)" : "")
                    + (hired > 0 ? "; it hired " + hired + " for this fight" : "") + ".";
            y += Ui.wrap(g, rs, rx + u, y, u * 2.1f, rw - u * 2, Col.TEXT_DIM);
            if (militia) g.text("Broke and beaten, the enemy has scraped together a militia.", rx + u, y, u * 2.1f, Col.ACCENT, Gfx.ALIGN_LEFT, false);
        }
        renderButtons(g);
    }

    static String fmt(float f) {
        return Text.fixed(f, 2);
    }

    private static int rivalCap(Company c, Scenario sc) {
        float lean = sc == null ? 1f : (c.side == Scenario.DEFENDER ? 1f + sc.capLean : 1f - sc.capLean)
                * Scenario.forceFactor(1 - c.side, c.planetBattles);
        return Math.round(c.deployedValue() * Rival.engagementLimit(c.difficulty) * lean);
    }
}
