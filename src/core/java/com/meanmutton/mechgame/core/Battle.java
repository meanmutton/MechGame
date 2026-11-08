package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Rules engine for one battle. Rounds are made of alternating activations: the side that wins
 * initiative activates one unit (move, then fire), then the other side activates one, and so on.
 * Results are decided immediately; the visual effects are queued and played back over time.
 */
public final class Battle {
    public static final int PLAYER = 0, ENEMY = 1;
    public static final int MAX_ROUNDS = 20;
    public static final int RESULT_NONE = 0, RESULT_WIN = 1, RESULT_LOSS = 2, RESULT_DRAW = 3, RESULT_WITHDRAW = 4;

    public final Game game;
    public final HexMap map;
    public final Random rnd;
    public final List<Unit> units = new ArrayList<Unit>();
    public final int difficulty;
    public int round = 1;
    public int initiative;     // side that activates first this round
    public int side;           // side whose activation it is
    public Unit active;        // unit currently activating (may be null while choosing)
    public int result = RESULT_NONE;
    public final List<String> log = new ArrayList<String>();
    public final List<Fx> fx = new ArrayList<Fx>();
    public float fxClock;
    public final int[] startValue = new int[2];
    /** Scenario rules (campaigns): length, initiative lean, edge, and time-out lean. */
    public int maxRounds = MAX_ROUNDS;
    /** Chance the player wins initiative each round. */
    public float playerInitiative = 0.5f;
    /** Side that wins initiative in round 1 (the scenario edge), or -1 to roll. */
    public int firstInitiative = -1;
    /** Added to the player's share of surviving value when time runs out. */
    public float timeEdge;

    // movement animation
    public int[] movePath;
    public float moveT;
    public Unit mover;

    // undo support for the human's move
    int undoQ, undoR, undoFacing, undoMoved;
    boolean undoAvailable;

    Ai.Turn aiTurn;
    volatile Ai.Planner aiPlanner;
    /** Bumped whenever the battle state changes in a way worth saving (see BattleSave). */
    public int version;
    /** Tests: the computer plays the player's side too (and, in a hotseat battle, both sides). */
    public boolean autoPlayer;
    /** Two people on one device: both sides are human, Player 1 is PLAYER (west) and Player 2 is ENEMY (east). */
    public final boolean hotseat;
    /** Hotseat deployment: whose units are being placed (the other side's are hidden meanwhile). */
    public int deployTeam = PLAYER;
    /** Hotseat: the side that conceded, or -1. */
    public int conceded = -1;
    public float aiDelay;
    /**
     * Online: the side this device plays, or -1 for a battle on one device. An online battle runs on both players'
     * devices at once (hotseat rules, both sides human); each device gives orders only for its own side, sends them to
     * the other through {@link #net}, and plays the other's orders as they arrive (see Online).
     */
    public int localTeam = -1;
    /** Online: where this device's orders go. */
    public Net net;
    /** Applying an order from the other device (so it isn't sent back), or a step the battle takes on its own. */
    boolean remote;

    /** Receives every order given on this device in an online battle, encoded as text (see Online.apply). */
    public interface Net {
        void order(String order);

        /** The game is waiting on the network: nothing on this device may give orders. */
        boolean waiting();
    }

    public boolean online() {
        return localTeam >= 0;
    }

    private Random aiNoise;

    /**
     * The computer's own dice for its planning. Online (tests) the computer plans on one device only, so it must not
     * roll the battle's dice, which both devices keep in step; elsewhere it uses them as it always has.
     */
    Random aiDice() {
        if (!online()) return rnd;
        if (aiNoise == null) aiNoise = new Random(localTeam * 7919L + 13);
        return aiNoise;
    }

    private void tell(String order) {
        if (net != null && !remote) net.order(order);
    }

    private int idx(Unit u) {
        return units.indexOf(u);
    }

    public Battle(Game game, List<Unit> mine, List<Unit> theirs, HexMap map, Random rnd, int difficulty) {
        this(game, mine, theirs, map, rnd, difficulty, false);
    }

    public Battle(Game game, List<Unit> mine, List<Unit> theirs, HexMap map, Random rnd, int difficulty, boolean hotseat) {
        this.game = game;
        this.hotseat = hotseat;
        this.map = map;
        this.rnd = rnd;
        this.difficulty = difficulty;
        for (Unit u : mine) {
            u.team = PLAYER;
            units.add(u);
        }
        for (Unit u : theirs) {
            u.team = ENEMY;
            units.add(u);
        }
        for (Unit u : units) {
            u.heat = 0;
            u.activated = false;
            u.shutdown = false;
            u.staggered = false;
            u.ionized = false;
            u.movedHexes = 0;
            u.deathShown = false;
            u.kills = 0;
            u.damageDealt = 0;
            for (int i = 0; i < u.equip.length; i++) u.eqUses[i] = u.equip[i] >= 0 ? Equip.ALL[u.equip[i]].uses : 0;
            startValue[u.team] += u.value();
        }
        deploy();
        if (!hotseat) deployInfantryForward(ENEMY);   // in a hotseat battle Player 2 places their own
        deploying = true;   // the player places units, then finishDeployment() starts round 1
    }

    /** Rebuilds a battle from a save (BattleSave): the units already carry their positions and state. */
    Battle(Game game, List<Unit> all, HexMap map, Random rnd, int difficulty, boolean hotseat) {
        this.game = game;
        this.hotseat = hotseat;
        this.map = map;
        this.rnd = rnd;
        this.difficulty = difficulty;
        units.addAll(all);
        for (Unit u : units) syncDraw(u);
    }

    // =====================================================================
    // Setup
    // =====================================================================

    // =====================================================================
    // Deployment phase
    // =====================================================================

    /** True before round 1 while the player arranges units. */
    public boolean deploying;
    public static final int DEPLOY_COLS = 4;

    /** Is this side played by a person (rather than the computer)? */
    public boolean human(int team) {
        return !autoPlayer && (hotseat || team == PLAYER);
    }

    /** The side a person is placing units for right now. */
    public int placingTeam() {
        return hotseat ? deployTeam : PLAYER;
    }

    /** Hotseat deployment hides the other side's units, so neither player sees where the other set up. */
    public boolean hidden(Unit u) {
        return hotseat && deploying && u.team != (localTeam >= 0 ? localTeam : deployTeam);
    }

    /** Is the hex inside this side's home deployment zone (its own edge of the map)? */
    public boolean inHomeZone(int team, int c) {
        return team == PLAYER ? c < DEPLOY_COLS : c >= map.cols - DEPLOY_COLS;
    }

    /**
     * Infantry may be placed on any open hex; everything else stays in the home deployment zone. Hidden units
     * don't block (that would give them away); clashes are sorted out when deployment ends.
     */
    public boolean canPlace(Unit u, int c, int r) {
        if (!deploying || u.team != placingTeam() || u.destroyed || !map.in(c, r)) return false;
        for (Unit at : units) {
            if (at != u && !at.destroyed && at.q == c && at.r == r && !hidden(at)) return false;
        }
        if (!map.passable(u.type, c, r)) return false;
        if (u.ch.fixed()) return false;   // emplacements are built where they stand
        if (u.type == UnitType.INFANTRY) return true;
        return inHomeZone(u.team, c);
    }

    public void place(Unit u, int c, int r) {
        version++;
        tell("P " + idx(u) + " " + c + " " + r);
        if (!canPlace(u, c, r)) return;
        u.q = c;
        u.r = r;
        syncDraw(u);
        game.sfx(Platform.SND_STEP, 0.4f);
    }

    public void finishDeployment() {
        version++;
        tell("D " + deployTeam);
        if (!deploying) return;
        if (hotseat && deployTeam == PLAYER) {
            // Player 1 is done: Player 2 places their units next, without seeing Player 1's
            if (autoPlayer) deployInfantryForward(PLAYER);
            deployTeam = ENEMY;
            return;
        }
        if (hotseat) {
            if (autoPlayer) deployInfantryForward(ENEMY);
            resolveClashes();
        } else if (autoPlayer) deployInfantryForward(PLAYER);
        deploying = false;
        startRound();
    }

