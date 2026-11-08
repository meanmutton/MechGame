package com.meanmutton.mechgame.core;

/** Two-player campaign: how the battle went for each player, side by side, and where the front stands now. */
public final class PvpDebriefScreen extends Screen {
    private static final int CONTINUE = 1;
    private final Battle b;
    private final Game.Debrief d;

    public PvpDebriefScreen(Game game, Battle b, Game.Debrief d) {
        super(game);
        this.b = b;
        this.d = d;
        game.sfx(Platform.SND_WIN, 1f);
    }

    private boolean over() {
        Company c = game.campaignMain();
        return c != null && c.over != 0;
    }

    protected void layout() {
        buttons.clear();
        Btn go = add(new Btn(CONTINUE, over() ? "Campaign Results" : "Continue").set(w - u * 43, h - u * 12, u * 40, u * 9));
        go.color = 0xFF2E7D4F;
        if (!over()) go.sub = "Player 1's turn at the HQ";
    }

    protected void onClick(Btn btn) {
        if (over()) game.setScreen(new CampaignEndScreen(game));
        else game.pvpNextTurn();
    }

    public boolean back() {
        onClick(null);
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Company c = game.campaignMain();
        String head = d.result == Battle.RESULT_WIN ? "PLAYER 1 WINS" : (d.result == Battle.RESULT_LOSS ? "PLAYER 2 WINS" : "DRAW");
        int hc = d.result == Battle.RESULT_WIN ? UnitArt.PLAYER_COLOR : (d.result == Battle.RESULT_LOSS ? UnitArt.ENEMY_COLOR : Col.ACCENT);
        g.text(head, w / 2f, h * 0.085f, u * 8, hc, Gfx.ALIGN_CENTER, true);
        String how = d.forfeit ? "Player " + (d.forfeitTeam + 1) + " had no unit that could fight, and forfeited."
                : "Battle lasted " + Math.min(b.maxRounds, b.round) + " rounds" + (b.conceded >= 0 ? "; Player " + (b.conceded + 1) + " conceded." : ".");
        g.text(Ui.fit(g, (d.scenario != null ? d.scenario + ": " : "") + how + "  " + frontLine(c), u * 2.5f, w - u * 6), w / 2f, h * 0.165f,
                u * 2.5f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        float cw = (w - u * 12) / 2, y0 = h * 0.22f, ph = h - y0 - u * 15;
        for (int seat = 0; seat < 2; seat++) {
            Game.Debrief s = seat == 0 ? d : d.p2;
            Company co = seat == 0 ? c : c.rival;
            if (s == null || co == null) continue;
            float x = u * 4 + seat * (cw + u * 4);
            Ui.panel(g, x - u, y0, cw + u * 2, ph, u * 2);
            int col = UnitArt.teamColor(seat);
            String res = s.result == Battle.RESULT_WIN ? "Victory" : (s.result == Battle.RESULT_DRAW ? "Draw" : "Defeat");
            g.text("Player " + (seat + 1) + "  ·  " + Scenario.SIDE_NAMES[co.side], x + u, y0 + u * 4.5f, u * 3.2f, col, Gfx.ALIGN_LEFT, true);
            g.text(res, x + cw - u, y0 + u * 4.5f, u * 3.2f, s.result == Battle.RESULT_WIN ? Col.GOOD : (s.result == Battle.RESULT_DRAW ? Col.ACCENT : Col.BAD),
                    Gfx.ALIGN_RIGHT, true);
            float y = y0 + u * 10;
            y = row(g, "Contract pay", "$" + Ui.money(s.pay), x, y, cw, Col.MONEY);
            y = row(g, "Destruction bonus", "$" + Ui.money(s.bonus), x, y, cw, Col.MONEY);
            if (s.campaignBonus > 0) y = row(g, "Planet taken", "$" + Ui.money(s.campaignBonus), x, y, cw, Col.MONEY);
            y = row(g, "Repairs needed", "$" + Ui.money(s.repairCost), x, y, cw, Col.BAD);
            y = row(g, "Cash now", "$" + Ui.money(co.cash), x, y, cw, Col.MONEY);
            y += u * 2;
            if (!s.salvage.isEmpty()) y += Ui.wrap(g, "Salvage: " + DebriefScreen.join(s.salvage), x + u, y, u * 2.3f, cw - u * 2, Col.TEXT) + u * 0.5f;
            if (!s.lost.isEmpty()) y += Ui.wrap(g, "Lost for good: " + DebriefScreen.join(s.lost), x + u, y, u * 2.3f, cw - u * 2, Col.BAD) + u * 0.5f;
            if (!s.wrecked.isEmpty()) Ui.wrap(g, "Recovered as wrecks: " + DebriefScreen.join(s.wrecked), x + u, y, u * 2.3f, cw - u * 2, Col.ACCENT);
        }
        renderButtons(g);
    }

    private float row(Gfx g, String label, String value, float x, float y, float cw, int col) {
        g.text(label, x + u, y, u * 2.5f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        g.text(value, x + cw - u, y, u * 2.5f, col, Gfx.ALIGN_RIGHT, true);
        return y + u * 3.6f;
    }

    private String frontLine(Company co) {
        if (d.over == Scenario.OUT_ATTACKER) return "The planet falls to the attacker!";
        if (d.over == Scenario.OUT_DEFENDER) return "The attacker leaves the planet!";
        String next = co.over == 0 ? " Next: " + Scenario.get(co.scenario).name + "." : "";
        if (d.frontAfter == d.frontBefore) return "The front holds at " + d.frontAfter + "." + next;
        return (d.frontAfter > d.frontBefore ? "The attacker" : "The defender") + " gains ground: front " + d.frontBefore + " to " + d.frontAfter + "." + next;
    }
}
