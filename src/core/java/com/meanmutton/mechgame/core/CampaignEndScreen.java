package com.meanmutton.mechgame.core;

/** The fight for a planet is decided. Move the company on to a new planet, or go back to the menu. */
public final class CampaignEndScreen extends Screen {
    private static final int MENU = 1, ATTACK = 2, DEFEND = 3;
    private float t;

    public CampaignEndScreen(Game game) {
        super(game);
    }

    private boolean won() {
        Company c = game.company;
        return c.over == (c.side == Scenario.ATTACKER ? Scenario.OUT_ATTACKER : Scenario.OUT_DEFENDER);
    }

    protected void layout() {
        buttons.clear();
        float bw = u * 46;
        Btn a = add(new Btn(ATTACK, "Attack a New Planet").set(w / 2f - bw - u * 2, h * 0.66f, bw, u * 11));
        a.sub = "keep your company, units and money";
        a.color = 0xFF8F4A2E;
        Btn d = add(new Btn(DEFEND, "Defend a New Planet").set(w / 2f + u * 2, h * 0.66f, bw, u * 11));
        d.sub = "keep your company, units and money";
        d.color = 0xFF2E6D8F;
        add(new Btn(MENU, "Main Menu").set(u * 3, h - u * 12, u * 28, u * 9));
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
    }

    protected void onClick(Btn b) {
        if (b.id == ATTACK) game.nextPlanet(Scenario.ATTACKER);
        else if (b.id == DEFEND) game.nextPlanet(Scenario.DEFENDER);
        else game.toMenu();
    }

    public boolean back() {
        game.toMenu();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Company c = game.company;
        boolean won = won();
        Ui.title(g, won ? "CAMPAIGN WON" : "CAMPAIGN LOST", w / 2f, h * 0.16f, u * 11);
        String what;
        if (c.over == Scenario.OUT_ATTACKER) what = c.side == Scenario.ATTACKER ? "You stormed the defender's base. The planet is yours."
                : "The attacker overran your base. The planet is lost.";
        else what = c.side == Scenario.DEFENDER ? "The attacker's last forces fled to their dropships and left the planet."
                : "Driven back to your dropships, you had to abandon the planet.";
        g.text(what, w / 2f, h * 0.29f, u * 3.2f, won ? Col.GOOD : Col.BAD, Gfx.ALIGN_CENTER, true);
        int worth = 0;
        for (Unit un : c.roster) worth += un.value();
        g.text("Record " + c.wins + "W " + c.losses + "L over " + (c.mission - 1) + (c.mission - 1 == 1 ? " contract" : " contracts")
                        + (c.planets > 0 ? " (" + (c.planets + 1) + " planets)" : ""),
                w / 2f, h * 0.38f, u * 2.8f, Col.TEXT, Gfx.ALIGN_CENTER, false);
        g.text("Cash $" + Ui.money(c.cash) + "   |   " + c.roster.size() + (c.roster.size() == 1 ? " unit" : " units") + " worth $" + Ui.money(worth),
                w / 2f, h * 0.44f, u * 2.8f, Col.MONEY, Gfx.ALIGN_CENTER, false);
        Ui.frontTrack(g, w / 2f - u * 50, h * 0.51f, u * 100, u, c.over == Scenario.OUT_ATTACKER ? Scenario.FRONT_BASE : Scenario.FRONT_MIN, c.side);
        g.text("Your company can fight on: pick a side for the next planet. A new enemy company meets you there.",
                w / 2f, h * 0.62f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        renderButtons(g);
    }
}
