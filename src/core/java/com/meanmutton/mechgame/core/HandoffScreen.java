package com.meanmutton.mechgame.core;

/**
 * Hotseat: an opaque screen between the two players, so the one taking the device sees nothing the other was
 * doing until they say they are ready.
 */
public final class HandoffScreen extends Screen {
    private static final int GO = 1, MENU = 2;
    private final int seat;
    private final String title, text;
    private final Screen next;

    public HandoffScreen(Game game, int seat, String title, String text, Screen next) {
        super(game);
        this.seat = seat;
        this.title = title;
        this.text = text;
        this.next = next;
    }

    protected void layout() {
        buttons.clear();
        Btn go = add(new Btn(GO, "I'm Player " + (seat + 1) + ": continue").set(w / 2f - u * 28, h * 0.66f, u * 56, u * 10));
        go.color = 0xFF2E7D4F;
        add(new Btn(MENU, "Main Menu").set(u * 3, h - u * 12, u * 26, u * 9));
    }

    protected void onClick(Btn b) {
        if (b.id == GO) game.setScreen(next);
        else game.toMenu();
    }

    public boolean back() {
        return true;   // nothing behind this screen should show by accident
    }

    public void render(Gfx g) {
        background(g);
        int tc = UnitArt.teamColor(seat);
        Ui.title(g, "PLAYER " + (seat + 1), w / 2f, h * 0.2f, u * 9);
        g.text(title, w / 2f, h * 0.31f, u * 4, tc, Gfx.ALIGN_CENTER, true);
        g.fillRect(w / 2f - u * 30, h * 0.37f, u * 60, u * 0.8f, tc);
        Ui.wrap(g, text, w / 2f - u * 55, h * 0.45f, u * 3, u * 110, Col.TEXT);
        renderButtons(g);
    }
}
