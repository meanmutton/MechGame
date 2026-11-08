package com.meanmutton.mechgame.web;

import com.meanmutton.mechgame.core.Game;
import com.meanmutton.mechgame.core.Link;
import com.meanmutton.mechgame.core.Platform;
import com.meanmutton.mechgame.core.SoundSynth;

import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The browser version of Hexfront. The page (web/index.html) owns the canvas, sizes it to the window, and queues
 * pointer, wheel and key events in a plain array; this loop drains that queue into the game once per animation
 * frame, then updates and draws. Saves go to the browser's local storage, sounds are the game's own synthesized
 * effects played through Web Audio.
 */
public final class WebMain implements Platform {
    private final Map<String, String> memory = new HashMap<String, String>();   // if local storage is unavailable
    private boolean audio;
    /** Opens connections to the relay (only in the online page, WebOnlineMain), or null. */
    private final Links links;

    /** Network connections, for the online page only. */
    interface Links {
        Link open(String url);

        /** Once a frame (tests watch the online game through this). */
        void frame(Game game);
    }

    private WebMain(Links links) {
        this.links = links;
    }

    /** The offline page (release/Hexfront.html): no network code at all. */
    public static void main(String[] args) {
        run(null);
    }

    static void run(Links links) {
        WebMain platform = new WebMain(links);
        platform.initAudio();
        Game game = new Game(platform, (long) (Math.random() * 1e15));
        CanvasGfx gfx = new CanvasGfx(canvas());
        double last = nextFrame(), started = last;
        while (true) {
            double now = nextFrame();
            float dt = (float) Math.min(0.05, Math.max(0, (now - last) / 1000.0));
            last = now;
            try {
                drainInput(game);
                int w = canvasWidth(), h = canvasHeight();
                if (w > 0 && h > 0) {
                    game.tick(dt, w, h);
                    if (platform.links != null) platform.links.frame(game);
                    gfx.begin();
                    game.render(gfx);
                }
            } catch (Throwable e) {
                // as on the phone: save a crash report, then start over; the report shows on the next start
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                logError("Hexfront crashed: " + sw);
                try {
                    game.recordCrash(Thread.currentThread(), e);
                } catch (Throwable ignored) {
                }
                // a crash right after starting would only crash again: stop instead of reloading forever
                if (now - started < 8000) {
                    showFatal(e.toString());
                    return;
                }
                reload();
                return;
            }
        }
    }

    /** Events the page queued since the last frame, four numbers each: type, pointer, x, y (canvas pixels). */
    private static void drainInput(Game game) {
        int n = queueLength();
        for (int i = 0; i + 3 < n; i += 4) {
            int type = (int) queueAt(i);
            double a = queueAt(i + 1), x = queueAt(i + 2), y = queueAt(i + 3);
            if (type == Game.WHEEL) game.wheel((float) x, (float) y, (float) a);
            else game.touch(type, (int) a, (float) x, (float) y);
        }
        queueClear();
    }

    // ---------------- Platform ----------------

    private void initAudio() {
        audio = audioInit(SND_COUNT);
        if (!audio) return;
        for (int i = 0; i < SND_COUNT; i++) {
            short[] pcm = SoundSynth.generate(i);
            JSObject data = audioBuffer(i, pcm.length, SoundSynth.RATE);
            if (data == null) continue;
            for (int k = 0; k < pcm.length; k++) setSample(data, k, pcm[k] / 32768.0);
        }
    }

    public void play(int sound, float volume) {
        if (audio && sound >= 0 && sound < SND_COUNT) audioPlay(sound, volume);
    }

    public String load(String key) {
        String v = storageGet("hexfront." + key);
        return v != null ? v : memory.get(key);
    }

    public void save(String key, String value) {
        memory.put(key, value);
        storageSet("hexfront." + key, value);
    }

    public void saveNow(String key, String value) {
        save(key, value);
    }

    public void promptText(String title, String initial, TextCallback callback) {
        String s = prompt(title, initial == null ? "" : initial);
        if (s != null) callback.done(s.length() > 14 ? s.substring(0, 14) : s);
    }

    /** There is nothing to quit to in a browser tab; closing the tab does it. */
    public void exit() {
    }

    public boolean canExit() {
        return false;
    }

    public void share(String subject, String text) {
        copyText(subject, text);
    }

    public Link openLink(String url) {
        return links == null ? null : links.open(url);
    }

