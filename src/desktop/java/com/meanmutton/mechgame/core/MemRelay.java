package com.meanmutton.mechgame.core;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

/**
 * Tests: the relay (server/relay.yaml) in memory, with the same protocol, so two games in one process can play
 * each other. Links can be cut to test reconnecting, and every message can be counted.
 */
final class MemRelay {
    private final Random rnd = new Random(7);
    private final Map<String, String[]> rooms = new HashMap<String, String[]>();   // code -> {host, hostToken, guest, guestToken, version}
    private final Map<MemLink, String[]> conns = new HashMap<MemLink, String[]>();   // link -> {code, role}
    int messages;
    /** Drop links when they open, as a server that can't be reached. */
    boolean unreachable;

    synchronized MemLink connect() {
        MemLink l = new MemLink(this);
        if (unreachable) l.state = Link.CLOSED;
        return l;
    }

    private void to(MemLink l, String m) {
        if (l != null && l.state == Link.OPEN) l.in.add(m);
    }

    private MemLink linkOf(String id) {
        for (MemLink l : conns.keySet()) if (l.id.equals(id)) return l;
        return null;
    }

    synchronized void receive(MemLink from, String body) {
        messages++;
        String[] w = Text.words(body);
        String cmd = w[0];
        String[] me = conns.get(from);
        if (cmd.equals("D")) {
            String[] r = me == null ? null : rooms.get(me[0]);
            if (r == null) {
                to(from, "NOROOM");
                return;
            }
            MemLink o = linkOf(me[1].equals("h") ? r[2] : r[0]);
            if (o == null || o.state != Link.OPEN) to(from, "AWAY");
            else to(o, body);
        } else if (cmd.equals("PING")) {
            to(from, "PONG");
        } else if (cmd.equals("HOST")) {
            String code;
            do {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < 6; i++) b.append((char) ('A' + rnd.nextInt(26)));
                code = b.toString();
            } while (rooms.containsKey(code));
            String tok = Long.toHexString(rnd.nextLong());
            rooms.put(code, new String[]{from.id, tok, null, null, w.length > 1 ? w[1] : "?"});
            conns.put(from, new String[]{code, "h"});
            to(from, "ROOM " + code + " " + tok);
        } else if (cmd.equals("JOIN") && w.length > 1) {
            String code = Text.upper(w[1]);
            String[] r = rooms.get(code);
            if (r == null) {
                to(from, "NOROOM");
                return;
            }
            if (r[3] != null) {
                to(from, "FULL");
                return;
            }
            r[2] = from.id;
            r[3] = Long.toHexString(rnd.nextLong());
            conns.put(from, new String[]{code, "g"});
            to(from, "JOINED " + code + " " + r[3] + " " + r[4]);
            to(linkOf(r[0]), "GUEST");
        } else if (cmd.equals("BACK") && w.length > 3) {
            String code = Text.upper(w[1]), role = w[2];
            String[] r = rooms.get(code);
            if (r == null || !w[3].equals(role.equals("h") ? r[1] : r[3])) {
                to(from, "NOROOM");
                return;
            }
            if (role.equals("h")) r[0] = from.id;
            else r[2] = from.id;
            conns.put(from, new String[]{code, role});
            MemLink o = linkOf(role.equals("h") ? r[2] : r[0]);
            boolean here = o != null && o.state == Link.OPEN;
            to(from, "BACKOK " + (here ? 1 : 0));
            if (here) to(o, "BACK");
        } else {
            to(from, "ERR unknown");
        }
    }

    synchronized void closed(MemLink l) {
        String[] me = conns.remove(l);
        if (me == null) return;
        String[] r = rooms.get(me[0]);
        if (r == null || !l.id.equals(me[1].equals("h") ? r[0] : r[2])) return;
        to(linkOf(me[1].equals("h") ? r[2] : r[0]), "GONE");
    }

    static int ids;

    static final class MemLink implements Link {
        final MemRelay relay;
        final String id = "c" + (ids++);
        volatile int state = Link.OPEN;
        final LinkedList<String> in = new LinkedList<String>();

        MemLink(MemRelay relay) {
            this.relay = relay;
        }

        public int state() {
            return state;
        }

        public void send(String text) {
            if (state == Link.OPEN) relay.receive(this, text);
        }

        public String poll() {
            synchronized (relay) {
                return in.isEmpty() ? null : in.removeFirst();
            }
        }

        public void close() {
            if (state == Link.CLOSED) return;
            state = Link.CLOSED;
            relay.closed(this);
        }
    }
}
