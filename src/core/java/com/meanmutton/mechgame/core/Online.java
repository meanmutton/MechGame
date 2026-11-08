package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * An online quick battle between two players on their own devices, through the relay in server/relay.yaml.
 *
 * The host opens a room and gets a six-letter code; the guest joins with it. Both build a force at the same time
 * with the host's budget. The guest sends its force to the host, the host lays out the battle and sends all of it
 * back, and from then on both devices run the same battle: each gives orders only for its own side, applies them at
 * once, and sends them over; the other device plays them when it gets them (with the animations). The dice match
 * because both roll with the same generator from the same state (Rng).
 *
 * Every batch of orders carries a fingerprint of the battle after it (Battle.stateHash). If the two devices ever
 * disagree, the host's battle counts: it sends the guest a copy of the whole battle and both carry on from there.
 *
 * Messages to the other player are numbered and kept, so after a dropped connection each side tells the other the
 * last number it got and the other sends the rest again. The relay only sees text (see its protocol).
 */
public final class Online implements Battle.Net {
    /** Change this when a build can no longer play against older ones (the battle rules or the messages change). */
    public static final String PROTOCOL = "1";
    /** The relay this build connects to: wss://... (empty where online play isn't set up). Platforms may override. */
    public static final String RELAY = "";
    /** The relay in use: set by builds that support online play; empty hides the Online button. */
    public static String relayUrl = "";

    public static final int CONNECTING = 0, HOSTING = 1, JOINING = 2, BUILDING = 3, READY = 4, BATTLE = 5, OVER = 6, FAILED = 7;
    private static final int CHUNK = 9000;   // characters per message part (the relay takes up to 32 KB a message)

    /**
     * Tests only (the browser page sets it from window.HEXFRONT_AUTOTEST): this device builds a force, deploys and
     * fights by itself, so a test can play a browser against another build without anyone tapping.
     */
    public static boolean autoplay;

    public static boolean available() {
        return relayUrl != null && relayUrl.length() > 0;
    }

    final Game game;
    public final boolean host;
    /** The side this device plays: the host is Player 1 (west), the guest Player 2 (east). */
    public final int team;
    public int phase = CONNECTING;
    /** Why the game can't go on (shown when phase is FAILED, or when the other player left). */
    public String problem;
    public String code;
    public int budget;
    /** The other player is connected to the relay right now. */
    public boolean partnerHere;
    /** The other player left the game for good. */
    public boolean partnerLeft;
    public Battle battle;
    /** Each side's colors in the battle (team 0 and team 1). */
    public Scheme[] schemes;
    public Company mine;

    private Link link;
    private String token;
    /** The current link has opened and said who we are. */
    private boolean greeted;
    private boolean everOpen;
    private float retryIn, sinceSent, sinceHeard;
    private int retries;
    // numbered messages: what was sent (kept for resending), the last number received, and what waits to be applied
    private final List<String> sent = new ArrayList<String>();
    private int got;
    private final LinkedList<String> inbox = new LinkedList<String>();
    private final LinkedList<Integer> inboxSeq = new LinkedList<Integer>();
    /** The number of the other player's last message dealt with (applied, for battle orders). */
    private int processed;
    private StringBuilder chunks;
    // battle sync
    private final List<String> pending = new ArrayList<String>();
    private boolean awaitingSnap, snapWanted;
    /** Times the guest's battle was replaced by the host's copy (tests). */
    int resyncs;
    // building
    private String theirForce;
    private boolean myForceReady;

    private Online(Game game, boolean host) {
        this.game = game;
        this.host = host;
        this.team = host ? Battle.PLAYER : Battle.ENEMY;
    }

    /** Opens a room with this budget; the code to share arrives shortly. */
    public static Online hostGame(Game game, int budget) {
        Online o = new Online(game, true);
        o.budget = budget;
        o.connect();
        return o;
    }

    public static Online joinGame(Game game, String code) {
        Online o = new Online(game, false);
        o.code = Text.upper(code.trim());
        o.connect();
        return o;
    }

    public static String version() {
        return PROTOCOL + "-" + fingerprint();
    }