    /**
     * Hotseat: two players placed units without seeing each other's, so two can end up on one hex. The unit away
     * from its own home zone (an ambush) gives way and sets up on the nearest open hex it can stand on.
     */
    private void resolveClashes() {
        for (Unit u : units) {
            if (u.destroyed) continue;
            Unit other = null;
            for (Unit o : units) if (o != u && !o.destroyed && o.q == u.q && o.r == u.r) other = o;
            if (other == null) continue;
            Unit mover = inHomeZone(u.team, u.q) && !inHomeZone(other.team, other.q) ? other : u;
            int bestC = -1, bestR = -1, bestD = Integer.MAX_VALUE;
            for (int c = 0; c < map.cols; c++) {
                for (int r = 0; r < map.rows; r++) {
                    if (unitAt(c, r) != null || !map.passable(mover.type, c, r)) continue;
                    if (mover.type != UnitType.INFANTRY && !inHomeZone(mover.team, c)) continue;
                    int d = HexMap.distance(mover.q, mover.r, c, r);
                    if (d < bestD) {
                        bestD = d;
                        bestC = c;
                        bestR = r;
                    }
                }
            }
            if (bestC < 0) continue;
            log(mover.name + "'s spot was taken; it set up nearby");
            mover.q = bestC;
            mover.r = bestR;
            syncDraw(mover);
        }
    }

    /** Computer-controlled infantry dig in on cover hexes out in the field (towns and woods first). */
    private void deployInfantryForward(int team) {
        for (Unit u : units) {
            if (u.team != team || u.type != UnitType.INFANTRY) continue;
            int bestC = u.q, bestR = u.r;
            float best = -Float.MAX_VALUE;
            for (int c = 0; c < map.cols; c++) {
                for (int r = 0; r < map.rows; r++) {
                    boolean ownHalf = team == ENEMY ? c >= map.cols / 2 - 1 : c <= map.cols / 2;
                    if (!ownHalf || unitAt(c, r) != null || !map.passable(u.type, c, r)) continue;
                    int k = map.idx(c, r);
                    int ter = map.terrain[k];
                    float s = ter == HexMap.TOWN ? 6 : (ter == HexMap.HEAVY_WOODS ? 5 : (ter == HexMap.LIGHT_WOODS ? 3 : 0));
                    s += map.elev[k] * 0.8f;
                    int near = 99;
                    for (Unit o : units) if (o.team != team) near = Math.min(near, HexMap.distance(c, r, o.q, o.r));
                    if (near < 4) s -= 10;
                    // mortars stay back; everyone else creeps toward the middle
                    float mid = Math.abs(c - map.cols / 2f);
                    s += u.ch.role == Chassis.R_ARTILLERY ? mid * 0.3f : -mid * 0.5f;
                    s += rnd.nextFloat() * 2f;
                    if (s > best) {
                        best = s;
                        bestC = c;
                        bestR = r;
                    }
                }
            }
            u.q = bestC;
            u.r = bestR;
            u.facing = team == PLAYER ? 1 : 4;
            syncDraw(u);
        }
    }

    private void deploy() {
        for (int team = 0; team < 2; team++) {
            List<Unit> list = new ArrayList<Unit>();
            for (Unit u : units) if (u.team == team) list.add(u);
            int[] colsOrder = team == PLAYER ? new int[]{1, 0, 2, 3} : new int[]{map.cols - 2, map.cols - 1, map.cols - 3, map.cols - 4};
            int n = list.size();
            for (int i = 0; i < n; i++) {
                Unit u = list.get(i);
                int wantRow = (int) ((i + 0.5f) * map.rows / n);
                boolean placed = false;
                for (int ring = 0; ring < map.rows && !placed; ring++) {
                    for (int ci = 0; ci < colsOrder.length && !placed; ci++) {
                        for (int sgn = -1; sgn <= 1 && !placed; sgn += 2) {
                            int r = wantRow + sgn * ring;
                            int c = colsOrder[ci];
                            if (!map.in(c, r) || unitAt(c, r) != null || !map.passable(u.type, c, r)) continue;
                            if (u.type != UnitType.VTOL && map.terrain[map.idx(c, r)] == HexMap.DEEP && !UnitType.isMech(u.type)
                                    && u.type != UnitType.HOVER) continue;
                            u.q = c;
                            u.r = r;
                            placed = true;
                        }
                    }
                }
                u.facing = team == PLAYER ? (rnd.nextBoolean() ? 0 : 1) : (rnd.nextBoolean() ? 3 : 4);
                syncDraw(u);
            }
        }
    }

    public void syncDraw(Unit u) {
        u.drawX = HexMap.px(u.q, u.r);
        u.drawY = HexMap.py(u.q, u.r);
        u.drawFacing = HexMap.DIR_ANGLE[u.facing];
    }

    // =====================================================================
    // Queries
    // =====================================================================

    public Unit unitAt(int c, int r) {
        for (Unit u : units) if (!u.destroyed && u.q == c && u.r == r) return u;
        return null;
    }

    public Unit unitAtDrawn(int c, int r) {
        for (Unit u : units) if ((!u.destroyed || !u.deathShown) && u.q == c && u.r == r) return u;
        return null;
    }

    public List<Unit> team(int t) {
        List<Unit> l = new ArrayList<Unit>();
        for (Unit u : units) if (u.team == t && !u.destroyed) l.add(u);
        return l;
    }

    public int alive(int t) {
        int n = 0;
        for (Unit u : units) if (u.team == t && !u.destroyed) n++;
        return n;
    }

    public boolean busy() {
        return mover != null || !fx.isEmpty();
    }

    /** Is it a person's turn (rather than the computer's)? In a hotseat battle it always is. */
    public boolean playerTurn() {
        return !deploying && result == RESULT_NONE && human(side);
    }

    /** "Player 1" / "Player 2" in a hotseat battle; "you" / "the enemy" otherwise. */
    public String sideName(int team) {
        return hotseat ? "Player " + (team + 1) : (team == PLAYER ? "you" : "the enemy");
    }

    public int mpAvailable(Unit u) {
        if (u.shutdown) return 0;
        int mp = u.baseMp() - u.heatPenaltyMp();
        if (u.staggered) mp = mp / 2;
        if (u.turboOn) mp += Math.max(3, u.equipPower(Equip.TURBO));
        return Math.max(0, mp);
    }

    // =====================================================================
    // Movement
    // =====================================================================

    /** Dijkstra: cost to reach each hex (Float.MAX_VALUE if unreachable) within the unit's MP. */
    public float[] reach(Unit u, int[] prev, int mp) {
        int n = map.cols * map.rows;
        float[] dist = new float[n];
        Arrays.fill(dist, Float.MAX_VALUE);
        if (prev != null) Arrays.fill(prev, -1);
        boolean[] done = new boolean[n];
        int s = map.idx(u.q, u.r);
        dist[s] = 0;
        int[] heap = new int[n];
        int hn = 0;
        heap[hn++] = s;
        while (hn > 0) {
            int bi = 0;
            for (int i = 1; i < hn; i++) if (dist[heap[i]] < dist[heap[bi]]) bi = i;
            int k = heap[bi];
            heap[bi] = heap[--hn];
            if (done[k]) continue;
            done[k] = true;
            int c = k % map.cols, r = k / map.cols;
            for (int d = 0; d < 6; d++) {
                int nc = map.ncol(c, r, d), nr = map.nrow(c, r, d);
                if (!map.in(nc, nr)) continue;
                int nk = map.idx(nc, nr);
                if (done[nk]) continue;
                Unit occ = unitAt(nc, nr);
                if (occ != null && occ.team != u.team && u.type != UnitType.VTOL) continue;
                float cost = map.moveCost(u.type, c, r, nc, nr);
                if (cost >= HexMap.IMPASSABLE) continue;
                float nd = dist[k] + cost;
                if (nd > mp + 0.001f) continue;
                if (nd < dist[nk]) {
                    dist[nk] = nd;
                    if (prev != null) prev[nk] = k;
                    heap[hn++] = nk;
                }
            }
        }
        // can't end in an occupied hex
        for (Unit o : units) {
            if (o == u || o.destroyed) continue;
            int k = map.idx(o.q, o.r);
            dist[k] = Float.MAX_VALUE;
        }
        return dist;
    }

