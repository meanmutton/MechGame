package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/** Base class for every screen: owns a button list and routes single-finger taps to it. */
public abstract class Screen {
    protected final Game game;
    protected final List<Btn> buttons = new ArrayList<Btn>();
    protected int w, h;
    protected float u;
    protected Btn pressed;
    private int pressedPointer = -1;
    private float repeatTimer, repeatRate;

    protected Screen(Game game) {
        this.game = game;
    }

    /** Called whenever the screen size changes (and before the first render). */
    protected abstract void layout();

    public final void resize(int w, int h) {
        if (w == this.w && h == this.h) return;
        this.w = w;
        this.h = h;
        this.u = Math.min(h / 100f, w / 170f);
        layout();
    }

    public void update(float dt) {
        if (pressed != null && pressed.repeat && pressed.pressed) {
            repeatTimer -= dt;
            if (repeatTimer <= 0) {
                onClick(pressed);
                repeatRate = Math.max(0.025f, repeatRate * 0.85f);
                repeatTimer = repeatRate;
            }
        }
    }

    public abstract void render(Gfx g);

    protected void renderButtons(Gfx g) {
        for (Btn b : buttons) Ui.button(g, b, u);
    }

    /** Buttons the screen wants to handle first (e.g. an overlay). Null means use the main list. */
    protected List<Btn> activeButtons() {
        return buttons;
    }

    public void touchDown(int pointer, float x, float y) {
        if (pressed != null) {
            onTouchDown(pointer, x, y);
            return;
        }
        List<Btn> list = activeButtons();
        for (int i = list.size() - 1; i >= 0; i--) {
            Btn b = list.get(i);
            if (b.visible && b.enabled && b.contains(x, y)) {
                pressed = b;
                pressedPointer = pointer;
                b.pressed = true;
                if (b.repeat) {
                    onClick(b);
                    repeatRate = 0.11f;
                    repeatTimer = 0.38f;
                }
                return;
            }
        }
        onTouchDown(pointer, x, y);
    }

    public void touchMove(int pointer, float x, float y) {
        if (pressed != null && pointer == pressedPointer) {
            pressed.pressed = pressed.contains(x, y) || (pressed.repeat && hold(pressed, x, y));
            return;
        }
        onTouchMove(pointer, x, y);
    }

    private boolean hold(Btn b, float x, float y) {
        float pad = b.h * 0.5f;
        return x >= b.x - pad && x <= b.x + b.w + pad && y >= b.y - pad && y <= b.y + b.h + pad;
    }

    public void touchUp(int pointer, float x, float y) {
        if (pressed != null && pointer == pressedPointer) {
            Btn b = pressed;
            boolean inside = b.pressed;
            b.pressed = false;
            pressed = null;
            pressedPointer = -1;
            if (inside && !b.repeat) {
                game.sfx(Platform.SND_CLICK, 0.5f);
                onClick(b);
            }
            onRelease(b);
            return;
        }
        onTouchUp(pointer, x, y);
    }

    /** Drops the current button press without clicking (used when a drag turns into a scroll). */
    protected void cancelPress() {
        if (pressed != null) pressed.pressed = false;
        pressed = null;
        pressedPointer = -1;
    }

    protected boolean isPressing(int pointer) {
        return pressed != null && pointer == pressedPointer;
    }

    /** Called when the finger leaves a button, clicked or not. */
    protected void onRelease(Btn b) {}

    protected void onClick(Btn b) {}

    protected void onTouchDown(int pointer, float x, float y) {}

    protected void onTouchMove(int pointer, float x, float y) {}

    protected void onTouchUp(int pointer, float x, float y) {}

    /** Back button. Return true if handled. */
    public boolean back() {
        return false;
    }

    protected Btn add(Btn b) {
        buttons.add(b);
        return b;
    }

    protected void background(Gfx g) {
        g.fillRectVGradient(0, 0, w, h, 0xFF0B1220, 0xFF1A2440);
        // subtle grid
        float s = u * 8;
        for (float x = 0; x < w; x += s) g.line(x, 0, x, h, 1, 0x0CFFFFFF);
        for (float y = 0; y < h; y += s) g.line(0, y, w, y, 1, 0x0CFFFFFF);
    }
}
