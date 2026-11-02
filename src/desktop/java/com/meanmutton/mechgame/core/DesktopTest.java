package com.meanmutton.mechgame.core;

import com.meanmutton.mechgame.desktop.AwtGfx;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Desktop harness (not in the APK).
 *   validate        check every factory loadout and print the unit table
 *   maps <dir>      render generated maps for each biome
 *   shots <dir>     render every screen
 *   sim <n>         computer-vs-computer battles: crashes, stalls, balance
 *   icons <resdir>  launcher icons
 */
public final class DesktopTest {
    static final class FakePlatform implements Platform {
        final Map<String, String> store = new HashMap<String, String>();
        public void play(int sound, float volume) {}
        public String load(String key) { return store.get(key); }
        public void save(String key, String value) { store.put(key, value); }
        public void promptText(String title, String initial, TextCallback cb) { cb.done(initial + " II"); }
        public void exit() {}
    }

    static int W = 2400, H = 1080;
    static File out;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "validate";
        if (mode.equals("validate")) validate();
        else if (mode.equals("maps")) maps(new File(args[1]));
        else if (mode.equals("shots")) {
            out = new File(args[1]);
            out.mkdirs();
            shots();
        } else if (mode.equals("sim")) sim(Integer.parseInt(args[1]));
        else if (mode.equals("icons")) icons(new File(args[1]));
    }

    // ------------------------------------------------------------------

    static void validate() {
        int bad = 0;
        System.out.printf("%-24s %-14s %-12s %6s %11s %6s %5s %5s %5s%n", "Unit", "Type", "Role", "Price", "Mass", "Armor", "Dmg", "Heat", "Cool");
        for (Chassis ch : Chassis.ALL) {
            Unit u = new Unit(ch);
            String err = u.validate();
            if (ch.stockEquip.length > ch.equipSlots) err = "too much equipment";
            for (int m = 0; m < ch.mounts.length; m++) {
                if (ch.mounts[m].cls > UnitType.MAX_CLASS[ch.type]) err = "mount class above type limit";
            }
            if (err != null) bad++;
            System.out.printf("%-24s %-14s %-12s %6d %5.1f/%-5.1f %6d %5d %5d %5d %s%n", ch.name, UnitType.NAMES[ch.type],
                    Chassis.ROLES[ch.role], u.value(), u.mass(), ch.capacity, u.armorTotal, Info.alphaDamage(u), Info.alphaHeat(u),
                    u.dissipation(), err == null ? "" : "  <-- " + err);
            // serialization round trip
            Unit back = Unit.parse(u.serialize());
            if (!back.serialize().equals(u.serialize())) {
                System.out.println("   serialization mismatch!");
                bad++;
            }
        }
        if (bad > 0) throw new RuntimeException(bad + " invalid designs");
        System.out.println("all " + Chassis.ALL.length + " designs valid");
    }

    static void maps(File dir) throws Exception {
        dir.mkdirs();
        for (int biome = 0; biome < 4; biome++) {
            for (int s = 0; s < 3; s++) {
                Random r = new Random(biome * 100 + s);
                HexMap m = new HexMap(20, 13, biome, new Weather(Weather.CLEAR));
                m.generate(r);
                BufferedImage img = new BufferedImage(1500, 1000, BufferedImage.TYPE_INT_ARGB);
                AwtGfx g = new AwtGfx(img);
                g.fillRect(0, 0, 1500, 1000, 0xFF000000);
                g.translate(48, 44);
                g.scale(46, 46);
                HexRender.draw(g, m, 0, true);
                g.dispose();
                ImageIO.write(img, "png", new File(dir, "map_" + HexMap.BIOME_NAMES[biome] + "_" + s + ".png"));
            }
        }
        System.out.println("maps written to " + dir);
    }

    // ------------------------------------------------------------------

    static void tick(Game g, int frames) {
        for (int i = 0; i < frames; i++) g.tick(1 / 60f, W, H);
    }

    static void shot(Game g, String name) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        AwtGfx gfx = new AwtGfx(img);
        g.tick(0, W, H);
        g.render(gfx);
        gfx.dispose();
        ImageIO.write(img, "png", new File(out, name + ".png"));
        System.out.println("wrote " + name);
    }

    @SuppressWarnings("unchecked")
    static Btn find(Game g, int id) throws Exception {
        Method m = Screen.class.getDeclaredMethod("activeButtons");
        m.setAccessible(true);
        for (Btn b : (List<Btn>) m.invoke(g.screen())) if (b.id == id) return b;
        throw new RuntimeException("no button " + id + " on " + g.screen().getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    static List<Btn> buttonsOf(Game g) throws Exception {
        Method m = Screen.class.getDeclaredMethod("activeButtons");
        m.setAccessible(true);
        return (List<Btn>) m.invoke(g.screen());
    }

    static void click(Game g, int id) throws Exception {
        Btn b = find(g, id);
        tap(g, b.x + b.w / 2, b.y + b.h / 2);
    }

    static void tap(Game g, float x, float y) {
        g.touch(Game.TOUCH_DOWN, 0, x, y);
        g.touch(Game.TOUCH_UP, 0, x, y);
        tick(g, 1);
    }

    static void shots() throws Exception {
        FakePlatform fp = new FakePlatform();
        Game g = new Game(fp, 11);
        tick(g, 10);
        shot(g, "01_menu");
        click(g, 2);
        shot(g, "02_new_game");
        g.newCampaign(1, 14000);
        Company c = g.company;
        int[] buys = {21, 4, 9, 11, 18, 3};
        for (int id : buys) {
            Unit u = new Unit(Chassis.ALL[id]);
            c.roster.add(u);
            c.deployed.add(u);
        }
        c.roster.add(new Unit(Chassis.ALL[27]));
        c.roster.get(1).armor[0] -= 20;
        c.roster.get(1).infra[0] -= 5;
        tick(g, 2);
        shot(g, "03_hq");
        click(g, 2);
        tick(g, 2);
        shot(g, "04_market");
        // regression: after scrolling the grid, the tabs must still work
        {
            MarketScreen ms = (MarketScreen) g.screen();
            Btn card = find(g, 100 + 4);
            g.touch(Game.TOUCH_DOWN, 0, card.x + card.w / 2, card.y + card.h / 2);
            for (int k = 1; k <= 10; k++) g.touch(Game.TOUCH_MOVE, 0, card.x + card.w / 2, card.y + card.h / 2 - k * 40);
            g.touch(Game.TOUCH_UP, 0, card.x + card.w / 2, card.y + card.h / 2 - 400);
            tick(g, 2);
            shot(g, "04b_market_scrolled");
            click(g, 12);   // "Vehicles" tab
            boolean ok = true;
            for (Btn b : buttonsOf(g)) if (b.id >= 100 && UnitType.isMech(Chassis.ALL[b.id - 100].type)) ok = false;
            if (!ok) throw new RuntimeException("market tab ignored after scrolling");
            System.out.println("market tabs after scrolling OK");
            click(g, 10);   // back to "All"
            // double-tap a card for the detail sheet
            Btn c2 = find(g, 100 + 5);
            tap(g, c2.x + c2.w / 2, c2.y + c2.h / 2);
            c2 = find(g, 100 + 5);
            tap(g, c2.x + c2.w / 2, c2.y + c2.h / 2);
            shot(g, "04c_market_detail");
            tap(g, 10, 10);   // closes
        }
        click(g, 11);
        click(g, 100 + 29);
        shot(g, "05_market_mechs");
        g.toHq();
        tick(g, 1);
        g.setScreen(new RefitScreen(g, c.roster.get(0)));
        tick(g, 2);
        shot(g, "06_refit");
        click(g, 100);
        shot(g, "07_refit_weapon_chooser");
        click(g, 499);
        click(g, 400);
        shot(g, "08_refit_equipment_chooser");
        click(g, 499);
        g.toHq();
        g.prepareMission();
        tick(g, 2);
        shot(g, "09_briefing");
        click(g, 2);
        BattleScreen bs = (BattleScreen) g.screen();
        Battle b = bs.battle();
        tick(g, 20);
        shot(g, "10_deployment");
        // place the infantry far forward, in the middle of the map
        Unit inf = null;
        for (Unit un : b.units) if (un.team == Battle.PLAYER && un.type == UnitType.INFANTRY) inf = un;
        float[] is = hexToScreen(bs, inf.q, inf.r);
        tap(g, is[0], is[1]);
        shot(g, "10b_deployment_infantry_selected");
        int tc = -1, tr = -1;
        for (int cc = b.map.cols / 2; cc < b.map.cols && tc < 0; cc++) for (int rr = 0; rr < b.map.rows && tc < 0; rr++) if (b.canPlace(inf, cc, rr) && b.unitAt(cc, rr) == null) { tc = cc; tr = rr; }
        float[] ts0 = hexToScreen(bs, tc, tr);
        tick(g, 40);   // avoid registering a double tap
        tap(g, ts0[0], ts0[1]);
        if (inf.q != tc || inf.r != tr) {
            // the hex may be off-screen at this zoom; place it directly so the flow continues
            b.place(inf, tc, tr);
        }
        System.out.println("infantry placed at " + inf.q + "," + inf.r);
        shot(g, "10c_deployment_placed");
        // double-tap a unit for its detail sheet
        Unit mech = b.units.get(0);
        float[] ms = hexToScreen(bs, mech.q, mech.r);
        tap(g, ms[0], ms[1]);
        tap(g, ms[0], ms[1]);
        shot(g, "10d_unit_detail");
        tap(g, 20, 20);
        click(g, 13);   // start battle
        tick(g, 20);
        shot(g, "10_battle_start");
        // let the AI go if it has initiative, then select our first unit
        for (int i = 0; i < 2000 && !(b.playerTurn() && !b.busy()); i++) tick(g, 1);
        click(g, 12); // next unit
        tick(g, 5);
        shot(g, "11_battle_unit_selected");
        // move the unit toward the enemy: tap a reachable hex twice
        Unit a = b.active;
        int[] prev = new int[b.map.cols * b.map.rows];
        float[] reach = b.reach(a, prev, b.mpAvailable(a));
        int best = -1;
        for (int k = 0; k < reach.length; k++) if (reach[k] < Float.MAX_VALUE && (best < 0 || k % b.map.cols > best % b.map.cols)) best = k;
        if (best >= 0) {
            float[] s = hexToScreen(bs, best % b.map.cols, best / b.map.cols);
            tap(g, s[0], s[1]);
            shot(g, "12_battle_move_preview");
            tap(g, s[0], s[1]);
            tick(g, 60);
        }
        // target the nearest enemy
        Unit tgt = null;
        for (Unit e : b.units) if (e.team == Battle.ENEMY && !e.destroyed && (tgt == null || HexMap.distance(a.q, a.r, e.q, e.r) < HexMap.distance(a.q, a.r, tgt.q, tgt.r))) tgt = e;
        float[] ts = hexToScreen(bs, tgt.q, tgt.r);
        tap(g, ts[0], ts[1]);
        shot(g, "13_battle_targeting");
        click(g, 2); // fire
        tick(g, 25);
        shot(g, "14_battle_firing");
        tick(g, 120);
        click(g, 1); // end turn
        // play the rest automatically, taking a few snapshots
        b.autoPlayer = true;
        int snap = 0;
        for (int i = 0; i < 60 * 60 * 20 && g.screen() == bs; i++) {
            tick(g, 1);
            if (i % 1500 == 700 && snap < 4) shot(g, "15_battle_progress_" + (snap++));
        }
        shot(g, "16_debrief");
        g.toHq();
        tick(g, 2);
        shot(g, "17_hq_after");
        for (int p = 0; p < 7; p++) {
            HelpScreen hs = new HelpScreen(g);
            g.setScreen(hs);
            for (int k = 0; k < p; k++) click(g, 3);
            if (p == 0 || p == 2 || p == 5) shot(g, "20_help_" + p);
        }
        // weather variety on a smaller screen
        W = 1600;
        H = 900;
        int[] wx = {Weather.RAIN, Weather.SNOW, Weather.FOG, Weather.NIGHT, Weather.SANDSTORM};
        int[] biomes = {0, 2, 0, 3, 1};
        for (int i = 0; i < wx.length; i++) {
            HexMap m = new HexMap(20, 13, biomes[i], new Weather(wx[i]));
            m.generate(new Random(77 + i));
            List<Unit> mine = new ArrayList<Unit>(), theirs = new ArrayList<Unit>();
            for (int k = 0; k < 4; k++) mine.add(new Unit(Chassis.ALL[(k * 7 + i) % Chassis.ALL.length]));
            for (int k = 0; k < 4; k++) theirs.add(new Unit(Chassis.ALL[(k * 5 + 3 + i) % Chassis.ALL.length]));
            Battle bb = new Battle(g, mine, theirs, m, new Random(i), 1);
            bb.finishDeployment();
            g.setScreen(new BattleScreen(g, bb));
            tick(g, 30);
            shot(g, "30_weather_" + Weather.NAMES[wx[i]].replace(' ', '_'));
        }
        W = 2400;
        H = 1080;
    }

    static float[] hexToScreen(BattleScreen bs, int c, int r) throws Exception {
        java.lang.reflect.Field fx = BattleScreen.class.getDeclaredField("camX");
        java.lang.reflect.Field fy = BattleScreen.class.getDeclaredField("camY");
        java.lang.reflect.Field fs = BattleScreen.class.getDeclaredField("cs");
        fx.setAccessible(true);
        fy.setAccessible(true);
        fs.setAccessible(true);
        float cs = fs.getFloat(bs);
        return new float[]{fx.getFloat(bs) + HexMap.px(c, r) * cs, fy.getFloat(bs) + HexMap.py(c, r) * cs};
    }

    // ------------------------------------------------------------------

    static List<Unit> force(Random r, int budget, int kind) {
        // kind: 0 any, 1 mechs only, 2 vehicles/infantry only
        List<Unit> l = new ArrayList<Unit>();
        int left = budget;
        for (int tries = 0; tries < 200 && l.size() < 6; tries++) {
            Chassis ch = Chassis.ALL[r.nextInt(Chassis.ALL.length)];
            if (kind == 1 && !UnitType.isMech(ch.type)) continue;
            if (kind == 2 && UnitType.isMech(ch.type)) continue;
            Unit u = new Unit(ch);
            if (u.value() > left) continue;
            int slotsLeft = 6 - l.size();
            if (slotsLeft > 1 && u.value() > left * 0.7f) continue;
            l.add(u);
            left -= u.value();
        }
        return l;
    }

    static int value(List<Unit> l) {
        int v = 0;
        for (Unit u : l) v += u.value();
        return v;
    }

    static void sim(int n) throws Exception {
        FakePlatform fp = new FakePlatform();
        Game g = new Game(fp, 5);
        g.headless = true;
        g.settings.aiSpeed = 2;
        Random r = new Random(1234);
        int[] results = new int[5];
        int mechWins = 0, vehWins = 0, mixed = 0, draws = 0;
        long worst = 0;
        int n2 = Chassis.ALL.length;
        int[] appear = new int[n2], killsBy = new int[n2], survived = new int[n2];
        float[] dealt = new float[n2], cost = new float[n2];
        int totalRounds = 0;
        for (int i = 0; i < n; i++) {
            int budget = 7000 + r.nextInt(14000);
            boolean mechTest = i % 2 == 1;
            List<Unit> e = force(r, budget, mechTest ? 2 : 0);
            // value-matched: the mech side gets exactly what the vehicle side actually spent
            List<Unit> a = force(r, value(e), mechTest ? 1 : 0);
            for (int tries = 0; tries < 40 && Math.abs(value(a) - value(e)) > value(e) * 0.08f; tries++) {
                List<Unit> alt = force(r, value(e), mechTest ? 1 : 0);
                if (Math.abs(value(alt) - value(e)) < Math.abs(value(a) - value(e))) a = alt;
            }
            if (a.isEmpty() || e.isEmpty()) continue;
            int biome = r.nextInt(4);
            HexMap m = new HexMap(20, 13, biome, Weather.random(biome, r));
            m.generate(r);
            if (Boolean.getBoolean("swap")) {
                List<Unit> tmp = a;
                a = e;
                e = tmp;
            }
            Battle b = new Battle(g, a, e, m, new Random(r.nextLong()), 1);
            b.autoPlayer = true;
            b.finishDeployment();
            int frames = 0;
            while (b.result == Battle.RESULT_NONE) {
                long t0 = System.nanoTime();
                b.update(1 / 20f);
                worst = Math.max(worst, System.nanoTime() - t0);
                if (++frames > 20 * 60 * 120) throw new RuntimeException("battle " + i + " stalled at round " + b.round);
            }
            totalRounds += Math.min(b.round, Battle.MAX_ROUNDS);
            for (Unit u : b.units) {
                int id = u.ch.id;
                appear[id]++;
                dealt[id] += u.damageDealt;
                killsBy[id] += u.kills;
                cost[id] += u.value();
                if (!u.destroyed) survived[id]++;
            }
            results[b.result]++;
            String winner = b.result == Battle.RESULT_WIN ? "A" : (b.result == Battle.RESULT_LOSS ? "B" : "draw");
            if (mechTest) {
                if (b.result == Battle.RESULT_WIN) mechWins++;
                else if (b.result == Battle.RESULT_LOSS) vehWins++;
                else draws++;
            } else mixed++;
            System.out.printf("battle %2d %-9s %-6s value %5d vs %5d (%d vs %d units) -> %s in %d rounds, survivors %d/%d%n", i,
                    HexMap.BIOME_NAMES[biome], mechTest ? "M-v-V" : "mixed", value(a), value(e), a.size(), e.size(), winner,
                    Math.min(b.round, Battle.MAX_ROUNDS), b.alive(0), b.alive(1));
        }
        System.out.println();
        System.out.printf("%-24s %5s %8s %8s %6s %8s%n", "design", "games", "dmg/game", "dmg/1k$", "kills", "survive");
        for (int i = 0; i < n2; i++) {
            if (appear[i] == 0) continue;
            System.out.printf("%-24s %5d %8.1f %8.1f %6.2f %7.0f%%%n", Chassis.ALL[i].name, appear[i], dealt[i] / appear[i],
                    dealt[i] / (cost[i] / 1000f), killsBy[i] / (float) appear[i], 100f * survived[i] / appear[i]);
        }
        System.out.printf("mechs vs equal-value vehicles: mechs won %d, vehicles won %d, draws %d%n", mechWins, vehWins, draws);
        System.out.printf("results: A %d, B %d, draw %d; average rounds %.1f; worst AI frame %.1f ms%n", results[1], results[2], results[3],
                totalRounds / (float) Math.max(1, n), worst / 1e6);
    }

    // ------------------------------------------------------------------

    static void icons(File res) throws Exception {
        String[] dens = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] px = {48, 72, 96, 144, 192};
        for (int i = 0; i < dens.length; i++) {
            int n = px[i];
            BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
            AwtGfx g = new AwtGfx(img);
            g.fillCircle(n / 2f, n / 2f, n * 0.47f, 0xFF1A2632);
            g.save();
            g.translate(n / 2f, n / 2f);
            g.scale(n * 0.17f, n * 0.17f);
            for (int c = -2; c <= 2; c++) for (int r = -2; r <= 2; r++) HexRender.strokeHex(g, c, r, 0.95f, 0.06f, 0x447AA8D8);
            g.restore();
            Chassis ch = Chassis.ALL[25];
            UnitArt.drawChassis(g, ch, ch.stockWeapons, n * 0.5f, n * 0.55f, -90, UnitArt.PLAYER_COLOR, n * 0.36f, 0, 1, 1);
            g.strokeCircle(n / 2f, n / 2f, n * 0.47f, n * 0.03f, 0xFFFFB02E);
            g.dispose();
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
                float dx = x + 0.5f - n / 2f, dy = y + 0.5f - n / 2f;
                if (dx * dx + dy * dy > (n * 0.49f) * (n * 0.49f)) img.setRGB(x, y, 0);
            }
            File d = new File(res, "mipmap-" + dens[i]);
            d.mkdirs();
            ImageIO.write(img, "png", new File(d, "ic_launcher.png"));
        }
    }
}
