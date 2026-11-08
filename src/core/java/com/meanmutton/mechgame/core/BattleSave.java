package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Saves a battle in progress so leaving the app and coming back puts you in the same battle.
 *
 * The save holds everything needed to rebuild it on its own: your company and the enemy company as they were
 * when the battle started, the map, and every unit on the field with its damage and battle state. Units that
 * belong to a company are linked back to their place in its roster, so the battle's result settles normally.
 *
 * It is written whenever the battle reaches a quiet moment (no animation playing, the computer not thinking)
 * after something changed: a unit placed, moved, fired, rotated, used equipment, or an activation began or ended.
 * In the middle of your activation it saves that too; during the computer's activation it keeps the save from
 * before that activation, and on resuming the computer plays it again.
 */
public final class BattleSave {
    private BattleSave() {}

    public static final String KEY = "battle";
    private static final String VERSION = "B1";
    private static final int OWN_NONE = 0, OWN_PLAYER = 1, OWN_RIVAL = 2;

    /** True when the battle is at a point that can be saved and resumed exactly. */
    static boolean quiet(Battle b) {
        if (b.result != Battle.RESULT_NONE || b.busy() || b.aiPlanner != null || b.aiTurn != null) return false;
        return b.active == null || b.human(b.active.team);
    }

    public static String write(Company c, Battle b) {
        StringBuilder s = new StringBuilder(VERSION).append('\n');
        s.append("company\t").append(c.serialize()).append('\n');
        if (c.rival != null) s.append("rival\t").append(c.rival.serialize()).append('\n');
        s.append("battle\t").append(b.round).append(',').append(b.initiative).append(',').append(b.side).append(',')
                .append(b.deploying ? 1 : 0).append(',').append(b.maxRounds).append(',').append(b.playerInitiative).append(',')
                .append(b.firstInitiative).append(',').append(b.timeEdge).append(',').append(b.startValue[0]).append(',')
                .append(b.startValue[1]).append(',').append(b.difficulty).append(',').append(b.active == null ? -1 : b.units.indexOf(b.active))
                .append('\n');
        HexMap m = b.map;
        s.append("map\t").append(m.cols).append(',').append(m.rows).append(',').append(m.biome).append(',').append(m.weather.id).append('\t');
        for (int i = 0; i < m.terrain.length; i++) s.append(m.terrain[i]);
        s.append('\t');
        for (int i = 0; i < m.elev.length; i++) s.append(m.elev[i]);
        s.append('\t');
        for (int i = 0; i < m.road.length; i++) s.append(m.road[i] ? '1' : '0');
        s.append('\t');
        for (int i = 0; i < m.smoke.length; i++) s.append(i > 0 ? "," : "").append(Math.round(m.smoke[i]));
        s.append('\n');
        for (Unit u : b.units) {
            int own = OWN_NONE, idx = -1;
            if (u.tag == null && c.roster.contains(u)) {
                own = OWN_PLAYER;
                idx = c.roster.indexOf(u);
            } else if (u.tag == null && c.rival != null && c.rival.roster.contains(u)) {
                own = OWN_RIVAL;
                idx = c.rival.roster.indexOf(u);
            }
            StringBuilder uses = new StringBuilder();
            for (int i = 0; i < u.eqUses.length; i++) uses.append(i > 0 ? "/" : "").append(u.eqUses[i]);
            s.append("unit\t").append(own).append(',').append(idx).append(',').append(u.tag == null ? "-" : u.tag).append(',')
                    .append(u.team).append(',').append(u.q).append(',').append(u.r).append(',').append(u.facing).append(',')
                    .append(u.heat).append(',').append(bits(u.activated, u.shutdown, u.staggered, u.ionized, u.turboOn, u.fired, u.moved))
                    .append(',').append(u.movedHexes).append(',').append(u.mpUsed).append(',').append(u.kills).append(',')
                    .append(u.damageDealt).append(',').append(uses.length() == 0 ? "-" : uses.toString())
                    .append('\t').append(u.serialize()).append('\n');
        }
        // hotseat battles: whose deployment it is, and who conceded (older builds ignore this line)
        if (b.hotseat) s.append("hotseat\t").append(b.deployTeam).append(',').append(b.conceded).append(',')
                .append(b.game.hotseatEnemy == null ? "-" : b.game.hotseatEnemy.serialize()).append('\n');
        int from = Math.max(0, b.log.size() - 12);
        for (int i = from; i < b.log.size(); i++) s.append("log\t").append(b.log.get(i).replace('\n', ' ').replace('\t', ' ')).append('\n');
        return s.toString();
    }

    private static String bits(boolean... f) {
        StringBuilder b = new StringBuilder();
        for (boolean x : f) b.append(x ? '1' : '0');
        return b.toString();
    }

