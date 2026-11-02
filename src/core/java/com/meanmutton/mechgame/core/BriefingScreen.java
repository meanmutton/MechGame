package com.meanmutton.mechgame.core;

import java.util.List;

/** Mission briefing: the map, the weather, and what you're up against. */
public final class BriefingScreen extends Screen {
    private static final int BACK = 1, GO = 2;
    private final HexMap map;
    private final List<Unit> enemy;
    private float t;

    public BriefingScreen(Game game, HexMap map, List<Unit> enemy) {
        super(game);
        this.map = map;
        this.enemy = enemy;
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
        else game.startBattle(map, enemy);
    }

    public boolean back() {
        game.toHq();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Company c = game.company;
        g.text(c.quick ? "BATTLE BRIEFING" : "CONTRACT " + c.mission + " BRIEFING", u * 3, h * 0.055f, u * 5, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text(HexMap.BIOME_NAMES[map.biome] + " battlefield.  Destroy the enemy force. 15 rounds; if time runs out, the side that kept more of its value wins.",
                u * 3, h * 0.105f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        // map thumbnail
        float mx = u * 3, my = h * 0.15f, mw = w * 0.55f, mh = h * 0.62f;
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
        g.text("You deploy on the west (blue), the enemy on the east (red).", mx, my + mh + u * 3, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);

        float rx = mx + mw + u * 4, rw = w - rx - u * 3;
        Ui.panel(g, rx - u, my, rw + u * 2, h * 0.7f, u * 2);
        float y = my + u * 5;
        g.text("Weather: " + Weather.NAMES[map.weather.id], rx + u, y, u * 3.4f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        y += u * 3;
        y += Ui.wrap(g, map.weather.desc, rx + u, y + u, u * 2.4f, rw - u * 2, Col.TEXT) + u * 2;
        g.text("Enemy force (" + enemy.size() + ")", rx + u, y, u * 3, Col.BAD, Gfx.ALIGN_LEFT, true);
        y += u * 4;
        int ev = 0;
        for (Unit e : enemy) {
            UnitArt.draw(g, e, rx + u * 3, y, 180, Battle.ENEMY, u * 2.2f, t, 1);
            g.text(e.name, rx + u * 6.5f, y, u * 2.5f, Col.TEXT, Gfx.ALIGN_LEFT, true);
            g.text(UnitType.SHORT[e.type] + " · " + Chassis.ROLES[e.ch.role], rx + rw - u, y, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            y += u * 4.5f;
            ev += e.value();
        }
        y += u;
        g.text("Your force (" + c.deployed.size() + ")", rx + u, y, u * 3, UnitArt.PLAYER_COLOR, Gfx.ALIGN_LEFT, true);
        y += u * 4;
        StringBuilder names = new StringBuilder();
        for (Unit un : c.deployed) names.append(names.length() > 0 ? ", " : "").append(un.name);
        y += Ui.wrap(g, names.toString(), rx + u, y, u * 2.3f, rw - u * 2, Col.TEXT);
        g.text("Value: yours $" + Ui.money(c.deployedValue()) + "  vs  theirs $" + Ui.money(ev), rx + u, y + u * 2, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        if (!c.quick) g.text("Pay: $" + Ui.money(c.reward) + " for a win", rx + u, y + u * 6, u * 2.6f, Col.MONEY, Gfx.ALIGN_LEFT, true);
        renderButtons(g);
    }
}
