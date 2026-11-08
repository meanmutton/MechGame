package com.meanmutton.mechgame.core;

/**
 * Online quick battle: host or join a game, then the waiting in between (for a friend to join, for both forces to
 * be ready). Once both players are in, the HQ takes over; once both forces are in, the battle does.
 */
public final class OnlineScreen extends Screen {
    private static final int BACK = 1, HOST = 2, JOIN = 3;
    private final int budget;
    private int laidOutPhase = -2;
    private String note;

    /** The start: host or join, the host's games using this budget. */
    public OnlineScreen(Game game, int budget) {
        super(game);
        this.budget = budget;
    }

    /** Waiting on the online game in progress. */
    public OnlineScreen(Game game) {
        this(game, 0);
    }

    private int phase() {
        return game.online == null ? -1 : game.online.phase;
    }

    protected void layout() {
        buttons.clear();
        laidOutPhase = phase();
        float bw = u * 60, bh = u * 12, x = (w - bw) / 2;
        if (game.online == null) {
            Btn hb = add(new Btn(HOST, "Host a game").set(x, h * 0.46f, bw, bh));
            hb.color = 0xFF2E7D4F;
            hb.sub = "get a code for your friend; $" + Ui.money(budget) + " each";
            Btn jb = add(new Btn(JOIN, "Join a game").set(x, h * 0.46f + bh + u * 3, bw, bh));
            jb.color = 0xFF2E6D8F;
            jb.sub = "type in your friend's code";
            add(new Btn(BACK, "Back").set(u * 4, h - u * 12, u * 24, u * 9));
            return;
        }
        add(new Btn(BACK, game.online.phase == Online.FAILED || game.online.phase == Online.OVER ? "Back" : "Leave").set(u * 4, h - u * 12, u * 24, u * 9));
    }

    public void update(float dt) {
        super.update(dt);
        if (phase() != laidOutPhase) {
            int ow = w;
            w = -1;
            resize(ow, h);
        }
    }

    protected void onClick(Btn b) {
        if (b.id == BACK) {
            game.leaveOnline();
            game.setScreen(new NewGameScreen(game));
            return;
        }
        if (b.id == HOST) {
            game.hostOnline(budget);
            return;
        }
        if (b.id == JOIN) {
            game.platform.promptText("Game code", "", new Platform.TextCallback() {
                public void done(final String text) {
                    game.post(new Runnable() {
                        public void run() {
                            String c = Text.upper(text == null ? "" : text.trim()).replace(" ", "");
                            if (c.length() != 6) {
                                note = "A game code is six letters.";
                                return;
                            }
                            for (int i = 0; i < c.length(); i++) {
                                if (c.charAt(i) < 'A' || c.charAt(i) > 'Z') {
                                    note = "A game code is six letters.";
                                    return;
                                }
                            }
                            note = null;
                            game.joinOnline(c);
                        }
                    });
                }
            });
        }
    }

    public boolean back() {
        onClick(new Btn(BACK, ""));
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Ui.title(g, "ONLINE BATTLE", w / 2f, h * 0.1f, u * 7);
        Online o = game.online;
        float tx = w / 2f - u * 60, tw = u * 120;
        if (o == null) {
            Ui.wrap(g, "Play a friend on another phone, tablet or computer. One of you hosts and gets a six-letter code; the other "
                    + "joins with it. You both build a force with the host's budget at the same time, then fight it out, each on "
                    + "your own screen.", tx, h * 0.2f, u * 2.8f, tw, Col.TEXT);
            if (note != null) g.text(note, w / 2f, h * 0.4f, u * 3, Col.BAD, Gfx.ALIGN_CENTER, true);
            renderButtons(g);
            return;
        }
        String dots = ".";
        for (int i = 0; i < (int) (game.time * 2) % 3; i++) dots += ".";
        String head, text = null;
        int color = Col.ACCENT;
        switch (o.phase) {
            case Online.CONNECTING:
                head = o.host ? "Opening a game" + dots : "Joining game " + spaced(o.code) + dots;
                break;
            case Online.HOSTING:
                head = "Your game code";
                text = "Tell your friend to choose New Game, Quick Battle, Online, then Join, and type in this code. "
                        + "The battle uses your budget: $" + Ui.money(o.budget) + " each.";
                break;
            case Online.JOINING:
                head = "Joined. Waiting for Player 1" + dots;
                break;
            case Online.BUILDING:
                head = "Building forces" + dots;
                break;
            case Online.READY:
                head = "Your force is ready";
                text = "Waiting for Player " + (2 - o.team) + " to finish building theirs" + dots;
                break;
            case Online.FAILED:
                head = "Can't play online right now";
                text = o.problem;
                color = Col.BAD;
                break;
            default:
                head = "The online game has ended.";
        }
        g.text(head, w / 2f, h * 0.27f, u * 4.5f, color, Gfx.ALIGN_CENTER, true);
        if (o.phase == Online.HOSTING && o.code != null) {
            Ui.panel(g, w / 2f - u * 40, h * 0.34f, u * 80, u * 20, u * 2);
            g.text(spaced(o.code), w / 2f, h * 0.34f + u * 10, u * 12, 0xFFFFFFFF, Gfx.ALIGN_CENTER, true);
            g.text("Waiting for your friend to join" + dots, w / 2f, h * 0.34f + u * 26, u * 3, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
            if (text != null) Ui.wrap(g, text, tx, h * 0.34f + u * 33, u * 2.6f, tw, Col.TEXT_DIM);
        } else if (text != null) {
            Ui.wrap(g, text, tx, h * 0.38f, u * 3, tw, Col.TEXT);
        }
        if (o.phase != Online.FAILED && o.phase != Online.HOSTING && !o.linkUp()) {
            g.text("Connecting to the server" + dots, w / 2f, h * 0.62f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        }
        renderButtons(g);
    }

    private static String spaced(String code) {
        if (code == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < code.length(); i++) b.append(i > 0 ? " " : "").append(code.charAt(i));
        return b.toString();
    }
}
