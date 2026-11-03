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
 *   campaign <n>    play n campaign missions computer-vs-computer (scenarios, front, rival, saves)
 *   odds            how often each scenario comes up, and how long campaigns run
 *   monkey <seeds> [steps]  random taps, drags and back presses through every screen, looking for crashes
 *   resume          battles saved mid-fight and resumed after a restart, in a campaign and a quick battle
 */
public final class DesktopTest {
    static final class FakePlatform implements Platform {
        final Map<String, String> store = new HashMap<String, String>();
        public void play(int sound, float volume) {}
        public String load(String key) { return store.get(key); }
        public void save(String key, String value) { store.put(key, value); }
        public void promptText(String title, String initial, TextCallback cb) { cb.done(initial + " II"); }
        public void exit() {}
        public void saveNow(String key, String value) { store.put(key, value); }
        String shared;
        public void share(String subject, String text) { shared = text; }
        public String buildInfo() { return "desktop harness"; }
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
        else if (mode.equals("gallery")) gallery(new File(args[1]));
        else if (mode.equals("campaign")) campaign(Integer.parseInt(args[1]));
        else if (mode.equals("resume")) resume();
        else if (mode.equals("monkey")) monkey(Integer.parseInt(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 30000, false);
        else if (mode.equals("monkey-threads")) monkey(Integer.parseInt(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 30000, true);
        else if (mode.equals("odds")) {
            System.out.print("attacker/defender resources by battle:");
            for (int b = 0; b <= 24; b += 2) System.out.printf(" %d: %.2f%s", b, Scenario.attackerRatio(b),
                    Scenario.extraUnits(0, b) > 0 ? " (A+" + Scenario.extraUnits(0, b) + ")" : (Scenario.extraUnits(1, b) > 0 ? " (D+" + Scenario.extraUnits(1, b) + ")" : ""));
            System.out.println();
            scenarioOdds(4000, 0f, false);
            scenarioOdds(4000, 1f, false);
            scenarioOdds(4000, 2f, true);
        }
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
        // artillery must get more accurate with range; direct fire the other way round
        {
            FakePlatform fp = new FakePlatform();
            Game g = new Game(fp, 1);
            g.headless = true;
            HexMap m = new HexMap(20, 13, 0, new Weather(Weather.CLEAR));   // flat open ground, clear sight
            Unit spg = new Unit(Chassis.ALL[6]);        // Thunder SPG: siege howitzer, min 4 / 5 / 11 / 18
            Unit tank = new Unit(Chassis.ALL[4]);       // Ironhide: battle cannon 3/6/9
            Unit target = new Unit(Chassis.ALL[15]);
            List<Unit> mine = new ArrayList<Unit>(), theirs = new ArrayList<Unit>();
            mine.add(spg);
            mine.add(tank);
            theirs.add(target);
            Battle b = new Battle(g, mine, theirs, m, new Random(1), 1);
            b.finishDeployment();
            target.q = 19;
            target.r = 6;
            target.movedHexes = 0;
            int[] dists = {2, 4, 8, 16};
            int[] arty = new int[dists.length], gun = new int[dists.length];
            for (int i = 0; i < dists.length; i++) {
                spg.q = tank.q = 19 - dists[i];
                spg.r = tank.r = 6;
                spg.facing = tank.facing = 1;
                spg.moved = tank.moved = false;
                arty[i] = b.hitChance(spg, 0, target, null);
                gun[i] = b.hitChance(tank, 0, target, null);
            }
            System.out.println("siege howitzer hit % at 2/4/8/16 hexes: " + arty[0] + " " + arty[1] + " " + arty[2] + " " + arty[3]);
            System.out.println("battle cannon  hit % at 2/4/8/16 hexes: " + gun[0] + " " + gun[1] + " " + gun[2] + " " + gun[3]);
            if (!(arty[3] > arty[2] && arty[2] > arty[1] && arty[1] > arty[0])) throw new RuntimeException("artillery accuracy should rise with range");
            if (!(gun[0] > gun[2])) throw new RuntimeException("direct fire should fall with range");
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
        c.scenario = 4;   // a pitched battle, so every unit type can deploy
        c.rollContract(g.rng);
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
        tick(g, 20);
        shot(g, "14b_battle_impacts");
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
        // a close-range exchange to show the hit feedback: impact panel, map bursts and the per-location log
        {
            HexMap m = new HexMap(20, 13, 0, new Weather(Weather.CLEAR));
            m.generate(new Random(5));
            List<Unit> mine = new ArrayList<Unit>(), theirs = new ArrayList<Unit>();
            Unit shooter = new Unit(Chassis.ALL[29]);   // Monolith
            Unit victim = new Unit(Chassis.ALL[21]);    // Halberd
            mine.add(shooter);
            theirs.add(victim);
            Battle bb = new Battle(g, mine, theirs, m, new Random(3), 1);
            bb.finishDeployment();
            for (int cc = 6; cc < 8; cc++) for (int rr = 5; rr < 7; rr++) { m.terrain[m.idx(cc, rr)] = HexMap.CLEAR; m.elev[m.idx(cc, rr)] = 0; }
            shooter.q = 6; shooter.r = 6; victim.q = 7; victim.r = 6;
            shooter.facing = 1; victim.facing = 4;
            bb.syncDraw(shooter);
            bb.syncDraw(victim);
            bb.side = Battle.PLAYER;
            bb.beginActivation(shooter);
            BattleScreen dbs = new BattleScreen(g, bb);
            g.setScreen(dbs);
            tick(g, 5);
            boolean[] all = new boolean[shooter.weapon.length];
            for (int k = 0; k < all.length; k++) all[k] = true;
            bb.fire(shooter, victim, all);
            for (int k = 0; k < 40; k++) {
                tick(g, 1);
                if (k == 22) shot(g, "14c_impact_panel");
            }
            tick(g, 120);
            shot(g, "14d_impact_log");
            System.out.println("last log: " + bb.log.get(bb.log.size() - 1));
        }
        // campaign scenarios: restrictions at HQ, a base assault with garrison and emplacements, the end screen
        {
            // a big roster: two columns at HQ, deployed up to the full-size limits
            c.scenario = 4;
            for (int id : new int[]{25, 29, 0, 1, 2, 14, 15, 16, 30, 12, 0}) c.roster.add(new Unit(Chassis.ALL[id]));
            c.deployed.clear();
            for (Unit un : c.roster) if (c.canDeploy(un)) c.deployed.add(un);
            if (!c.limits().holds(c.deployed) || c.deployed.size() != c.deployLimit()) throw new RuntimeException("full-size deployment wrong: " + c.deployed.size());
            g.toHq();
            tick(g, 2);
            shot(g, "39_hq_big_roster");
            c.scenario = Scenario.INITIAL_DROP;
            c.front = Scenario.FRONT_START;
            c.fitDeployment();
            g.toHq();
            tick(g, 2);
            shot(g, "40_hq_initial_drop");
            click(g, 2);   // Market
            tick(g, 2);
            shot(g, "40b_market_initial_drop");
            c.scenario = Scenario.BASE_ASSAULT;
            c.front = Scenario.FRONT_BASE;
            c.planetBattles = 9;
            c.edge = Scenario.ATTACKER;
            c.rollContract(g.rng);
            for (Unit un : c.roster) un.repairFully();
            c.deployed.clear();
            for (Unit un : c.roster) if (c.canDeploy(un)) c.deployed.add(un);
            g.prepareMission();
            tick(g, 2);
            shot(g, "41_briefing_base_assault");
            click(g, 2);
            tick(g, 20);
            shot(g, "42_deployment_base_assault");
            BattleScreen bs2 = (BattleScreen) g.screen();
            Battle b2 = bs2.battle();
            int fixedCount = 0;
            for (Unit un : b2.units) if (un.ch.fixed()) {
                fixedCount++;
                if (b2.canPlace(un, un.q, un.r)) throw new RuntimeException("emplacement can be moved");
            }
            if (fixedCount != 2) throw new RuntimeException("expected 2 emplacements, found " + fixedCount);
            b2.autoPlayer = true;
            click(g, 13);
            long until = System.currentTimeMillis() + 180000;
            for (int i = 0; g.screen() == bs2 && System.currentTimeMillis() < until; i++) {
                tick(g, 1);
                if (i % 20 == 0) Thread.sleep(1);   // the AI plans on its own thread: give it real time
                if (i == 900) shot(g, "43_base_assault_battle");
            }
            System.out.println("base assault: round " + b2.round + ", result " + b2.result + ", side " + b2.side + ", active "
                    + (b2.active == null ? "-" : b2.active.name) + ", busy " + b2.busy() + ", fx " + b2.fx.size() + ", mover " + b2.mover);
            shot(g, "44_debrief_campaign");
            c.over = Scenario.OUT_ATTACKER;
            g.setScreen(new CampaignEndScreen(g));
            tick(g, 2);
            shot(g, "45_campaign_end");
            click(g, 3);   // defend a new planet
            tick(g, 2);
            if (c.side != Scenario.DEFENDER || c.scenario != Scenario.INITIAL_DROP) throw new RuntimeException("next planet not set up");
            shot(g, "46_hq_new_planet_defending");
            HelpScreen hs = new HelpScreen(g);
            g.setScreen(hs);
            for (int k = 0; k < 7; k++) click(g, 3);
            shot(g, "47_help_scenarios");
            click(g, 3);
            shot(g, "48_help_time_and_money");
        }
        // a crash report: saved when the game dies, shown on the next launch, shared or closed
        {
            g.prepareMission();
            tick(g, 2);
            click(g, 2);   // begin battle
            tick(g, 5);
            try {
                Object o = null;
                o.toString();
            } catch (NullPointerException e) {
                g.recordCrash(Thread.currentThread(), e);
            }
            Game g2 = new Game(fp, 5);
            tick(g2, 2);
            if (!(g2.screen() instanceof CrashScreen)) throw new RuntimeException("crash report not shown at launch");
            shot(g2, "50_crash_report");
            click(g2, 1);   // share
            if (fp.shared == null || !fp.shared.contains("NullPointerException") || !fp.shared.contains("Battle: round"))
                throw new RuntimeException("crash report not shared in full");
            click(g2, 2);   // close
            tick(g2, 1);
            if (!(g2.screen() instanceof MenuScreen) || new Game(fp, 6).screen() instanceof CrashScreen)
                throw new RuntimeException("crash report not cleared");
            System.out.println("crash report OK (" + fp.shared.length() + " chars)");
            // a freeze report, as the phone's watchdog saves it
            g2.recordFreeze("The game's drawing thread made no progress for 6 s.", Thread.getAllStackTraces());
            Game g3 = new Game(fp, 7);
            tick(g3, 2);
            if (!(g3.screen() instanceof CrashScreen)) throw new RuntimeException("freeze report not shown at launch");
            shot(g3, "51_freeze_report");
            click(g3, 1);
            if (!fp.shared.startsWith("Hexfront freeze report") || !fp.shared.contains("Thread \"main\""))
                throw new RuntimeException("freeze report incomplete");
            click(g3, 2);
            System.out.println("freeze report OK (" + fp.shared.length() + " chars)");
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
            for (int k = 0; k < 4; k++) mine.add(new Unit(Chassis.SHOP[(k * 7 + i) % Chassis.SHOP.length]));
            for (int k = 0; k < 4; k++) theirs.add(new Unit(Chassis.SHOP[(k * 5 + 3 + i) % Chassis.SHOP.length]));
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
            Chassis ch = Chassis.SHOP[r.nextInt(Chassis.SHOP.length)];
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

    /**
     * Plays campaigns with the computer on both sides: scenarios, the front, unit restrictions, garrisons,
     * the rival company's money and roster, and saves. When a planet is decided it moves on to the next,
     * alternating attack and defense.
     */
    static void campaign(int missions) throws Exception {
        FakePlatform fp = new FakePlatform();
        Game g = new Game(fp, 2024);
        g.headless = true;
        g.newCampaign(1, 10000, Scenario.ATTACKER);
        Company c = g.company;
        Random r = new Random(7);
        int[] seen = new int[Scenario.ALL.length];
        int[][] won = new int[2][2];   // [player side][planet taken by attacker?]
        int planetStart = 1, forfeits = 0;
        List<Integer> lengths = new ArrayList<Integer>();
        String[] res = {"", "WIN", "LOSS", "DRAW", "WITHDRAW"};
        for (int m = 1; m <= missions; m++) {
            Scenario sc = c.next();
            seen[sc.id]++;
            // the player side: repair, buy for the next scenario (as a player who read the briefing would), deploy
            for (Unit u : c.roster) if (u.isDamaged() && c.cash >= u.repairCost()) { c.cash -= u.repairCost(); u.repairFully(); }
            int allowed = 0;
            for (Unit u : c.roster) if (!u.destroyed && c.allowed(u)) allowed++;
            // buy for the open slots: the best allowed unit of each kind (mech, vehicle, infantry) it can afford with
            // a share of its cash; with a full roster, sell the cheapest unit to make room for a much better one
            Limits lim = c.limits();
            for (int buys = 0; buys < 5; buys++) {
                int[] have = new int[4];
                for (Unit u : c.roster) if (!u.destroyed && c.allowed(u)) have[Limits.kind(u.type)]++;
                int[] room = {lim.mechs - have[0], lim.heavy - Math.min(have[0], lim.mechs) - have[1], lim.infantry - have[2], 0};
                Unit best = null;
                for (Chassis ch : Chassis.SHOP) {
                    int k = Limits.kind(ch.type);
                    if (!sc.allows(ch) || room[k] <= 0 || (k == Limits.MECH && room[1] + room[0] <= 0)) continue;
                    Unit u = new Unit(ch);
                    if (u.value() <= c.cash * 0.5f && (best == null || u.value() > best.value()) && r.nextFloat() < 0.8f) best = u;
                }
                if (best == null) break;
                if (c.roster.size() >= Company.MAX_ROSTER - 1) {
                    Unit cheapest = null;
                    for (Unit u : c.roster) if (cheapest == null || u.value() < cheapest.value()) cheapest = u;
                    if (cheapest.value() * 2 > best.value()) break;
                    c.cash += cheapest.destroyed ? cheapest.value() / 5 : cheapest.value() / 2;
                    c.roster.remove(cheapest);
                    c.deployed.remove(cheapest);
                }
                c.cash -= best.value();
                c.roster.add(best);
            }
            java.util.Collections.sort(c.roster, new java.util.Comparator<Unit>() {
                public int compare(Unit x, Unit y) { return y.value() - x.value(); }
            });
            if (c.noneAllowed() && !c.mustForfeit()) {   // the HQ won't offer a forfeit while one is affordable: buy the cheapest
                Unit cheapest = null;
                for (Chassis ch : Chassis.SHOP) {
                    Unit u = new Unit(ch);
                    if (sc.allows(ch) && u.value() <= c.cash && (cheapest == null || u.value() < cheapest.value())) cheapest = u;
                }
                c.cash -= cheapest.value();
                c.roster.add(cheapest);
            }
            c.deployed.clear();
            for (Unit u : c.roster) if (c.canDeploy(u) && !c.deployed.contains(u)) c.deployed.add(u);
            if (!c.limits().holds(c.deployed)) throw new RuntimeException("deployed past the scenario limits");
            for (Unit u : c.deployed) if (!sc.allows(u)) throw new RuntimeException(u.name + " deployed in " + sc.name);
            int rivalCashBefore = c.rival.cash, rivalUnitsBefore = c.rival.roster.size(), frontBefore = c.front;
            if (c.deployed.isEmpty()) {
                if (!c.mustForfeit()) throw new RuntimeException("forfeit offered while an allowed unit is affordable");
                g.forfeitMission();
                forfeits++;
                System.out.printf("mission %2d: %-21s FORFEIT (no allowed units)%n", m, sc.name);
            } else {
                g.prepareMission();
                BriefingScreen bs = (BriefingScreen) g.screen();
                java.lang.reflect.Field fm = BriefingScreen.class.getDeclaredField("map");
                java.lang.reflect.Field fe = BriefingScreen.class.getDeclaredField("enemy");
                java.lang.reflect.Field fa = BriefingScreen.class.getDeclaredField("allies");
                fm.setAccessible(true);
                fe.setAccessible(true);
                fa.setAccessible(true);
                @SuppressWarnings("unchecked") List<Unit> enemy = (List<Unit>) fe.get(bs);
                @SuppressWarnings("unchecked") List<Unit> allies = (List<Unit>) fa.get(bs);
                int fielded = 0;
                for (Unit e : enemy) {
                    if (e.tag == null && !c.rival.roster.contains(e)) throw new RuntimeException("enemy unit not from the rival roster");
                    if (!"garrison".equals(e.tag)) {
                        fielded++;
                        if (!sc.allows(e)) throw new RuntimeException("enemy " + e.name + " not allowed in " + sc.name);
                    }
                }
                if (!sc.limitsFor(1 - c.side, c.planetBattles).holds(enemy)) throw new RuntimeException("enemy over the unit limits");
                if (fielded == 0) throw new RuntimeException("enemy fielded nothing");
                g.startBattle((HexMap) fm.get(bs), enemy, allies);
                Battle b = ((BattleScreen) g.screen()).battle();
                if (b.maxRounds != sc.rounds) throw new RuntimeException("scenario rounds not applied");
                b.autoPlayer = true;
                b.finishDeployment();
                int frames = 0;
                while (b.result == Battle.RESULT_NONE) {
                    b.update(1 / 20f);
                    if (++frames > 20 * 60 * 120) throw new RuntimeException("stalled");
                }
                for (Unit u : b.units) if (u.ch.fixed() && (u.q < 0 || u.r < 0)) throw new RuntimeException("emplacement not placed");
                g.finishBattle(b);
                int garrison = 0, hired = 0;
                for (Unit e : enemy) if ("garrison".equals(e.tag)) garrison++; else if ("hired".equals(e.tag)) hired++;
                System.out.printf("mission %2d: %-21s %-8s %-4s front %d->%d  you $%6d %2d units | rival $%6d->$%6d %d->%d units, fielded %d%s%s%s%n",
                        m, sc.name, Scenario.SIDE_NAMES[c.side], res[b.result], frontBefore, c.front, c.cash, c.roster.size(),
                        rivalCashBefore, c.rival.cash, rivalUnitsBefore, c.rival.roster.size(), fielded,
                        hired > 0 ? " (" + hired + " hired)" : "", garrison > 0 ? " +" + garrison + " enemy garrison" : "",
                        allies.isEmpty() ? "" : " +" + allies.size() + " your garrison");
            }
            // the save must round-trip both companies and the campaign state
            Company back = Company.parse(fp.load("company"));
            Company rback = Company.parse(fp.load("rival"));
            if (back == null || rback == null || rback.cash != c.rival.cash || rback.roster.size() != c.rival.roster.size()
                    || back.front != c.front || back.scenario != c.scenario || back.side != c.side || back.over != c.over || back.edge != c.edge)
                throw new RuntimeException("save round trip failed");
            if (c.over != 0) {
                boolean playerWon = c.over == (c.side == Scenario.ATTACKER ? Scenario.OUT_ATTACKER : Scenario.OUT_DEFENDER);
                System.out.println("== planet decided after " + (m - planetStart + 1) + " battles (attacker/defender resources "
                        + String.format(java.util.Locale.US, "%.2f", Scenario.attackerRatio(m - planetStart + 1)) + "): " + (c.over == Scenario.OUT_ATTACKER
                        ? "the attacker took it" : "the attacker left") + " (you were the " + Scenario.SIDE_NAMES[c.side].toLowerCase()
                        + ", " + (playerWon ? "won" : "lost") + ")");
                won[c.side][c.over == Scenario.OUT_ATTACKER ? 1 : 0]++;
                lengths.add(m - planetStart + 1);
                planetStart = m + 1;
                g.nextPlanet(1 - c.side);
                if (c.scenario != Scenario.INITIAL_DROP || c.front != Scenario.FRONT_START) throw new RuntimeException("new planet not reset");
            }
        }
        System.out.println("scenarios played:");
        for (Scenario s : Scenario.ALL) if (seen[s.id] > 0) System.out.printf("  %-22s %d%n", s.name, seen[s.id]);
        System.out.println("planets decided: " + lengths + " battles each; attacker took " + (won[0][1] + won[1][1])
                + ", attacker left " + (won[0][0] + won[1][0]) + "; forfeits " + forfeits);
    }

    /**
     * Rolls many campaigns' worth of scenario sequences to show how often each comes up, how long campaigns run,
     * and who takes the planet. A rough model of the economy decides each battle: the attacker's chance follows
     * its share of the two sides' resources, R^k / (1 + R^k), where R is Scenario.attackerRatio and k says how
     * much money matters. 10% of battles are draws.
     */
    static void scenarioOdds(int campaigns, float k, boolean verbose) {
        Random r = new Random(99);
        int[] seen = new int[Scenario.ALL.length];
        int attackerTook = 0, total = 0, unfinished = 0, longest = 0;
        int[] lengths = new int[61];
        for (int c = 0; c < campaigns; c++) {
            int front = Scenario.FRONT_START, id = Scenario.INITIAL_DROP, last = -1, m = 0;
            boolean done = false;
            for (; m < 60 && !done; m++) {
                seen[id]++;
                total++;
                double rk = Math.pow(Scenario.attackerRatio(m), k);
                float p = (float) (rk / (1 + rk));
                float roll = r.nextFloat();
                int out = roll < 0.1f ? Scenario.OUT_DRAW : (roll < 0.1f + 0.9f * p ? Scenario.OUT_ATTACKER : Scenario.OUT_DEFENDER);
                Scenario sc = Scenario.get(id);
                if (id == Scenario.BASE_ASSAULT && out == Scenario.OUT_ATTACKER) { attackerTook++; done = true; }
                else if (id == Scenario.EVACUATION && out == Scenario.OUT_DEFENDER) done = true;
                else {
                    front = sc.frontAfter(front, out);
                    last = id;
                    id = Scenario.roll(front, out, last, r);
                }
            }
            if (!done) unfinished++;
            longest = Math.max(longest, m);
            lengths[m]++;
        }
        int median = 0, acc = 0;
        while (acc + lengths[median] < campaigns / 2) acc += lengths[median++];
        int p90 = 0;
        acc = 0;
        while (acc + lengths[p90] < campaigns * 9 / 10) acc += lengths[p90++];
        System.out.printf("money weight k=%.1f: attacker took %4.1f%% of planets; battles per planet: mean %.1f, median %d, 90%% within %d, longest %d; %d unfinished at 60%n",
                k, attackerTook * 100f / campaigns, total / (float) campaigns, median, p90, longest, unfinished);
        if (verbose) for (Scenario s : Scenario.ALL) System.out.printf("  %-22s %5.1f%%%n", s.name, seen[s.id] * 100f / total);
    }

    /** One line per unit plus the turn state: two battles with the same fingerprint are in the same position. */
    static String fingerprint(Battle b) {
        StringBuilder s = new StringBuilder("round " + b.round + " side " + b.side + " init " + b.initiative + " deploying " + b.deploying
                + " active " + (b.active == null ? "-" : b.units.indexOf(b.active)) + "\n");
        for (Unit u : b.units) {
            s.append(u.name).append(' ').append(u.team).append(' ').append(u.q).append(',').append(u.r).append(" f").append(u.facing)
                    .append(" a").append(u.totalArmor()).append(" i").append(u.totalInfra()).append(" h").append(Math.round(u.heat))
                    .append(u.destroyed ? " dead" : "").append(u.activated ? " done" : "").append(u.moved ? " moved" : "")
                    .append(u.fired ? " fired" : "").append(" tag ").append(u.tag).append(" ammo ").append(java.util.Arrays.toString(u.ammo)).append('\n');
        }
        return s.toString();
    }

    /**
     * Saving a battle in progress: the screen saves on its own as the battle goes, a restart (a new Game on the same
     * stored data) resumes it in exactly the same position, mid-activation included, and the resumed battle plays
     * to a result that settles on the company's own units.
     */
    static void resume() throws Exception {
        for (int mode = 0; mode < 2; mode++) {
            FakePlatform fp = new FakePlatform();
            Game g = new Game(fp, 77);
            g.headless = true;
            if (mode == 0) {
                g.newCampaign(1, 14000, Scenario.ATTACKER);
                g.company.scenario = 4;
            } else g.quickBattle(1, 13000);
            Company c = g.company;
            for (int id : new int[]{21, 4, 9, 11, 18, 3, 0}) {
                Unit u = new Unit(Chassis.ALL[id]);
                c.roster.add(u);
                if (c.canDeploy(u)) c.deployed.add(u);
            }
            g.saveCompany();
            g.prepareMission();
            tick(g, 2);
            click(g, 2);   // begin battle
            tick(g, 2);
            BattleScreen bs = (BattleScreen) g.screen();
            Battle b = bs.battle();
            tick(g, 3);
            if (!g.hasBattle()) throw new RuntimeException("no save during deployment");
            Battle r0 = BattleSave.read(new Game(fp, 1), fp.load(BattleSave.KEY));
            if (r0 == null || !r0.deploying) throw new RuntimeException("deployment not resumed");
            // the computer plays both sides for a few rounds
            b.autoPlayer = true;
            click(g, 13);   // start battle
            for (int i = 0; i < 20 * 60 * 60 && b.round < 3; i++) tick(g, 1);
            for (int i = 0; i < 20 * 60 * 60 && !(BattleSave.quiet(b) && b.active == null); i++) tick(g, 1);
            tick(g, 1);
            String before = fingerprint(b);
            String saved = fp.load(BattleSave.KEY);
            Game g2 = new Game(fp, 2);   // as if the app were closed and opened again
            g2.headless = true;
            if (!g2.hasBattle()) throw new RuntimeException("menu would not offer to resume");
            g2.resumeBattle();
            Battle b2 = ((BattleScreen) g2.screen()).battle();
            String after = fingerprint(b2);
            if (!before.equals(after)) {
                System.out.println("BEFORE\n" + before + "AFTER\n" + after);
                throw new RuntimeException("resumed battle differs from the saved one");
            }
            // mid-activation: the player moves a unit, then leaves
            b2.autoPlayer = false;
            for (int i = 0; i < 20 * 60 * 60 && !(b2.playerTurn() && b2.active == null && !b2.busy() && BattleSave.quiet(b2)); i++) tick(g2, 1);
            if (b2.result == Battle.RESULT_NONE) {
                Unit mover = null;
                for (Unit u : b2.units) if (u.team == Battle.PLAYER && !u.destroyed && !u.activated && !u.shutdown && u.baseMp() > 0) { mover = u; break; }
                if (mover != null) {
                    b2.beginActivation(mover);
                    int[] prev = new int[b2.map.cols * b2.map.rows];
                    float[] reach = b2.reach(mover, prev, b2.mpAvailable(mover));
                    int to = -1;
                    for (int k = 0; k < reach.length; k++) if (reach[k] > 0 && reach[k] < Float.MAX_VALUE && b2.unitAt(k % b2.map.cols, k / b2.map.cols) == null) { to = k; break; }
                    if (to >= 0) b2.move(mover, b2.pathTo(prev, b2.map.idx(mover.q, mover.r), to));
                    for (int i = 0; i < 600 && b2.busy(); i++) tick(g2, 1);
                    tick(g2, 1);
                    String mid = fingerprint(b2);
                    Game g3 = new Game(fp, 3);
                    g3.headless = true;
                    g3.resumeBattle();
                    Battle b3 = ((BattleScreen) g3.screen()).battle();
                    if (!mid.equals(fingerprint(b3))) {
                        System.out.println("MID\n" + mid + "RESUMED\n" + fingerprint(b3));
                        throw new RuntimeException("mid-activation resume differs");
                    }
                    if (b3.active == null || !b3.active.moved) throw new RuntimeException("activation not resumed");
                    b3.endActivation(b3.active);
                    g2 = g3;
                    b2 = b3;
                }
            }
            // play the resumed battle out: it must settle on the company's units and clear the save
            b2.autoPlayer = true;
            Company c2 = g2.company;
            for (int i = 0; i < 20 * 60 * 200 && g2.screen() instanceof BattleScreen; i++) tick(g2, 1);
            if (!(g2.screen() instanceof DebriefScreen)) throw new RuntimeException("resumed battle did not finish");
            if (g2.hasBattle()) throw new RuntimeException("save not cleared after the battle");
            for (Unit u : b2.units) if (u.team == Battle.PLAYER && u.tag == null && !u.destroyed && !c2.roster.contains(u))
                throw new RuntimeException(u.name + " fought but is not on the roster");
            String[] res = {"", "WIN", "LOSS", "DRAW", "WITHDRAW"};
            System.out.println((mode == 0 ? "campaign" : "quick battle") + ": saved and resumed at round " + b.round
                    + " (save " + saved.length() + " chars), mid-activation resume OK, finished " + res[b2.result]);
        }
    }

    /**
     * Plays the game with random input: mostly taps on buttons that are showing, plus taps and drags anywhere
     * (the map, lists) and the back key, with time passing in between. Any exception stops the run and prints a
     * crash report with the steps that led to it. Every few thousand steps the "app" is restarted on the same
     * saved data, to exercise resuming.
     */
    static volatile Throwable threadCrash;
    static volatile Thread threadCrashed;

    static void monkey(int seeds, int steps, boolean threads) throws Exception {
        int crashes = 0;
        // errors on the computer's planning thread, as the phone's crash handler would see them
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                threadCrashed = t;
                threadCrash = e;
            }
        });
        for (int seed = 1; seed <= seeds; seed++) {
            Random r = new Random(seed);
            FakePlatform fp = new FakePlatform();
            Game g = new Game(fp, seed);
            g.headless = !threads;   // with threads, the computer plans on its own thread as on the phone
            int battles = 0, restarts = 0;
            Screen last = null;
            try {
                for (int i = 0; i < steps; i++) {
                    if (g.screen() != last && g.screen() instanceof BattleScreen) battles++;
                    last = g.screen();
                    int roll = r.nextInt(100);
                    List<Btn> bs = new ArrayList<Btn>();
                    for (Btn b : buttonsOf(g)) if (b.visible && b.enabled) bs.add(b);
                    if (roll < 62 && !bs.isEmpty()) {
                        Btn b = bs.get(r.nextInt(bs.size()));
                        tap(g, b.x + r.nextFloat() * b.w, b.y + r.nextFloat() * b.h);
                    } else if (roll < 82) {
                        tap(g, r.nextFloat() * W, r.nextFloat() * H);
                    } else if (roll < 90) {
                        float x = r.nextFloat() * W, y = r.nextFloat() * H;
                        g.touch(Game.TOUCH_DOWN, 0, x, y);
                        for (int k = 1; k <= 6; k++) g.touch(Game.TOUCH_MOVE, 0, x + k * (r.nextFloat() - 0.5f) * 80, y + k * (r.nextFloat() - 0.5f) * 80);
                        g.touch(Game.TOUCH_UP, 0, x, y);
                        tick(g, 1);
                    } else if (roll < 92) {
                        g.touch(Game.BACK, 0, 0, 0);
                        tick(g, 1);
                    } else if (roll < 93 && i > 0) {
                        g = new Game(fp, seed * 1000 + i);   // the app is closed and opened again
                        g.headless = !threads;
                        restarts++;
                    } else {
                        // let time pass; in battle, sometimes long enough for the computer to play
                        int n = g.screen() instanceof BattleScreen ? 20 + r.nextInt(400) : 1 + r.nextInt(5);
                        for (int k = 0; k < n; k++) {
                            tick(g, 1);
                            if (threads && k % 10 == 0) Thread.sleep(1);
                        }
                    }
                    if (threadCrash != null) throw threadCrash;
                    if (i % 500 == 0) {   // render every screen now and then, as the phone does every frame
                        BufferedImage img = new BufferedImage(W / 4, H / 4, BufferedImage.TYPE_INT_ARGB);
                        AwtGfx gfx = new AwtGfx(img);
                        g.render(gfx);
                        gfx.dispose();
                    }
                }
                System.out.println("seed " + seed + ": " + steps + " steps, " + battles + " battles entered, " + restarts + " restarts, no crash");
            } catch (Throwable e) {
                crashes++;
                System.out.println("seed " + seed + ": CRASH");
                System.out.println(CrashReport.build(g, e == threadCrash ? threadCrashed : Thread.currentThread(), e));
                threadCrash = null;
            }
        }
        System.out.println(crashes == 0 ? "monkey: no crashes" : "monkey: " + crashes + " crash(es)");
    }

    /** Every design side by side, in both team colors, to check they are all distinct. */
    static void gallery(File f) throws Exception {
        int cols = 8, cell = 230;
        int rows = (Chassis.ALL.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB);
        AwtGfx g = new AwtGfx(img);
        g.fillRect(0, 0, cols * cell, rows * cell, 0xFF5A6A4A);
        for (int i = 0; i < Chassis.ALL.length; i++) {
            Chassis ch = Chassis.ALL[i];
            float cx = (i % cols) * cell + cell / 2f, cy = (i / cols) * cell + cell / 2f - 10;
            g.fillRect((i % cols) * cell + 2, (i / cols) * cell + 2, cell - 4, cell - 4, 0xFF6E8A56);
            UnitArt.drawChassis(g, ch, ch.stockWeapons, cx, cy, -90, i % 2 == 0 ? UnitArt.PLAYER_COLOR : UnitArt.ENEMY_COLOR, 90, 0.4f, 1, 1);
            g.text(ch.name, cx, (i / cols) * cell + cell - 18, 17, 0xFFFFFFFF, Gfx.ALIGN_CENTER, true);
        }
        g.dispose();
        ImageIO.write(img, "png", f);
    }

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
