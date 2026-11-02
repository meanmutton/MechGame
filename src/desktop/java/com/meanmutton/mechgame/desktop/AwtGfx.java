package com.meanmutton.mechgame.desktop;

import com.meanmutton.mechgame.core.Gfx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/** Java2D implementation of Gfx, used for screenshots and tests on a desktop JVM. */
public final class AwtGfx implements Gfx {
    private final Graphics2D g;
    private final int w, h;
    private final Deque<AffineTransform> tStack = new ArrayDeque<AffineTransform>();
    private final Deque<Shape> cStack = new ArrayDeque<Shape>();

    public AwtGfx(BufferedImage img) {
        this.g = img.createGraphics();
        this.w = img.getWidth();
        this.h = img.getHeight();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    public void dispose() {
        g.dispose();
    }

    private static Color c(int argb) {
        return new Color(argb, true);
    }

    public int width() { return w; }
    public int height() { return h; }

    public void save() {
        tStack.push(g.getTransform());
        cStack.push(g.getClip() == null ? new Rectangle2D.Float(-1e6f, -1e6f, 2e6f, 2e6f) : g.getClip());
    }

    public void restore() {
        g.setTransform(tStack.pop());
        Shape s = cStack.pop();
        g.setClip(s.getBounds2D().getWidth() > 1e5 ? null : s);
    }

    public void translate(float x, float y) { g.translate(x, y); }
    public void scale(float sx, float sy) { g.scale(sx, sy); }
    public void rotate(float degrees) { g.rotate(Math.toRadians(degrees)); }
    public void clipRect(float x, float y, float w, float h) { g.clip(new Rectangle2D.Float(x, y, w, h)); }

    public void fillRect(float x, float y, float w, float h, int color) {
        g.setColor(c(color));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    public void fillRoundRect(float x, float y, float w, float h, float r, int color) {
        g.setColor(c(color));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
    }

    public void strokeRoundRect(float x, float y, float w, float h, float r, float lw, int color) {
        g.setColor(c(color));
        g.setStroke(new BasicStroke(lw));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, r * 2, r * 2));
    }

    public void fillRectVGradient(float x, float y, float w, float h, int top, int bottom) {
        g.setPaint(new GradientPaint(0, y, c(top), 0, y + h, c(bottom)));
        g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    public void fillCircle(float cx, float cy, float r, int color) {
        g.setColor(c(color));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    public void strokeCircle(float cx, float cy, float r, float lw, int color) {
        g.setColor(c(color));
        g.setStroke(new BasicStroke(lw));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    public void fillRadial(float cx, float cy, float r, int inner, int outer) {
        if (r <= 0.01f) return;
        g.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 1f}, new Color[]{c(inner), c(outer)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
    }

    public void line(float x1, float y1, float x2, float y2, float lw, int color) {
        g.setColor(c(color));
        g.setStroke(new BasicStroke(lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    private Path2D path(float[] xs, float[] ys, int n) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) p.lineTo(xs[i], ys[i]);
        p.closePath();
        return p;
    }

    public void fillPoly(float[] xs, float[] ys, int n, int color) {
        g.setColor(c(color));
        g.fill(path(xs, ys, n));
    }

    public void fillPolyVGradient(float[] xs, float[] ys, int n, float y0, int c0, float y1, int c1) {
        g.setPaint(new GradientPaint(0, y0, c(c0), 0, y1, c(c1)));
        g.fill(path(xs, ys, n));
    }

    private Font font(float size, boolean bold) {
        return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 1).deriveFont(size);
    }

    public void text(String s, float x, float y, float size, int color, int align, boolean bold) {
        if (size < 0.5f) return;
        Font f = font(size, bold);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        float tw = (float) fm.getStringBounds(s, g).getWidth();
        float dx = align == ALIGN_CENTER ? -tw / 2 : (align == ALIGN_RIGHT ? -tw : 0);
        float base = y + (fm.getAscent() - fm.getDescent()) / 2f;
        g.setColor(c(color));
        g.drawString(s, x + dx, base);
    }

    public float textWidth(String s, float size, boolean bold) {
        g.setFont(font(size, bold));
        return (float) g.getFontMetrics().getStringBounds(s, g).getWidth();
    }
}