    public int[] pathTo(int[] prev, int from, int to) {
        List<Integer> l = new ArrayList<Integer>();
        int k = to;
        int guard = 0;
        while (k >= 0 && k != from && guard++ < 500) {
            l.add(0, k);
            k = prev[k];
        }
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    /** Starts animated movement along a path (list of hex indices, not including the start). */
    public void move(Unit u, int[] path) {
        version++;
        if (net != null && !remote) {
            StringBuilder o = new StringBuilder("M ").append(idx(u)).append(' ');
            for (int i = 0; i < path.length; i++) o.append(i > 0 ? "," : "").append(path[i]);
            tell(o.toString());
        }
        if (path.length == 0) return;
        undoQ = u.q;
        undoR = u.r;
        undoFacing = u.facing;
        undoMoved = u.movedHexes;
        undoAvailable = human(u.team);
        mover = u;
        movePath = path;
        moveT = 0;
        u.moved = true;
        u.movedHexes = path.length;
        int last = path[path.length - 1];
        int pk = path.length >= 2 ? path[path.length - 2] : map.idx(u.q, u.r);
        u.facing = HexMap.dirFromAngle(HexMap.angleBetween(pk % map.cols, pk / map.cols, last % map.cols, last / map.cols));
        u.q = last % map.cols;
        u.r = last / map.cols;
        game.sfx(UnitType.isMech(u.type) ? Platform.SND_STEP : Platform.SND_HOVER, 0.6f);
    }

    public boolean canUndo(Unit u) {
        return undoAvailable && u == active && u.moved && !u.fired && mover == null;
    }

    public void undoMove(Unit u) {
        version++;
        tell("U " + idx(u));
        if (!canUndo(u)) return;
        u.q = undoQ;
        u.r = undoR;
        u.facing = undoFacing;
        u.movedHexes = undoMoved;
        u.moved = false;
        undoAvailable = false;
        syncDraw(u);
    }

    private void updateMove(float dt) {
        if (mover == null) return;
        moveT += dt * 7f;
        int seg = (int) moveT;
        Unit u = mover;
        if (seg >= movePath.length) {
            syncDraw(u);
            mover = null;
            return;
        }
        float f = moveT - seg;
        int a = seg == 0 ? -1 : movePath[seg - 1];
        int b = movePath[seg];
        float ax, ay;
        if (a < 0) {
            int start = startOfPath();
            ax = HexMap.px(start % map.cols, start / map.cols);
            ay = HexMap.py(start % map.cols, start / map.cols);
        } else {
            ax = HexMap.px(a % map.cols, a / map.cols);
            ay = HexMap.py(a % map.cols, a / map.cols);
        }
        float bx = HexMap.px(b % map.cols, b / map.cols), by = HexMap.py(b % map.cols, b / map.cols);
        u.drawX = ax + (bx - ax) * f;
        u.drawY = ay + (by - ay) * f;
        float want = (float) Math.toDegrees(Math.atan2(by - ay, bx - ax));
        u.drawFacing += HexMap.angleDiff(want, u.drawFacing) * Math.min(1f, dt * 14f);
        if (f < dt * 7f) game.sfx(UnitType.isMech(u.type) ? Platform.SND_STEP : Platform.SND_HOVER, 0.25f);
    }

    private int startOfPath() {
        return map.idx(undoQ, undoR);
    }

    // =====================================================================
    // Targeting
    // =====================================================================

    public boolean inEcm(Unit target) {
        if (map.weather.electronicsDown) return false;
        for (Unit o : units) {
            if (o.destroyed || o.team != target.team || !o.hasEquip(Equip.ECM)) continue;
            if (HexMap.distance(o.q, o.r, target.q, target.r) <= o.equipPower(Equip.ECM)) return true;
        }
        return false;
    }

    /** The best command uplink bonus reaching this unit (its own or a friend's within 3 hexes), or 0. */
    private int uplinkNear(Unit a) {
        if (map.weather.electronicsDown) return 0;
        int best = 0;
        for (Unit o : units) {
            if (o.destroyed || o.team != a.team || !o.hasEquip(Equip.UPLINK)) continue;
            if (HexMap.distance(o.q, o.r, a.q, a.r) <= 3) best = Math.max(best, o.equipPower(Equip.UPLINK));
        }
        return best;
    }

    public int losPoints(Unit a, Unit t) {
        int d = HexMap.distance(a.q, a.r, t.q, t.r);
        if (d > map.weather.maxSight) return -1;
        return map.los(a.q, a.r, UnitType.HEIGHT[a.type], t.q, t.r, UnitType.HEIGHT[t.type]);
    }

    public boolean sensorsWork(Unit u) {
        return u.hasEquip(Equip.SENSORS) && !map.weather.electronicsDown;
    }

    /** A friendly unit (other than the shooter) that can see the target, for indirect fire. */
    public Unit spotter(Unit a, Unit t) {
        boolean ecm = inEcm(t);
        Unit best = null;
        for (Unit o : units) {
            if (o.destroyed || o.team != a.team || o.shutdown) continue;
            if (ecm && !sensorsWork(o)) continue;
            if (losPoints(o, t) >= 0) {
                best = o;
                if (o != a) break;
            }
        }
        return best;
    }

    public boolean inArc(Unit a, int mount, Unit t) {
        int arc = a.ch.mounts[mount].arc;
        if (arc == Chassis.ARC_TURRET) return true;
        float ang = HexMap.angleBetween(a.q, a.r, t.q, t.r);
        float diff = Math.abs(HexMap.angleDiff(ang, HexMap.DIR_ANGLE[a.facing]));
        return diff <= (arc == Chassis.ARC_WIDE ? 120.5f : 60.5f);
    }

    /** Arc of the target that faces the attacker. */
    public int attackArc(Unit a, Unit t) {
        float ang = HexMap.angleBetween(t.q, t.r, a.q, a.r);
        float diff = HexMap.angleDiff(ang, HexMap.DIR_ANGLE[t.facing]);
        if (Math.abs(diff) <= 60.5f) return UnitType.ARC_FRONT;
        if (Math.abs(diff) >= 119.5f) return UnitType.ARC_REAR;
        return diff > 0 ? UnitType.ARC_RIGHT : UnitType.ARC_LEFT;
    }

    public String lastReason = "";

    /** Percent chance to hit, or -1 if this weapon can't fire at that target right now (lastReason says why). */
    public int hitChance(Unit a, int m, Unit t, StringBuilder why) {
        Wpn w = a.wpn(m);
        lastReason = "";
        if (w == null || !a.weaponUsable(m)) {
            lastReason = a.wpnBroken[m] || (w != null && a.locDestroyed(a.ch.mounts[m].loc)) ? "Destroyed" : "No ammo";
            return -1;
        }
        int d = HexMap.distance(a.q, a.r, t.q, t.r);
        if (w.isMelee() || (w.family == Wpn.F_INFANTRY && w.rLong == 1)) {
            if (d != 1) {
                lastReason = "Must be adjacent";
                return -1;
            }
            if (t.type == UnitType.VTOL) {
                lastReason = "Can't reach a VTOL";
                return -1;
            }
        }
        if (d > w.rLong) {
            lastReason = "Out of range";
            return -1;
        }
        if (!inArc(a, m, t)) {
            lastReason = "Out of arc";
            return -1;
        }
        int pts = losPoints(a, t);
        boolean indirect = false;
        if (pts < 0) {
            if (w.has(Wpn.INDIRECT)) {
                if (spotter(a, t) == null) {
                    lastReason = inEcm(t) ? "ECM blocks spotting" : "No spotter";
                    return -1;
                }
                indirect = true;
            } else {
                lastReason = "No line of sight";
                return -1;
            }
        }
        int chance = 80 + w.acc;
        if (why != null) why.append("Base ").append(80 + w.acc).append('%');
        boolean ecm = inEcm(t);
        boolean elecUp = !map.weather.electronicsDown;
        if (w.isMelee()) {
            chance += mod(why, -Math.min(20, (t.movedHexes / 2) * 5), "target moved");
            chance += mod(why, -a.heatPenaltyHit(), "heat");
            if (a.staggered) chance += mod(why, -15, "staggered");
            return clamp(chance, 10, 95);
        }
        if (w.family == Wpn.F_ARTILLERY) {
            // Artillery is the reverse of direct fire: deadly at long range, wild up close.
            if (d < w.rMin) chance += mod(why, -55 - 10 * (w.rMin - d), "artillery inside minimum range");
            else if (d <= w.rShort) chance += mod(why, -40, "artillery at short range");
            else if (d <= w.rMed) chance += mod(why, -10, "artillery at medium range");
        } else {
            if (d <= w.rShort) { /* short range */ } else if (d <= w.rMed) chance += mod(why, -15, "medium range");
            else chance += mod(why, -30, "long range");
            if (d < w.rMin) chance += mod(why, -15 * (w.rMin - d), "minimum range");
        }
        if (indirect) chance += mod(why, -20, "indirect fire");
        chance += mod(why, map.weather.hitAll, Text.lower(Weather.NAMES[map.weather.id]));
        if (d > w.rShort && d <= w.rMed) chance += mod(why, map.weather.hitMed, Text.lower(Weather.NAMES[map.weather.id]));
        if (d > w.rMed) chance += mod(why, map.weather.hitLong, Text.lower(Weather.NAMES[map.weather.id]));
        if (map.weather.darkness && d > 4 && !sensorsWork(a)) chance += mod(why, -15, "night");
        if (w.family == Wpn.F_MISSILE) {
            chance += mod(why, map.weather.missileMod, "weather");
            if (ecm) chance += mod(why, -15, "enemy ECM");
            if (ecm && w.has(Wpn.SEEKER)) chance += mod(why, -w.acc, "seeker lock jammed");
        }
        if (a.hasEquip(Equip.TARGETING) && elecUp && w.direct()) {
            if (ecm) {
                if (why != null) why.append(", targeting computer jammed");
            } else chance += mod(why, a.equipPower(Equip.TARGETING), "targeting computer");
        }
        int uplink = uplinkNear(a);
        if (uplink > 0) chance += mod(why, uplink, "uplink");
        if (UnitType.isMech(a.type)) chance += mod(why, 10, "mech fire control");
        if (a.moved) {
            int pen = a.type == UnitType.VTOL || a.type == UnitType.INFANTRY || UnitType.isMech(a.type) ? -5 : -10;
            chance += mod(why, pen, "you moved");
        }
        if (a.turboOn) chance += mod(why, -10, "turbo");
        chance += mod(why, -a.heatPenaltyHit(), "heat");
        if (a.ionized) chance += mod(why, -10, "ionized");
        if (a.staggered) chance += mod(why, -15, "staggered");
        chance += mod(why, -Math.min(20, (t.movedHexes / 2) * 5), "target moved");
        if (t.type == UnitType.VTOL) chance += mod(why, -10, "airborne target");
        if (w.has(Wpn.VS_AIR) && t.type == UnitType.VTOL) chance += mod(why, 25, "flak vs air");
        int k = map.idx(t.q, t.r);
        if (t.type != UnitType.VTOL) {
            int ter = map.terrain[k];
            if (ter == HexMap.LIGHT_WOODS) chance += mod(why, -10, "target in light woods");
            if (ter == HexMap.HEAVY_WOODS) chance += mod(why, -20, "target in heavy woods");
            if (ter == HexMap.TOWN) chance += mod(why, -15, "target in town");
            if (ter == HexMap.DEEP && UnitType.isMech(t.type)) chance += mod(why, -10, "target half-submerged");
        }
        if (map.smoke[k] > 0) chance += mod(why, -25, "target in smoke");
        if (pts > 0 && !indirect) {
            int p = pts;
            if (sensorsWork(a)) p = Math.max(0, p - a.equipPower(Equip.SENSORS));
            if (p > 0) chance += mod(why, -10 * p, "woods in the way");
        }
        if (map.elev[map.idx(a.q, a.r)] > map.elev[k] && a.type != UnitType.VTOL) chance += mod(why, 5, "high ground");
        if (t.type == UnitType.INFANTRY && !w.has(Wpn.VS_INF) && w.family != Wpn.F_ARTILLERY) chance += mod(why, -10, "small target");
        return clamp(chance, 5, 95);
    }

    private static int mod(StringBuilder why, int v, String label) {
        if (v != 0 && why != null) why.append(", ").append(label).append(' ').append(v > 0 ? "+" : "").append(v);
        return v;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Expected damage of one weapon (for previews and the AI). */
    public float expected(Unit a, int m, Unit t) {
        int ch = hitChance(a, m, t, null);
        if (ch < 0) return 0;
        Wpn w = a.wpn(m);
        float dmg = w.dmg * a.firepowerScale();
        if (w.family == Wpn.F_MISSILE && !w.has(Wpn.SEEKER)) dmg *= 0.7f;
        dmg *= damageMultiplier(w, t);
        return dmg * ch / 100f;
    }

    float damageMultiplier(Wpn w, Unit t) {
        float m = 1f;
        if (t.type == UnitType.INFANTRY) {
            if (w.has(Wpn.VS_INF)) m *= 1.5f;
            else if (w.family != Wpn.F_ARTILLERY && w.family != Wpn.F_MISSILE && !t.ch.exposedCrew()) m *= 0.5f;
        }
        if (w.has(Wpn.VS_AIR) && t.type == UnitType.VTOL) m *= 2f;
        if (w.family == Wpn.F_ENERGY && t.hasEquip(Equip.REFLECTIVE)) m *= 1f - t.equipPower(Equip.REFLECTIVE) / 100f;
        if ((w.family == Wpn.F_MISSILE || w.family == Wpn.F_ARTILLERY) && t.hasEquip(Equip.REACTIVE)) m *= 1f - t.equipPower(Equip.REACTIVE) / 100f;
        return m;
    }

    // =====================================================================
    // Attacks
    // =====================================================================

    /** Fires the selected weapons at the target. */
    /** Damage per location for every unit hurt during the attack being resolved (target first). */
    private java.util.LinkedHashMap<Unit, int[]> ledger;
    private float ledgerLast;

    public void fire(Unit a, Unit t, boolean[] use) {
        version++;
        if (net != null && !remote) {
            StringBuilder o = new StringBuilder("X ").append(idx(a)).append(' ').append(idx(t)).append(' ');
            for (boolean x : use) o.append(x ? '1' : '0');
            tell(o.toString());
        }
        if (a.fired || a.destroyed) return;
        ledger = new java.util.LinkedHashMap<Unit, int[]>();
        ledger.put(t, new int[UnitType.locCount(t.type)]);
        ledgerLast = 0;
        a.fired = true;
        undoAvailable = false;
        int arc = attackArc(a, t);
        float delay = 0;
        int totalHeat = 0;
        int hits = 0, shots = 0;
        int dmgBefore = t.totalArmor() + t.totalInfra();
        boolean meleeUsed = false;
        for (int m = 0; m < a.weapon.length; m++) {
            if (!use[m]) continue;
            Wpn w = a.wpn(m);
            int chance = hitChance(a, m, t, null);
            if (chance < 0) continue;
            if (w.isMelee()) {
                if (meleeUsed) continue;
                meleeUsed = true;
            }
            shots++;
            if (w.usesAmmo()) a.ammo[m]--;
            if (a.type != UnitType.INFANTRY) totalHeat += w.heat;
            boolean hit = rnd.nextInt(100) < chance;
            float travel = travelTime(w, a, t);
            queue(Fx.shot(w, a, t, delay, travel, hit, map));
            sound(w, delay);
            if (w.family == Wpn.F_ARTILLERY) {
                int ic = t.q, ir = t.r;
                if (!hit) {
                    int dd = rnd.nextInt(6);
                    int nc = map.ncol(t.q, t.r, dd), nr = map.nrow(t.q, t.r, dd);
                    if (map.in(nc, nr)) {
                        ic = nc;
                        ir = nr;
                    }
                }
                artilleryImpact(a, w, t, hit, ic, ir, delay + travel);
                if (hit) hits++;
            } else if (hit) {
                hits++;
                resolveHit(a, m, w, t, arc, delay + travel);
            } else {
                queue(Fx.text("MISS", t, delay + travel, 0xFFBBBBBB));
            }
            delay += 0.28f;
        }
        if (shots == 0) {
            ledger = null;
            return;
        }
        a.heat += totalHeat;
        // "Aegis fired at Halberd (front): 3/4 hit, 24 damage: 12 to core, 8 to left arm, 4 to right leg"
        StringBuilder msg = new StringBuilder();
        msg.append(a.name).append(" fired at ").append(t.name).append(" (").append(UnitType.ARC_NAMES[arc]).append("): ")
                .append(hits).append('/').append(shots).append(" hit");
        int dealt = 0;
        boolean first = true;
        for (java.util.Map.Entry<Unit, int[]> en : ledger.entrySet()) {
            Unit u = en.getKey();
            int[] d = en.getValue();
            int sum = 0;
            for (int v : d) sum += v;
            dealt += sum;
            if (first) {
                if (sum == 0) msg.append(hits == 0 ? "" : ", no damage");
                else msg.append(", ").append(sum).append(" damage: ").append(breakdown(u, d));
                first = false;
            } else if (sum > 0) {
                msg.append("; ").append(u == a ? "itself" : u.name).append(" caught ").append(sum).append(" splash: ").append(breakdown(u, d));
            }
        }
        logAt(msg.toString(), Math.max(delay - 0.28f, ledgerLast));
        ledger = null;
        a.damageDealt += Math.max(0, dealt);
        checkHeat(a, delay);
        checkResult();
    }

    static String breakdown(Unit u, int[] d) {
        StringBuilder b = new StringBuilder();
        String[] names = UnitType.locNames(u.type);
        for (int i = 0; i < d.length; i++) {
            if (d[i] <= 0) continue;
            if (b.length() > 0) b.append(", ");
            b.append(d[i]).append(" to ").append(Text.lower(names[i]));
        }
        return b.toString();
    }

    /** Adds a combat log line when the visuals reach it, so the log reads in the order things happen. */
    void logAt(final String s, float when) {
        if (when <= 0.01f) {
            log(s);
            return;
        }
        Fx f = new Fx();
        f.kind = Fx.SOUND;
        f.start = when;
        f.dur = 0.01f;
        f.onStart = new Runnable() {
            public void run() {
                log(s);
            }
        };
        queue(f);
    }

    private float travelTime(Wpn w, Unit a, Unit t) {
        int d = HexMap.distance(a.q, a.r, t.q, t.r);
        switch (w.family) {
            case Wpn.F_ENERGY: return 0.12f;
            case Wpn.F_BALLISTIC: return w.base == Wpn.RAIL ? 0.08f : 0.08f + d * 0.035f;
            case Wpn.F_MISSILE: return 0.3f + d * 0.06f;
            case Wpn.F_ARTILLERY: return 0.9f + d * 0.05f;
            case Wpn.F_MELEE: return 0.2f;
            default: return 0.1f + d * 0.04f;
        }
    }

    private void sound(Wpn w, float delay) {
        int s;
        switch (w.family) {
            case Wpn.F_ENERGY: s = Platform.SND_LASER; break;
            case Wpn.F_BALLISTIC: s = w.base == Wpn.RAIL ? Platform.SND_RAIL : Platform.SND_CANNON; break;
            case Wpn.F_MISSILE: s = Platform.SND_MISSILE; break;
            case Wpn.F_ARTILLERY: s = Platform.SND_ARTY; break;
            case Wpn.F_MELEE: s = Platform.SND_MELEE; break;
            default: s = Platform.SND_CANNON;
        }
        queue(Fx.sound(s, delay));
    }

    private void resolveHit(Unit a, int m, Wpn w, Unit t, int arc, float when) {
        float total = w.dmg * a.firepowerScale();
        if (w.family == Wpn.F_MISSILE && !w.has(Wpn.SEEKER)) total *= 0.4f + rnd.nextFloat() * 0.6f;
        total *= damageMultiplier(w, t);
        if (w.has(Wpn.FLANKER) && arc != UnitType.ARC_FRONT) total *= 1.25f;   // raider weapons punish exposed flanks
        int remaining = Math.max(1, Math.round(total));
        float stagger = 0;
        while (remaining > 0 && !t.destroyed) {
            int g = Math.min(w.group, remaining);
            remaining -= g;
            int loc = rollLocation(t, arc);
            applyDamage(t, loc, g, w, a, when + stagger);
            stagger += 0.06f;
        }
        if (!t.destroyed) {
            if (w.has(Wpn.HEAT_TARGET) && t.type != UnitType.INFANTRY) {
                t.heat += 6;
                queue(Fx.text("+6 heat", t, when + 0.2f, 0xFFFF8A3A));
            }
            if (w.has(Wpn.ION)) {
                t.ionized = true;
                queue(Fx.text("IONIZED", t, when + 0.25f, 0xFF7AD8FF));
            }
            if (w.has(Wpn.STAGGER)) {
                t.staggered = true;
                queue(Fx.text("STAGGERED", t, when + 0.25f, 0xFFFFD27A));
            }
        }
    }

    private void artilleryImpact(Unit a, Wpn w, Unit target, boolean hit, int ic, int ir, float when) {
        queue(Fx.explosion(map, ic, ir, when, 0.9f));
        Unit main = unitAt(ic, ir);
        if (main != null && hit) {
            int arc = UnitType.ARC_FRONT;
            float total = w.dmg * a.firepowerScale() * damageMultiplier(w, main);
            int rem = Math.max(1, Math.round(total));
            while (rem > 0 && !main.destroyed) {
                int g = Math.min(w.group, rem);
                rem -= g;
                arc = rnd.nextInt(4);
                applyDamage(main, rollLocation(main, arc), g, w, a, when);
            }
        } else if (!hit) {
            queue(Fx.text("MISS", target, when, 0xFFBBBBBB));
        }
        // splash to the impact hex (if it scattered onto someone) and the six around it
        for (int d = -1; d < 6; d++) {
            int c = d < 0 ? ic : map.ncol(ic, ir, d), r = d < 0 ? ir : map.nrow(ic, ir, d);
            if (!map.in(c, r)) continue;
            Unit u = unitAt(c, r);
            if (u == null || (d < 0 && hit)) continue;
            float total = w.splash * a.firepowerScale() * damageMultiplier(w, u);
            if (u.type == UnitType.VTOL) total *= 0.5f;
            int rem = Math.round(total);
            while (rem > 0 && !u.destroyed) {
                int g = Math.min(5, rem);
                rem -= g;
                applyDamage(u, rollLocation(u, rnd.nextInt(4)), g, w, a, when + 0.1f);
            }
        }
    }

    public int rollLocation(Unit t, int arc) {
        int[] table = UnitType.hitTable(t.type, arc);
        int total = 0;
        for (int v : table) total += v;
        // Destroyed locations stay in the table: hits on them transfer to the core at half damage.
        int roll = rnd.nextInt(total);
        for (int i = 0; i < table.length; i++) {
            roll -= table[i];
            if (roll < 0) return i;
        }
        return 0;
    }

    /** Damage to one location: armor first, then infrastructure, with criticals and destruction. */
    public void applyDamage(Unit t, int loc, int dmg, Wpn w, Unit src, float when) {
        if (t.destroyed || dmg <= 0) return;
        boolean mech = UnitType.isMech(t.type);
        // A hit on an already-destroyed location carries through to the core (a vehicle's front hull) at half damage.
        if (transfers(t, loc) && t.locDestroyed(loc)) {
            int half = Math.max(1, Math.round(dmg * 0.5f));
            applyDamage(t, coreOf(t), half, w, src, when);
            return;
        }
        String locName = UnitType.locShort(t.type)[loc];
        int absorbed = Math.min(t.armor[loc], dmg);
        t.armor[loc] -= absorbed;
        int rem = dmg - absorbed;
        int il = UnitType.infraLoc(t.type, loc);
        boolean crit = false;
        int overflow = 0;
        if (rem > 0) {
            int before = t.infra[il];
            t.infra[il] = Math.max(0, t.infra[il] - rem);
            int over = rem - before;
            if (rnd.nextFloat() < 0.3f) crit = true;
            if (t.infra[il] <= 0 && before > 0) {
                queue(Fx.text(Text.upper(UnitType.locNames(t.type)[il]) + " DESTROYED", t, when + 0.15f, 0xFFFF5A3A));
                logAt(t.name + ": " + Text.lower(UnitType.locNames(t.type)[il]) + " destroyed", when + 0.15f);
                queue(Fx.explosion(map, t.q, t.r, when + 0.1f, 0.55f));
            }
            if (over > 0 && transfers(t, loc)) overflow = Math.max(1, Math.round(over * 0.5f));
        }
        if (w != null && w.has(Wpn.AP) && rnd.nextFloat() < 0.15f) crit = true;
        if (ledger != null) {
            int[] d = ledger.get(t);
            if (d == null) ledger.put(t, d = new int[UnitType.locCount(t.type)]);
            d[loc] += dmg;
            ledgerLast = Math.max(ledgerLast, when);
        }
        queue(Fx.text("-" + dmg + " " + locName, t, when, rem > 0 ? 0xFFFF6A4A : 0xFFFFE08A));
        queue(Fx.burst(t, loc, when, Math.min(0.55f, 0.18f + dmg * 0.02f)));
        queue(Fx.hitmark(t, loc, dmg, rem > 0, t.infra[il] <= 0, when));
        t.hitFlash = 0.4f;
        if (crit) critical(t, il, when + 0.2f);
        // motive damage for vehicles
        if (UnitType.isVehicle(t.type) && loc != UnitType.V_TOP && rnd.nextFloat() < UnitType.MOTIVE[t.type]) {
            int lose = t.type == UnitType.HOVER ? 2 : 1;
            t.mpLost += lose;
            queue(Fx.text("MOTIVE HIT", t, when + 0.3f, 0xFFFFB02E));
            logAt(t.name + ": motive system damaged (-" + lose + " MP)", when + 0.3f);
        }
        if (t.type == UnitType.VTOL && rnd.nextFloat() < 0.06f) {
            t.mpLost += 2;
            queue(Fx.text("ROTOR DAMAGE", t, when + 0.3f, 0xFFFFB02E));
            if (t.baseMp() <= 0) {
                kill(t, src, when + 0.4f, "crashed");
                return;
            }
        }
        // destruction
        boolean dead;
        if (t.type == UnitType.INFANTRY) dead = t.infra[0] <= 0;
        else if (mech) dead = t.infra[UnitType.M_CORE] <= 0 || t.infra[UnitType.M_COCKPIT] <= 0;
        else {
            dead = false;
            for (int i = 0; i <= 3; i++) if (t.infra[i] <= 0) dead = true;
            if (t.type == UnitType.VTOL && t.infra[UnitType.V_TOP] <= 0) dead = true;
        }
        if (dead) kill(t, src, when + 0.25f, null);
        else if (overflow > 0) applyDamage(t, coreOf(t), overflow, w, src, when + 0.05f);
    }

    /** Locations whose loss passes later hits (and excess damage) on to the core. */
    static boolean transfers(Unit t, int loc) {
        if (UnitType.isMech(t.type)) {
            return loc == UnitType.M_LARM || loc == UnitType.M_RARM || loc == UnitType.M_LLEG || loc == UnitType.M_RLEG;
        }
        return t.type != UnitType.INFANTRY && t.type != UnitType.VTOL && loc == UnitType.V_TOP;
    }

    static int coreOf(Unit t) {
        return UnitType.isMech(t.type) ? UnitType.M_CORE : UnitType.V_FRONT;
    }

    private void critical(Unit t, int loc, float when) {
        List<int[]> opts = new ArrayList<int[]>();   // {kind, index}
        for (int m = 0; m < t.weapon.length; m++) {
            if (t.weapon[m] >= 0 && !t.wpnBroken[m] && t.ch.mounts[m].loc == loc) opts.add(new int[]{0, m});
        }
        int eqLoc = UnitType.isMech(t.type) ? UnitType.M_CORE : 0;
        if (loc == eqLoc) {
            for (int e = 0; e < t.equip.length; e++) if (t.equip[e] >= 0 && !t.eqBroken[e]) opts.add(new int[]{1, e});
            opts.add(new int[]{2, 0});
        }
        if (opts.isEmpty()) return;
        int[] pick = opts.get(rnd.nextInt(opts.size()));
        if (pick[0] == 0) {
            int m = pick[1];
            Wpn w = t.wpn(m);
            t.wpnBroken[m] = true;
            queue(Fx.text("CRIT: " + w.name, t, when, 0xFFFF9A3A));
            logAt(t.name + ": critical hit, " + w.name + " destroyed", when);
            if (w.usesAmmo() && t.ammo[m] > 0 && rnd.nextFloat() < 0.35f) {
                int boom = Math.min(30, Math.max(4, t.ammo[m] * w.dmg / 3));
                t.ammo[m] = 0;
                queue(Fx.text("AMMO EXPLOSION!", t, when + 0.25f, 0xFFFF3A2A));
                queue(Fx.explosion(map, t.q, t.r, when + 0.25f, 1f));
                logAt(t.name + ": ammunition explodes for " + boom + " damage", when + 0.25f);
                int il = UnitType.infraLoc(t.type, loc);
                t.infra[il] = Math.max(0, t.infra[il] - boom);
                // re-run destruction checks with zero extra damage via a tiny hit
                applyDamage(t, loc, 1, null, null, when + 0.3f);
            }
        } else if (pick[0] == 1) {
            int e = pick[1];
            t.eqBroken[e] = true;
            queue(Fx.text("CRIT: " + Equip.ALL[t.equip[e]].shortName, t, when, 0xFFFF9A3A));
            logAt(t.name + ": critical hit, " + Equip.ALL[t.equip[e]].name + " destroyed", when);
        } else {
            if (UnitType.isMech(t.type)) {
                t.sinksLost++;
                queue(Fx.text("CRIT: COOLING", t, when, 0xFFFF9A3A));
                logAt(t.name + ": critical hit, cooling damaged", when);
            } else if (t.type != UnitType.INFANTRY) {
                t.shutdown = true;
                queue(Fx.text("CREW STUNNED", t, when, 0xFFFF9A3A));
                logAt(t.name + ": crew stunned, loses its next activation", when);
            }
        }
    }

    private void kill(Unit t, Unit src, float when, String how) {
        if (t.destroyed) return;
        t.destroyed = true;
        if (src != null && src.team != t.team) src.kills++;
        queue(Fx.death(map, t, when));
        logAt(t.name + (how != null ? " " + how + "!" : " destroyed!") + (src != null && src != t ? " (" + src.name + ")" : ""), when);
    }

    private void checkHeat(Unit a, float when) {
        if (a.destroyed || a.type == UnitType.INFANTRY) return;
        if (a.heat >= a.ch.heatCap) {
            a.shutdown = true;
            queue(Fx.text("OVERHEAT - SHUTDOWN", a, when + 0.3f, 0xFFFF5A2A));
            log(a.name + " overheats and shuts down");
            int core = UnitType.isMech(a.type) ? UnitType.M_CORE : 0;
            a.infra[core] = Math.max(1, a.infra[core] - 3);
            for (int m = 0; m < a.weapon.length; m++) {
                Wpn w = a.wpn(m);
                if (w != null && w.usesAmmo() && a.ammo[m] > 0 && rnd.nextFloat() < 0.15f) {
                    int boom = Math.min(20, a.ammo[m] * w.dmg / 4 + 3);
                    a.ammo[m] = 0;
                    queue(Fx.text("HEAT COOKS OFF AMMO!", a, when + 0.5f, 0xFFFF3A2A));
                    log(a.name + ": heat sets off its ammunition");
                    int loc = a.ch.mounts[m].loc;
                    a.infra[UnitType.infraLoc(a.type, loc)] = Math.max(0, a.infra[UnitType.infraLoc(a.type, loc)] - boom);
                    applyDamage(a, loc, 1, null, null, when + 0.5f);
                    break;
                }
            }
        }
    }

    // =====================================================================
    // Equipment actions
    // =====================================================================

    public boolean canTurbo(Unit u) {
        return u == active && !u.moved && !u.turboOn && u.equipSlot(Equip.TURBO) >= 0 && u.eqUses[u.equipSlot(Equip.TURBO)] > 0
                && u.baseMp() > 0 && !u.shutdown;
    }

    public void turbo(Unit u) {
        version++;
        tell("T " + idx(u));
        if (!canTurbo(u)) return;
        int s = u.equipSlot(Equip.TURBO);
        u.eqUses[s]--;
        u.turboOn = true;
        if (UnitType.isMech(u.type)) u.heat += 5;
        log(u.name + " engages turbo (+" + Math.max(3, u.equipPower(Equip.TURBO)) + " MP)");
    }

    boolean canTurboAi(Unit u) {
        int s = u.equipSlot(Equip.TURBO);
        return s >= 0 && u.eqUses[s] > 0 && !u.turboOn && u.baseMp() > 0 && !u.shutdown;
    }

    public boolean canUse(Unit u, int equipId) {
        int s = u.equipSlot(equipId);
        return u == active && s >= 0 && u.eqUses[s] > 0 && !u.destroyed;
    }

    public void useEquip(Unit u, int equipId) {
        version++;
        tell("E " + idx(u) + " " + equipId);
        if (!canUse(u, equipId)) return;
        int s = u.equipSlot(equipId);
        u.eqUses[s]--;
        switch (equipId) {
            case Equip.COOLANT: {
                int dump = Equip.ALL[u.equip[s]].power;
                u.heat = Math.max(0, u.heat - dump);
                queue(Fx.text("-" + dump + " HEAT", u, 0, 0xFF7AD8FF));
                log(u.name + " flushes coolant");
                break;
            }
            case Equip.SMOKE:
                map.smoke[map.idx(u.q, u.r)] = Equip.ALL[u.equip[s]].power;
                queue(Fx.text("SMOKE", u, 0, 0xFFDDDDDD));
                log(u.name + " pops smoke");
                break;
            case Equip.REPAIR_KIT: {
                int best = -1, missing = 0;
                for (int i = 0; i < u.armor.length; i++) {
                    if (u.armorMax[i] - u.armor[i] > missing && !u.locDestroyed(i)) {
                        missing = u.armorMax[i] - u.armor[i];
                        best = i;
                    }
                }
                if (best >= 0) {
                    int add = Math.min(Equip.ALL[u.equip[s]].power, missing);
                    u.armor[best] += add;
                    queue(Fx.text("+" + add + " ARMOR", u, 0, 0xFF5BD46A));
                    log(u.name + " patches its " + UnitType.locNames(u.type)[best]);
                }
                break;
            }
        }
    }

    public void rotate(Unit u, int dir) {
        version++;
        tell("R " + idx(u) + " " + dir);
        if (u != active || u.destroyed) return;
        u.facing = (u.facing + dir + 6) % 6;
        u.drawFacing = HexMap.DIR_ANGLE[u.facing];
    }

    // =====================================================================
    // Turn flow
    // =====================================================================

    private void startRound() {
        for (Unit u : units) {
            u.activated = false;
        }
        float roll = rnd.nextFloat();
        initiative = round == 1 && firstInitiative >= 0 ? firstInitiative : (roll < playerInitiative ? PLAYER : ENEMY);
        side = initiative;
        log("Round " + round + ": " + (hotseat ? sideName(initiative) + " has" : (initiative == PLAYER ? "you have" : "the enemy has")) + " initiative");
        beginSideActivation();
    }

    private boolean hasReady(int t) {
        for (Unit u : units) if (u.team == t && !u.destroyed && !u.activated) return true;
        return false;
    }

    private void beginSideActivation() {
        if (result != RESULT_NONE) return;
        if (!hasReady(side)) side = 1 - side;
        if (!hasReady(side)) {
            endRound();
            return;
        }
        active = null;
        aiTurn = null;
        if (!human(side)) aiDelay = 0.4f;
        else game.sfx(Platform.SND_ALERT, 0.5f);
        // A human side with only shut-down units left just restarts one of them automatically.
        if (human(side) && onlyShutdownLeft(side)) {
            for (Unit u : units) {
                if (u.team == side && !u.destroyed && !u.activated && u.shutdown) {
                    boolean was = remote;
                    remote = true;   // both devices take this step themselves
                    try {
                        beginActivation(u);
                        endActivation(u);
                    } finally {
                        remote = was;
                    }
                    return;
                }
            }
        }
    }

    private boolean onlyShutdownLeft(int t) {
        for (Unit u : units) if (u.team == t && !u.destroyed && !u.activated && !u.shutdown) return false;
        return true;
    }

    /** Called when a unit starts its activation (selected by the human or the AI). */
    public void beginActivation(Unit u) {
        version++;
        tell("B " + idx(u));
        if (u.team != side || u.activated || u.destroyed) return;
        active = u;
        u.moved = false;
        u.fired = false;
        u.turboOn = false;
        u.mpUsed = 0;
        u.movedHexes = 0;
        undoAvailable = false;
        if (u.shutdown) {
            log(u.name + " is restarting after a shutdown");
            queue(Fx.text("RESTARTING", u, 0, 0xFFFFB02E));
        }
    }

    public void endActivation(Unit u) {
        version++;
        tell("N " + idx(u));
        if (u != active) return;
        u.activated = true;
        if (u.shutdown) {
            u.shutdown = false; // the lost activation is served
        } else {
            u.staggered = false;
            u.ionized = false;
        }
        u.turboOn = false;
        active = null;
        undoAvailable = false;
        checkResult();
        if (result != RESULT_NONE) return;
        side = 1 - side;
        beginSideActivation();
    }

    private void endRound() {
        for (Unit u : units) {
            if (u.destroyed) continue;
            int k = map.idx(u.q, u.r);
            int cool = u.dissipation() + map.weather.cooling;
            if (u.type != UnitType.VTOL) {
                if (map.terrain[k] == HexMap.SHALLOW && !map.road[k]) cool += 2;
                if (map.terrain[k] == HexMap.DEEP) cool += 4;
            }
            u.heat = Math.max(0, u.heat - Math.max(0, cool));
        }
        for (int i = 0; i < map.smoke.length; i++) if (map.smoke[i] > 0) map.smoke[i] -= 1;
        round++;
        if (round > maxRounds) {
            float p = remainingValue(PLAYER) / Math.max(1f, startValue[PLAYER]) + timeEdge;
            float e = remainingValue(ENEMY) / Math.max(1f, startValue[ENEMY]);
            result = Math.abs(p - e) < 0.05f ? RESULT_DRAW : (p > e ? RESULT_WIN : RESULT_LOSS);
            log("Time is up.");
            return;
        }
        startRound();
    }

    public float remainingValue(int t) {
        float v = 0;
        for (Unit u : units) if (u.team == t && !u.destroyed) v += u.value() * u.condition();
        return v;
    }

    private void checkResult() {
        if (result != RESULT_NONE) return;
        if (alive(ENEMY) == 0) result = RESULT_WIN;
        else if (alive(PLAYER) == 0) result = RESULT_LOSS;
    }

    public void withdraw() {
        if (hotseat) {
            // the side whose turn it is concedes (online: this device's side), and the other side wins
            concede(localTeam >= 0 ? localTeam : side);
            return;
        }
        result = RESULT_WITHDRAW;
    }

    /** Two players: this side gives up, and the other side wins. */
    public void concede(int team) {
        version++;
        tell("W " + team);
        if (result != RESULT_NONE) return;
        conceded = team;
        result = team == PLAYER ? RESULT_LOSS : RESULT_WIN;
        log(sideName(team) + " concedes.");
    }

    /** Turns a unit to face a direction during its activation (the computer aims this way before it fires). */
    public void face(Unit u, int facing) {
        version++;
        tell("A " + idx(u) + " " + facing);
        if (u != active || u.destroyed) return;
        u.facing = ((facing % 6) + 6) % 6;
        u.drawFacing = HexMap.DIR_ANGLE[u.facing];
    }

    /** Picks a different unit to activate instead of the one picked, before it has done anything. */
    public void switchTo(Unit u) {
        version++;
        tell("S " + idx(u));
        Unit a = active;
        if (a != null && (a == u || a.moved || a.fired || a.turboOn)) return;
        boolean was = remote;
        remote = true;
        try {
            active = null;
            beginActivation(u);
        } finally {
            remote = was;
        }
    }

    /**
     * A fingerprint of everything that decides the battle (not its animations), to check that the two devices in an
     * online battle still agree.
     */
    public int stateHash() {
        int h = round;
        h = h * 31 + side;
        h = h * 31 + initiative;
        h = h * 31 + (deploying ? 1 : 0);
        h = h * 31 + deployTeam;
        h = h * 31 + result;
        h = h * 31 + conceded;
        h = h * 31 + (active == null ? -1 : idx(active));
        if (rnd instanceof Rng) {
            long st = ((Rng) rnd).state();
            h = h * 31 + (int) (st ^ (st >>> 32));
        }
        for (Unit u : units) {
            h = h * 31 + u.q;
            h = h * 31 + u.r;
            h = h * 31 + u.facing;
            h = h * 31 + Math.round(u.heat * 100);
            h = h * 31 + (u.activated ? 1 : 0) + (u.shutdown ? 2 : 0) + (u.staggered ? 4 : 0) + (u.ionized ? 8 : 0)
                    + (u.turboOn ? 16 : 0) + (u.fired ? 32 : 0) + (u.moved ? 64 : 0) + (u.destroyed ? 128 : 0);
            h = h * 31 + u.movedHexes;
            h = h * 31 + u.mpLost * 7 + u.sinksLost;
            for (int v : u.armor) h = h * 31 + v;
            for (int v : u.infra) h = h * 31 + v;
            for (int v : u.ammo) h = h * 31 + v;
            for (boolean v : u.wpnBroken) h = h * 31 + (v ? 1 : 0);
            for (boolean v : u.eqBroken) h = h * 31 + (v ? 1 : 0);
            for (int v : u.eqUses) h = h * 31 + v;
        }
        for (float v : map.smoke) h = h * 31 + Math.round(v);
        return h;
    }

    // =====================================================================

    public void update(float dt) {
        updateMove(dt);
        fxClock += dt;
        for (int i = fx.size() - 1; i >= 0; i--) {
            Fx f = fx.get(i);
            if (!f.started && fxClock >= f.start) {
                f.started = true;
                f.age = fxClock - f.start;
                if (f.sound >= 0) game.sfx(f.sound, f.volume);
                if (f.onStart != null) f.onStart.run();
            }
            if (f.started) {
                f.age += dt;
                if (f.age >= f.dur) {
                    if (f.onEnd != null) f.onEnd.run();
                    fx.remove(i);
                }
            }
        }
        if (fx.isEmpty()) fxClock = 0;
        for (Unit u : units) if (u.hitFlash > 0) u.hitFlash -= dt;
        if (!deploying && result == RESULT_NONE && !human(side) && !busy() && (localTeam < 0 || side == localTeam)
                && (net == null || !net.waiting())) {
            aiDelay -= dt * game.settings.aiSpeedFactor();
            if (aiDelay <= 0) Ai.step(this);
        }
    }

    public void log(String s) {
        log.add(s);
        if (log.size() > 60) log.remove(0);
    }

    /** Puts a new effect at the current time offset (fx start times are relative to now). */
    void queue(Fx f) {
        f.start += fxClock;
        fx.add(f);
    }

    // =====================================================================

    /** A timed visual effect. Times are relative to when it was queued. */
    public static final class Fx {
        public static final int BEAM = 0, TRACER = 1, MISSILES = 2, SHELL = 3, MELEE = 4, EXPLOSION = 5, TEXT = 6, SOUND = 7, DEATH = 8,
                HITMARK = 9, BURST = 10;
        public int kind;
        public float start, dur, age;
        public boolean started;
        public float x1, y1, x2, y2;    // map units (hex radius = 1)
        public int color;
        public boolean hit;
        public String text;
        public float size;
        public int sound = -1;
        public float volume = 0.8f;
        public Runnable onStart, onEnd;
        public int count;
        /** HITMARK: which unit and location took how much (structure = got past the armor). */
        public Unit unit;
        public int loc, dmg;
        public boolean structure, locLost;

        static Fx hitmark(Unit u, int loc, int dmg, boolean structure, boolean locLost, float delay) {
            Fx f = new Fx();
            f.kind = HITMARK;
            f.unit = u;
            f.loc = loc;
            f.dmg = dmg;
            f.structure = structure;
            f.locLost = locLost;
            f.start = delay;
            f.dur = 1.8f;
            return f;
        }

        /** A small explosion on the part of the unit that was hit, turned to match its facing. */
        static Fx burst(Unit u, int loc, float delay, float size) {
            float ox, oy;
            if (u.type == UnitType.INFANTRY) {
                ox = (float) (Math.random() - 0.5) * 0.5f;
                oy = (float) (Math.random() - 0.5) * 0.5f;
            } else if (UnitType.isMech(u.type)) {
                float[][] o = {{0.28f, 0}, {0.08f, 0}, {-0.3f, 0}, {0, -0.45f}, {0, 0.45f}, {-0.2f, -0.24f}, {-0.2f, 0.24f}};
                ox = o[loc][0];
                oy = o[loc][1];
            } else {
                float[][] o = {{0.45f, 0}, {0, -0.38f}, {0, 0.38f}, {-0.45f, 0}, {0, 0}};
                ox = o[loc][0];
                oy = o[loc][1];
            }
            double a = Math.toRadians(HexMap.DIR_ANGLE[u.facing]);
            Fx f = new Fx();
            f.kind = BURST;
            f.start = delay;
            f.dur = 0.4f;
            f.x1 = HexMap.px(u.q, u.r) + (float) (ox * Math.cos(a) - oy * Math.sin(a));
            f.y1 = HexMap.py(u.q, u.r) + (float) (ox * Math.sin(a) + oy * Math.cos(a));
            f.size = size;
            return f;
        }

        static float now;

        static Fx shot(Wpn w, Unit a, Unit t, float delay, float travel, boolean hit, HexMap map) {
            Fx f = new Fx();
            f.start = delay;
            f.dur = travel + (w.family == Wpn.F_ENERGY ? 0.18f : 0.05f);
            f.x1 = HexMap.px(a.q, a.r);
            f.y1 = HexMap.py(a.q, a.r);
            f.x2 = HexMap.px(t.q, t.r);
            f.y2 = HexMap.py(t.q, t.r);
            if (!hit) {
                float ang = (float) (Math.random() * Math.PI * 2);
                f.x2 += (float) Math.cos(ang) * 0.6f;
                f.y2 += (float) Math.sin(ang) * 0.6f;
            }
            f.hit = hit;
            f.color = colorFor(w);
            switch (w.family) {
                case Wpn.F_ENERGY: f.kind = BEAM; f.size = w.cls; break;
                case Wpn.F_MISSILE: f.kind = MISSILES; f.count = Math.min(8, Math.max(3, w.dmg / 2)); break;
                case Wpn.F_ARTILLERY: f.kind = SHELL; break;
                case Wpn.F_MELEE: f.kind = MELEE; break;
                default: f.kind = TRACER; f.count = w.base == Wpn.CHAINGUN || w.base == Wpn.RIFLES ? 5 : (w.base == Wpn.FLAK ? 3 : 1);
                    f.size = w.cls;
            }
            return f;
        }

        static int colorFor(Wpn w) {
            switch (w.base) {
                case Wpn.PLASMA: return 0xFFFF7AD8;
                case Wpn.ION_LANCE: return 0xFF7AD8FF;
                case Wpn.LIGHT_BURST:
                case Wpn.BURST_LASER: return 0xFF5AFF8A;
                case Wpn.RAIL: return 0xFF9AC8FF;
                default:
                    if (w.family == Wpn.F_ENERGY) return 0xFFFF4A4A;
                    if (w.family == Wpn.F_MISSILE) return 0xFFFFD27A;
                    return 0xFFFFE8A0;
            }
        }

        static Fx text(String s, Unit u, float delay, int color) {
            Fx f = new Fx();
            f.kind = TEXT;
            f.text = s;
            f.start = delay;
            f.dur = 1.3f;
            f.x1 = HexMap.px(u.q, u.r) + (float) (Math.random() - 0.5) * 0.4f;
            f.y1 = HexMap.py(u.q, u.r) - 0.3f;
            f.color = color;
            return f;
        }

        static Fx explosion(HexMap map, int c, int r, float delay, float size) {
            Fx f = new Fx();
            f.kind = EXPLOSION;
            f.start = delay;
            f.dur = 0.45f + size * 0.3f;
            f.x1 = HexMap.px(c, r);
            f.y1 = HexMap.py(c, r);
            f.size = size;
            f.sound = size >= 0.8f ? Platform.SND_BIG_BOOM : Platform.SND_BOOM;
            f.volume = Math.min(1f, 0.4f + size * 0.5f);
            return f;
        }

        static Fx death(HexMap map, final Unit u, float delay) {
            Fx f = explosion(map, u.q, u.r, delay, 1.4f);
            f.kind = DEATH;
            f.dur = 1.1f;
            f.onStart = new Runnable() {
                public void run() {
                    u.deathShown = true;
                }
            };
            return f;
        }

        static Fx sound(int s, float delay) {
            Fx f = new Fx();
            f.kind = SOUND;
            f.start = delay;
            f.dur = 0.01f;
            f.sound = s;
            return f;
        }
    }
}
