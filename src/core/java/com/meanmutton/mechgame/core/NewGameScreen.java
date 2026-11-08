package com.meanmutton.mechgame.core;

/** Choose a campaign, a mercenary career or a one-off quick battle. */
public final class NewGameScreen extends Screen {
    private static final int BACK = 1, QUICK = 3, MERC = 4, HOTSEAT = 5, C_VS = 6, ONLINE = 7, C_DIFF = 10, C_FUNDS = 20, Q_DIFF = 30, Q_BUDGET = 40, C_SIDE = 50,
            M_DIFF = 60, M_FUNDS = 70;
    private static final String[] DIFF_TEXT = {"Smaller enemy forces; the computer takes more risks.",
            "Even fights; the computer plays carefully.",
            "Bigger enemy forces that focus fire and protect themselves."};
    private int cDiff = 1, cFunds = 1, mDiff = 1, mFunds = 1, qDiff = 1, qBudget = 2, cSide = Scenario.ATTACKER;
    private boolean confirmOverwrite, confirmMerc, confirmQuick, confirmHotseat;
    /** Campaign against: 0 the computer, 1 a second player on this device, 2 a friend online. */
    private int cVs;
    private static final String[] VS = {"vs Computer", "vs Player 2", "vs Friend Online"};

    public NewGameScreen(Game game) {
        super(game);
    }

    private float pw() {
        return (w - u * 16) / 3;
    }

    private float px(int panel) {
        return u * 4 + panel * (pw() + u * 4);
    }

    private float py() {
        return h * 0.17f;
    }

    private Btn choice(int id, String label, float x, float y, float bw, boolean selected) {
        Btn b = add(new Btn(id, label).set(x, y, bw, u * 7));
        b.selected = selected;
        b.textSize = u * 2.5f;
        return b;
    }