    /**
     * A short code for the game's weapons, equipment and designs. Online play needs both players to fight by the
     * same numbers, so builds whose catalogs differ don't pair up (see version).
     */
    static String fingerprint() {
        Chassis[] designs = Chassis.ALL;
        int h = 17;
        for (Wpn w : Wpn.ALL) {
            int[] v = {w.id, w.cls, w.family, w.dmg, w.group, w.heat, w.ammo, w.rMin, w.rShort, w.rMed, w.rLong, w.acc, w.flags, w.splash,
                    w.base, Float.floatToIntBits(w.binMass)};
            for (int x : v) h = h * 31 + x;
            h = h * 31 + w.name.hashCode();
        }
        for (Equip e : Equip.ALL) {
            h = h * 31 + e.id;
            h = h * 31 + e.base;
            h = h * 31 + e.uses;
            h = h * 31 + e.power;
        }
        for (Chassis c : designs) {
            int[] v = {c.id, c.type, c.role, c.maxArmor, c.infra, c.mp, c.sinks, c.heatCap, c.maxExtraSinks, c.equipSlots, c.mounts.length};
            for (int x : v) h = h * 31 + x;
            for (Chassis.Mount m : c.mounts) h = h * 31 + m.loc * 100 + m.cls * 10 + m.arc;
            h = h * 31 + c.name.hashCode();
        }
        String hex = Integer.toHexString(h);
        while (hex.length() < 8) hex = "0" + hex;
        return hex;
    }

    private void connect() {
        link = game.platform.openLink(relayUrl);
        if (link == null) fail("This version of Hexfront can't play online.");
    }

    private void fail(String why) {
        boolean beforeBattle = phase < BATTLE;
        phase = FAILED;
        problem = why;
        if (link != null) link.close();
        link = null;
        // before the battle the waiting screen says what happened; in battle the map shows it in a banner
        if (beforeBattle && game.online == this && !(game.screen() instanceof OnlineScreen)) game.setScreen(new OnlineScreen(game));
    }

    /** Leaves the game for good: the other player is told, and the connection closes. */
    public void leave() {
        if (link != null && link.state() == Link.OPEN && phase != FAILED) {
            flush();
            sendNow("D 0 LEAVE");
        }
        if (link != null) link.close();
        link = null;
        if (phase != FAILED) phase = OVER;
    }

    /** Connected to the relay right now (reconnecting otherwise). */
    public boolean linkUp() {
        return link != null && link.state() == Link.OPEN;
    }

    /** Input waits: the connection is down, the other player is away, or the battle is being re-synced. */
    public boolean blocked() {
        return !linkUp() || !partnerHere || awaitingSnap;
    }

    /** What to tell the player while the game waits on the network, or null. */
    public String waitingText() {
        if (phase == FAILED) return problem;
        if (partnerLeft) return "Your opponent left the game.";
        if (!linkUp()) return "Connection lost. Reconnecting...";
        if (!partnerHere) return "Waiting for your opponent to reconnect...";
        if (awaitingSnap) return "Re-syncing the battle with Player 1...";
        return null;
    }

    // =====================================================================
    // Each frame
    // =====================================================================

    /** Reads what arrived, keeps the connection alive, and plays the other side's orders. Before the screen updates. */
    public void tick(float dt) {
        if (phase == FAILED || phase == OVER) return;
        if (link == null || link.state() == Link.CLOSED) {
            reconnect(dt);
            return;
        }
        if (link.state() == Link.CONNECTING) return;
        if (!greeted) {
            greeted = true;
            everOpen = true;
            retries = 0;
            sinceHeard = 0;
            if (token == null) sendNow(host ? "HOST " + version() : "JOIN " + code);
            else sendNow("BACK " + code + " " + (host ? "h" : "g") + " " + token);   // back in the same room
        }
        for (String m = link.poll(); m != null; m = link.poll()) {
            sinceHeard = 0;
            relay(m);
            if (phase == FAILED) return;
        }
        sinceSent += dt;
        sinceHeard += dt;
        // the relay drops a connection after ten quiet minutes, and the pings show when it has died unnoticed
        if (sinceSent > 30) sendNow("PING");
        if (sinceHeard > 75) {
            link.close();
            return;
        }
        if (partnerLeft && phase != BATTLE && phase != OVER) {
            fail("Your opponent left the game.");
            return;
        }
        process();
        if (autoplay) autoStep();
    }

    /** Tests: one step of playing by itself (see autoplay). */
    private void autoStep() {
        if (phase == BUILDING && mine != null && !myForceReady && game.screen() instanceof HqScreen) {
            java.util.Random r = new java.util.Random(team * 31 + 7);
            for (int tries = 0; tries < 200 && mine.deployed.size() < 5; tries++) {
                Chassis ch = Chassis.SHOP[r.nextInt(Chassis.SHOP.length)];
                Unit u = new Unit(ch);
                if (u.value() > mine.cash || ch.type == UnitType.INFANTRY) continue;
                mine.cash -= u.value();
                mine.roster.add(u);
                mine.deployed.add(u);
            }
            ready(mine);
            if (phase == READY) game.setScreen(new OnlineScreen(game));
            return;
        }
        if (battle != null) {
            battle.autoPlayer = true;
            if (battle.deploying && battle.deployTeam == team && !battle.busy() && !blocked()) battle.finishDeployment();
            if (battle.result != Battle.RESULT_NONE && !battle.busy()) game.crumb("online result " + battle.result + " hash " + battle.stateHash());
        }
    }

