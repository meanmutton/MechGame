package com.meanmutton.mechgame.core;

/** Results after a battle. */
public final class DebriefScreen extends Screen {
    private static final int CONTINUE = 1;
    private final Battle b;
    private final Game.Debrief d;
    private float t;

    public DebriefScreen(Game game, Battle b, Game.Debrief d) {
        super(game);
        this.b = b;
        this.d = d;
        game.sfx(d.result == Battle.RESULT_WIN ? Platform.SND_WIN : Platform.SND_LOSE, 1f);
    }

    private boolean campaignOver() {
        return !game.company.quick && game.company.over != 0;
    }

    protected void layout() {
        buttons.clear();
        add(new Btn(CONTINUE, game.company.quick ? "Main Menu" : (campaignOver() ? "Campaign Results" : "Return to HQ")).set(w - u * 43, h - u * 12, u * 40, u * 9)).color = 0xFF2E7D4F;
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
    }

    protected void onClick(Btn btn) {
        if (game.company.quick) game.toMenu();
        else if (campaignOver()) game.setScreen(new CampaignEndScreen(game));
        else game.toHq();
    }

    public boolean back() {
        onClick(null);
        return true;
    }

    public void render(Gfx g) {
        background(g);
        String[] titles = {"", "VICTORY", "DEFEAT", "DRAW", "WITHDRAWN"};
        int[] colors = {0, Col.GOOD, Col.BAD, Col.ACCENT, Col.TEXT_DIM};
        Ui.title(g, d.forfeit ? "FORFEITED" : titles[d.result], w / 2f, h * 0.09f, u * 10);
        Company co = game.company;
        String line = b == null ? "No unit on your roster could take part, so the mission was forfeited."
                : "Battle lasted " + Math.min(b.maxRounds, b.round) + " rounds.";
        if (d.scenario != null) line = d.scenario + ": " + line + "  " + campaignLine(co);
        g.text(Ui.fit(g, line, u * 2.6f, w - u * 6), w / 2f, h * 0.18f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        float lx = u * 4, cw = (w - u * 12) / 2, y0 = h * 0.25f;
        Ui.panel(g, lx - u, y0, cw + u * 2, h * 0.6f, u * 2);
        float y = y0 + u * 5;
        g.text("Payment", lx + u, y, u * 3.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        y += u * 5;
        g.text("Contract pay", lx + u, y, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        g.text("$" + Ui.money(d.pay), lx + cw - u, y, u * 2.6f, Col.MONEY, Gfx.ALIGN_RIGHT, true);
        y += u * 4;
        g.text("Destruction bonus", lx + u, y, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        g.text("$" + Ui.money(d.bonus), lx + cw - u, y, u * 2.6f, Col.MONEY, Gfx.ALIGN_RIGHT, true);
        y += u * 4;
        if (d.campaignBonus > 0) {
            g.text("Campaign victory bonus", lx + u, y, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            g.text("$" + Ui.money(d.campaignBonus), lx + cw - u, y, u * 2.6f, Col.MONEY, Gfx.ALIGN_RIGHT, true);
            y += u * 4;
        }
        g.text("Repairs needed", lx + u, y, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, false);
        g.text("$" + Ui.money(d.repairCost), lx + cw - u, y, u * 2.6f, Col.BAD, Gfx.ALIGN_RIGHT, true);
        y += u * 4;
        g.text("Cash now", lx + u, y, u * 2.8f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("$" + Ui.money(game.company.cash), lx + cw - u, y, u * 2.8f, Col.MONEY, Gfx.ALIGN_RIGHT, true);
        y += u * 6;
        g.text("Salvage", lx + u, y, u * 3.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        y += u * 4;
        if (d.salvage.isEmpty()) y += Ui.wrap(g, "None recovered.", lx + u, y, u * 2.4f, cw - u * 2, Col.TEXT_DIM);
        else y += Ui.wrap(g, join(d.salvage) + " (added to your spare parts)", lx + u, y, u * 2.4f, cw - u * 2, Col.TEXT);
        y += u * 2;
        if (!d.lost.isEmpty()) y += Ui.wrap(g, "Lost for good: " + join(d.lost), lx + u, y, u * 2.4f, cw - u * 2, Col.BAD);
        if (!d.wrecked.isEmpty()) Ui.wrap(g, "Recovered as wrecks (repair at HQ): " + join(d.wrecked), lx + u, y, u * 2.4f, cw - u * 2, Col.ACCENT);

        float rx = lx + cw + u * 4;
        Ui.panel(g, rx - u, y0, cw + u * 2, h * 0.6f, u * 2);
        y = y0 + u * 5;
        g.text("Unit performance", rx + u, y, u * 3.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        y += u * 5;
        int mine = 0;
        if (b != null) for (Unit un : b.units) if (un.team == Battle.PLAYER) mine++;
        float step = Math.min(u * 5, u * 44 / Math.max(1, mine));
        for (Unit un : b == null ? new java.util.ArrayList<Unit>() : b.units) {
            if (un.team != Battle.PLAYER) continue;
            UnitArt.draw(g, un, rx + u * 3, y, -30, Battle.PLAYER, u * 2.2f, t, 1);
            g.text(un.name + (un.tag != null ? " (" + un.tag + ")" : ""), rx + u * 6.5f, y, u * 2.6f, un.destroyed ? Col.BAD : Col.TEXT, Gfx.ALIGN_LEFT, true);
            g.text(un.kills + " kills, " + Math.round(un.damageDealt) + " dmg", rx + cw - u, y, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
            y += step;
        }
        y += u * 2;
        if (!d.enemyLost.isEmpty()) y += Ui.wrap(g, "Enemy destroyed: " + join(d.enemyLost), rx + u, y, u * 2.3f, cw - u * 2, Col.TEXT_DIM) + u;
        if (!game.company.quick && game.company.rival != null) {
            String s = "Enemy company was paid $" + Ui.money(d.rivalPay)
                    + (d.rivalLost > 0 ? ", lost " + d.rivalLost + " wreck" + (d.rivalLost > 1 ? "s" : "") + " on the field" : "")
                    + (d.rivalBought.isEmpty() ? "" : ", and bought " + join(d.rivalBought))
                    + ". It now has $" + Ui.money(d.rivalCash) + " and " + d.rivalUnits + " units.";
            Ui.wrap(g, s, rx + u, y, u * 2.3f, cw - u * 2, Col.ACCENT);
        }
        renderButtons(g);
    }

    private String campaignLine(Company co) {
        if (d.over == Scenario.OUT_ATTACKER) return "The planet falls to the attacker!";
        if (d.over == Scenario.OUT_DEFENDER) return "The attacker leaves the planet!";
        if (d.frontAfter == d.frontBefore) return "The front holds at " + d.frontAfter + ". Next: " + Scenario.get(co.scenario).name + ".";
        return (d.frontAfter > d.frontBefore ? "The attacker" : "The defender") + " gains ground: front " + d.frontBefore + " to "
                + d.frontAfter + ". Next: " + Scenario.get(co.scenario).name + ".";
    }

    static String join(java.util.List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(b.length() > 0 ? ", " : "").append(s);
        return b.toString();
    }
}
