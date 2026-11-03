package com.meanmutton.mechgame.core;

/**
 * Company colors: pick main, trim and accent from a palette, or a ready-made scheme, with a live preview of your
 * units beside an enemy's. Paint is only looks; the ring under each unit on the map says whose it is.
 */
public final class PaintScreen extends Screen {
    private static final int DONE = 1, CANCEL = 2, SWATCH = 100, PRESET = 400;
    private static final String[] ROWS = {"Main", "Trim", "Accent"};
    private static final int[] PREVIEW = {29, 21, 4, 9, 12, 0};
    private final Company c;
    private int main, trim, accent;
    private float t;
    private final Unit[] previews = new Unit[PREVIEW.length];
    private final Unit enemy = new Unit(Chassis.ALL[25]);

    public PaintScreen(Game game) {
        super(game);
        c = game.company;
        Scheme s = c.scheme != null ? c.scheme : Scheme.PLAYER_DEFAULT;
        main = s.main;
        trim = s.trim;
        accent = s.accent;
        for (int i = 0; i < PREVIEW.length; i++) previews[i] = new Unit(Chassis.ALL[PREVIEW[i]]);
    }

    private Scheme working() {
        return new Scheme(main, trim, accent);
    }

    private float panelX() {
        return w * 0.47f;
    }

    protected void layout() {
        buttons.clear();
        float px = panelX(), pw = w - px - u * 3;
        float sw = swatch(pw);
        for (int row = 0; row < 3; row++) {
            int cur = row == 0 ? main : (row == 1 ? trim : accent);
            float y0 = h * 0.17f + row * (sw + u * 7);
            for (int i = 0; i < Scheme.PALETTE.length; i++) {
                Btn b = add(new Btn(SWATCH + row * 100 + i, "").set(px + i * (sw + u * 0.6f), y0, sw, sw));
                b.color = Scheme.PALETTE[i];
                b.selected = (Scheme.PALETTE[i] | 0xFF000000) == cur;
            }
        }
        float py = h * 0.17f + 3 * (sw + u * 7) + u * 3;
        float bw = (pw - u * 3) / 4;
        for (int i = 0; i < Scheme.PRESETS.length; i++) {
            Btn b = add(new Btn(PRESET + i, Scheme.PRESET_NAMES[i]).set(px + (i % 4) * (bw + u), py + (i / 4) * u * 8, bw, u * 7));
            b.textSize = u * 2.3f;
            b.color = Col.darker(Scheme.PRESETS[i].main, 0.35f);
            b.selected = Scheme.PRESETS[i].equals(working());
        }
        add(new Btn(CANCEL, "Cancel").set(u * 3, h - u * 12, u * 26, u * 9));
        add(new Btn(DONE, "Done").set(w - u * 33, h - u * 12, u * 30, u * 9)).color = 0xFF2E7D4F;
    }

    /** Swatch size: the whole palette fits on one row. */
    private float swatch(float pw) {
        return Math.min(u * 7, (pw - u * 0.6f * (Scheme.PALETTE.length - 1)) / Scheme.PALETTE.length);
    }

    private void relayout() {
        w = -1;
        game.setScreen(this);
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
    }

    protected void onClick(Btn b) {
        if (b.id == DONE) {
            c.scheme = working();
            game.saveCompany();
            game.toHq();
            return;
        }
        if (b.id == CANCEL) {
            game.toHq();
            return;
        }
        if (b.id >= PRESET) {
            Scheme s = Scheme.PRESETS[b.id - PRESET];
            main = s.main;
            trim = s.trim;
            accent = s.accent;
        } else if (b.id >= SWATCH) {
            int row = (b.id - SWATCH) / 100, i = (b.id - SWATCH) % 100;
            int col = Scheme.PALETTE[i] | 0xFF000000;
            if (row == 0) main = col;
            else if (row == 1) trim = col;
            else accent = col;
        }
        relayout();
    }

    public boolean back() {
        game.toHq();
        return true;
    }

    public void render(Gfx g) {
        background(g);
        Ui.title(g, "COMPANY COLORS", w * 0.24f, h * 0.07f, u * 5.5f);
        // preview: your units on a patch of ground, with an enemy for contrast
        float lx = u * 3, ly = h * 0.15f, lw = panelX() - u * 6, lh = h - ly - u * 16;
        Ui.panel(g, lx, ly, lw, lh, u * 2);
        g.fillRoundRect(lx + u, ly + u, lw - u * 2, lh - u * 2, u * 1.5f, 0xFF4E6A3E);
        Scheme s = working();
        Scheme was = Scheme.player;
        Scheme.player = s;
        float cell = Math.min((lw - u * 4) / 3f, (lh - u * 10) / 2.6f);
        for (int i = 0; i < previews.length; i++) {
            float cx = lx + u * 2 + cell * (i % 3 + 0.5f), cy = ly + u * 3 + cell * (i / 3 + 0.55f);
            g.strokeCircle(cx, cy, cell * 0.38f, u * 0.5f, Col.alpha(UnitArt.PLAYER_COLOR, 0.9f));
            UnitArt.draw(g, previews[i], cx, cy, -60 + (float) Math.sin(t * 0.7f + i) * 25, Battle.PLAYER, cell * 0.42f, t, 1);
        }
        Scheme.player = was;
        float ex = lx + lw - cell * 0.6f, ey = ly + lh - cell * 0.55f;
        g.strokeCircle(ex, ey, cell * 0.32f, u * 0.5f, Col.alpha(UnitArt.ENEMY_COLOR, 0.9f));
        UnitArt.draw(g, enemy, ex, ey, 160, Battle.ENEMY, cell * 0.36f, t, 1);
        g.text("Enemy", ex, ey + cell * 0.42f, u * 2.2f, UnitArt.ENEMY_COLOR, Gfx.ALIGN_CENTER, true);
        Ui.wrap(g, "On the map, the ring under each unit says whose it is: blue for yours, red for the enemy's. Paint is just paint.",
                lx + u * 2, ly + lh + u * 3, u * 2.2f, lw - u * 4, Col.TEXT_DIM);
        // swatch row labels
        float px = panelX(), pw = w - px - u * 3;
        float sw = swatch(pw);
        for (int row = 0; row < 3; row++) {
            float y0 = h * 0.17f + row * (sw + u * 7);
            g.text(ROWS[row], px, y0 - u * 2.4f, u * 2.6f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        }
        float py = h * 0.17f + 3 * (sw + u * 7) + u * 3;
        g.text("Ready-made", px, py - u * 2.4f, u * 2.6f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
        renderButtons(g);
        // swatches that are selected get a white frame on top of the button's own highlight
        for (Btn b : buttons) {
            if (b.id >= SWATCH && b.id < PRESET && b.selected) g.strokeRoundRect(b.x - u * 0.4f, b.y - u * 0.4f, b.w + u * 0.8f, b.h + u * 0.8f, u, u * 0.5f, 0xFFFFFFFF);
        }
    }
}