    /** Tests: the battle's fingerprint and result, to compare two devices. */
    public String testState() {
        return battle == null ? "none" : battle.result + " " + battle.stateHash() + " " + resyncs;
    }

    private void reconnect(float dt) {
        if (token == null) {
            // never got into a room: nothing to come back to
            fail(everOpen ? "The connection to the server closed." : "Couldn't reach the server. Check your internet connection.");
            return;
        }
        retryIn -= dt;
        if (retryIn > 0) return;
        retries++;
        retryIn = Math.min(8, 1 << Math.min(3, retries));
        link = game.platform.openLink(relayUrl);
        greeted = false;
    }

    /** Sends the orders given this frame. After the screen updates. */
    public void flush() {
        if (pending.isEmpty() || battle == null) return;
        StringBuilder o = new StringBuilder();
        for (String p : pending) o.append(o.length() > 0 ? ";" : "").append(p);
        pending.clear();
        send("C " + battle.stateHash() + " " + o);
    }

    /** Battle.Net: an order given on this device. */
    public void order(String order) {
        pending.add(order);
    }

    /** Battle.Net: the computer (tests) waits too while the game waits on the network. */
    public boolean waiting() {
        return blocked();
    }

    // =====================================================================
    // The relay's messages
    // =====================================================================

    private void relay(String m) {
        String[] w = Text.words(m);
        String cmd = w[0];
        if (cmd.equals("D")) {
            data(m);
        } else if (cmd.equals("ROOM")) {
            code = w[1];
            token = w[2];
            phase = HOSTING;
        } else if (cmd.equals("JOINED")) {
            token = w[2];
            partnerHere = true;
            if (!w[3].equals(version())) {
                send("LEAVE version");
                fail("Your friend has a different version of Hexfront. Both of you need the latest one.");
                return;
            }
            phase = JOINING;   // the host's setup message comes next
        } else if (cmd.equals("GUEST")) {
            partnerHere = true;
            if (phase == HOSTING) {
                send("SETUP " + budget);
                phase = BUILDING;
                game.onlineBuild();
            }
        } else if (cmd.equals("NOROOM")) {
            if (token == null) fail(host ? "The server lost the room. Try again." : "There's no game with the code " + code + ". Check it with your friend.");
            else fail("The game has ended on the server.");
        } else if (cmd.equals("FULL")) {
            fail("Someone has already joined the game " + code + ".");
        } else if (cmd.equals("GONE")) {
            partnerHere = false;
        } else if (cmd.equals("BACK")) {
            partnerHere = true;
            sendNow("D 0 HAVE " + got);
        } else if (cmd.equals("BACKOK")) {
            partnerHere = w.length > 1 && w[1].equals("1");
            if (partnerHere) sendNow("D 0 HAVE " + got);
        } else if (cmd.equals("AWAY")) {
            partnerHere = false;
        } else if (cmd.equals("ERR")) {
            if (token == null) fail("The server refused: " + m.substring(Math.min(m.length(), 4)));
        }
    }

    /** "D <number> <type> <rest>": a message from the other player. Number 0 is not kept or resent. */
    private void data(String m) {
        int a = m.indexOf(' ', 2);
        if (a < 0) return;
        int n;
        try {
            n = Integer.parseInt(m.substring(2, a));
        } catch (NumberFormatException e) {
            return;
        }
        String body = m.substring(a + 1);
        if (n == 0) {
            if (body.startsWith("HAVE ")) {
                int have = Integer.parseInt(body.substring(5).trim());
                for (int i = have; i < sent.size(); i++) sendNow(sent.get(i));
            } else if (body.startsWith("LEAVE")) {
                partnerLeft = true;
                partnerHere = false;
            }
            return;
        }
        if (n <= got) return;   // seen it already (sent again after a reconnect)
        if (n != got + 1) return;   // a gap: the rest comes again after HAVE
        got = n;
        if (body.startsWith("K ")) {
            // one part of a long message: "K <last 0/1> <text>"
            if (chunks == null) chunks = new StringBuilder();
            chunks.append(body.substring(4));
            if (body.charAt(2) == '1') {
                inbox.add(chunks.toString());
                inboxSeq.add(n);
                chunks = null;
            }
            return;
        }
        inbox.add(body);
        inboxSeq.add(n);
    }

