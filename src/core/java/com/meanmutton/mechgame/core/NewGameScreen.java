package com.meanmutton.mechgame.core;

/** Choose a campaign or a one-off quick battle. */
public final class NewGameScreen extends Screen {
    private static final int BACK = 1, CAMPAIGN = 2, QUICK = 3, C_DIFF = 10, C_FUNDS = 20, Q_DIFF = 30, Q_BUDGET = 40, C_SIDE = 50;
    private int cDiff = 1, cFunds = 1, qDiff = 1, qBudget = 2, cSide = Scenario.ATTACKER;
    private boolean confirmOverwrite;

    public NewGameScreen(Game game) {
        super(game);
    }

    protected void layout() {
        buttons.clear();
        float pw = (w - u * 12) / 2, px1 = u * 4, px2 = px1 + pw + u * 4, py = h * 0.17f;
        float bw = (pw - u * 6) / 3, bh = u * 9;
        for (int i = 0; i < 3; i++) {
            Btn b = add(new Btn(C_DIFF + i, Company.DIFFICULTY[i]).set(px1 + u * 2 + i * (bw + u), py + u * 16, bw, bh));
            b.selected = cDiff == i;
            Btn q = add(new Btn(Q_DIFF + i, Company.DIFFICULTY[i]).set(px2 + u * 2 + i * (bw + u), py + u * 16, bw, bh));
            q.selected = qDiff == i;
        }
        for (int i = 0; i < Game.CAMPAIGN_FUNDS.length; i++) {
            Btn b = add(new Btn(C_FUNDS + i, "$" + Ui.money(Game.CAMPAIGN_FUNDS[i])).set(px1 + u * 2 + i * (bw + u), py + u * 36, bw, bh));
            b.selected = cFunds == i;
        }
        float qbw = (pw - u * 8) / 5;
        for (int i = 0; i < Game.QUICK_BUDGETS.length; i++) {
            Btn b = add(new Btn(Q_BUDGET + i, "$" + Game.QUICK_BUDGETS[i] / 1000 + "k").set(px2 + u * 2 + i * (qbw + u), py + u * 36, qbw, bh));
            b.selected = qBudget == i;
        }
        float sbw = (pw - u * 5) / 2;
        for (int i = 0; i < 2; i++) {
            boolean confirm = confirmOverwrite && cSide == i;
            Btn c = add(new Btn(C_SIDE + i, confirm ? "Overwrite save?" : (i == Scenario.ATTACKER ? "Start as Attacker" : "Start as Defender"))
                    .set(px1 + u * 2 + i * (sbw + u), h - u * 26, sbw, u * 11));
            c.sub = confirm ? "tap again to replace it" : (i == Scenario.ATTACKER ? "land and take the planet" : "hold the planet");
            c.color = confirm ? 0xFF8F3A2E : (i == Scenario.ATTACKER ? 0xFF8F4A2E : 0xFF2E7D4F);
        }
        add(new Btn(QUICK, "Build Force").set(px2 + u * 2, h - u * 26, pw - u * 4, u * 11)).color = 0xFF2E6D8F;
        add(new Btn(BACK, "Back").set(u * 4, h - u * 12, u * 24, u * 9));
    }

    private void relayout() {
        w = -1;
        game.setScreen(this);
    }

    protected void onClick(Btn b) {
        int id = b.id;
        if (id == BACK) game.toMenu();
        else if (id >= C_SIDE && id < C_SIDE + 2) {
            int side = id - C_SIDE;
            if (game.hasSave() && !(confirmOverwrite && cSide == side)) {
                confirmOverwrite = true;
                cSide = side;
                relayout();
            } else game.newCampaign(cDiff, Game.CAMPAIGN_FUNDS[cFunds], side);
            return;
        } else if (id == QUICK) game.quickBattle(qDiff, Game.QUICK_BUDGETS[qBudget]);
        else if (id >= C_DIFF && id < C_DIFF + 3) cDiff = id - C_DIFF;
        else if (id >= C_FUNDS && id < C_FUNDS + 3) cFunds = id - C_FUNDS;
        else if (id >= Q_DIFF && id < Q_DIFF + 3) qDiff = id - Q_DIFF;
        else if (id >= Q_BUDGET && id < Q_BUDGET + 5) qBudget = id - Q_BUDGET;
        if (id != BACK && id != CAMPAIGN && id != QUICK) relayout();
    }

    public boolean back() {
        game.toMenu();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Ui.title(g, "NEW GAME", w / 2f, h * 0.08f, u * 7);
        float pw = (w - u * 12) / 2, px1 = u * 4, px2 = px1 + pw + u * 4, py = h * 0.17f, ph = h - py - u * 14;
        Ui.panel(g, px1, py, pw, ph, u * 2);
        Ui.panel(g, px2, py, pw, ph, u * 2);
        g.text("Campaign", px1 + u * 3, py + u * 5, u * 4.6f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text("A fight for a planet, battle by battle. Damage, losses and money carry over.", px1 + u * 3, py + u * 10, u * 2.5f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("Difficulty", px1 + u * 3, py + u * 14, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Starting funds", px1 + u * 3, py + u * 34, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Quick Battle", px2 + u * 3, py + u * 5, u * 4.6f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        g.text("Spend a budget on a force and fight one battle.", px2 + u * 3, py + u * 10, u * 2.5f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("Difficulty", px2 + u * 3, py + u * 14, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Budget", px2 + u * 3, py + u * 34, u * 2.6f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        String[] diffText = {"Smaller enemy forces; the computer takes more risks.",
                "Even fights; the computer plays carefully.",
                "Bigger enemy forces that focus fire and protect themselves."};
        g.text(diffText[cDiff], px1 + u * 3, py + u * 28, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text(diffText[qDiff], px2 + u * 3, py + u * 28, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("A medium mech costs about $2,200 to $2,600; a light vehicle about $500.", px1 + u * 3, py + u * 48, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        g.text("The defender starts with the upper hand.", px1 + u * 3, py + u * 52, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        renderButtons(g);
    }
}