    /** Rebuilds the company and the battle from a save, or returns null if there is none or it can't be read. */
    public static Battle read(Game game, String s) {
        if (s == null || !s.startsWith(VERSION + "\n")) return null;
        try {
            Company c = null, rival = null;
            String[] battle = null;
            String[] map = null, hot = null;
            List<String> unitLines = new ArrayList<String>(), log = new ArrayList<String>();
            for (String line : Text.split(s, '\n')) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String key = line.substring(0, tab), val = line.substring(tab + 1);
                if (key.equals("company")) c = Company.parse(val);
                else if (key.equals("rival")) rival = Company.parse(val);
                else if (key.equals("battle")) battle = Text.split(val, ',');
                else if (key.equals("map")) map = Text.split(val, '\t', true);
                else if (key.equals("unit")) unitLines.add(val);
                else if (key.equals("log")) log.add(val);
                else if (key.equals("hotseat")) hot = Text.split(val, ',');
            }
            if (c == null || battle == null || map == null || unitLines.isEmpty()) return null;
            c.rival = rival;
            String[] mh = Text.split(map[0], ',');
            int cols = Integer.parseInt(mh[0]), rows = Integer.parseInt(mh[1]);
            HexMap m = new HexMap(cols, rows, Integer.parseInt(mh[2]), new Weather(Integer.parseInt(mh[3])));
            String[] smoke = Text.split(map[4], ',');
            for (int i = 0; i < cols * rows; i++) {
                m.terrain[i] = map[1].charAt(i) - '0';
                m.elev[i] = map[2].charAt(i) - '0';
                m.road[i] = map[3].charAt(i) == '1';
                m.smoke[i] = Integer.parseInt(smoke[i]);
            }
            List<Unit> units = new ArrayList<Unit>();
            List<Unit> mine = new ArrayList<Unit>(), theirs = new ArrayList<Unit>();
            for (String ul : unitLines) {
                int tab = ul.indexOf('\t');
                String[] f = Text.split(ul.substring(0, tab), ',');
                Unit u = Unit.parse(ul.substring(tab + 1));
                int own = Integer.parseInt(f[0]), idx = Integer.parseInt(f[1]);
                u.tag = f[2].equals("-") ? null : f[2];
                // put the battle's copy back in its company's roster, so the result settles on the same unit
                if (own == OWN_PLAYER && idx >= 0 && idx < c.roster.size()) {
                    c.roster.set(idx, u);
                    mine.add(u);
                } else if (own == OWN_RIVAL && rival != null && idx >= 0 && idx < rival.roster.size()) {
                    rival.roster.set(idx, u);
                    theirs.add(u);
                }
                u.team = Integer.parseInt(f[3]);
                u.q = Integer.parseInt(f[4]);
                u.r = Integer.parseInt(f[5]);
                u.facing = Integer.parseInt(f[6]);
                u.heat = Float.parseFloat(f[7]);
                String bits = f[8];
                u.activated = bits.charAt(0) == '1';
                u.shutdown = bits.charAt(1) == '1';
                u.staggered = bits.charAt(2) == '1';
                u.ionized = bits.charAt(3) == '1';
                u.turboOn = bits.charAt(4) == '1';
                u.fired = bits.charAt(5) == '1';
                u.moved = bits.charAt(6) == '1';
                u.movedHexes = Integer.parseInt(f[9]);
                u.mpUsed = Integer.parseInt(f[10]);
                u.kills = Integer.parseInt(f[11]);
                u.damageDealt = Float.parseFloat(f[12]);
                if (!f[13].equals("-")) {
                    String[] us = Text.split(f[13], '/');
                    for (int i = 0; i < us.length && i < u.eqUses.length; i++) u.eqUses[i] = Integer.parseInt(us[i]);
                }
                u.deathShown = u.destroyed;
                units.add(u);
            }
            c.deployed.clear();
            c.deployed.addAll(mine);
            if (rival != null && c.pvp) {
                // two-player campaign: Player 2's force is settled from its deployed list, so it must be the battle's units
                rival.deployed.clear();
                rival.deployed.addAll(theirs);
                rival.rival = c;
            }
            Battle b = new Battle(game, units, m, new Random(), Integer.parseInt(battle[10]), hot != null);
            if (hot != null) {
                b.deployTeam = Integer.parseInt(hot[0]);
                b.conceded = Integer.parseInt(hot[1]);
                game.hotseatEnemy = hot.length > 2 && !hot[2].equals("-") ? Scheme.parse(hot[2], null) : Scheme.ENEMY_DEFAULT;
                if (!c.pvp) {   // a hotseat quick battle (a two-player campaign's battle stays a campaign battle)
                    c.hotseat = true;
                    c.seatNo = 0;
                    game.seats = null;   // a resumed battle: a rematch rebuilds the two forces from the battle
                }
            }
            b.round = Integer.parseInt(battle[0]);
            b.initiative = Integer.parseInt(battle[1]);
            b.side = Integer.parseInt(battle[2]);
            b.deploying = battle[3].equals("1");
            b.maxRounds = Integer.parseInt(battle[4]);
            b.playerInitiative = Float.parseFloat(battle[5]);
            b.firstInitiative = Integer.parseInt(battle[6]);
            b.timeEdge = Float.parseFloat(battle[7]);
            b.startValue[0] = Integer.parseInt(battle[8]);
            b.startValue[1] = Integer.parseInt(battle[9]);
            int act = Integer.parseInt(battle[11]);
            if (act >= 0 && act < units.size()) b.active = units.get(act);
            b.log.addAll(log);
            b.log("Battle resumed.");
            game.company = c;
            game.pvpMain = null;
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
