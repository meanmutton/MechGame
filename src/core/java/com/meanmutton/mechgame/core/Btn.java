package com.meanmutton.mechgame.core;

/** A tappable rectangle. */
public final class Btn {
    public float x, y, w, h;
    public String label, sub;
    public int id;
    public int color, textColor;
    public float textSize;
    public boolean enabled = true, visible = true, pressed, selected, repeat;
    public Object tag;

    public Btn(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public Btn set(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public Btn tint(int c) {
        this.color = c;
        return this;
    }

    public boolean contains(float px, float py) {
        return visible && px >= x && px <= x + w && py >= y && py <= y + h;
    }
}
