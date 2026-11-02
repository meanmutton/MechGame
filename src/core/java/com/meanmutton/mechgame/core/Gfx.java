package com.meanmutton.mechgame.core;

/**
 * Minimal drawing surface. The Android build implements it on a Canvas;
 * the desktop test harness implements it on Java2D so screens can be
 * rendered to PNG and the game simulated headless.
 *
 * Colors are ARGB ints. Text y coordinates are the vertical CENTER of the text.
 */
public interface Gfx {
    int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2;

    int width();
    int height();

    void save();
    void restore();
    void translate(float x, float y);
    void scale(float sx, float sy);
    void rotate(float degrees);
    void clipRect(float x, float y, float w, float h);

    void fillRect(float x, float y, float w, float h, int color);
    void fillRoundRect(float x, float y, float w, float h, float r, int color);
    void strokeRoundRect(float x, float y, float w, float h, float r, float lineWidth, int color);
    void fillRectVGradient(float x, float y, float w, float h, int top, int bottom);
    void fillCircle(float cx, float cy, float r, int color);
    void strokeCircle(float cx, float cy, float r, float lineWidth, int color);
    void fillRadial(float cx, float cy, float r, int inner, int outer);
    void line(float x1, float y1, float x2, float y2, float lineWidth, int color);
    void fillPoly(float[] xs, float[] ys, int n, int color);
    void fillPolyVGradient(float[] xs, float[] ys, int n, float y0, int c0, float y1, int c1);
    void text(String s, float x, float y, float size, int color, int align, boolean bold);
    float textWidth(String s, float size, boolean bold);
}