    public String buildInfo() {
        return "Hexfront " + buildStamp() + ", browser: " + userAgent();
    }

    // ---------------- the page ----------------

    @JSFunctor
    interface FrameCallback extends JSObject {
        void frame(double time);
    }

    @Async
    private static native double nextFrame();

    private static void nextFrame(final AsyncCallback<Double> cb) {
        requestFrame(new FrameCallback() {
            public void frame(double time) {
                cb.complete(time);
            }
        });
    }

    @JSBody(params = "cb", script = "window.requestAnimationFrame(function (t) { cb(t); });")
    private static native void requestFrame(FrameCallback cb);

    @JSBody(script = "return document.getElementById('game');")
    private static native JSObject canvas();

    @JSBody(script = "return document.getElementById('game').width;")
    private static native int canvasWidth();

    @JSBody(script = "return document.getElementById('game').height;")
    private static native int canvasHeight();

    @JSBody(script = "return window.hexfrontInput.length;")
    private static native int queueLength();

    @JSBody(params = "i", script = "return window.hexfrontInput[i];")
    private static native double queueAt(int i);

    @JSBody(script = "window.hexfrontInput.length = 0;")
    private static native void queueClear();

    @JSBody(params = "m", script = "console.error(m);")
    private static native void logError(String message);

    @JSBody(params = "m", script = "var hfD = document.createElement('div'); hfD.style.cssText = 'position:fixed;inset:0;display:flex;"
            + "align-items:center;justify-content:center;padding:24px;color:#f5a623;background:#0b1220;font:16px sans-serif;"
            + "text-align:center;white-space:pre-wrap'; hfD.textContent = 'Hexfront stopped with an error.\\n\\n' + m"
            + " + '\\n\\nReload the page to try again.'; document.body.appendChild(hfD);")
    private static native void showFatal(String message);

    @JSBody(script = "window.location.reload();")
    private static native void reload();

    @JSBody(params = "k", script = "try { var hfVal = window.localStorage.getItem(k); return hfVal === null ? null : hfVal; } catch (hfE) { return null; }")
    private static native String storageGet(String key);

    @JSBody(params = {"k", "v"}, script = "try { window.localStorage.setItem(k, v); } catch (hfE) {}")
    private static native void storageSet(String key, String value);

    @JSBody(params = {"t", "v"}, script = "var hfRes = window.prompt(t, v); return hfRes === null ? null : hfRes;")
    private static native String prompt(String title, String initial);

    @JSBody(params = {"s", "t"}, script = "window.hexfrontCopy(s, t);")
    private static native void copyText(String subject, String text);

    @JSBody(script = "return window.HEXFRONT_BUILD || 'dev';")
    private static native String buildStamp();

    @JSBody(script = "return navigator.userAgent;")
    private static native String userAgent();

    @JSBody(params = "n", script = "try { var hfCls = window.AudioContext || window.webkitAudioContext; if (!hfCls) return false;"
            + "window.hexfrontAudio = { ctx: new hfCls(), buffers: [] }; window.hexfrontAudio.master = window.hexfrontAudio.ctx.createGain();"
            + "window.hexfrontAudio.master.gain.value = 0.7; window.hexfrontAudio.master.connect(window.hexfrontAudio.ctx.destination);"
            + "return true; } catch (hfE) { return false; }")
    private static native boolean audioInit(int count);

    @JSBody(params = {"i", "n", "rate"}, script = "try { var hfA = window.hexfrontAudio; var hfB = hfA.ctx.createBuffer(1, Math.max(1, n), rate);"
            + "hfA.buffers[i] = hfB; return hfB.getChannelData(0); } catch (hfE) { return null; }")
    private static native JSObject audioBuffer(int index, int samples, int rate);

    @JSBody(params = {"d", "k", "v"}, script = "d[k] = v;")
    private static native void setSample(JSObject data, int index, double value);

    @JSBody(params = {"i", "v"}, script = "var hfA = window.hexfrontAudio; if (!hfA || hfA.ctx.state !== 'running' || !hfA.buffers[i]) return;"
            + "var hfS = hfA.ctx.createBufferSource(); hfS.buffer = hfA.buffers[i]; var hfG = hfA.ctx.createGain(); hfG.gain.value = v;"
            + "hfS.connect(hfG); hfG.connect(hfA.master); hfS.start();")
    private static native void audioPlay(int index, double volume);
}
