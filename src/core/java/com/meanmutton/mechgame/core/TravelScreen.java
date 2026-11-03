package com.meanmutton.mechgame.core;

import java.util.Random;

/** Pick the next planet: each shows who is fighting there and how they feel about you. */
public final class TravelScreen extends Screen {
    private static final int BACK = 1, GO = 100;
    private final Company c;
    private final Merc.Planet[] options = new Merc.Planet[3];

    public TravelScreen(Game game) {
        super(game);
        c = game.company;
        // the same destinations while you look (they change after each contract)
        Random r = new Random(c.seed ^ 0x7A5E11L);
        for (int i = 0; i < options.length; i++) options[i] = Merc.planet(r);
    }

    protected void layout() {
        buttons.clear();
        float cw = (w - u * 12) / 3, top = h * 0.2f, ch = h - top - u * 16;
        int cost = Merc.travelCost(c);
        for (int i = 0; i < options.length; i++) {
            Btn b = add(new Btn(GO + i, "Travel for $" + Ui.money(cost)).set(u * 3 + i * (cw + u * 3) + u * 2, top + ch - u * 10, cw - u * 4, u * 8));
            b.enabled = c.cash >= cost;
            b.color = 0xFF2E7D4F;
        }
        add(new Btn(BACK, "Stay").set(u * 3, h - u * 12, u * 26, u * 9));
    }

    protected void onClick(Btn b) {
        if (b.id == BACK) game.setScreen(new ContractsScreen(game));
        else if (b.id >= GO) game.travel(options[b.id - GO]);
    }

    public boolean back() {
        game.setScreen(new ContractsScreen(game));
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Ui.title(g, "TRAVEL", w / 2f, h * 0.07f, u * 6);
        g.text("Leaving " + c.planet.name + ". Choose where to take the company next; contracts and markets depend on who is fighting there.",
                w / 2f, h * 0.14f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        float cw = (w - u * 12) / 3, top = h * 0.2f, ch = h - top - u * 16;
        for (int i = 0; i < options.length; i++) {
            Merc.Planet p = options[i];
            float x = u * 3 + i * (cw + u * 3);
            Ui.panel(g, x, top, cw, ch, u * 2);
            g.text(p.name, x + u * 2.5f, top + u * 5, u * 3.6f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            g.text(HexMap.BIOME_NAMES[p.biome] + " world", x + u * 2.5f, top + u * 9.5f, u * 2.4f, Col.TEXT, Gfx.ALIGN_LEFT, false);
            g.text("Fighting here:", x + u * 2.5f, top + u * 14, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            float y = top + u * 18.5f;
            int hires = 0, trades = 0;
            for (Faction f : Faction.ALL) {
                if (!p.has(f.id)) continue;
                int tier = Merc.tier(c.rep[f.id]);
                Emblem.label(g, f.id, x + u * 2.5f, y, u * 2.6f, true);
                g.text(Merc.TIERS[tier], x + cw - u * 2.5f, y, u * 2.2f, Merc.TIER_COLORS[tier], Gfx.ALIGN_RIGHT, false);
                if (Merc.hires(c.rep[f.id])) hires++;
                if (Merc.trades(c.rep[f.id])) trades++;
                y += u * 5;
            }
            String note = hires == 0 ? "Nobody here will hire you." : hires + " of them will hire you; " + trades + " will sell to you.";
            Ui.wrap(g, note, x + u * 2.5f, top + ch - u * 15, u * 2.2f, cw - u * 5, hires == 0 ? Col.BAD : Col.TEXT_DIM);
        }
        renderButtons(g);
    }
}