    /** Works through the other player's messages in order; battle orders wait for the battle's animations. */
    private void process() {
        if (host && snapWanted && battle != null && quiet()) sendSnapshot();
        while (!inbox.isEmpty()) {
            String body = inbox.getFirst();
            if (body.startsWith("C ")) {
                if (battle == null || awaitingSnap) {   // already wrong: skip to the copy that is on its way
                    take();
                    continue;
                }
                if (battle.busy()) return;
                take();
                int sp = body.indexOf(' ', 2);
                int want = Integer.parseInt(body.substring(2, sp));
                apply(battle, body.substring(sp + 1));
                if (battle.stateHash() != want) outOfStep();
                continue;
            }
            take();
            message(body);
            if (phase == FAILED) return;
        }
    }

    private void take() {
        inbox.removeFirst();
        processed = inboxSeq.removeFirst();
    }

    private boolean quiet() {
        return !battle.busy() && battle.aiPlanner == null;
    }

    /** The two battles disagree: the host's counts. */
    private void outOfStep() {
        game.crumb("online: out of step");
        if (host) snapWanted = true;
        else {
            awaitingSnap = true;
            send("SYNC");
        }
    }

    private void message(String body) {
        String type = body.indexOf(' ') < 0 ? body : body.substring(0, body.indexOf(' '));
        String rest = body.indexOf(' ') < 0 ? "" : body.substring(body.indexOf(' ') + 1);
        if (type.equals("SETUP") && !host) {
            budget = Integer.parseInt(rest.trim());
            phase = BUILDING;
            game.onlineBuild();
        } else if (type.equals("FORCE") && host) {
            theirForce = rest;
            if (myForceReady) startBattle();
        } else if (type.equals("START") && !host) {
            receiveBattle(rest, true);
        } else if (type.equals("SYNC") && host) {
            snapWanted = true;
        } else if (type.equals("SNAP") && !host) {
            receiveBattle(rest, false);
        } else if (type.equals("LEAVE")) {
            partnerLeft = true;
            if (rest.startsWith("version")) fail("Your friend has a different version of Hexfront. Both of you need the latest one.");
            else if (phase != BATTLE) fail("Your opponent left the game.");
        }
    }

    // =====================================================================
    // Sending
    // =====================================================================

    private void sendNow(String m) {
        if (link != null && link.state() == Link.OPEN) {
            link.send(m);
            sinceSent = 0;
        }
    }

    /** A numbered message to the other player, kept to send again after a reconnect; long ones go in parts. */
    private void send(String body) {
        if (body.length() <= CHUNK) {
            numbered(body);
            return;
        }
        for (int i = 0; i < body.length(); i += CHUNK) {
            boolean last = i + CHUNK >= body.length();
            numbered("K " + (last ? 1 : 0) + " " + body.substring(i, Math.min(body.length(), i + CHUNK)));
        }
    }

    private void numbered(String body) {
        String m = "D " + (sent.size() + 1) + " " + body;
        sent.add(m);
        sendNow(m);
    }

    // =====================================================================
    // Building forces and starting the battle
    // =====================================================================

    /** This player's force is ready (the HQ's Ready button). */
    public void ready(Company c) {
        myForceReady = true;
        phase = READY;
        if (host) {
            if (theirForce != null) startBattle();
        } else {
            StringBuilder f = new StringBuilder(c.scheme == null ? Scheme.ENEMY_DEFAULT.serialize() : c.scheme.serialize());
            for (Unit u : c.deployed) f.append('\n').append(u.serialize());
            send("FORCE " + f);
        }
    }