    protected void layout() {
        buttons.clear();
        float pw = pw(), py = py();
        float bw = (pw - u * 6) / 3;
        boolean cPvp = cVs != 0;
        Btn vs = add(new Btn(C_VS, VS[cVs]).set(px(0) + pw - u * 25, py + u * 0.9f, u * 23, u * 5.2f));
        vs.textSize = u * 2.4f;
        vs.color = cVs == 2 ? 0xFF2E7D6F : (cPvp ? 0xFF6A3A8F : 0xFF2A3346);
        for (int i = 0; i < 3; i++) {
            Btn cd = choice(C_DIFF + i, Company.DIFFICULTY[i], px(0) + u * 2 + i * (bw + u), py + u * 17, bw, cDiff == i && !cPvp);
            cd.enabled = !cPvp;   // difficulty is the computer's; two players don't need it
            choice(M_DIFF + i, Company.DIFFICULTY[i], px(1) + u * 2 + i * (bw + u), py + u * 17, bw, mDiff == i);
            choice(Q_DIFF + i, Company.DIFFICULTY[i], px(2) + u * 2 + i * (bw + u), py + u * 17, bw, qDiff == i);
        }
        for (int i = 0; i < Game.CAMPAIGN_FUNDS.length; i++) {
            String label = "$" + Ui.money(Game.CAMPAIGN_FUNDS[i]);
            choice(C_FUNDS + i, label, px(0) + u * 2 + i * (bw + u), py + u * 34, bw, cFunds == i);
            choice(M_FUNDS + i, label, px(1) + u * 2 + i * (bw + u), py + u * 34, bw, mFunds == i);
        }
        float qbw = (pw - u * 8) / 5;
        for (int i = 0; i < Game.QUICK_BUDGETS.length; i++)
            choice(Q_BUDGET + i, "$" + Game.QUICK_BUDGETS[i] / 1000 + "k", px(2) + u * 2 + i * (qbw + u), py + u * 34, qbw, qBudget == i);

        float bx = u * 2, bwid = pw - u * 4, bh = u * 9;
        for (int i = 0; i < 2; i++) {
            boolean confirm = confirmOverwrite && cSide == i;
            Btn c = add(new Btn(C_SIDE + i, confirm ? "Overwrite save?" : (i == Scenario.ATTACKER ? "Start as Attacker" : "Start as Defender"))
                    .set(px(0) + bx, h - u * (i == Scenario.ATTACKER ? 35.5f : 25.5f), bwid, bh));
            c.sub = confirm ? "tap again to replace it"
                    : cVs == 2 ? (i == Scenario.ATTACKER ? "you attack; your friend defends, online" : "you defend; your friend attacks, online")
                    : cPvp ? (i == Scenario.ATTACKER ? "Player 1 attacks, Player 2 defends" : "Player 1 defends, Player 2 attacks")
                    : (i == Scenario.ATTACKER ? "land and take the planet" : "hold the planet; the defender starts ahead");
            c.color = confirm ? 0xFF8F3A2E : (i == Scenario.ATTACKER ? 0xFF8F4A2E : 0xFF2E7D4F);
        }
        Btn m = add(new Btn(MERC, confirmMerc ? "Overwrite save?" : "Start as Mercenary").set(px(1) + bx, h - u * 25.5f, bwid, bh));
        m.sub = confirmMerc ? "tap again to replace it" : "find work on a contested planet";
        m.color = confirmMerc ? 0xFF8F3A2E : 0xFF5A4A2A;
        // three choices when this build can play online, two otherwise
        boolean online = Online.available();
        int n = online ? 3 : 2;
        float part = (bwid - u * (n - 1)) / n;
        Btn qb = add(new Btn(QUICK, confirmQuick ? "Abandon battle?" : "vs Computer").set(px(2) + bx, h - u * 25.5f, part, bh));
        qb.color = confirmQuick ? 0xFF8F3A2E : 0xFF2E6D8F;
        qb.sub = confirmQuick ? "tap again to start" : "build your force";
        Btn hb = add(new Btn(HOTSEAT, confirmHotseat ? "Abandon battle?" : "Two Players").set(px(2) + bx + part + u, h - u * 25.5f, part, bh));
        hb.color = confirmHotseat ? 0xFF8F3A2E : 0xFF6A3A8F;
        hb.sub = confirmHotseat ? "tap again to start" : online ? "one device" : "one device, take turns";
        qb.textSize = hb.textSize = u * (online ? 2.6f : 3f);
        if (online) {
            Btn ob = add(new Btn(ONLINE, "Online").set(px(2) + bx + (part + u) * 2, h - u * 25.5f, part, bh));
            ob.color = 0xFF2E7D6F;
            ob.sub = "play a friend";
            ob.textSize = u * 2.6f;
        }
        add(new Btn(BACK, "Back").set(u * 4, h - u * 12, u * 24, u * 9));
    }

    private void relayout() {
        w = -1;
        game.setScreen(this);
    }

