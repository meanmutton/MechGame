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
        public boolean canExit() { return true; }
        public void saveNow(String key, String value) { store.put(key, value); }
        String shared;
        public void share(String subject, String text) { shared = text; }
        public String buildInfo() { return "desktop harness"; }
        /** Online tests: the in-memory relay, or a real one (ws:// or wss://). */
        MemRelay relay;
        public Link openLink(String url) {
            if (url.startsWith("mem:")) return relay == null ? null : relay.connect();
            return new SocketLink(url);
        }
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
        else if (mode.equals("merc")) merc(Integer.parseInt(args[1]));
        else if (mode.equals("pvp")) pvpCampaign(Integer.parseInt(args[1]), false);
        else if (mode.equals("onlineself")) onlineSelf(args[1], args[2]);
        else if (mode.equals("onlinecampaign")) onlineCampaign(Integer.parseInt(args[1]), false, args.length > 2 ? args[2] : null);
        else if (mode.equals("online")) online(Integer.parseInt(args[1]), false, args.length > 2 ? args[2] : null);
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

    /** The Text helpers must give exactly what the Java methods they replace give (saves depend on split). */
    static void textHelpers() {
        String[] samples = {"", ",", ",,", "a", "a,", "a,,", ",a", ",a,", "a,b", "a,,b", "a,b,,", " a b ", "a|b||", "|", "x:y:z",
                "a\tb\t\t", "a\nb\n", "\n", "1,2,3,4,5,6,7,8,9,10,,", "Hex front"};
        char[] seps = {',', '|', ':', '\t', '\n', ' '};
        int checks = 0;
        for (String str : samples) {
            for (char sep : seps) {
                String rx = sep == '|' ? "\\|" : String.valueOf(sep);
                if (!java.util.Arrays.equals(str.split(rx), Text.split(str, sep)))
                    throw new RuntimeException("Text.split differs for \"" + str + "\" on '" + sep + "'");
                if (!java.util.Arrays.equals(str.split(rx, -1), Text.split(str, sep, true)))
                    throw new RuntimeException("Text.split(-1) differs for \"" + str + "\" on '" + sep + "'");
                checks += 2;
            }
            if (!java.util.Arrays.equals(str.trim().split("\\s+"), Text.words(str)))
                throw new RuntimeException("Text.words differs for \"" + str + "\"");
            if (!str.toLowerCase(java.util.Locale.ROOT).equals(Text.lower(str)) || !str.toUpperCase(java.util.Locale.ROOT).equals(Text.upper(str)))
                throw new RuntimeException("Text case differs for \"" + str + "\"");
            checks += 2;
        }
        for (Chassis ch : Chassis.ALL) {
            for (String n : new String[]{ch.name, Chassis.ROLES[ch.role], UnitType.NAMES[ch.type]})
                if (!n.toLowerCase(java.util.Locale.ROOT).equals(Text.lower(n))) throw new RuntimeException("not ASCII: " + n);
        }
        double[] nums = {0, 0.004, 0.005, 0.015, 0.125, 1.25, 1.35, 2.675, 9.995, 12.3456, 99.95, 1234.5, -0.4, -1.25, -0.004};
        for (double v : nums) {
            for (int dec = 1; dec <= 2; dec++) {
                String want = String.format(java.util.Locale.US, "%." + dec + "f", v);
                if (!want.equals(Text.fixed(v, dec))) throw new RuntimeException("Text.fixed(" + v + ", " + dec + ") = " + Text.fixed(v, dec) + ", want " + want);
                checks++;
            }
        }
        // the game passes floats: income multipliers, move costs, clock times
        java.util.Random fr = new java.util.Random(9);
        for (int i = 0; i < 4000; i++) {
            float f = i < 2000 ? fr.nextInt(4000) / 100f : fr.nextFloat() * 1000f;
            for (int dec = 1; dec <= 2; dec++) {
                String want = String.format(java.util.Locale.US, "%." + dec + "f", f);
                if (!want.equals(Text.fixed(f, dec))) throw new RuntimeException("Text.fixed(" + f + "f, " + dec + ") = " + Text.fixed(f, dec) + ", want " + want);
                checks++;
            }
        }
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        long[] times = {0L, 951782400000L, 1709208000123L, 1793923199999L, 4102444800000L, System.currentTimeMillis()};
        for (long t : times) {
            if (!(df.format(new java.util.Date(t)) + " UTC").equals(Text.utc(t))) throw new RuntimeException("Text.utc(" + t + ") = " + Text.utc(t));
            checks++;
        }
        if (!"   12.5s".equals(Text.pad(Text.fixed(12.5, 1), 7) + "s")) throw new RuntimeException("Text.pad");
        System.out.println("text helpers match Java's (" + checks + " checks)");
    }

    static void validate() {
        textHelpers();
        int bad = 0;
        for (Chassis ch : Chassis.ALL) if (new Unit(ch).meleeCount() > 1) {
            System.out.println("INVALID " + ch.name + ": more than one melee weapon");
            bad++;
        }
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
        // faction-made and unique items: names are unique, versions match their standard item, uniques are never sold here
        java.util.Set<String> names = new java.util.HashSet<String>();
        int[] made = new int[Faction.COUNT];
        int uniques = 0;
        Company shop = new Company();
        for (Wpn w : Wpn.ALL) {
            if (!names.add(w.name)) throw new RuntimeException("duplicate weapon name " + w.name);
            Wpn b = Wpn.ALL[w.base];
            if (w.cls != b.cls || w.family != b.family) throw new RuntimeException(w.name + " differs in class or family from " + b.name);
            if (w.maker >= 0 && !w.unique) made[w.maker]++;
            if (w.unique) uniques++;
            if ((w.unique || w.maker >= 0) && shop.canBuy(w)) throw new RuntimeException(w.name + " for sale in the campaign");
        }
        for (Equip e : Equip.ALL) {
            if (!names.add(e.name)) throw new RuntimeException("duplicate equipment name " + e.name);
            if (e.maker >= 0 && !e.unique) made[e.maker]++;
            if (e.unique) uniques++;
            if ((e.unique || e.maker >= 0) && shop.canBuy(e)) throw new RuntimeException(e.name + " for sale in the campaign");
        }
        int protos = 0;
        for (Chassis c : Chassis.ALL) if (c.unique) {
            protos++;
            for (Chassis s2 : Chassis.SHOP) if (s2 == c) throw new RuntimeException(c.name + " is unique but on sale");
        }
        StringBuilder mb = new StringBuilder();
        for (Faction f : Faction.ALL) mb.append(mb.length() > 0 ? ", " : "").append(f.shortName).append(' ').append(made[f.id]);
        System.out.println(Wpn.ALL.length + " weapons and " + Equip.ALL.length + " equipment; faction-made: " + mb + "; "
                + uniques + " unique items, " + protos + " unique designs");
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
    static boolean has(Game g, int id) throws Exception {
        for (Btn b : buttonsOf(g)) if (b.id == id) return true;
        return false;
    }

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
        // company colors: pick a preset and a custom accent, then see them at HQ
        click(g, 9);
        tick(g, 2);
        shot(g, "03b_company_colors");
        click(g, 400 + 1);   // Desert
        click(g, 300 + 0);   // accent: blue
        tick(g, 2);
        shot(g, "03c_company_colors_desert");
        click(g, 1);         // done
        if (c.scheme == null || c.scheme.main != Scheme.PRESETS[1].main || c.scheme.accent != (Scheme.PALETTE[0] | 0xFF000000))
            throw new RuntimeException("company colors not applied");
        if (!Company.parse(c.serialize()).scheme.equals(c.scheme)) throw new RuntimeException("company colors not saved");
        tick(g, 2);
        shot(g, "03d_hq_desert");
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
            click(g, 3);   // faction filter: Helion
            click(g, 3);   // Vardan
            tick(g, 2);
            for (Btn b : buttonsOf(g)) if (b.id >= 100 && (Chassis.ALL[b.id - 100].factions & Faction.bit(Faction.VARDAN)) == 0)
                throw new RuntimeException("faction filter shows " + Chassis.ALL[b.id - 100].name);
            click(g, 11);   // mechs tab: Vardan's own Warden is near the top
            Btn vc = null;
            for (Btn b : buttonsOf(g)) if (b.id >= 100 && b.visible && Chassis.ALL[b.id - 100].factions == Faction.bit(Faction.VARDAN)) { vc = b; break; }
            if (vc != null) tap(g, vc.x + vc.w / 2, vc.y + vc.h / 2);
            shot(g, "04d_market_faction");
            for (int k = 0; k < Faction.COUNT - 1; k++) click(g, 3);   // back to any faction
            click(g, 10);   // all
            // sorting and filters: most expensive first, front-line only, then only what you can afford
            click(g, 4);
            click(g, 4);   // Price: high
            click(g, 5);
            click(g, 5);   // Front-line
            tick(g, 2);
            int last = Integer.MAX_VALUE, n = 0;
            for (Btn b : buttonsOf(g)) {
                if (b.id < 100) continue;
                Chassis ch = Chassis.ALL[b.id - 100];
                int v = new Unit(ch).value();
                if (v > last) throw new RuntimeException("market not sorted by price");
                if (ch.role != 1) throw new RuntimeException("role filter shows " + ch.name);
                last = v;
                n++;
            }
            if (n == 0) throw new RuntimeException("role filter shows nothing");
            shot(g, "04e_market_sorted_filtered");
            click(g, 6);   // affordable only
            for (Btn b : buttonsOf(g)) if (b.id >= 100 && new Unit(Chassis.ALL[b.id - 100]).value() > c.cash)
                throw new RuntimeException("affordable filter shows " + Chassis.ALL[b.id - 100].name);
            click(g, 6);
            for (int k = 0; k < Chassis.ROLES.length - 1; k++) click(g, 5);   // back to any role
            for (int k = 0; k < MarketScreen.SORTS.length - 2; k++) click(g, 4);   // back to catalog order
            if (!find(g, 4).label.endsWith(MarketScreen.SORTS[0]) || !find(g, 5).label.equals("Any role")) throw new RuntimeException("filters did not reset");
            System.out.println("market sort and filters OK (" + n + " front-line designs)");
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
        // a faction design: its own makes of weapons and equipment, shown with their bonuses
        {
            Unit corona = new Unit(Chassis.ALL[37]);
            c.roster.add(corona);
            c.armoryW[Wpn.made(Faction.VARDAN, Wpn.BATTLE_CANNON)]++;   // a salvaged spare
            g.setScreen(new RefitScreen(g, corona));
            tick(g, 2);
            shot(g, "08b_refit_faction_design");
            click(g, 100);
            shot(g, "08c_refit_faction_weapons");
            click(g, 499);
            // a big pile of spares: the chooser scrolls at full size, and filters and sorts
            int[] saved = c.armoryW.clone();
            for (Wpn wp : Wpn.ALL) if (wp.maker >= 0 && !wp.unique) c.armoryW[wp.id]++;
            click(g, 100);
            List<Btn> rows = new ArrayList<Btn>();
            for (Btn b : buttonsOf(g)) if (b.id > 500 && b.id < 1499) rows.add(b);
            Btn lastRow = rows.get(rows.size() - 1);
            if (lastRow.visible) throw new RuntimeException("expected the chooser to need scrolling (" + rows.size() + " items)");
            if (rows.get(0).h < 60) throw new RuntimeException("chooser rows shrank to " + rows.get(0).h);
            float y0 = rows.get(0).y;
            g.touch(Game.TOUCH_DOWN, 0, rows.get(0).x + 20, rows.get(0).y + 20);
            for (int k = 1; k <= 10; k++) g.touch(Game.TOUCH_MOVE, 0, rows.get(0).x + 20, rows.get(0).y + 20 - k * 60);
            g.touch(Game.TOUCH_UP, 0, rows.get(0).x + 20, rows.get(0).y + 20 - 600);
            tick(g, 2);
            if (find(g, rows.get(0).id).y >= y0) throw new RuntimeException("chooser did not scroll");
            if (!(g.screen() instanceof RefitScreen)) throw new RuntimeException("scrolling picked an item");
            shot(g, "08d_refit_chooser_scrolled");
            click(g, 8);
            click(g, 8);
            click(g, 8);   // faction makes only
            click(g, 7);
            click(g, 7);   // sort: damage
            tick(g, 2);
            int lastDmg = Integer.MAX_VALUE;
            for (Btn b : buttonsOf(g)) {
                if (b.id <= 500 || b.id >= 1499) continue;
                Wpn wp = Wpn.ALL[b.id - 500];
                if (wp.maker < 0 && corona.weapon[0] != wp.id) throw new RuntimeException("faction filter shows " + wp.name);
                if (wp.dmg > lastDmg && corona.weapon[0] != wp.id) throw new RuntimeException("chooser not sorted by damage");
                lastDmg = wp.dmg;
            }
            shot(g, "08e_refit_chooser_filtered");
            click(g, 8);   // back to all makes
            for (int k = 0; k < RefitScreen.W_SORTS.length - 2; k++) click(g, 7);   // back to catalog order
            click(g, 499);
            System.arraycopy(saved, 0, c.armoryW, 0, saved.length);
            System.out.println("refit chooser scroll, filter and sort OK (" + rows.size() + " items)");
            c.roster.remove(corona);
        }
        // one melee weapon per unit: the workshop won't fit a second, and says why
        {
            Chassis br = null;
            for (Chassis ch : Chassis.ALL) if (ch.name.equals("Bramble")) br = ch;
            Unit bu = new Unit(br);
            c.roster.add(bu);
            int mm = bu.meleeMount(-1);
            if (mm < 0 || bu.meleeCount() != 1) throw new RuntimeException("the Bramble should carry exactly one melee weapon");
            int other = -1;
            for (int k = 0; k < bu.weapon.length && other < 0; k++)
                if (k != mm && Wpn.canMount(Wpn.ALL[Wpn.VIBRO_BLADE], bu.type, bu.ch.mounts[k].cls)) other = k;
            c.armoryW[Wpn.VIBRO_BLADE]++;
            g.setScreen(new RefitScreen(g, bu));
            tick(g, 2);
            click(g, 100 + other);
            Btn blade = find(g, 500 + Wpn.VIBRO_BLADE);
            if (blade.enabled) throw new RuntimeException("a second melee weapon is offered");
            g.wheel(W / 2f, H / 2f, 100000);   // down to the melee weapons at the end of the list
            tick(g, 2);
            shot(g, "08f_refit_one_melee");
            click(g, 499);
            // a unit refitted before the rule, with two: a warning, and only the best one counts toward its damage
            bu.weapon[other] = Wpn.VIBRO_BLADE;
            bu.computeMaxes();
            bu.repairFully();
            int expect = 0, best = 0;
            for (int k = 0; k < bu.weapon.length; k++) {
                Wpn w = bu.wpn(k);
                if (w == null) continue;
                if (w.isMelee()) best = Math.max(best, w.dmg);
                else expect += w.dmg;
            }
            if (Info.alphaDamage(bu) != expect + best) throw new RuntimeException("alpha damage counts more than one melee weapon");
            g.setScreen(new RefitScreen(g, bu));
            tick(g, 2);
            shot(g, "08g_refit_two_melee_warning");
            c.roster.remove(bu);
            c.armoryW[Wpn.VIBRO_BLADE]--;
            System.out.println("one melee weapon per unit OK (" + br.name + ", melee in mount " + mm + ", tried mount " + other + ")");
        }
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
        // tap an empty hex out of reach (woods if there are any on screen) for the terrain card
        int info = -1;
        for (int k = 0; k < reach.length; k++) {
            int ic = k % b.map.cols, ir = k / b.map.cols;
            float[] sp = hexToScreen(bs, ic, ir);
            if (reach[k] < Float.MAX_VALUE || b.unitAt(ic, ir) != null || sp[0] < 40 || sp[1] < 120 || sp[0] > W * 0.6f || sp[1] > H - 60) continue;
            if (info < 0 || b.map.terrain[k] == HexMap.HEAVY_WOODS || (b.map.terrain[k] == HexMap.LIGHT_WOODS && b.map.terrain[info] == HexMap.CLEAR)) info = k;
        }
        if (info >= 0) {
            float[] sp = hexToScreen(bs, info % b.map.cols, info / b.map.cols);
            tap(g, sp[0], sp[1]);
            shot(g, "11b_terrain_card");
            List<String> fx = b.map.effects(info % b.map.cols, info / b.map.cols);
            if (fx.isEmpty()) throw new RuntimeException("terrain card has no effects");
        }
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
            // the map scrolls up far enough that its bottom row clears the combat log, zoomed in or all the way out
            BattleScreen lbs = (BattleScreen) g.screen();
            java.lang.reflect.Field lt0 = BattleScreen.class.getDeclaredField("logTop");
            java.lang.reflect.Field fcs0 = BattleScreen.class.getDeclaredField("cs");
            lt0.setAccessible(true);
            fcs0.setAccessible(true);
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) {
                    g.wheel(W * 0.3f, H * 0.5f, 100000);   // zoom all the way out: the map sits above the log with no dragging
                    tick(g, 2);
                    shot(g, "14g_zoomed_out_no_drag");
                    float b0 = hexToScreen(lbs, 1, m.rows - 1)[1] + 0.866f * fcs0.getFloat(lbs);
                    if (b0 > lt0.getFloat(lbs) + 1) throw new RuntimeException("zoomed out, the bottom row starts under the log");
                }
                for (int k = 0; k < 3; k++) {
                    float x = W * 0.3f;
                    g.touch(Game.TOUCH_DOWN, 0, x, H * 0.8f);
                    for (int i = 1; i <= 10; i++) g.touch(Game.TOUCH_MOVE, 0, x, H * 0.8f - i * 60);
                    g.touch(Game.TOUCH_UP, 0, x, H * 0.8f - 600);
                    tick(g, 2);
                }
                java.lang.reflect.Field lt = BattleScreen.class.getDeclaredField("logTop");
                java.lang.reflect.Field fcs = BattleScreen.class.getDeclaredField("cs");
                lt.setAccessible(true);
                fcs.setAccessible(true);
                float bottom = hexToScreen(lbs, 1, m.rows - 1)[1] + 0.866f * fcs.getFloat(lbs);
                if (bottom > lt.getFloat(lbs) + 1) throw new RuntimeException("the bottom row stays under the log (" + bottom + " vs " + lt.getFloat(lbs) + ")");
                shot(g, pass == 0 ? "14e_map_clear_of_log" : "14f_map_clear_of_log_zoomed_out");
                if (pass == 0) {
                    // the log grows while the view is at the bottom: the map moves up with it
                    float before = lt.getFloat(lbs);
                    for (int k = 0; k < 4; k++)
                        bb.log("A long entry that wraps onto a second line in the log box so the log is taller than before, number " + k
                                + ", with plenty of words to make it wrap at this width");
                    shot(g, "14h_map_follows_log");   // drawing a frame measures the taller log
                    tick(g, 2);                         // the next update moves the map up with it
                    shot(g, "14h_map_follows_log");
                    float after = lt.getFloat(lbs), bot = hexToScreen(lbs, 1, m.rows - 1)[1] + 0.866f * fcs.getFloat(lbs);
                    if (after >= before) throw new RuntimeException("the test log did not grow");
                    if (bot > after + 1) throw new RuntimeException("the map did not follow the growing log (" + bot + " vs " + after + ")");
                }
            }
            System.out.println("map scrolls clear of the combat log OK");
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
            // the roster: filter by type, sort, and scroll a long list at a readable size
            {
                List<Btn> rows = new ArrayList<Btn>();
                for (Btn rb : buttonsOf(g)) if (rb.id >= 100 && rb.id < 200) rows.add(rb);
                if (rows.size() != c.roster.size()) throw new RuntimeException("HQ shows " + rows.size() + " of " + c.roster.size());
                if (rows.get(0).h < H / 100f * 6.5f) throw new RuntimeException("HQ rows shrank to " + rows.get(0).h);
                boolean hidden = false;
                for (Btn rb : rows) if (!rb.visible) hidden = true;
                if (hidden) {
                    Btn r0 = rows.get(0);
                    float y0 = r0.y;
                    g.touch(Game.TOUCH_DOWN, 0, r0.x + 30, r0.y + r0.h / 2);
                    for (int k = 1; k <= 10; k++) g.touch(Game.TOUCH_MOVE, 0, r0.x + 30, r0.y + r0.h / 2 - k * 50);
                    g.touch(Game.TOUCH_UP, 0, r0.x + 30, r0.y + r0.h / 2 - 500);
                    tick(g, 2);
                    if (find(g, r0.id).y >= y0) throw new RuntimeException("HQ roster did not scroll");
                    shot(g, "39b_hq_roster_scrolled");
                }
                click(g, 21);   // mechs
                click(g, 11);
                click(g, 11);
                click(g, 11);   // sort: value
                tick(g, 2);
                int lastV = Integer.MAX_VALUE, mechs = 0;
                List<Btn> mrows = new ArrayList<Btn>();
                for (Btn rb : buttonsOf(g)) if (rb.id >= 100 && rb.id < 200) mrows.add(rb);
                java.util.Collections.sort(mrows, new java.util.Comparator<Btn>() {
                    public int compare(Btn p1, Btn p2) { return p1.x != p2.x ? Float.compare(p1.x, p2.x) : Float.compare(p1.y, p2.y); }
                });
                for (Btn rb : mrows) {
                    Unit un = c.roster.get(rb.id - 100);
                    if (!UnitType.isMech(un.type)) throw new RuntimeException("mech tab shows " + un.name);
                    if (un.value() > lastV) throw new RuntimeException("HQ not sorted by value");
                    lastV = un.value();
                    mechs++;
                }
                int expect = 0;
                for (Unit un : c.roster) if (UnitType.isMech(un.type)) expect++;
                if (mechs != expect) throw new RuntimeException("mech tab shows " + mechs + " of " + expect);
                // the Monolith carries the most weapons: its loadout stays readable in the detail panel
                for (Btn rb : mrows) if (c.roster.get(rb.id - 100).ch.id == 29 && rb.visible) { tap(g, rb.x + rb.w / 2, rb.y + rb.h / 2); break; }
                tick(g, 2);
                shot(g, "39c_hq_mechs_by_value");
                click(g, 20);   // all
                for (int k = 0; k < HqScreen.SORTS.length - 3; k++) click(g, 11);   // back to roster order
                System.out.println("HQ roster filter, sort and scroll OK (" + mechs + " mechs of " + c.roster.size() + ")");
            }
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
            click(g, 3);
            shot(g, "49_help_factions");
            click(g, 3);
            shot(g, "49b_help_mercenary");
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
        // mercenary play: HQ without a job, the contract board, travel, a job, its market, briefing, battle and debrief
        {
            FakePlatform mp = new FakePlatform();
            Game gm = new Game(mp, 31);
            tick(gm, 2);
            gm.newMercenary(1, 14000);
            Company mc = gm.company;
            System.arraycopy(new int[]{35, -55, 12, -20, 64, 0, -8, 22}, 0, mc.rep, 0, Faction.COUNT);
            mc.contracts.clear();
            Merc.fillBoard(mc, gm.rng);
            tick(gm, 2);
            shot(gm, "52_merc_hq_no_job");
            click(gm, 10);   // Contracts
            tick(gm, 2);
            shot(gm, "53_merc_contracts");
            click(gm, 2);   // Travel
            tick(gm, 2);
            shot(gm, "54_merc_travel");
            click(gm, 1);   // stay
            tick(gm, 1);
            int pick = 0;
            for (int i = 0; i < mc.contracts.size(); i++) if (mc.contracts.get(i).threat == 2) pick = i;
            click(gm, 100 + pick);   // take the job
            tick(gm, 2);
            Scenario msc = mc.next();
            for (Chassis ch : Chassis.ALL) {
                if (mc.roster.size() >= 6 || !mc.sells(ch) || !msc.allows(ch)) continue;
                Unit un = new Unit(ch);
                if (un.value() <= 2600 && mc.limits().fits(mc.roster, un)) mc.roster.add(un);
            }
            mc.deployed.clear();
            for (Unit un : mc.roster) if (mc.canDeploy(un)) mc.deployed.add(un);
            gm.toHq();
            tick(gm, 2);
            shot(gm, "55_merc_hq_job");
            click(gm, 2);   // Market
            tick(gm, 2);
            shot(gm, "56_merc_market");
            gm.prepareMission();
            tick(gm, 2);
            shot(gm, "57_merc_briefing");
            click(gm, 2);
            tick(gm, 20);
            Battle mb = ((BattleScreen) gm.screen()).battle();
            mb.finishDeployment();
            tick(gm, 30);
            shot(gm, "58_merc_battle");
            mb.autoPlayer = true;
            gm.headless = true;   // plan on this thread, so the battle plays out in the loop
            int frames = 0;
            while (mb.result == Battle.RESULT_NONE && ++frames < 20 * 60 * 120) mb.update(1 / 20f);
            if (mb.result == Battle.RESULT_NONE) throw new RuntimeException("mercenary battle did not finish");
            gm.finishBattle(mb);
            gm.headless = false;
            tick(gm, 2);
            shot(gm, "59_merc_debrief");
            gm.toMenu();
            tick(gm, 2);
            shot(gm, "60_menu_merc");
        }
        // the unit with the most weapons, in battle: full-size weapon rows, and the list scrolls if it must
        {
            HexMap m = new HexMap(20, 13, 0, new Weather(Weather.CLEAR));
            m.generate(new Random(5));
            List<Unit> mine = new ArrayList<Unit>(), theirs = new ArrayList<Unit>();
            Unit mono = new Unit(Chassis.ALL[29]);
            int smoke = Equip.SMOKE, turbo = Equip.TURBO;
            mono.equip[0] = smoke;
            mono.equip[1] = turbo;
            mono.computeMaxes();
            mono.repairFully();
            mine.add(mono);
            theirs.add(new Unit(Chassis.ALL[21]));
            Battle bb = new Battle(g, mine, theirs, m, new Random(3), 1);
            bb.finishDeployment();
            BattleScreen mbs = new BattleScreen(g, bb);
            g.setScreen(mbs);
            for (int i = 0; i < 4000 && !(bb.playerTurn() && !bb.busy()); i++) {
                tick(g, 1);
                if (i % 20 == 0) Thread.sleep(1);
            }
            click(g, 12);   // next unit: the Monolith
            tick(g, 5);
            List<Btn> wr = new ArrayList<Btn>();
            for (Btn wb : buttonsOf(g)) if (wb.id >= 100 && wb.id < 300) wr.add(wb);
            if (wr.size() != mono.weapon.length) throw new RuntimeException("battle panel shows " + wr.size() + " weapons");
            if (wr.get(0).h < H / 100f * 4.8f) throw new RuntimeException("battle weapon rows shrank to " + wr.get(0).h);
            shot(g, "11c_battle_many_weapons");
            Btn last = find(g, wr.get(wr.size() - 1).id);
            boolean scrolls = !last.visible;
            if (scrolls) {
                Btn w0 = find(g, wr.get(0).id);
                float y0 = w0.y;
                g.touch(Game.TOUCH_DOWN, 0, w0.x + 30, w0.y + w0.h / 2);
                for (int k = 1; k <= 8; k++) g.touch(Game.TOUCH_MOVE, 0, w0.x + 30, w0.y + w0.h / 2 - k * 40);
                g.touch(Game.TOUCH_UP, 0, w0.x + 30, w0.y + w0.h / 2 - 320);
                tick(g, 2);
                if (find(g, w0.id).y >= y0 || !find(g, last.id).visible) throw new RuntimeException("battle weapon list did not scroll to the end");
                shot(g, "11d_battle_weapons_scrolled");
            }
            System.out.println("battle weapon list OK (" + wr.size() + " weapons, row " + Math.round(wr.get(0).h) + " px"
                    + (scrolls ? ", scrolls to the last" : ", all visible") + ")");
        }
        // hotseat: two players on one device, building forces, deploying in secret, playing the battle
        hotseat(new FakePlatform());
        // a two-player campaign: both HQs, the shared briefing, a battle, both settled
        pvpCampaign(3, true);
        online(1, true, null);
        onlineCampaign(6, true, null);
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

    static void hotseat(FakePlatform hp) throws Exception {
        Game g = new Game(hp, 61);
        tick(g, 2);
        g.newHotseat(9000);
        tick(g, 2);
        if (!(g.screen() instanceof HandoffScreen)) throw new RuntimeException("hotseat should start on a hand-over screen");
        shot(g, "61_hotseat_handoff_p1");
        click(g, 1);   // I'm Player 1
        tick(g, 2);
        Company p1 = g.seats[0], p2 = g.seats[1];
        if (g.company != p1 || !(g.screen() instanceof HqScreen)) throw new RuntimeException("Player 1 should build first");
        for (int id : new int[]{21, 21, 4, 11, 0}) { Unit u = new Unit(Chassis.ALL[id]); p1.roster.add(u); p1.deployed.add(u); }
        g.setScreen(new HqScreen(g));
        tick(g, 2);
        click(g, 4);   // Done
        tick(g, 2);
        if (!(g.screen() instanceof HandoffScreen) || g.company != p2) throw new RuntimeException("Player 2 should build next");
        click(g, 1);
        tick(g, 2);
        for (int id : new int[]{25, 9, 12, 14, 2, 1}) { Unit u = new Unit(Chassis.ALL[id]); p2.roster.add(u); p2.deployed.add(u); }
        g.setScreen(new HqScreen(g));
        tick(g, 2);
        shot(g, "62_hotseat_hq_p2");
        click(g, 4);   // Fight
        tick(g, 2);
        if (!(g.screen() instanceof BriefingScreen) || g.company != p1) throw new RuntimeException("the briefing should follow Player 2's Fight");
        click(g, 1);   // Back: Player 2 still holds the device
        tick(g, 2);
        if (g.company != p2 || !(g.screen() instanceof HqScreen)) throw new RuntimeException("Back from the briefing should return to Player 2");
        click(g, 4);
        tick(g, 2);
        shot(g, "63_hotseat_briefing");
        click(g, 2);   // Begin battle
        tick(g, 5);
        BattleScreen bs = (BattleScreen) g.screen();
        Battle b = bs.battle();
        if (!b.hotseat || !b.deploying || b.deployTeam != Battle.PLAYER) throw new RuntimeException("Player 1 should deploy first");
        boolean handoff = false;
        for (Btn bt : buttonsOf(g)) if (bt.id == 207) handoff = true;
        if (!handoff) throw new RuntimeException("the map should be hidden until Player 1 is ready");
        shot(g, "64_hotseat_handoff_deploy");
        click(g, 207);
        tick(g, 5);
        // nothing of Player 2's shows or blocks while Player 1 deploys: an infantry ambush onto a Player 2 unit is allowed
        Unit p1inf = null, p2unit = null;
        for (Unit u : b.units) {
            if (u.team == Battle.ENEMY && !b.hidden(u)) throw new RuntimeException("Player 2's units show during Player 1's deployment");
            if (u.team == Battle.PLAYER && u.type == UnitType.INFANTRY) p1inf = u;
            if (u.team == Battle.ENEMY && p2unit == null && u.type != UnitType.INFANTRY) p2unit = u;
        }
        if (!b.canPlace(p1inf, p2unit.q, p2unit.r)) throw new RuntimeException("a hidden unit gives itself away by blocking placement");
        b.place(p1inf, p2unit.q, p2unit.r);
        shot(g, "65_hotseat_deploy_p1");
        click(g, 13);   // Done: pass to Player 2
        tick(g, 5);
        if (b.deployTeam != Battle.ENEMY || !b.deploying) throw new RuntimeException("Player 2 should deploy next");
        for (Unit u : b.units) if (u.team == Battle.PLAYER && !b.hidden(u)) throw new RuntimeException("Player 1's units show during Player 2's deployment");
        // the battle saves here and resumes on Player 2's deployment, behind the hand-over screen
        g.saveBattle(b);
        Game g2 = new Game(hp, 62);
        tick(g2, 2);
        g2.resumeBattle();
        tick(g2, 5);
        Battle rb = ((BattleScreen) g2.screen()).battle();
        if (!rb.hotseat || rb.deployTeam != Battle.ENEMY || !rb.deploying || !g2.company.hotseat || g2.hotseatEnemy == null)
            throw new RuntimeException("a hotseat battle did not resume as one");
        boolean hidden = false;
        for (Btn bt : buttonsOf(g2)) if (bt.id == 207) hidden = true;
        if (!hidden) throw new RuntimeException("a resumed deployment should start behind the hand-over screen");
        click(g, 207);
        tick(g, 5);
        shot(g, "66_hotseat_deploy_p2");
        click(g, 13);   // Start battle
        tick(g, 5);
        if (b.deploying) throw new RuntimeException("the battle should start after Player 2 deploys");
        for (Unit u : b.units) for (Unit o : b.units)
            if (u != o && !u.destroyed && !o.destroyed && u.q == o.q && u.r == o.r) throw new RuntimeException("two units share a hex after deployment");
        if (p1inf.q == p2unit.q && p1inf.r == p2unit.r) throw new RuntimeException("the clash was not resolved");
        // each player controls their own side in turn
        for (int turn = 0; turn < 4; turn++) {
            for (int i = 0; i < 400 && b.busy(); i++) tick(g, 1);
            int side = b.side;
            click(g, 12);   // Next unit
            tick(g, 2);
            if (b.active == null || b.active.team != side) throw new RuntimeException("player " + (side + 1) + " could not pick a unit");
            if (turn == 1) shot(g, "67_hotseat_battle");
            click(g, 1);    // End turn
            tick(g, 2);
        }
        // the rest of the battle plays itself
        b.autoPlayer = true;
        g.headless = true;
        int frames = 0;
        while (b.result == Battle.RESULT_NONE && ++frames < 20 * 60 * 120) b.update(1 / 20f);
        if (b.result == Battle.RESULT_NONE) throw new RuntimeException("the hotseat battle did not finish");
        g.finishHotseat(b);
        g.headless = false;
        tick(g, 2);
        shot(g, "68_hotseat_end");
        click(g, 2);   // Rematch
        tick(g, 2);
        if (!(g.screen() instanceof BriefingScreen) || g.seats[0].deployed.size() != 5 || g.seats[1].deployed.size() != 6)
            throw new RuntimeException("the rematch should bring both forces back");
        for (Unit u : g.seats[1].deployed) if (u.isDamaged()) throw new RuntimeException("rematch forces should be repaired");
        System.out.println("hotseat OK (result " + b.result + " after " + b.round + " rounds, rematch ready)");
    }

    /** Online tests over a real relay: give the network real time between frames. */
    static int pumpSleep;

    /**
     * Cross-play tests: one game that hosts ("host") or joins ("join:CODE") at url and plays by itself (Online.autoplay),
     * printing the code and, at the end, the result and the battle's fingerprint to compare with the other device.
     */
    static void onlineSelf(String url, String how) throws Exception {
        FakePlatform p = new FakePlatform();
        Online.relayUrl = url;
        Online.autoplay = Online.autoplayCampaign = true;
        Online.buildId = System.getProperty("hexfront.build", Online.buildId);   // to pair with a browser page's build
        Game g = new Game(p, 123);
        g.tick(0, W, H);
        if (how.startsWith("join:")) g.joinOnline(how.substring(5));
        else if (how.equals("campaign")) g.newOnlineCampaign(10000, Scenario.ATTACKER);
        else g.hostOnline(9000);
        boolean told = false;
        long until = System.currentTimeMillis() + 15 * 60 * 1000L;
        while (System.currentTimeMillis() < until) {
            g.tick(1 / 20f, W, H);
            Thread.sleep(3);
            Online o = g.online;
            if (o == null) throw new RuntimeException("left the game");
            if (!told && o.code != null && o.phase == Online.HOSTING) {
                System.out.println("CODE " + o.code);
                told = true;
            }
            if (o.phase == Online.FAILED) throw new RuntimeException("failed: " + o.problem);
            if (o.campaign && o.turns >= Online.autoplayTurns && g.screen() instanceof PvpDebriefScreen) {
                for (int i = 0; i < 60; i++) {   // the other device gets the last results meanwhile
                    g.tick(1 / 20f, W, H);
                    Thread.sleep(5);
                }
                System.out.println("DONE " + o.testState());
                return;
            }
            if (o.battle != null && o.battle.result != Battle.RESULT_NONE && !o.battle.busy() && g.screen() instanceof HotseatEndScreen) {
                for (int i = 0; i < 100; i++) {
                    g.tick(1 / 20f, W, H);
                    Thread.sleep(5);
                }
                System.out.println("DONE " + o.testState() + " rounds " + o.battle.round);
                return;
            }
        }
        throw new RuntimeException("timed out: " + g.online.phase + " " + g.online.testState());
    }

    static void pump(Game a, Game b, int frames, float dt) {
        for (int i = 0; i < frames; i++) {
            a.tick(dt, W, H);
            b.tick(dt, W, H);
            if (pumpSleep > 0) {
                try {
                    Thread.sleep(pumpSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    static int dmg(Battle b, int team) {
        float d = 0;
        for (Unit u : b.units) if (u.team == team) d += u.damageDealt;
        return Math.round(d);
    }

    static Battle battleOf(Game g) {
        return g.screen() instanceof BattleScreen ? ((BattleScreen) g.screen()).battle() : null;
    }

    static void cutLink(Game g) throws Exception {
        java.lang.reflect.Field f = Online.class.getDeclaredField("link");
        f.setAccessible(true);
        Link l = (Link) f.get(g.online);
        if (l != null) l.close();
    }

    /**
     * Two games play an online quick battle through a relay: the in-memory one, or a real one at url (ws:// or wss://).
     * Hosting and joining, building at the same time, deployment, the whole battle on both devices in step, a dropped
     * connection mid-battle, a forced disagreement that the host's copy fixes, leaving, and the failures a player sees.
     */
    static void online(int battles, boolean shots, String url) throws Exception {
        MemRelay relay = new MemRelay();
        FakePlatform hp = new FakePlatform(), gp = new FakePlatform();
        hp.relay = gp.relay = relay;
        String saved = Online.relayUrl;
        Online.relayUrl = url != null ? url : "mem:relay";
        float dt = 1 / 20f;
        int frames = url != null ? 1000 : 60;
        pumpSleep = url != null ? 20 : 0;   // a real relay: about real-time pace, as people play (it limits messages a second)
        try {
            int totalResync = 0;
            for (int n = 0; n < battles; n++) {
                Game gh = new Game(hp, 71 + n * 2), gg = new Game(gp, 72 + n * 2);
                pump(gh, gg, 2, dt);
                gh.setScreen(new NewGameScreen(gh));
                pump(gh, gg, 2, dt);
                if (!has(gh, 7)) throw new RuntimeException("no Online button on the New Game screen");
                if (shots && n == 0) shot(gh, "80_online_newgame");
                click(gh, 7);
                pump(gh, gg, 2, dt);
                if (!(gh.screen() instanceof OnlineScreen)) throw new RuntimeException("Online should open the online screen");
                if (shots && n == 0) shot(gh, "81_online_start");
                click(gh, 2);   // Host
                for (int i = 0; i < frames && gh.online.phase != Online.HOSTING; i++) pump(gh, gg, 1, dt);
                if (gh.online.phase != Online.HOSTING || gh.online.code == null) throw new RuntimeException("hosting failed: " + gh.online.problem);
                if (shots && n == 0) shot(gh, "82_online_code");
                String code = gh.online.code;
                if (n == 0) {
                    // a wrong code and a full room say so
                    gg.joinOnline(code.equals("AAAAAA") ? "BBBBBB" : "AAAAAA");
                    for (int i = 0; i < frames && gg.online.phase != Online.FAILED; i++) pump(gh, gg, 1, dt);
                    if (gg.online.phase != Online.FAILED || !gg.online.problem.contains("no game")) throw new RuntimeException("a wrong code: " + gg.online.problem);
                    if (shots) shot(gg, "83_online_wrong_code");
                    gg.leaveOnline();
                }
                if (n == 0) {
                    // different builds don't play each other, and both players are told why
                    FakePlatform op = new FakePlatform(), np = new FakePlatform();
                    op.relay = np.relay = relay;
                    String keep = Online.buildId;
                    Online.buildId = "aaa1111";
                    Game old = new Game(op, 97);
                    old.hostOnline(9000);
                    for (int i = 0; i < frames && old.online.phase != Online.HOSTING; i++) pump(old, old, 1, dt);
                    Online.buildId = "bbb2222";
                    Game nw = new Game(np, 96);
                    nw.joinOnline(old.online.code);
                    for (int i = 0; i < frames && (old.online.phase != Online.FAILED || nw.online.phase != Online.FAILED); i++) pump(old, nw, 1, dt);
                    Online.buildId = keep;
                    if (nw.online.phase != Online.FAILED || !nw.online.problem.contains("you: bbb2222, your friend: aaa1111"))
                        throw new RuntimeException("the joining player should hear the versions differ: " + nw.online.problem);
                    if (old.online.phase != Online.FAILED || !old.online.problem.contains("you: aaa1111, your friend: bbb2222"))
                        throw new RuntimeException("the host should hear the versions differ: " + old.online.problem);
                    if (shots) shot(nw, "83b_online_version");
                }
                gg.joinOnline(Text.lower(code));
                for (int i = 0; i < frames && !(gh.screen() instanceof HqScreen && gg.screen() instanceof HqScreen); i++) pump(gh, gg, 1, dt);
                if (!(gh.screen() instanceof HqScreen) || !(gg.screen() instanceof HqScreen)) throw new RuntimeException("both players should be building");
                if (gg.company.cash != gh.company.cash || gg.company.seatNo != 1 || gh.company.seatNo != 0) throw new RuntimeException("budgets or seats differ");
                if (n == 0) {
                    FakePlatform xp = new FakePlatform();
                    xp.relay = relay;
                    Game gx = new Game(xp, 99);
                    gx.joinOnline(code);
                    for (int i = 0; i < frames && gx.online.phase != Online.FAILED; i++) {
                        gx.tick(dt, W, H);
                        pump(gh, gg, 1, dt);
                    }
                    if (gx.online.phase != Online.FAILED || !gx.online.problem.contains("already joined")) throw new RuntimeException("a full game: " + gx.online.problem);
                }
                Random r = new Random(500 + n);
                boolean mirror = Boolean.getBoolean("online.mirror");
                for (Game g : new Game[]{gh, gg}) {
                    Company c = g.company;
                    if (mirror) r = new Random(500 + n);
                    for (Unit u : force(r, 9000, n % 3)) {
                        c.roster.add(u);
                        if (c.deployed.size() < 5) c.deployed.add(u);
                    }
                    g.setScreen(new HqScreen(g));
                }
                pump(gh, gg, 2, dt);
                if (shots && n == 0) shot(gg, "84_online_hq_p2");
                click(gg, 4);   // Ready (the guest first: the host's battle starts on the host's Ready)
                pump(gh, gg, 3, dt);
                if (!(gg.screen() instanceof OnlineScreen) || gg.online.phase != Online.READY) throw new RuntimeException("the guest should wait");
                if (shots && n == 0) shot(gg, "85_online_waiting");
                click(gh, 4);
                for (int i = 0; i < frames && (battleOf(gh) == null || battleOf(gg) == null); i++) pump(gh, gg, 1, dt);
                Battle bh = battleOf(gh), bg = battleOf(gg);
                if (bh == null || bg == null) throw new RuntimeException("the battle did not start on both devices");
                if (bh.stateHash() != bg.stateHash()) throw new RuntimeException("the battles start out different");
                if (bh.localTeam != 0 || bg.localTeam != 1) throw new RuntimeException("sides");
                for (Unit u : bg.units) if (u.team == 0 && !bg.hidden(u)) throw new RuntimeException("the guest sees the host's deployment");
                if (shots && n == 0) shot(gg, "86_online_guest_waits_deploy");
                if (has(gg, 13) && find(gg, 13).visible) throw new RuntimeException("the guest may not end the host's deployment");
                click(gh, 13);   // Done
                for (int i = 0; i < frames && bg.deployTeam != 1; i++) pump(gh, gg, 1, dt);
                if (bg.deployTeam != 1 || bh.deployTeam != 1) throw new RuntimeException("Player 2 should deploy next on both");
                if (shots && n == 0) shot(gg, "87_online_guest_deploys");
                click(gg, 13);   // Start battle
                for (int i = 0; i < frames && (bh.deploying || bg.deploying); i++) pump(gh, gg, 1, dt);
                if (bh.deploying || bg.deploying) throw new RuntimeException("the battle did not start");
                bh.autoPlayer = bg.autoPlayer = true;
                gh.headless = gg.headless = true;
                relay.lossy = url == null && n % 2 == 1;   // every other battle: messages lost and overtaken, as AWS may
                if (Boolean.getBoolean("online.offline")) {
                    // the same battle on one device, the computer playing both sides: is any lean the game's or online's?
                    FakePlatform op = new FakePlatform();
                    Game go = new Game(op, 5);
                    go.headless = true;
                    Battle ob = BattleSave.read(go, BattleSave.write(gh.company, bh));
                    ob.autoPlayer = true;
                    int k = 0;
                    while (ob.result == Battle.RESULT_NONE && ++k < 20 * 60 * 200) ob.update(1 / 20f);
                    System.out.println("  offline copy: result " + ob.result + "; initiative " + bh.playerInitiative + ", first " + bh.firstInitiative + ", time edge " + bh.timeEdge);
                }
                boolean clean = Boolean.getBoolean("online.clean");
                int f = 0, cutAt = clean ? -1 : 400 + n * 37, skewAt = clean ? -1 : 900 + n * 53;
                boolean shotTaken = false;
                while ((bh.result == Battle.RESULT_NONE || bg.result == Battle.RESULT_NONE || bh.busy() || bg.busy()) && ++f < 20 * 60 * 200) {
                    if (f == cutAt) cutLink(n % 2 == 0 ? gg : gh);   // a dropped connection: it reconnects and carries on
                    if (f == skewAt && bg.result == Battle.RESULT_NONE) {
                        // a disagreement: the guest's copy goes wrong; the host's copy puts it right
                        for (Unit u : bg.units) if (!u.destroyed) { u.heat += 3; break; }
                    }
                    pump(gh, gg, 1, dt);
                    if (url != null && f % 2000 == 0) System.out.println("  frame " + f + ": host round " + bh.round + " side " + bh.side + ", guest round " + bg.round
                            + " side " + bg.side + ", wait '" + gh.online.waitingText() + "' / '" + gg.online.waitingText() + "'");
                    if (gg.screen() instanceof BattleScreen) bg = battleOf(gg);   // a re-sync replaces the guest's battle
                    if (bg != null && bg.localTeam == 1) bg.autoPlayer = true;
                    if (shots && n == 0 && !shotTaken && f > 200 && bg.side == 0 && bg.active != null) {
                        shot(gg, "88_online_their_turn");
                        shotTaken = true;
                    }
                }
                if (bh.result == Battle.RESULT_NONE || bg.result == Battle.RESULT_NONE) {
                    throw new RuntimeException("the online battle did not finish (frame " + f + "): host round " + bh.round + " side " + bh.side
                            + " result " + bh.result + " wait '" + gh.online.waitingText() + "' phase " + gh.online.phase + "; guest round " + bg.round
                            + " side " + bg.side + " result " + bg.result + " wait '" + gg.online.waitingText() + "' phase " + gg.online.phase
                            + "; hashes " + (bh.stateHash() == bg.stateHash() ? "equal" : "differ") + "; guest screen " + gg.screen().getClass().getSimpleName()
                            + "; odd messages " + gh.online.oddMessages + "/" + gg.online.oddMessages + " " + gh.online.lastOdd + " " + gg.online.lastOdd);
                }
                for (int i = 0; i < 200 && (bh.busy() || bg.busy()); i++) pump(gh, gg, 1, dt);
                if (bh.result != bg.result) throw new RuntimeException("the two devices disagree on the result: " + bh.result + " vs " + bg.result);
                if (bh.stateHash() != bg.stateHash()) throw new RuntimeException("the battles ended out of step");
                java.lang.reflect.Field rs = Online.class.getDeclaredField("resyncs");
                rs.setAccessible(true);
                int resyncs = rs.getInt(gg.online);
                if (resyncs < 1 && !clean) throw new RuntimeException("the forced disagreement was not fixed by a copy from the host");
                totalResync += resyncs;
                pump(gh, gg, 80, dt);
                if (!(gh.screen() instanceof HotseatEndScreen) || !(gg.screen() instanceof HotseatEndScreen)) throw new RuntimeException("both should show the result");
                if (has(gg, 2)) throw new RuntimeException("no rematch online");
                if (shots && n == 0) shot(gg, "89_online_end");
                if (relay.lossy) System.out.println("  lossy relay: " + relay.dropped + " messages lost, " + relay.swapped + " overtaken");
                relay.lossy = false;
                if (gh.online.oddMessages + gg.online.oddMessages > 0) System.out.println("  relay messages not understood: "
                        + gh.online.oddMessages + "/" + gg.online.oddMessages + ", last: " + gh.online.lastOdd + " / " + gg.online.lastOdd);
                click(gh, 1);   // the host goes to the menu: the guest hears it left
                for (int i = 0; i < frames && !gg.online.partnerLeft; i++) pump(gh, gg, 1, dt);
                if (gh.online != null || !gg.online.partnerLeft) throw new RuntimeException("leaving should tell the other player");
                click(gg, 1);
                pump(gh, gg, 2, dt);
                if (gg.online != null || !(gg.screen() instanceof MenuScreen)) throw new RuntimeException("the guest should be back at the menu");
                System.out.println("online battle " + (n + 1) + ": result " + bh.result + " in " + bh.round + " rounds, " + resyncs + " re-sync(s), in step; standing "
                        + bh.alive(0) + " vs " + bh.alive(1) + ", damage " + dmg(bh, 0) + " vs " + dmg(bh, 1) + ", value " + Math.round(bh.startValue[0]) + " vs " + Math.round(bh.startValue[1]));
            }
            // a server that can't be reached
            relay.unreachable = true;
            Game gz = new Game(hp, 98);
            gz.hostOnline(9000);
            pump(gz, gz, 3, dt);
            if (url == null && (gz.online.phase != Online.FAILED || !gz.online.problem.contains("reach"))) throw new RuntimeException("an unreachable server: " + gz.online.problem);
            System.out.println("online OK (" + battles + " battles over " + (url == null ? "the in-memory relay, " + relay.messages + " messages" : url)
                    + ", " + totalResync + " re-syncs)");
        } finally {
            Online.relayUrl = saved;
            pumpSleep = 0;
        }
    }

    static void waitFor(Game a, Game b, float dt, int frames, java.util.concurrent.Callable<Boolean> cond) throws Exception {
        for (int i = 0; i < frames && !cond.call(); i++) pump(a, b, 1, dt);
    }

    /**
     * An online two-player campaign between two games: the host starts it from New Game, the friend joins from the
     * main menu, both HQs are used at once, the battles are fought and settled on the host and shown on both, a
     * decided planet moves on, the host leaves and continues later with a new code, and the guest never saves it.
     */
    static void onlineCampaign(int battles, boolean shots, String url) throws Exception {
        MemRelay relay = new MemRelay();
        final FakePlatform hp = new FakePlatform(), gp = new FakePlatform();
        hp.relay = gp.relay = relay;
        String saved = Online.relayUrl;
        boolean savedAuto = Online.autoplay;
        Online.relayUrl = url != null ? url : "mem:relay";
        final float dt = 1 / 20f;
        final int frames = url != null ? 3000 : 80;
        pumpSleep = url != null ? 20 : 0;
        try {
            Game gh = new Game(hp, 301), gg = new Game(gp, 302);
            pump(gh, gg, 2, dt);
            gh.setScreen(new NewGameScreen(gh));
            pump(gh, gg, 2, dt);
            click(gh, 6);
            click(gh, 6);   // vs Friend Online
            pump(gh, gg, 2, dt);
            if (shots) shot(gh, "90_online_campaign_new");
            click(gh, 50);   // start as attacker
            final Game fh = gh, fg = gg;
            waitFor(gh, gg, dt, frames, () -> fh.online != null && fh.online.phase == Online.HOSTING);
            if (gh.online == null || !gh.online.campaign || gh.online.phase != Online.HOSTING) throw new RuntimeException("the campaign should host a game");
            if (!gh.saveIsOnlineCampaign()) throw new RuntimeException("the host should save an online campaign");
            if (shots) shot(gh, "91_online_campaign_code");
            gg.setScreen(new MenuScreen(gg));
            pump(gh, gg, 2, dt);
            if (shots) shot(gg, "92_menu_play_online");
            click(gg, 7);   // Play Online
            pump(gh, gg, 2, dt);
            if (!(gg.screen() instanceof OnlineScreen)) throw new RuntimeException("Play Online should open the online screen");
            gg.joinOnline(gh.online.code);
            waitFor(gh, gg, dt, frames, () -> fh.screen() instanceof HqScreen && fg.screen() instanceof HqScreen);
            if (!(gh.screen() instanceof HqScreen) || !(gg.screen() instanceof HqScreen)) throw new RuntimeException("both HQs should open");
            if (gg.pvpMain == null || gg.company == gg.campaignMain() || gh.pvpMain != null) throw new RuntimeException("the guest should hold Player 2's company");
            if (shots) shot(gg, "93_online_campaign_hq_p2");
            Online.autoplay = true;   // deployment and battles play themselves
            gh.headless = gg.headless = true;
            relay.lossy = url == null;   // messages lost and overtaken now and then, as AWS may
            Random r = new Random(77);
            int fought = 0, planets = 0, forfeits = 0;
            for (int turn = 0; fought + forfeits < battles; turn++) {
                if (turn == 2) {
                    // the host leaves and continues later: a new room, and the friend joins it
                    click(gh, 1);   // Main Menu (from the HQ)
                    waitFor(gh, gg, dt, frames, () -> fg.online != null && fg.online.phase == Online.FAILED);
                    if (gg.online == null || gg.online.phase != Online.FAILED) throw new RuntimeException("the guest should hear the host left");
                    gg.toMenu();
                    gh = new Game(hp, 303);
                    gh.headless = true;
                    pump(gh, gg, 2, dt);
                    if (!has(gh, 1) || !"Continue Online Campaign".equals(find(gh, 1).label)) throw new RuntimeException("the menu should offer the online campaign");
                    click(gh, 1);
                    final Game h2 = gh;
                    waitFor(gh, gg, dt, frames, () -> h2.online != null && h2.online.phase == Online.HOSTING);
                    gg.joinOnline(gh.online.code);
                    final Game h3 = gh;
                    waitFor(gh, gg, dt, frames, () -> h3.screen() instanceof HqScreen && fg.screen() instanceof HqScreen);
                    if (!(gh.screen() instanceof HqScreen) || !(gg.screen() instanceof HqScreen)) throw new RuntimeException("the continued campaign should open both HQs");
                }
                Company p1 = gh.campaignMain(), p2 = gg.company;
                if (!p1.serialize().equals(gg.campaignMain().serialize()) || !p1.rival.serialize().equals(p2.serialize()))
                    throw new RuntimeException("the two devices should start the turn with the same campaign");
                stockUp(p1, r);
                stockUp(p2, r);
                gh.setScreen(new HqScreen(gh));
                gg.setScreen(new HqScreen(gg));
                pump(gh, gg, 2, dt);
                // whoever is ready first waits for the other
                Game first = turn % 2 == 0 ? gg : gh, second = first == gg ? gh : gg;
                if (turn == 3 && first != gg) {
                    first = gg;
                    second = gh;
                }
                if (turn == 3) {
                    // Player 2 sends nothing: a forfeit, settled without a battle
                    p2.deployed.clear();
                    gg.online.ready(p2);
                    gg.setScreen(new OnlineScreen(gg));
                } else click(first, 4);
                pump(gh, gg, 3, dt);
                if (!(first.screen() instanceof OnlineScreen)) throw new RuntimeException("the first player ready should wait");
                if (shots && turn == 0) shot(first, "94_online_campaign_waiting");
                click(second, 4);
                final Game h = gh;
                waitFor(gh, gg, dt, url != null ? 200000 : 240000, () -> (h.screen() instanceof PvpDebriefScreen) && (fg.screen() instanceof PvpDebriefScreen)
                        || h.online == null || h.online.phase == Online.FAILED);
                if (!(gh.screen() instanceof PvpDebriefScreen) || !(gg.screen() instanceof PvpDebriefScreen))
                    throw new RuntimeException("both should see the debrief: " + gh.screen().getClass().getSimpleName() + " / " + gg.screen().getClass().getSimpleName());
                Company c = gh.campaignMain();
                if (!c.serialize().equals(gg.campaignMain().serialize()) || !c.rival.serialize().equals(gg.company.serialize()))
                    throw new RuntimeException("the debrief should leave both devices with the same campaign");
                java.lang.reflect.Field fd = PvpDebriefScreen.class.getDeclaredField("d");
                fd.setAccessible(true);
                Game.Debrief dh = (Game.Debrief) fd.get(gh.screen()), dg = (Game.Debrief) fd.get(gg.screen());
                if (!dh.serialize().equals(dg.serialize()) || !dh.p2.serialize().equals(dg.p2.serialize())) throw new RuntimeException("the debriefs differ");
                if (dh.forfeit) forfeits++;
                else fought++;
                if (shots && turn == 0) shot(gg, "95_online_campaign_debrief_p2");
                System.out.println("online campaign turn " + (turn + 1) + ": " + (dh.forfeit ? "forfeit by Player " + (dh.forfeitTeam + 1) : "result " + dh.result)
                        + ", front " + dh.frontBefore + "->" + dh.frontAfter + ", P1 $" + c.cash + " (" + c.roster.size() + " units), P2 $" + c.rival.cash
                        + " (" + c.rival.roster.size() + " units)" + (c.over != 0 ? ", planet decided" : ""));
                click(gh, 1);
                click(gg, 1);
                pump(gh, gg, 3, dt);
                if (c.over != 0) {
                    planets++;
                    if (!(gh.screen() instanceof CampaignEndScreen) || !(gg.screen() instanceof CampaignEndScreen)) throw new RuntimeException("both should see the planet decided");
                    if (has(gg, 2)) throw new RuntimeException("only the host picks the next planet");
                    if (shots && planets == 1) shot(gg, "96_online_campaign_end_p2");
                    click(gh, 2);   // next: Player 1 attacks
                    waitFor(gh, gg, dt, frames, () -> h.screen() instanceof HqScreen && fg.screen() instanceof HqScreen);
                }
                if (!(gh.screen() instanceof HqScreen) || !(gg.screen() instanceof HqScreen)) throw new RuntimeException("both HQs should open for the next turn");
            }
            if (gp.store.containsKey("company") || gp.store.containsKey("rival")) throw new RuntimeException("the guest should not save the host's campaign");
            if (relay.lossy) System.out.println("  lossy relay: " + relay.dropped + " messages lost, " + relay.swapped + " overtaken");
            System.out.println("online campaign OK (" + fought + " battles, " + forfeits + " forfeits, " + planets + " planets decided"
                    + (url == null ? ", " + relay.messages + " messages" : " over " + url) + ")");
            gh.toMenu();
            gg.toMenu();
        } finally {
            Online.relayUrl = saved;
            Online.autoplay = savedAuto;
            pumpSleep = 0;
        }
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
    /**
     * Mercenary play end to end: take jobs from the board, buy only what the planet sells you, fight the target
     * faction's force, and check standing, the board, offers, travel and the save after every job.
     */
    static void merc(int jobs) throws Exception {
        FakePlatform fp = new FakePlatform();
        Game g = new Game(fp, 4242);
        g.headless = true;
        g.newMercenary(1, 10000);
        Company c = g.company;
        if (!c.merc || c.planet == null || c.contracts.isEmpty() || c.job() != null) throw new RuntimeException("bad mercenary start");
        Random r = new Random(11);
        String[] res = {"", "WIN", "LOSS", "DRAW", "WITHDRAW"};
        int travels = 0, protos = 0;
        for (int m = 1; m <= jobs; m++) {
            if (c.contracts.isEmpty() || m % 5 == 0) {
                // move on: pick the destination with the most factions that hire you
                TravelScreen ts = new TravelScreen(g);
                java.lang.reflect.Field fo = TravelScreen.class.getDeclaredField("options");
                fo.setAccessible(true);
                Merc.Planet[] opts = (Merc.Planet[]) fo.get(ts);
                Merc.Planet best = null;
                int bestHires = -1;
                for (Merc.Planet p : opts) {
                    int hires = 0;
                    for (int f = 0; f < Faction.COUNT; f++) if (p.has(f) && Merc.hires(c.rep[f])) hires++;
                    if (hires > bestHires) { bestHires = hires; best = p; }
                }
                int cash = c.cash, cost = Merc.travelCost(c);
                if (cash < cost) c.cash = cash = cost + 2000;
                g.travel(best);
                if (c.planet != best || c.cash != cash - cost) throw new RuntimeException("travel failed");
                travels++;
                System.out.println("-- travelled to " + best.name + " for $" + cost);
                if (c.contracts.isEmpty()) continue;
            }
            for (Merc.Contract k : c.contracts) {
                if (!c.planet.has(k.employer) || !c.planet.has(k.target) || !Merc.hires(c.rep[k.employer]) || Merc.allied(k.employer, k.target)
                        || k.employer == k.target) throw new RuntimeException("bad contract on the board: " + k.serialize());
                if (k.scenario == 0 || k.scenario == 20) throw new RuntimeException("campaign-only scenario offered");
            }
            int pick = r.nextInt(c.contracts.size());
            g.acceptContract(pick);
            Merc.Contract k = c.job();
            Scenario sc = c.next();
            if (k == null || sc.id != k.scenario || c.side != k.role || c.reward != k.reward || c.limits() != sc.caps)
                throw new RuntimeException("contract not applied");
            // the market: only designs from factions here that trade with you, uniques only when offered
            for (Chassis ch : Chassis.ALL) {
                if (!c.sells(ch)) continue;
                if (ch.unique && ch.id != c.offerChassis) throw new RuntimeException("unique sold without an offer");
                boolean ok = ch.unique;
                for (int f = 0; f < Faction.COUNT; f++) if (c.planet.has(f) && Merc.trades(c.rep[f]) && (ch.factions & Faction.bit(f)) != 0) ok = true;
                if (!ok) throw new RuntimeException(ch.name + " sold by nobody here");
            }
            for (Wpn w : Wpn.ALL) if (c.canBuy(w) && w.unique && c.offerItem != w.id) throw new RuntimeException("unique weapon sold without an offer");
            // repair, then buy for the job's limits from this planet's market
            for (Unit u : c.roster) if (u.isDamaged() && c.cash >= u.repairCost()) { c.cash -= u.repairCost(); u.repairFully(); }
            Limits lim = c.limits();
            for (int buys = 0; buys < 5; buys++) {
                int[] have = new int[4];
                for (Unit u : c.roster) if (!u.destroyed && c.allowed(u)) have[Limits.kind(u.type)]++;
                int[] room = {lim.mechs - have[0], lim.heavy - Math.min(have[0], lim.mechs) - have[1], lim.infantry - have[2], 0};
                Unit best = null;
                for (Chassis ch : Chassis.ALL) {
                    int kd = Limits.kind(ch.type);
                    if (!c.sells(ch) || !sc.allows(ch) || room[kd] <= 0 || (kd == Limits.MECH && room[1] + room[0] <= 0)) continue;
                    Unit u = new Unit(ch);
                    if (u.value() <= c.cash * 0.5f && (best == null || u.value() > best.value()) && r.nextFloat() < 0.8f) best = u;
                }
                if (best == null || c.roster.size() >= Company.MAX_ROSTER - 1) break;
                c.cash -= best.value();
                c.roster.add(best);
            }
            c.deployed.clear();
            for (Unit u : c.roster) if (c.canDeploy(u) && !c.deployed.contains(u)) c.deployed.add(u);
            if (!lim.holds(c.deployed)) throw new RuntimeException("deployed past the job's limits");
            int[] repBefore = c.rep.clone();
            int boardBefore = c.contracts.size();
            if (c.deployed.isEmpty()) {
                g.forfeitMission();
                System.out.printf("job %2d: %-21s FORFEIT (nothing allowed)%n", m, sc.name);
                int[] dr = Merc.repChanges(k, Battle.RESULT_WITHDRAW);
                for (int f = 0; f < Faction.COUNT; f++)
                    if (c.rep[f] != Math.max(Merc.REP_MIN, Math.min(Merc.REP_MAX, repBefore[f] + dr[f]))) throw new RuntimeException("standing not applied");
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
                List<Unit> fielded = new ArrayList<Unit>();
                for (Unit e : enemy) {
                    if (e.paint != Faction.ALL[k.target].scheme) throw new RuntimeException("enemy not in the target's colors");
                    if ("garrison".equals(e.tag)) continue;
                    fielded.add(e);
                    if ((e.ch.factions & Faction.bit(k.target)) == 0) throw new RuntimeException(e.name + " is not a " + Faction.ALL[k.target].name + " design");
                    if (!sc.allows(e)) throw new RuntimeException("enemy " + e.name + " not allowed in " + sc.name);
                    if (e.ch.unique) protos++;
                    for (int w : e.weapon) if (w >= 0 && Wpn.ALL[w].maker >= 0 && Wpn.ALL[w].maker != k.target && !Wpn.ALL[w].unique)
                        throw new RuntimeException("enemy carries another faction's make");
                }
                if (fielded.isEmpty()) throw new RuntimeException("enemy fielded nothing");
                if (!sc.caps.holds(fielded)) throw new RuntimeException("enemy over the job's limits");
                g.startBattle((HexMap) fm.get(bs), enemy, allies);
                Battle b = ((BattleScreen) g.screen()).battle();
                b.autoPlayer = true;
                b.finishDeployment();
                int frames = 0;
                while (b.result == Battle.RESULT_NONE) {
                    b.update(1 / 20f);
                    if (++frames > 20 * 60 * 120) throw new RuntimeException("stalled");
                }
                int cashBefore = c.cash;
                g.finishBattle(b);
                int[] dr = Merc.repChanges(k, b.result);
                for (int f = 0; f < Faction.COUNT; f++)
                    if (c.rep[f] != Math.max(Merc.REP_MIN, Math.min(Merc.REP_MAX, repBefore[f] + dr[f]))) throw new RuntimeException("standing not applied");
                System.out.printf("job %2d: %-21s %-8s %-4s for %-9s vs %-9s threat %d pay $%5d  you $%6d->$%6d %2d units | %s %+d, %s %+d%n",
                        m, sc.name, Scenario.SIDE_NAMES[c.side], res[b.result], Faction.ALL[k.employer].shortName, Faction.ALL[k.target].shortName,
                        k.threat, k.reward, cashBefore, c.cash, c.roster.size(), Faction.ALL[k.employer].shortName, dr[k.employer],
                        Faction.ALL[k.target].shortName, dr[k.target]);
            }
            if (c.job() != null || c.contracts.contains(k)) throw new RuntimeException("finished contract still on the board");
            if (c.contracts.size() < Math.min(boardBefore - 1, 1)) throw new RuntimeException("board not refilled");
            // the save must round-trip the mercenary state
            Company back = Company.parse(fp.load("company"));
            if (back == null || !back.merc || !java.util.Arrays.equals(back.rep, c.rep) || !back.planet.name.equals(c.planet.name)
                    || back.planet.factions != c.planet.factions || back.contracts.size() != c.contracts.size() || back.contract != c.contract
                    || back.offerChassis != c.offerChassis || back.offerItem != c.offerItem || back.cash != c.cash)
                throw new RuntimeException("mercenary save round trip failed");
            for (int i = 0; i < c.contracts.size(); i++)
                if (!back.contracts.get(i).serialize().equals(c.contracts.get(i).serialize())) throw new RuntimeException("contract round trip failed");
        }
        String standing = java.util.Arrays.toString(c.rep);
        // trust brings offers: make a faction here an ally and roll until something special turns up
        int f0 = -1;
        for (int f = 0; f < Faction.COUNT; f++) if (c.planet.has(f)) f0 = f;
        c.rep[f0] = 80;
        boolean offered = false;
        for (int i = 0; i < 40 && !offered; i++) {
            Merc.rollOffers(c, r);
            offered = c.offerChassis >= 0 || c.offerItem >= 0;
        }
        if (!offered) throw new RuntimeException("an allied faction never offered anything");
        if (c.offerChassis >= 0 && !c.sells(Chassis.ALL[c.offerChassis])) throw new RuntimeException("offered prototype not for sale");
        if (c.offerItem >= 0 && c.offerItem < 1000 && !c.canBuy(Wpn.ALL[c.offerItem])) throw new RuntimeException("offered weapon not for sale");
        if (c.offerItem >= 1000 && !c.canBuy(Equip.ALL[c.offerItem - 1000])) throw new RuntimeException("offered equipment not for sale");
        // hostility closes doors: nobody hires or sells when everyone here hates you
        for (int f = 0; f < Faction.COUNT; f++) c.rep[f] = -60;
        c.contracts.clear();
        Merc.fillBoard(c, r);
        if (!c.contracts.isEmpty()) throw new RuntimeException("hostile factions still hire");
        for (Chassis ch : Chassis.ALL) if (c.sells(ch) && !ch.unique) throw new RuntimeException("hostile faction still sells " + ch.name);
        // campaign and quick-battle companies never see faction makes or uniques
        Company camp = new Company();
        for (Wpn w : Wpn.ALL) if (camp.canBuy(w) && (w.unique || w.maker >= 0)) throw new RuntimeException("campaign sells " + w.name);
        for (Equip e : Equip.ALL) if (camp.canBuy(e) && (e.unique || e.maker >= 0)) throw new RuntimeException("campaign sells " + e.name);
        for (Chassis ch : Chassis.SHOP) if (ch.unique) throw new RuntimeException("unique in the shop");
        System.out.println("mercenary: " + jobs + " jobs, " + travels + " travels, " + protos + " prototypes fielded against you; standing "
                + standing + "; checks passed");
    }

    /** Buys units for the next scenario the way a player reading the briefing would, then deploys what may fight. */
    static void stockUp(Company c, Random r) {
        Scenario sc = c.next();
        for (Unit u : c.roster) if (u.isDamaged() && c.cash >= u.repairCost()) { c.cash -= u.repairCost(); u.repairFully(); }
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
            if (best == null || c.roster.size() >= Company.MAX_ROSTER - 1) break;
            c.cash -= best.value();
            c.roster.add(best);
        }
        if (c.noneAllowed() && !c.mustForfeit()) {   // the HQ won't offer a forfeit while one is affordable: buy the cheapest
            Unit cheapest = null;
            for (Chassis ch : Chassis.SHOP) {
                Unit u = new Unit(ch);
                if (sc.allows(ch) && u.value() <= c.cash && (cheapest == null || u.value() < cheapest.value())) cheapest = u;
            }
            if (cheapest != null) {
                c.cash -= cheapest.value();
                c.roster.add(cheapest);
            }
        }
        c.deployed.clear();
        for (Unit u : c.roster) if (c.canDeploy(u) && !c.deployed.contains(u)) c.deployed.add(u);
    }

    /**
     * A two-player campaign played through the real screens, with the computer making both players' battle moves:
     * Player 1's HQ, the hand-over, Player 2's HQ (mirrored campaign state), the shared briefing, a hotseat battle
     * under the scenario's rules, both companies settled, and the next turn. Checks the save at every step.
     */
    static void pvpCampaign(int battles, boolean shots) throws Exception {
        FakePlatform fp = new FakePlatform();
        Game g = new Game(fp, 77);
        tick(g, 2);
        if (shots) {
            g.setScreen(new NewGameScreen(g));
            tick(g, 2);
            click(g, 6);   // vs Player 2
            shot(g, "70_pvp_new_game");
            click(g, 50);  // Start as Attacker
            tick(g, 2);
        } else g.newPvpCampaign(10000, Scenario.ATTACKER);
        Company c = g.campaignMain();
        if (!c.pvp || c.rival == null || !c.rival.pvp || c.side != Scenario.ATTACKER || c.rival.side != Scenario.DEFENDER)
            throw new RuntimeException("bad two-player campaign start");
        if (c.cash <= c.rival.cash) throw new RuntimeException("the attacker should start with more money");
        Random r = new Random(5);
        String[] res = {"", "P1 WIN", "P2 WIN", "DRAW", "?"};
        int planets = 0;
        for (int m = 1; m <= battles; m++) {
            if (!(g.screen() instanceof HandoffScreen)) throw new RuntimeException("each turn should start behind a hand-over screen");
            click(g, 1);   // I'm Player 1
            tick(g, 2);
            if (g.company != c || g.pvpMain != null || !(g.screen() instanceof HqScreen)) throw new RuntimeException("Player 1 should have the HQ");
            stockUp(c, r);
            g.setScreen(new HqScreen(g));
            tick(g, 2);
            if (c.deployed.isEmpty()) {
                click(g, 4);   // forfeit
                tick(g, 2);
                System.out.printf("battle %d: Player 1 forfeits%n", m);
            } else {
                click(g, 4);   // Done
                tick(g, 2);
                Company p2 = c.rival;
                if (g.company != p2 || g.pvpMain != c) throw new RuntimeException("Player 2 should have the HQ next");
                if (p2.side != 1 - c.side || p2.front != c.front || p2.scenario != c.scenario || p2.planetBattles != c.planetBattles)
                    throw new RuntimeException("Player 2's campaign view doesn't match Player 1's");
                // saved mid-turn: continuing lands on Player 2's hand-over
                Company saved = Company.parse(fp.load("company"));
                if (saved == null || !saved.pvp || saved.pvpTurn != 1) throw new RuntimeException("the turn isn't saved");
                Game g2 = new Game(fp, 78);
                tick(g2, 2);
                g2.continueCampaign();
                tick(g2, 2);
                if (g2.pvpMain == null || g2.company != g2.pvpMain.rival || !(g2.screen() instanceof HandoffScreen))
                    throw new RuntimeException("continuing should resume at Player 2's turn");
                click(g, 1);   // I'm Player 2
                tick(g, 2);
                stockUp(p2, r);
                g.setScreen(new HqScreen(g));
                tick(g, 2);
                if (shots && m == 1) shot(g, "71_pvp_hq_p2");
                if (p2.deployed.isEmpty()) {
                    click(g, 4);
                    tick(g, 2);
                    System.out.printf("battle %d: Player 2 forfeits%n", m);
                } else {
                    click(g, 4);   // Fight
                    tick(g, 2);
                    if (!(g.screen() instanceof BriefingScreen) || g.company != c) throw new RuntimeException("the briefing should follow Player 2's Fight");
                    if (shots && m == 1) shot(g, "72_pvp_briefing");
                    Scenario sc = c.next();
                    click(g, 2);   // Begin battle
                    tick(g, 3);
                    Battle b = ((BattleScreen) g.screen()).battle();
                    if (!b.hotseat || b.maxRounds != sc.rounds) throw new RuntimeException("the battle should be two-player under the scenario's rules");
                    for (Unit e : b.units) if (e.tag == null && e.team == Battle.ENEMY && !p2.roster.contains(e)) throw new RuntimeException("a Player 2 unit isn't from their roster");
                    if (!sc.limitsFor(p2.side, p2.planetBattles).holds(p2.deployed)) throw new RuntimeException("Player 2 is over the scenario's limits");
                    int p1Cash = c.cash, p2Cash = p2.cash, front = c.front;
                    g.saveBattle(b);
                    // the battle resumes as a campaign battle, with Player 2's force linked to their roster
                    Game g3 = new Game(fp, 79);
                    tick(g3, 2);
                    g3.resumeBattle();
                    tick(g3, 2);
                    Battle rb = ((BattleScreen) g3.screen()).battle();
                    Company rc = g3.company;
                    if (!rb.hotseat || !rc.pvp || rc.hotseat || rc.rival == null || rc.rival.deployed.size() != p2.deployed.size())
                        throw new RuntimeException("a two-player campaign battle did not resume as one");
                    for (Unit u : rc.rival.deployed) if (!rb.units.contains(u) || !rc.rival.roster.contains(u)) throw new RuntimeException("Player 2's resumed force isn't the battle's");
                    b.autoPlayer = true;
                    g.headless = true;
                    b.finishDeployment();
                    b.finishDeployment();
                    int frames = 0;
                    while (b.result == Battle.RESULT_NONE && ++frames < 20 * 60 * 120) b.update(1 / 20f);
                    if (b.result == Battle.RESULT_NONE) throw new RuntimeException("stalled");
                    g.finishBattle(b);
                    g.headless = false;
                    tick(g, 2);
                    if (!(g.screen() instanceof PvpDebriefScreen)) throw new RuntimeException("expected the two-player debrief");
                    if (p2.cash <= p2Cash || c.cash <= p1Cash) throw new RuntimeException("both players should be paid for a battle");
                    for (Unit u : p2.roster) if (u.destroyed && b.result == Battle.RESULT_WIN) throw new RuntimeException("Player 2 kept a wreck after losing");
                    if (shots && m == 1) shot(g, "73_pvp_debrief");
                    System.out.printf("battle %d: %-21s %-6s front %d->%d  P1 $%6d %2d units  P2 $%6d %2d units%n", m, sc.name, res[b.result], front, c.front,
                            c.cash, c.roster.size(), p2.cash, p2.roster.size());
                }
            }
            Company back = Company.parse(fp.load("company")), rback = Company.parse(fp.load("rival"));
            if (back == null || rback == null || !back.pvp || !rback.pvp || back.front != c.front || back.pvpTurn != 0
                    || back.cash != c.cash || rback.cash != c.rival.cash || rback.roster.size() != c.rival.roster.size())
                throw new RuntimeException("two-player save round trip failed: " + (back == null ? "no company" : "front " + back.front + "/" + c.front
                        + " turn " + back.pvpTurn + " cash " + back.cash + "/" + c.cash) + (rback == null ? " no rival" : " rival cash " + rback.cash + "/"
                        + c.rival.cash + " roster " + rback.roster.size() + "/" + c.rival.roster.size()));
            click(g, 1);   // Continue / Campaign Results
            tick(g, 2);
            if (c.over != 0) {
                if (!(g.screen() instanceof CampaignEndScreen)) throw new RuntimeException("expected the campaign end");
                if (shots) shot(g, "74_pvp_campaign_end");
                planets++;
                click(g, 3);   // next planet, Player 1 defends
                tick(g, 2);
                if (c.side != Scenario.DEFENDER || c.rival.side != Scenario.ATTACKER || c.over != 0) throw new RuntimeException("next planet not set up");
            }
        }
        System.out.println("two-player campaign OK (" + battles + " battles, " + planets + " planets decided; P1 " + c.wins + "W " + c.losses
                + "L, P2 " + c.rival.wins + "W " + c.rival.losses + "L)");
    }

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
            // online screens too: hosting works (nobody joins); joining finds no game
            fp.relay = new MemRelay();
            Online.relayUrl = "mem:relay";
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
