package com.meanmutton.mechgame.web;

import com.meanmutton.mechgame.core.Link;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * The browser's WebSocket as a Link: received messages wait in a plain array until the game reads them. Only the
 * online page (WebOnlineMain) uses this, so the offline page's JavaScript has no network code at all.
 */
final class WebLink implements Link {
    private final JSObject ws;

    WebLink(String url) {
        ws = open(url);
    }

    public int state() {
        return ws == null ? CLOSED : readyState(ws);
    }

    public void send(String text) {
        if (state() == OPEN) send(ws, text);
    }

    public String poll() {
        return ws == null ? null : next(ws);
    }

    public void close() {
        if (ws != null) close(ws);
    }

    /** The socket, with its messages queued on it; null if the browser refused to open it. */
    @JSBody(params = "u", script = "try { var hfWs = new WebSocket(u); hfWs.hfQueue = []; hfWs.hfDead = false;"
            + "hfWs.onmessage = function (e) { if (typeof e.data === 'string') hfWs.hfQueue.push(e.data); };"
            + "hfWs.onclose = function () { hfWs.hfDead = true; }; hfWs.onerror = function () { hfWs.hfDead = true; };"
            + "return hfWs; } catch (hfE) { return null; }")
    private static native JSObject open(String url);

    /** CONNECTING, OPEN or CLOSED (a socket that is closing counts as closed). */
    @JSBody(params = "w", script = "if (w.hfDead) return 2; return w.readyState === 0 ? 0 : (w.readyState === 1 ? 1 : 2);")
    private static native int readyState(JSObject ws);

    @JSBody(params = {"w", "t"}, script = "try { w.send(t); } catch (hfE) {}")
    private static native void send(JSObject ws, String text);

    @JSBody(params = "w", script = "return w.hfQueue.length > 0 ? w.hfQueue.shift() : null;")
    private static native String next(JSObject ws);

    @JSBody(params = "w", script = "try { w.close(); } catch (hfE) {} w.hfDead = true;")
    private static native void close(JSObject ws);
}
