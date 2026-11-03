package com.meanmutton.mechgame.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Builds a plain-text report when the game crashes: the error and where it happened, the build and device, what
 * the game was doing (screen, campaign, battle), and the player's last few actions. The host saves it the moment
 * the crash happens; the next launch shows it (CrashScreen) so it can be shared.
 */
public final class CrashReport {
    private CrashReport() {}

    public static final String KEY = "crash";

    /** A freeze: nothing threw, but a thread stopped making progress. Includes where every thread is stuck. */
    public static String freeze(Game game, String what, java.util.Map<Thread, StackTraceElement[]> stacks) {
        StringBuilder s = new StringBuilder("Hexfront freeze report\n");
        s.append(what).append('\n');
        StringBuilder threads = new StringBuilder();
        // the game's own threads first: the main (UI) thread, the drawing loop, the computer's planning
        java.util.List<java.util.Map.Entry<Thread, StackTraceElement[]>> order =
                new java.util.ArrayList<java.util.Map.Entry<Thread, StackTraceElement[]>>(stacks.entrySet());
        java.util.Collections.sort(order, new java.util.Comparator<java.util.Map.Entry<Thread, StackTraceElement[]>>() {
            public int compare(java.util.Map.Entry<Thread, StackTraceElement[]> a, java.util.Map.Entry<Thread, StackTraceElement[]> b) {
                return rank(a.getKey().getName()) - rank(b.getKey().getName());
            }
        });
        for (java.util.Map.Entry<Thread, StackTraceElement[]> e : order) {
            Thread t = e.getKey();
            StackTraceElement[] st = e.getValue();
            if (st.length == 0) continue;
            threads.append("Thread \"").append(t.getName()).append("\" ").append(t.getState()).append('\n');
            for (int i = 0; i < st.length && i < 25; i++) threads.append("    at ").append(st[i]).append('\n');
        }
        return context(game, s, threads.toString());
    }

    private static int rank(String name) {
        if (name.equals("main")) return 0;
        if (name.equals("game")) return 1;
        if (name.equals("ai-plan")) return 2;
        return 3;
    }

    public static String build(Game game, Thread thread, Throwable error) {
        StringBuilder s = new StringBuilder("Hexfront crash report\n");
        s.append("When: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        try {
            s.append("Build: ").append(game.platform.buildInfo()).append('\n');
        } catch (Throwable t) {
            s.append("Build: unknown\n");
        }
        s.append("Thread: ").append(thread == null ? "?" : thread.getName()).append('\n');
        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));
        return context(game, s, sw.toString().trim());
    }

    private static String context(Game game, StringBuilder s, String trace) {
        if (s.indexOf("When: ") < 0) {
            s.append("When: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
            try {
                s.append("Build: ").append(game.platform.buildInfo()).append('\n');
            } catch (Throwable t) {
                s.append("Build: unknown\n");
            }
        }
        s.append('\n').append(trace).append("\n\n");
        // Each section is read while the game may be half-way through something; one failing must not lose the rest.
        try {
            Screen sc = game.screen();
            s.append("Screen: ").append(sc == null ? "-" : sc.getClass().getSimpleName()).append('\n');
        } catch (Throwable t) {
            s.append("Screen: ?\n");
        }
        try {
            Company c = game.company;
            if (c != null) {
                s.append("Company: ").append(c.quick ? "quick battle" : "campaign").append(", ").append(Company.DIFFICULTY[c.difficulty])
                        .append(", contract ").append(c.mission).append(", $").append(c.cash).append(", ").append(c.roster.size())
                        .append(" units, ").append(c.deployed.size()).append(" deployed\n");
                if (!c.quick) {
                    s.append("Campaign: ").append(Scenario.SIDE_NAMES[c.side]).append(", front ").append(c.front).append(", next ")
                            .append(Scenario.get(c.scenario).name).append(", battle ").append(c.planetBattles + 1).append(" on planet ")
                            .append(c.planets + 1).append(c.over != 0 ? ", decided" : "").append('\n');
                }
            }
        } catch (Throwable t) {
            s.append("Company: ?\n");
        }
        try {
            if (game.screen() instanceof BattleScreen) {
                Battle b = ((BattleScreen) game.screen()).battle();
                s.append("Battle: round ").append(b.round).append('/').append(b.maxRounds).append(b.deploying ? ", deploying" : "")
                        .append(", ").append(b.side == Battle.PLAYER ? "your" : "enemy").append(" side, active ")
                        .append(b.active == null ? "-" : b.active.name).append(", result ").append(b.result)
                        .append(", ").append(b.alive(Battle.PLAYER)).append(" vs ").append(b.alive(Battle.ENEMY)).append(" alive, map ")
                        .append(HexMap.BIOME_NAMES[b.map.biome]).append(' ').append(Weather.NAMES[b.map.weather.id]).append('\n');
                StringBuilder us = new StringBuilder();
                for (Unit u : b.units) {
                    us.append("  ").append(u.team == Battle.PLAYER ? "you   " : "enemy ").append(u.name).append(" (").append(u.ch.name)
                            .append(u.tag != null ? ", " + u.tag : "").append(") at ").append(u.q).append(',').append(u.r)
                            .append(u.destroyed ? " destroyed" : "").append(u.activated ? " done" : "").append('\n');
                }
                s.append(us);
                int from = Math.max(0, b.log.size() - 15);
                s.append("Battle log:\n");
                for (int i = from; i < b.log.size(); i++) s.append("  ").append(b.log.get(i)).append('\n');
            }
        } catch (Throwable t) {
            s.append("Battle: ?\n");
        }
        s.append("Recent actions:\n");
        for (String c : game.crumbs()) s.append("  ").append(c).append('\n');
        return s.toString();
    }
}
