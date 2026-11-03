package com.meanmutton.mechgame.core;

/**
 * Mercenary contract board: the jobs on offer on this planet, each showing who is hiring (emblem and name in
 * their colors), who you would be fighting, the scenario and its limits, the pay, the threat, and what it does to
 * your standing. Beside it, your standing with every faction. Take a job, then set up your deployment at HQ.
 */
public final class ContractsScreen extends Screen {
    private static final int BACK = 1, TRAVEL = 2, TAKE = 100;
    private final Company c;
    private float t;

    public ContractsScreen(Game game) {
        super(game);
        c = game.company;
    }

    private float listW() {
        return w * 0.62f;
    }

    private float cardH() {
        return Math.min(u * 18, (h - h * 0.17f - u * 15) / 4f);
    }

    protected void layout() {
        buttons.clear();
        float lx = u * 3, ly = h * 0.17f, lw = listW() - u * 3, chh = cardH();
        for (int i = 0; i < c.contracts.size(); i++) {
            boolean taken = c.contract == i;
            Btn b = add(new Btn(TAKE + i, taken ? "Accepted" : "Take Job").set(lx + lw - u * 22, ly + i * chh + chh - u * 9.5f, u * 20, u * 7.5f));
            b.color = taken ? 0xFF2E7D4F : 0xFF2E6D8F;
            b.textSize = u * 2.6f;
        }
        add(new Btn(BACK, "Back to HQ").set(u * 3, h - u * 12, u * 30, u * 9));
        int cost = Merc.travelCost(c);
        Btn tr = add(new Btn(TRAVEL, "Travel").set(w - u * 41, h - u * 12, u * 38, u * 9));
        tr.sub = "to another planet, $" + Ui.money(cost);
        tr.color = 0xFF5A4A2A;
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
    }

    protected void onClick(Btn b) {
        if (b.id == BACK) game.toHq();
        else if (b.id == TRAVEL) game.setScreen(new TravelScreen(game));
        else if (b.id >= TAKE) game.acceptContract(b.id - TAKE);
    }

    public boolean back() {
        game.toHq();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        g.text("CONTRACTS", u * 3, h * 0.055f, u * 5.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        Merc.Planet p = c.planet;
        g.text(p.name + "  ·  " + HexMap.BIOME_NAMES[p.biome], u * 3, h * 0.11f, u * 2.8f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        float fx = u * 3 + g.textWidth(p.name + "  ·  " + HexMap.BIOME_NAMES[p.biome], u * 2.8f, true) + u * 4;
        g.text("Fighting here:", fx, h * 0.11f, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        fx += g.textWidth("Fighting here:", u * 2.3f, false) + u * 1.5f;
        for (Faction f : Faction.ALL) if (p.has(f.id)) fx += Emblem.label(g, f.id, fx, h * 0.11f, u * 2.3f, false) + u * 2.5f;
        g.text("$" + Ui.money(c.cash), w - u * 3, h * 0.065f, u * 6.5f, Col.MONEY, Gfx.ALIGN_RIGHT, true);

        float lx = u * 3, ly = h * 0.17f, lw = listW() - u * 3, chh = cardH();
        if (c.contracts.isEmpty()) {
            Ui.wrap(g, "Nobody on " + p.name + " will hire you. Travel to another planet, where your name means something else.",
                    lx, ly + u * 6, u * 3, lw, Col.ACCENT);
        }
        for (int i = 0; i < c.contracts.size(); i++) {
            Merc.Contract k = c.contracts.get(i);
            Scenario sc = Scenario.get(k.scenario);
            float y = ly + i * chh;
            Ui.panel(g, lx, y, lw, chh - u, u * 1.5f);
            if (c.contract == i) g.strokeRoundRect(lx, y, lw, chh - u, u * 1.5f, u * 0.5f, Col.GOOD);
            float r1 = y + chh * 0.2f;
            float x = lx + u * 2;
            x += Emblem.label(g, k.employer, x, r1, u * 2.8f, true) + u * 1.5f;
            String verb = k.role == Scenario.ATTACKER ? "hires you to attack" : "hires you to defend against";
            g.text(verb, x, r1, u * 2.4f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            x += g.textWidth(verb, u * 2.4f, false) + u * 1.5f;
            Emblem.label(g, k.target, x, r1, u * 2.8f, true);
            g.text(Ui.fit(g, sc.name + ".  " + sc.limits() + ".", u * 2.3f, lw - u * 26), lx + u * 2, y + chh * 0.44f, u * 2.3f,
                    Col.ACCENT, Gfx.ALIGN_LEFT, true);
            StringBuilder stars = new StringBuilder();
            for (int s = 0; s < 3; s++) stars.append(s < k.threat ? "★" : "☆");
            g.text("Threat " + stars + "   Pay $" + Ui.money(k.reward) + "   Enemy force about $" + Ui.money(k.enemyBudget),
                    lx + u * 2, y + chh * 0.64f, u * 2.3f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            int[] win = Merc.repChanges(k, Battle.RESULT_WIN);
            String st = "Standing if you win: " + Faction.ALL[k.employer].shortName + " " + signed(win[k.employer]) + ", "
                    + Faction.ALL[k.target].shortName + " " + signed(win[k.target]) + " (and their allies)";
            g.text(Ui.fit(g, st, u * 2.1f, lw - u * 26), lx + u * 2, y + chh * 0.83f, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        }
        renderStanding(g, listW() + u * 2, h * 0.17f, w - listW() - u * 5, h - h * 0.17f - u * 15);
        renderButtons(g);
    }

    static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    /** Standing with every faction: emblem, name, tier and a bar; factions on this planet are marked. */
    private void renderStanding(Gfx g, float x, float y, float w2, float h2) {
        Ui.panel(g, x, y, w2, h2, u * 1.5f);
        g.text("Your standing", x + u * 2, y + u * 3.5f, u * 2.8f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        float rowH = (h2 - u * 8) / Faction.COUNT;
        for (Faction f : Faction.ALL) {
            float ry = y + u * 7.5f + f.id * rowH;
            int rep = c.rep[f.id], tier = Merc.tier(rep);
            boolean here = c.planet.has(f.id);
            Emblem.draw(g, f.id, x + u * 3.5f, ry, u * 1.7f);
            g.text(f.shortName, x + u * 6.5f, ry - rowH * 0.18f, u * 2.3f, here ? f.color : Col.alpha(f.color, 0.55f), Gfx.ALIGN_LEFT, true);
            g.text(Merc.TIERS[tier] + (here ? "" : "  (not here)"), x + w2 - u * 2, ry - rowH * 0.18f, u * 2f, Merc.TIER_COLORS[tier],
                    Gfx.ALIGN_RIGHT, false);
            float bx = x + u * 6.5f, bw = w2 - u * 8.5f, by = ry + rowH * 0.2f;
            g.fillRoundRect(bx, by, bw, u * 1f, u * 0.5f, 0xFF0A0F18);
            float mid = bx + bw / 2, len = bw / 2 * Math.abs(rep) / 100f;
            g.fillRect(rep >= 0 ? mid : mid - len, by, len, u * 1f, Merc.TIER_COLORS[tier]);
            g.fillRect(mid - u * 0.1f, by - u * 0.3f, u * 0.2f, u * 1.6f, Col.TEXT_DIM);
        }
    }
}
