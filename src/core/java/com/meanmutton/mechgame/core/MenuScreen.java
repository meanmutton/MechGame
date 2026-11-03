package com.meanmutton.mechgame.core;

/** Title screen. */
public final class MenuScreen extends Screen {
    private static final int CONTINUE = 1, NEW = 2, HELP = 3, SOUND = 4, SPEED = 5, QUIT = 6;
    private Btn sound, speed;
    private float t;
    private final boolean hasSave, hasBattle;

    public MenuScreen(Game game) {
        super(game);
        hasSave = game.hasSave();
        hasBattle = game.hasBattle();
    }

    protected void layout() {
        buttons.clear();
        float bw = u * 50, bh = u * 10, x = w * 0.5f - bw / 2, y = h * 0.4f;
        Btn c = add(new Btn(CONTINUE, hasBattle ? "Resume Battle" : "Continue Campaign").set(x, y, bw, bh));
        c.enabled = hasSave || hasBattle;
        c.color = 0xFF2E6D8F;
        add(new Btn(NEW, "New Game").set(x, y + bh * 1.2f, bw, bh)).color = 0xFF2E7D4F;
        add(new Btn(HELP, "How to Play").set(x, y + bh * 2.4f, bw, bh));
        sound = add(new Btn(SOUND, "").set(x, y + bh * 3.6f, bw * 0.49f, bh));
        speed = add(new Btn(SPEED, "").set(x + bw * 0.51f, y + bh * 3.6f, bw * 0.49f, bh));
        sound.textSize = speed.textSize = u * 3f;
        add(new Btn(QUIT, "Quit").set(w - u * 26, h - u * 12, u * 23, u * 9));
    }

    public void update(float dt) {
        super.update(dt);
        t += dt;
        sound.label = "Sound: " + (game.settings.sound ? "On" : "Off");
        speed.label = "AI speed: " + Settings.SPEED_NAMES[game.settings.aiSpeed];
    }

    protected void onClick(Btn b) {
        switch (b.id) {
            case CONTINUE:
                if (hasBattle) game.resumeBattle();
                else game.continueCampaign();
                break;
            case NEW: game.setScreen(new NewGameScreen(game)); break;
            case HELP: game.setScreen(new HelpScreen(game)); break;
            case SOUND:
                game.settings.sound = !game.settings.sound;
                game.saveSettings();
                break;
            case SPEED:
                game.settings.aiSpeed = (game.settings.aiSpeed + 1) % 3;
                game.saveSettings();
                break;
            case QUIT: game.platform.exit(); break;
        }
    }

    public void render(Gfx g) {
        g.fillRectVGradient(0, 0, w, h, 0xFF0C1420, 0xFF22303E);
        // drifting hex field
        float s = u * 7;
        g.save();
        g.translate(-((t * 6) % (s * 3)), 0);
        for (int c = -1; c < w / (s * 1.5f) + 4; c++) {
            for (int r = -1; r < h / (s * 1.73f) + 2; r++) {
                float cx = c * 1.5f * s, cy = (r + 0.5f * (c & 1)) * 1.732f * s;
                float pulse = 0.04f + 0.04f * (float) Math.sin(t * 0.8f + c * 0.7f + r * 1.3f);
                g.save();
                g.translate(cx, cy);
                g.scale(s, s);
                HexRender.strokeHex(g, 0, 0, 0.95f, 0.03f, Col.alpha(0xFF7AA8D8, pulse * 2.5f));
                g.restore();
            }
        }
        g.restore();
        // a pair of mechs facing off
        Chassis a = Chassis.ALL[25], b2 = Chassis.ALL[29];
        UnitArt.drawChassis(g, a, a.stockWeapons, w * 0.17f, h * 0.62f, -20 + (float) Math.sin(t) * 6,
                UnitArt.PLAYER_COLOR, u * 16, t, 1, 1);
        UnitArt.drawChassis(g, b2, b2.stockWeapons, w * 0.83f, h * 0.6f, 200 + (float) Math.sin(t * 0.8f) * 6,
                UnitArt.ENEMY_COLOR, u * 17, t, 1, 1);
        Ui.title(g, "HEXFRONT", w / 2f, h * 0.17f, u * 14);
        g.text("Mech & armor tactics", w / 2f, h * 0.285f, u * 4, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        renderButtons(g);
    }
}