    /** Host: both forces are in. Lay out the battle, send the guest all of it, and start. */
    private void startBattle() {
        List<Unit> theirs = new ArrayList<Unit>();
        String[] lines = Text.split(theirForce, '\n');
        Scheme their = Scheme.parse(lines[0], Scheme.ENEMY_DEFAULT);
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].length() == 0) continue;
            theirs.add(Unit.parse(lines[i]));
        }
        if (theirs.isEmpty()) {
            fail("Your opponent's force didn't arrive.");
            return;
        }
        game.hotseatEnemy = their;
        schemes = new Scheme[]{mine.scheme != null ? mine.scheme : Scheme.PLAYER_DEFAULT, their};
        Battle b = game.onlineBattle(mine, theirs);
        attach(b);
        send("START " + snapshot(b));
        phase = BATTLE;
        game.setScreen(new BattleScreen(game, b));
    }

    private void attach(Battle b) {
        battle = b;
        b.localTeam = team;
        b.net = this;
    }

    private String snapshot(Battle b) {
        return BattleSave.write(game.company, b);
    }

    /** Host: sends the guest a copy of the battle as it is now (after the guest's orders received so far). */
    private void sendSnapshot() {
        snapWanted = false;
        send("SNAP " + processed + "\n" + snapshot(battle));
    }

    /** Guest: the battle from the host, at the start or to get back in step. */
    private void receiveBattle(String data, boolean first) {
        int hostHad = 0;
        if (!first) {
            int nl = data.indexOf('\n');
            hostHad = Integer.parseInt(data.substring(0, nl).trim());
            data = data.substring(nl + 1);
        }
        Company keep = game.company;
        Scheme keepEnemy = game.hotseatEnemy;
        Battle b = BattleSave.read(game, data);
        Company hostCo = game.company;
        game.company = keep;
        if (b == null) {
            game.hotseatEnemy = keepEnemy;
            fail("The battle didn't arrive in one piece.");
            return;
        }
        if (!b.log.isEmpty() && b.log.get(b.log.size() - 1).equals("Battle resumed.")) b.log.remove(b.log.size() - 1);
        schemes = new Scheme[]{hostCo.scheme != null ? hostCo.scheme : Scheme.PLAYER_DEFAULT,
                game.hotseatEnemy != null ? game.hotseatEnemy : Scheme.ENEMY_DEFAULT};
        if (!first) {
            // orders this device gave after the ones the host had applied are still on their way to it: replay them here
            for (int i = hostHad; i < sent.size(); i++) {
                String m = sent.get(i);
                String body = m.substring(m.indexOf(' ', 2) + 1);
                if (body.startsWith("C ")) apply(b, body.substring(body.indexOf(' ', 2) + 1));
            }
        }
        if (!first) resyncs++;
        attach(b);
        awaitingSnap = false;
        phase = BATTLE;
        game.setScreen(new BattleScreen(game, b));
    }

    // =====================================================================
    // Orders
    // =====================================================================

    /** Plays orders given on the other device ("B 3;M 3 41,42;N 3"), in order, without sending them back. */
    static void apply(Battle b, String orders) {
        boolean was = b.remote;
        b.remote = true;
        try {
            for (String o : Text.split(orders, ';')) applyOne(b, o);
        } finally {
            b.remote = was;
        }
    }

    private static void applyOne(Battle b, String o) {
        String[] f = Text.words(o);
        if (f.length == 0 || f[0].length() != 1) return;
        try {
            char c = f[0].charAt(0);
            if (c == 'D') {
                if (b.deploying && b.deployTeam == Integer.parseInt(f[1])) b.finishDeployment();
                return;
            }
            if (c == 'W') {
                b.concede(Integer.parseInt(f[1]));
                return;
            }
            Unit u = unit(b, f[1]);
            if (u == null) return;
            switch (c) {
                case 'B': b.beginActivation(u); break;
                case 'S': b.switchTo(u); break;
                case 'N': b.endActivation(u); break;
                case 'U': b.undoMove(u); break;
                case 'T': b.turbo(u); break;
                case 'R': b.rotate(u, Integer.parseInt(f[2])); break;
                case 'A': b.face(u, Integer.parseInt(f[2])); break;
                case 'E': b.useEquip(u, Integer.parseInt(f[2])); break;
                case 'P': b.place(u, Integer.parseInt(f[2]), Integer.parseInt(f[3])); break;
                case 'M': {
                    String[] p = Text.split(f[2], ',');
                    int[] path = new int[p.length];
                    for (int i = 0; i < p.length; i++) path[i] = Integer.parseInt(p[i]);
                    b.move(u, path);
                    break;
                }
                case 'X': {
                    Unit t = unit(b, f[2]);
                    String bits = f[3];
                    boolean[] use = new boolean[u.weapon.length];
                    for (int i = 0; i < use.length && i < bits.length(); i++) use[i] = bits.charAt(i) == '1';
                    if (t != null) b.fire(u, t, use);
                    break;
                }
                default: break;
            }
        } catch (RuntimeException e) {
            // a garbled order changes nothing here; the fingerprint check then puts the battles back in step
        }
    }

    private static Unit unit(Battle b, String s) {
        int i = Integer.parseInt(s);
        return i >= 0 && i < b.units.size() ? b.units.get(i) : null;
    }
}
