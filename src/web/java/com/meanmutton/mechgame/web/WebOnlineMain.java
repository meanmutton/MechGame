package com.meanmutton.mechgame.web;

import com.meanmutton.mechgame.core.Game;
import com.meanmutton.mechgame.core.Link;
import com.meanmutton.mechgame.core.Online;

import org.teavm.jso.JSBody;

/**
 * The browser version with online play (release/Hexfront-online.html). The same game as WebMain, plus WebSocket
 * connections to the relay. A page may name another relay in window.HEXFRONT_RELAY (for testing).
 */
public final class WebOnlineMain {
    private WebOnlineMain() {}

    public static void main(String[] args) {
        String relay = relayOverride();
        Online.relayUrl = relay != null && relay.length() > 0 ? relay : Online.RELAY;
        Online.buildId = buildCommit();   // online play pairs only the same build
        final String test = autotest();
        if (test != null) Online.autoplay = true;
        WebMain.run(new WebMain.Links() {
            private boolean started;

            public Link open(String url) {
                return new WebLink(url);
            }

            public void frame(Game game) {
                if (test == null) return;
                // tests: host or join by itself, then report how the online game stands
                if (!started) {
                    started = true;
                    if (test.startsWith("join:")) game.joinOnline(test.substring(5));
                    else game.hostOnline(9000);
                }
                Online o = game.online;
                report(o == null ? "none" : o.phase + " " + (o.code == null ? "-" : o.code) + " " + o.testState());
            }
        });
    }

    /** The commit in the page's build stamp ("6bd22ff (2026-11-08)" gives "6bd22ff"). */
    @JSBody(script = "var hfB = String(window.HEXFRONT_BUILD || 'dev'); return hfB.split(' ')[0];")
    private static native String buildCommit();

    @JSBody(script = "return window.HEXFRONT_AUTOTEST || null;")
    private static native String autotest();

    @JSBody(params = "s", script = "window.hexfrontState = s;")
    private static native void report(String s);

    @JSBody(script = "return window.HEXFRONT_RELAY || null;")
    private static native String relayOverride();
}
