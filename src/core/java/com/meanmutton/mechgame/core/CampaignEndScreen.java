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
        boolean pvp = game.company.pvp;
        Btn a = add(new Btn(ATTACK, pvp ? "Next: Player 1 Attacks" : "Attack a New Planet").set(w / 2f - bw - u * 2, h * 0.66f, bw, u * 11));
        a.sub = pvp ? "Player 2 defends; both keep their companies" : "keep your company, units and money";
        a.color = 0xFF8F4A2E;
        Btn d = add(new Btn(DEFEND, pvp ? "Next: Player 1 Defends" : "Defend a New Planet").set(w / 2f + u * 2, h * 0.66f, bw, u * 11));
        d.sub = pvp ? "Player 2 attacks; both keep their companies" : "keep your company, units and money";
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
        if (c.pvp && c.rival != null) {
            renderPvp(g, c);
            return;
        }
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

    /** Two-player campaign: who took the planet, and both companies as they stand. */
    private void renderPvp(Gfx g, Company c) {
        int winner = won() ? 0 : 1;
        Ui.title(g, "PLAYER " + (winner + 1) + " WINS THE PLANET", w / 2f, h * 0.15f, u * 9);
        String what = c.over == Scenario.OUT_ATTACKER ? "The attacker stormed the defender's base and took the planet."
                : "The attacker's last forces fled to their dropships and left the planet.";
        g.text(what, w / 2f, h * 0.27f, u * 3f, UnitArt.teamColor(winner), Gfx.ALIGN_CENTER, true);
        for (int seat = 0; seat < 2; seat++) {
            Company co = seat == 0 ? c : c.rival;
            int worth = 0;
            for (Unit un : co.roster) worth += un.value();
            float x = w / 2f + (seat == 0 ? -u * 30 : u * 30);
            g.text("Player " + (seat + 1) + " (" + Text.lower(Scenario.SIDE_NAMES[co.side]) + ")", x, h * 0.36f, u * 3, UnitArt.teamColor(seat), Gfx.ALIGN_CENTER, true);
            g.text("Record " + co.wins + "W " + co.losses + "L", x, h * 0.42f, u * 2.5f, Col.TEXT, Gfx.ALIGN_CENTER, false);
            g.text("Cash $" + Ui.money(co.cash) + ", " + co.roster.size() + " units worth $" + Ui.money(worth), x, h * 0.47f, u * 2.5f, Col.MONEY,
                    Gfx.ALIGN_CENTER, false);
        }
        Ui.frontTrack(g, w / 2f - u * 50, h * 0.53f, u * 100, u, c.over == Scenario.OUT_ATTACKER ? Scenario.FRONT_BASE : Scenario.FRONT_MIN, c.side);
        g.text("Fight on: both companies keep their units and money. Pick who attacks the next planet.",
                w / 2f, h * 0.62f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        renderButtons(g);
    }
}
