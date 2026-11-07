package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/** Hotseat: who won, and how each player's units did. Rematch replays the same two forces on a new map. */
public final class HotseatEndScreen extends Screen {
    private static final int MENU = 1, REMATCH = 2;
    private final Battle b;
    private float t;

    public HotseatEndScreen(Game game, Battle b) {
        super(game);
        this.b = b;
        game.sfx(Platform.SND_WIN, 1f);
    }

    protected void layout() {
        buttons.clear();
        add(new Btn(MENU, "Main Menu").set(u * 3, h - u * 12, u * 30, u * 9));
        Btn r = add(new Btn(REMATCH, "Rematch").set(w - u * 43, h - u * 12, u * 40, u * 9));
        r.color = 0xFF2E7D4F;
        r.sub = "same forces, repaired, new map";
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
    }

    protected void onClick(Btn btn) {
        if (btn.id == REMATCH) game.hotseatRematch(b);
        else game.toMenu();
    }

    public boolean back() {
        game.toMenu();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        String title = b.result == Battle.RESULT_WIN ? "PLAYER 1 WINS" : (b.result == Battle.RESULT_LOSS ? "PLAYER 2 WINS" : "DRAW");
        int tc = b.result == Battle.RESULT_WIN ? UnitArt.PLAYER_COLOR : (b.result == Battle.RESULT_LOSS ? UnitArt.ENEMY_COLOR : Col.ACCENT);
        g.text(title, w / 2f, h * 0.09f, u * 9, tc, Gfx.ALIGN_CENTER, true);
        String how;
        if (b.conceded >= 0) how = "Player " + (b.conceded + 1) + " conceded";
        else if (b.alive(Battle.PLAYER) == 0 || b.alive(Battle.ENEMY) == 0) how = "Force destroyed";
        else how = "Time ran out: decided by the value each side kept";
        g.text(how + ", round " + Math.min(b.round, b.maxRounds) + ".", w / 2f, h * 0.18f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        float cw = (w - u * 12) / 2, y0 = h * 0.25f, ph = h * 0.6f;
        for (int team = 0; team < 2; team++) {
            float x = u * 4 + team * (cw + u * 4);
            Ui.panel(g, x - u, y0, cw + u * 2, ph, u * 2);
            int col = UnitArt.teamColor(team);
            List<Unit> mine = new ArrayList<Unit>();
            int lost = 0, kept = 0;
            for (Unit un : b.units) {
                if (un.team != team) continue;
                mine.add(un);
                if (un.destroyed) lost++;
                else kept++;
            }
            g.text("Player " + (team + 1), x + u, y0 + u * 4.5f, u * 3.4f, col, Gfx.ALIGN_LEFT, true);
            g.text(kept + " standing, " + lost + " lost", x + cw - u, y0 + u * 4.5f, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            float step = Math.min(u * 5, (ph - u * 10) / Math.max(1, mine.size()));
            float y = y0 + u * 10;
            for (Unit un : mine) {
                UnitArt.draw(g, un, x + u * 3, y, team == Battle.PLAYER ? -30 : 150, team, Math.min(u * 2.2f, step * 0.45f), t, un.destroyed ? 0.3f : 1);
                float ts = Math.min(u * 2.6f, step * 0.55f);
                g.text(Ui.fit(g, un.name, ts, cw * 0.5f), x + u * 6.5f, y, ts, un.destroyed ? Col.BAD : Col.TEXT, Gfx.ALIGN_LEFT, true);
                g.text(un.kills + (un.kills == 1 ? " kill, " : " kills, ") + Math.round(un.damageDealt) + " dmg", x + cw - u, y, ts * 0.9f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
                y += step;
            }
        }
        renderButtons(g);
    }
}
