package com.meanmutton.mechgame.web;

import com.meanmutton.mechgame.core.Gfx;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The game's drawing interface on an HTML canvas. Coordinates are canvas pixels (the page sizes the canvas to the
 * window times the screen's pixel ratio), and text is centered vertically on y, as on Android.
 */
final class CanvasGfx implements Gfx {
    private static final String FONT = "px -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";
    private final JSObject canvas, ctx;
    private final Map<Integer, String> colors = new HashMap<Integer, String>();
    private String font = "";

    CanvasGfx(JSObject canvas) {
        this.canvas = canvas;
        this.ctx = context(canvas);
    }

    /** Called at the start of each frame: the canvas state is reset whenever the page resizes it. */
    void begin() {
        font = "";
        setup(ctx);
    }

    private String css(int c) {
        String s = colors.get(c);
        if (s == null) {
            int a = (c >>> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            s = a == 255 ? "rgb(" + r + "," + g + "," + b + ")" : "rgba(" + r + "," + g + "," + b + "," + (a / 255.0) + ")";
            if (colors.size() > 4000) colors.clear();
            colors.put(c, s);
        }
        return s;
    }

    private void useFont(float size, boolean bold) {
        String f = (bold ? "bold " : "") + Math.round(size * 10) / 10.0 + FONT;
        if (!f.equals(font)) {
            font = f;
            setFont(ctx, f);
        }
    }

    public int width() { return canvasWidth(canvas); }
    public int height() { return canvasHeight(canvas); }
    public void save() { save(ctx); }
    public void restore() {
        restore(ctx);
        font = "";   // the font is part of the saved state
    }
    public void translate(float x, float y) { translate(ctx, x, y); }
    public void scale(float sx, float sy) { scale(ctx, sx, sy); }
    public void rotate(float degrees) { rotate(ctx, degrees * Math.PI / 180); }
    public void clipRect(float x, float y, float w, float h) { clip(ctx, x, y, w, h); }

    public void fillRect(float x, float y, float w, float h, int color) { fillRect(ctx, x, y, w, h, css(color)); }

    public void fillRoundRect(float x, float y, float w, float h, float r, int color) {
        fillRound(ctx, x, y, w, h, Math.max(0, Math.min(r, Math.min(w, h) / 2)), css(color));
    }

    public void strokeRoundRect(float x, float y, float w, float h, float r, float lw, int color) {
        strokeRound(ctx, x, y, w, h, Math.max(0, Math.min(r, Math.min(w, h) / 2)), lw, css(color));
    }

    public void fillRectVGradient(float x, float y, float w, float h, int top, int bottom) {
        fillRectGradient(ctx, x, y, w, h, css(top), css(bottom));
    }

    public void fillCircle(float cx, float cy, float r, int color) {
        if (r > 0) fillCircle(ctx, cx, cy, r, css(color));
    }

    public void strokeCircle(float cx, float cy, float r, float lw, int color) {
        if (r > 0) strokeCircle(ctx, cx, cy, r, lw, css(color));
    }

    public void fillRadial(float cx, float cy, float r, int inner, int outer) {
        if (r > 0.01f) fillRadial(ctx, cx, cy, r, css(inner), css(outer));
    }

    public void line(float x1, float y1, float x2, float y2, float lw, int color) {
        line(ctx, x1, y1, x2, y2, lw, css(color));
    }

    private void path(float[] xs, float[] ys, int n) {
        beginPath(ctx, xs[0], ys[0]);
        for (int i = 1; i < n; i++) lineTo(ctx, xs[i], ys[i]);
    }

    public void fillPoly(float[] xs, float[] ys, int n, int color) {
        if (n < 3) return;
        path(xs, ys, n);
        fillPath(ctx, css(color));
    }

    public void fillPolyVGradient(float[] xs, float[] ys, int n, float y0, int c0, float y1, int c1) {
        if (n < 3) return;
        path(xs, ys, n);
        fillPathGradient(ctx, y0, css(c0), y1, css(c1));
    }

    public void text(String s, float x, float y, float size, int color, int align, boolean bold) {
        if (size < 0.5f || s == null || s.isEmpty()) return;
        useFont(size, bold);
        fillText(ctx, s, x, y, align == ALIGN_CENTER ? "center" : (align == ALIGN_RIGHT ? "right" : "left"), css(color));
    }

    public float textWidth(String s, float size, boolean bold) {
        if (s == null || s.isEmpty()) return 0;
        useFont(size, bold);
        return (float) measure(ctx, s);
    }

    // ---------------- canvas calls ----------------

    @JSBody(params = "c", script = "return c.getContext('2d');")
    private static native JSObject context(JSObject canvas);

    @JSBody(params = "c", script = "c.textBaseline = 'middle'; c.lineCap = 'round'; c.lineJoin = 'round';")
    private static native void setup(JSObject ctx);

    @JSBody(params = "c", script = "return c.width;")
    private static native int canvasWidth(JSObject canvas);

    @JSBody(params = "c", script = "return c.height;")
    private static native int canvasHeight(JSObject canvas);

    @JSBody(params = "c", script = "c.save();")
    private static native void save(JSObject ctx);

    @JSBody(params = "c", script = "c.restore();")
    private static native void restore(JSObject ctx);

    @JSBody(params = {"c", "x", "y"}, script = "c.translate(x, y);")
    private static native void translate(JSObject ctx, double x, double y);

    @JSBody(params = {"c", "x", "y"}, script = "c.scale(x, y);")
    private static native void scale(JSObject ctx, double x, double y);

    @JSBody(params = {"c", "a"}, script = "c.rotate(a);")
    private static native void rotate(JSObject ctx, double a);

    @JSBody(params = {"c", "x", "y", "w", "h"}, script = "c.beginPath(); c.rect(x, y, w, h); c.clip();")
    private static native void clip(JSObject ctx, double x, double y, double w, double h);

    @JSBody(params = {"c", "f"}, script = "c.font = f;")
    private static native void setFont(JSObject ctx, String font);

    @JSBody(params = {"c", "x", "y", "w", "h", "s"}, script = "c.fillStyle = s; c.fillRect(x, y, w, h);")
    private static native void fillRect(JSObject ctx, double x, double y, double w, double h, String style);

    private static final String ROUND = "c.beginPath(); c.moveTo(x + r, y); c.lineTo(x + w - r, y); c.arcTo(x + w, y, x + w, y + r, r);"
            + "c.lineTo(x + w, y + h - r); c.arcTo(x + w, y + h, x + w - r, y + h, r); c.lineTo(x + r, y + h);"
            + "c.arcTo(x, y + h, x, y + h - r, r); c.lineTo(x, y + r); c.arcTo(x, y, x + r, y, r); c.closePath();";

    @JSBody(params = {"c", "x", "y", "w", "h", "r", "s"}, script = ROUND + "c.fillStyle = s; c.fill();")
    private static native void fillRound(JSObject ctx, double x, double y, double w, double h, double r, String style);

    @JSBody(params = {"c", "x", "y", "w", "h", "r", "lw", "s"}, script = ROUND + "c.lineWidth = lw; c.strokeStyle = s; c.stroke();")
    private static native void strokeRound(JSObject ctx, double x, double y, double w, double h, double r, double lw, String style);

    @JSBody(params = {"c", "x", "y", "w", "h", "a", "b"}, script = "var hfGrad = c.createLinearGradient(0, y, 0, y + h);"
            + "hfGrad.addColorStop(0, a); hfGrad.addColorStop(1, b); c.fillStyle = hfGrad; c.fillRect(x, y, w, h);")
    private static native void fillRectGradient(JSObject ctx, double x, double y, double w, double h, String top, String bottom);

    @JSBody(params = {"c", "x", "y", "r", "s"}, script = "c.beginPath(); c.arc(x, y, r, 0, 6.283185307179586); c.fillStyle = s; c.fill();")
    private static native void fillCircle(JSObject ctx, double x, double y, double r, String style);

    @JSBody(params = {"c", "x", "y", "r", "lw", "s"},
            script = "c.beginPath(); c.arc(x, y, r, 0, 6.283185307179586); c.lineWidth = lw; c.strokeStyle = s; c.stroke();")
    private static native void strokeCircle(JSObject ctx, double x, double y, double r, double lw, String style);

    @JSBody(params = {"c", "x", "y", "r", "a", "b"}, script = "var hfGrad = c.createRadialGradient(x, y, 0, x, y, r);"
            + "hfGrad.addColorStop(0, a); hfGrad.addColorStop(1, b); c.beginPath(); c.arc(x, y, r, 0, 6.283185307179586); c.fillStyle = hfGrad; c.fill();")
    private static native void fillRadial(JSObject ctx, double x, double y, double r, String inner, String outer);

    @JSBody(params = {"c", "x1", "y1", "x2", "y2", "lw", "s"},
            script = "c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.lineWidth = lw; c.strokeStyle = s; c.stroke();")
    private static native void line(JSObject ctx, double x1, double y1, double x2, double y2, double lw, String style);

    @JSBody(params = {"c", "x", "y"}, script = "c.beginPath(); c.moveTo(x, y);")
    private static native void beginPath(JSObject ctx, double x, double y);

    @JSBody(params = {"c", "x", "y"}, script = "c.lineTo(x, y);")
    private static native void lineTo(JSObject ctx, double x, double y);

    @JSBody(params = {"c", "s"}, script = "c.closePath(); c.fillStyle = s; c.fill();")
    private static native void fillPath(JSObject ctx, String style);

    @JSBody(params = {"c", "y0", "a", "y1", "b"}, script = "var hfGrad = c.createLinearGradient(0, y0, 0, y1);"
            + "hfGrad.addColorStop(0, a); hfGrad.addColorStop(1, b); c.closePath(); c.fillStyle = hfGrad; c.fill();")
    private static native void fillPathGradient(JSObject ctx, double y0, String c0, double y1, String c1);

    @JSBody(params = {"c", "t", "x", "y", "al", "s"}, script = "c.textAlign = al; c.fillStyle = s; c.fillText(t, x, y);")
    private static native void fillText(JSObject ctx, String text, double x, double y, String align, String style);

    @JSBody(params = {"c", "t"}, script = "return c.measureText(t).width;")
    private static native double measure(JSObject ctx, String text);
}