    protected void onClick(Btn b) {
        int id = b.id;
        if (id == BACK) {
            game.toMenu();
            return;
        }
        if (id >= C_SIDE && id < C_SIDE + 2) {
            int side = id - C_SIDE;
            if ((game.hasSave() || game.hasBattle()) && !(confirmOverwrite && cSide == side)) {
                confirmOverwrite = true;
                confirmMerc = false;
                cSide = side;
                relayout();
            } else if (cVs == 2) game.newOnlineCampaign(Game.CAMPAIGN_FUNDS[cFunds], side);
            else if (cVs == 1) game.newPvpCampaign(Game.CAMPAIGN_FUNDS[cFunds], side);
            else game.newCampaign(cDiff, Game.CAMPAIGN_FUNDS[cFunds], side);
            return;
        }
        if (id == MERC) {
            if ((game.hasSave() || game.hasBattle()) && !confirmMerc) {
                confirmMerc = true;
                confirmOverwrite = false;
                relayout();
            } else game.newMercenary(mDiff, Game.CAMPAIGN_FUNDS[mFunds]);
            return;
        }
        if (id == QUICK) {
            if (game.hasBattle() && !confirmQuick) {
                confirmQuick = true;
                confirmHotseat = false;
                relayout();
                return;
            }
            game.quickBattle(qDiff, Game.QUICK_BUDGETS[qBudget]);
            return;
        }
        if (id == HOTSEAT) {
            if (game.hasBattle() && !confirmHotseat) {
                confirmHotseat = true;
                confirmQuick = false;
                relayout();
                return;
            }
            game.newHotseat(Game.QUICK_BUDGETS[qBudget]);
            return;
        }
        if (id == ONLINE) {
            game.setScreen(new OnlineScreen(game, Game.QUICK_BUDGETS[qBudget]));
            return;
        }
        if (id == C_VS) cVs = (cVs + 1) % (Online.available() ? 3 : 2);
        else if (id >= C_DIFF && id < C_DIFF + 3) cDiff = id - C_DIFF;
        else if (id >= C_FUNDS && id < C_FUNDS + 3) cFunds = id - C_FUNDS;
        else if (id >= M_DIFF && id < M_DIFF + 3) mDiff = id - M_DIFF;
        else if (id >= M_FUNDS && id < M_FUNDS + 3) mFunds = id - M_FUNDS;
        else if (id >= Q_DIFF && id < Q_DIFF + 3) qDiff = id - Q_DIFF;
        else if (id >= Q_BUDGET && id < Q_BUDGET + 5) qBudget = id - Q_BUDGET;
        relayout();
    }

    public boolean back() {
        game.toMenu();
        return true;
    }

    private void panel(Gfx g, int i, String title, String desc, int diff, String second) {
        float x = px(i), py = py(), pw = pw(), ph = h - py - u * 14;
        Ui.panel(g, x, py, pw, ph, u * 2);
        g.text(title, x + u * 3, py + u * 4.2f, u * 4.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        Ui.wrap(g, desc, x + u * 3, py + u * 8f, u * 2.1f, pw - u * 6, Col.TEXT_DIM);
        g.text("Difficulty", x + u * 3, py + u * 15f, u * 2.5f, i == 0 && cVs != 0 ? Col.TEXT_DIM : Col.TEXT, Gfx.ALIGN_LEFT, true);
        String dt = i == 0 && cVs == 2 ? "A friend on another device runs the rival company; you host the campaign."
                : i == 0 && cVs == 1 ? "Two players on this device take turns at their HQs and fight each battle." : DIFF_TEXT[diff];
        Ui.wrap(g, dt, x + u * 3, py + u * 27, u * 2.1f, pw - u * 6, Col.TEXT_DIM);
        g.text(second, x + u * 3, py + u * 32.5f, u * 2.5f, Col.TEXT, Gfx.ALIGN_LEFT, true);
    }

    public void render(Gfx g) {
        background(g);
        Ui.title(g, "NEW GAME", w / 2f, h * 0.08f, u * 7);
        panel(g, 0, "Campaign", "A fight for a planet, battle by battle. Damage, losses and money carry over.", cDiff, "Starting funds");
        panel(g, 1, "Mercenary", "Go planet to planet taking single contracts for pay. Who you fight decides who will hire you and sell to you.",
                mDiff, "Starting funds");
        panel(g, 2, "Quick Battle", "Spend a budget on a force and fight one battle, against the computer or a friend.", qDiff, "Budget");
        float py = py(), pw = pw();
        Ui.wrap(g, "Eight factions, their own designs and makes, and rare prototypes for allies who trust you.",
                px(1) + u * 3, py + u * 44, u * 2.1f, pw - u * 6, Col.TEXT_DIM);
        Ui.wrap(g, "A medium mech costs about $2,200 to $2,600; a light vehicle about $500.",
                px(2) + u * 3, py + u * 44, u * 2.1f, pw - u * 6, Col.TEXT_DIM);
        renderButtons(g);
    }
}
