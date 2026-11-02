package com.meanmutton.mechgame;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.meanmutton.mechgame.core.Gfx;

/** Canvas implementation of the game's drawing interface. */
final class AndroidGfx implements Gfx {
    private Canvas c;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textBold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    AndroidGfx() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        shade.setStyle(Paint.Style.FILL);
        shade.setDither(true);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        textBold.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    }

    void begin(Canvas canvas) {
        this.c = canvas;
    }

    public int width() { return c.getWidth(); }
    public int height() { return c.getHeight(); }
    public void save() { c.save(); }
    public void restore() { c.restore(); }
    public void translate(float x, float y) { c.translate(x, y); }
    public void scale(float sx, float sy) { c.scale(sx, sy); }
    public void rotate(float degrees) { c.rotate(degrees); }
    public void clipRect(float x, float y, float w, float h) { c.clipRect(x, y, x + w, y + h); }

    public void fillRect(float x, float y, float w, float h, int color) {
        fill.setColor(color);
        c.drawRect(x, y, x + w, y + h, fill);
    }

    public void fillRoundRect(float x, float y, float w, float h, float r, int color) {
        fill.setColor(color);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, r, r, fill);
    }

    public void strokeRoundRect(float x, float y, float w, float h, float r, float lw, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(lw);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, r, r, stroke);
    }

    public void fillRectVGradient(float x, float y, float w, float h, int top, int bottom) {
        shade.setShader(new LinearGradient(0, y, 0, y + h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + w, y + h, shade);
        shade.setShader(null);
    }

    public void fillCircle(float cx, float cy, float r, int color) {
        fill.setColor(color);
        c.drawCircle(cx, cy, r, fill);
    }

    public void strokeCircle(float cx, float cy, float r, float lw, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(lw);
        c.drawCircle(cx, cy, r, stroke);
    }

    public void fillRadial(float cx, float cy, float r, int inner, int outer) {
        if (r <= 0.01f) return;
        shade.setShader(new RadialGradient(cx, cy, r, inner, outer, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, shade);
        shade.setShader(null);
    }

    public void line(float x1, float y1, float x2, float y2, float lw, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(lw);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void buildPath(float[] xs, float[] ys, int n) {
        path.rewind();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);
        path.close();
    }

    public void fillPoly(float[] xs, float[] ys, int n, int color) {
        buildPath(xs, ys, n);
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    public void fillPolyVGradient(float[] xs, float[] ys, int n, float y0, int c0, float y1, int c1) {
        buildPath(xs, ys, n);
        shade.setShader(new LinearGradient(0, y0, 0, y1, c0, c1, Shader.TileMode.CLAMP));
        c.drawPath(path, shade);
        shade.setShader(null);
    }

    public void text(String s, float x, float y, float size, int color, int align, boolean bold) {
        if (size < 0.5f) return;
        Paint p = bold ? textBold : text;
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(align == ALIGN_CENTER ? Paint.Align.CENTER : (align == ALIGN_RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT));
        p.getFontMetrics(fm);
        c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p);
    }

    public float textWidth(String s, float size, boolean bold) {
        Paint p = bold ? textBold : text;
        p.setTextSize(size);
        return p.measureText(s);
    }
}
