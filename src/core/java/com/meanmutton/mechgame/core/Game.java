package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Top level: current screen, input queue, settings, the company, and the flow between screens. */
public final class Game {
    public static final int TOUCH_DOWN = 0, TOUCH_MOVE = 1, TOUCH_UP = 2, BACK = 3, WHEEL = 4;
    public static final int[] QUICK_BUDGETS = {6000, 9000, 13000, 18000, 25000};
    public static final int[] CAMPAIGN_FUNDS = {7000, 10000, 14000};

    public final Platform platform;
    public final Random rng;
    public Settings settings;
    public Company company;
    public float time;
    public boolean headless;   // tests: no delays on screens that wait for the player

    private Screen screen;
    private int w = 1, h = 1;
    private final List<float[]> input = new ArrayList<float[]>();
    private final List<Runnable> posted = new ArrayList<Runnable>();

    public Game(Platform platform, long seed) {
        this.platform = platform;
        this.rng = new Random(seed);
        this.settings = Settings.parse(platform.load("settings"));
        String crash = platform.load(CrashReport.KEY);
        this.screen = crash != null && crash.length() > 0 ? new CrashScreen(this, crash) : new MenuScreen(this);
    }

    public Screen screen() {
        return screen;
    }

    public void setScreen(Screen s) {
        if (s != screen) crumb("screen " + s.getClass().getSimpleName());
        screen = s;
        s.resize(w, h);
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public void post(Runnable r) {
        synchronized (posted) {
            posted.add(r);
        }
    }

    /** A mouse wheel or trackpad scroll at (x, y): positive amounts scroll down (desktop and browser only). */
    public void wheel(float x, float y, float amount) {
        synchronized (input) {
            input.add(new float[]{WHEEL, amount, x, y});
        }
    }

    public void touch(int type, int pointer, float x, float y) {
        synchronized (input) {
            input.add(new float[]{type, pointer, x, y});
        }
    }

    public synchronized void tick(float dt, int width, int height) {
        w = Math.max(1, width);
        h = Math.max(1, height);
        screen.resize(w, h);
        List<Runnable> run = null;
        synchronized (posted) {
            if (!posted.isEmpty()) {
                run = new ArrayList<Runnable>(posted);
                posted.clear();
            }
        }
        if (run != null) for (Runnable r : run) r.run();
        List<float[]> ev = null;
        synchronized (input) {
            if (!input.isEmpty()) {
                ev = new ArrayList<float[]>(input);
                input.clear();
            }
        }
        if (ev != null) {
            for (float[] e : ev) {
                Screen s = screen;
                switch ((int) e[0]) {
                    case TOUCH_DOWN: s.touchDown((int) e[1], e[2], e[3]); break;
                    case TOUCH_MOVE: s.touchMove((int) e[1], e[2], e[3]); break;
                    case TOUCH_UP: s.touchUp((int) e[1], e[2], e[3]); break;
                    case WHEEL: s.wheel(e[2], e[3], e[1]); break;
                    case BACK:
                        if (!s.closeOverlay() && !s.back()) platform.exit();
                        break;
                }
            }
        }
        time += dt;
        if (online != null) online.tick(dt);   // the other player's messages and orders, before this frame's own
        screen.update(dt);
        if (online != null) online.flush();    // this frame's orders go to the other player
    }

    public synchronized void render(Gfx g) {
        Company c = company;
        Scheme.player = c != null && c.scheme != null ? c.scheme : Scheme.PLAYER_DEFAULT;
        Scheme.enemy = c != null && c.rival != null && c.rival.scheme != null ? c.rival.scheme : Scheme.ENEMY_DEFAULT;
        if (c != null && c.hotseat && c.seatNo == 0 && hotseatEnemy != null) Scheme.enemy = hotseatEnemy;
        if (online != null && online.schemes != null && (screen instanceof BattleScreen || screen instanceof HotseatEndScreen)) {
            // an online battle: each side in its own player's colors (the HQ shows this device's own)
            Scheme.player = online.schemes[0];
            Scheme.enemy = online.schemes[1];
        }
        screen.resize(w, h);
        screen.render(g);
    }

    // ---------------- crash reports ----------------

    private final String[] crumbs = new String[30];
    private int crumbCount;

    /** Remembers a recent action for crash reports (screen changes, button taps). */
    public void crumb(String s) {
        synchronized (crumbs) {
            crumbs[crumbCount % crumbs.length] = Text.pad(Text.fixed(time, 1), 7) + "s " + s;
            crumbCount++;
        }
    }

    public java.util.List<String> crumbs() {
        java.util.List<String> out = new ArrayList<String>();
        synchronized (crumbs) {
            int n = Math.min(crumbCount, crumbs.length);
            for (int i = crumbCount - n; i < crumbCount; i++) out.add(crumbs[i % crumbs.length]);
        }
        return out;
    }

    /** Called by the host when an uncaught error is about to end the app: saves a report for the next launch. */
    public void recordCrash(Thread thread, Throwable error) {
        String r;
        try {
            r = CrashReport.build(this, thread, error);
        } catch (Throwable t) {
            java.io.StringWriter sw = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(sw));
            r = "Hexfront crash report\n\n" + sw;
        }
        platform.saveNow(CrashReport.KEY, r);
    }

    /** Called by the host's watchdog when a thread has been stuck for seconds: saves a report for the next launch. */
    public void recordFreeze(String what, java.util.Map<Thread, StackTraceElement[]> stacks) {
        String r;
        try {
            r = CrashReport.freeze(this, what, stacks);
        } catch (Throwable t) {
            r = "Hexfront freeze report\n" + what;
        }
        platform.saveNow(CrashReport.KEY, r);
    }

    public void sfx(int id, float vol) {
        if (settings.sound && !headless) platform.play(id, vol);
    }

    public void saveSettings() {
        platform.save("settings", settings.serialize());
    }

    public void saveCompany() {
        if (online != null && !online.host) return;   // an online campaign belongs to the host: the guest keeps no copy
        Company c = campaignMain();
        if (c != null && !c.quick) {
            platform.save("company", c.serialize());
            if (c.rival != null) platform.save("rival", c.rival.serialize());
        }
    }

    // ---------------- two-player campaign ----------------

    /** Two-player campaign: Player 1's company while Player 2 has the HQ (then {@link #company} is Player 2's). */
    public Company pvpMain;

    /** The company the campaign save belongs to: Player 1's, whoever is at the HQ. */
    public Company campaignMain() {
        return pvpMain != null ? pvpMain : company;
    }

    /** A campaign for two people on one device: Player 1 takes the chosen side, Player 2 the other. */
    public void newPvpCampaign(int funds, int side) {
        clearBattle();
        pvpMain = null;
        Company c = new Company();
        c.pvp = true;
        c.difficulty = 1;
        c.side = side;
        c.cash = Math.round(funds * Scenario.startFunds(side));
        c.rollContract(rng);
        Company rv = new Company();
        rv.pvp = true;
        rv.difficulty = 1;
        rv.cash = Math.round(funds * Scenario.startFunds(1 - side));
        rv.scheme = Scheme.ENEMY_DEFAULT;   // Player 2 starts in red, as their side looks in battle
        c.rival = rv;
        rv.rival = c;
        syncRival(c);
        company = c;
        saveCompany();
        setScreen(pvpHandoff(0));
    }

    /**
     * Player 2's company follows the campaign from the other side: the same front, scenario and battle count, the
     * opposite role, and its own pay for the next battle.
     */
    public void syncRival(Company c) {
        Company rv = c.rival;
        if (rv == null) return;
        rv.pvp = true;
        rv.rival = c;
        rv.difficulty = c.difficulty;
        rv.side = 1 - c.side;
        rv.front = c.front;
        rv.scenario = c.scenario;
        rv.lastScenario = c.lastScenario;
        rv.lastOutcome = c.lastOutcome;
        rv.edge = c.edge;
        rv.over = c.over;
        rv.planets = c.planets;
        rv.planetBattles = c.planetBattles;
        rv.mission = c.mission;
        if (c.over == 0) rv.rollContract(rng);
        rv.biome = c.biome;
        rv.seed = c.seed;
    }

    /** The hand-over screen in front of a player's HQ. */
    private Screen pvpHandoff(int seat) {
        Company c = campaignMain();
        Company mine = seat == 0 ? c : c.rival;
        Scenario sc = mine.next();
        String role = Text.lower(Scenario.SIDE_NAMES[mine.side]);
        String text = (seat == 0 ? "Player 1" : "Pass the device to Player 2. Player 2") + " is the " + role + ". Next: "
                + (sc != null ? sc.name : "battle") + ". Repair, buy, customize and tick the units to send, then press "
                + (seat == 0 ? "Done." : "Fight.");
        return new HandoffScreen(this, seat, "YOUR HQ", text, new HqScreen(this));
    }

    /** After a battle: Player 1's turn at the HQ comes next. */
    public void pvpNextTurn() {
        setScreen(pvpHandoff(0));
    }

    /** Player 1 is done at the HQ: Player 2's turn. */
    public void pvpHandToP2() {
        Company c = campaignMain();
        c.pvpTurn = 1;
        syncRival(c);
        pvpMain = c;
        company = c.rival;
        company.fitDeployment();
        saveCompany();
        setScreen(pvpHandoff(1));
    }

    /** Back to Player 2's HQ (from the briefing, which Player 2 opened). */
    public void pvpBackToP2() {
        Company c = campaignMain();
        pvpMain = c;
        company = c.rival;
        setScreen(new HqScreen(this));
    }

    /** Player 2 is ready: the briefing, then the battle, seen from Player 1's side as every campaign battle is. */
    public void pvpFight() {
        Company c = campaignMain();
        pvpMain = null;
        company = c;
        prepareMission();
    }

    /** A player has no unit that may fight in the next scenario: they forfeit, and the other player wins it. */
    public void pvpForfeit(int team) {
        Company c = campaignMain();
        pvpMain = null;
        company = c;
        int result = team == Battle.PLAYER ? Battle.RESULT_LOSS : Battle.RESULT_WIN;
        Debrief d = settleSide(c, null, Battle.PLAYER, result);
        d.p2 = settleSide(c.rival, null, Battle.ENEMY, flip(result));
        d.forfeit = true;
        d.forfeitTeam = team;
        settle(d, result, 0, null);
    }

    static int flip(int result) {
        return result == Battle.RESULT_WIN ? Battle.RESULT_LOSS : (result == Battle.RESULT_DRAW ? result : Battle.RESULT_WIN);
    }

    /**
     * One company's share of a battle's result, as the player's is settled: pay for the result, a bonus and salvage
     * for what it destroyed, and its destroyed units recovered as wrecks (win or draw) or lost for good.
     */
    private Debrief settleSide(Company c, Battle b, int team, int result) {
        Debrief d = new Debrief();
        d.result = result;
        d.pay = result == Battle.RESULT_WIN ? c.reward : (result == Battle.RESULT_DRAW ? c.reward / 2 : c.reward / 4);
        int destroyedValue = 0;
        boolean recover = result == Battle.RESULT_WIN || result == Battle.RESULT_DRAW;
        if (b != null) {
            for (Unit u : b.units) {
                if (u.team == team || !u.destroyed) continue;
                destroyedValue += u.value();
                d.enemyLost.add(u.name);
                if (!recover) continue;
                List<Integer> intact = new ArrayList<Integer>();
                for (int m = 0; m < u.weapon.length; m++) if (u.weapon[m] >= 0 && !u.wpnBroken[m]) intact.add(u.weapon[m]);
                if (!intact.isEmpty() && b.rnd.nextFloat() < 0.6f) {
                    int wid = intact.get(b.rnd.nextInt(intact.size()));
                    c.armoryW[wid]++;
                    d.salvage.add(Wpn.ALL[wid].name);
                }
                for (int e = 0; e < u.equip.length; e++) {
                    if (u.equip[e] >= 0 && !u.eqBroken[e] && b.rnd.nextFloat() < 0.25f) {
                        c.armoryE[u.equip[e]]++;
                        d.salvage.add(Equip.ALL[u.equip[e]].name);
                        break;
                    }
                }
            }
        }
        if (result == Battle.RESULT_WIN) d.bonus = destroyedValue * 15 / 100;
        else if (result == Battle.RESULT_DRAW) d.bonus = destroyedValue * 8 / 100;
        c.cash += d.pay + d.bonus;
        for (Unit u : new ArrayList<Unit>(c.deployed)) {
            u.shutdown = false;
            u.staggered = false;
            u.ionized = false;
            u.heat = 0;
            if (u.destroyed) {
                if (recover) d.wrecked.add(u.name);
                else {
                    d.lost.add(u.name);
                    c.roster.remove(u);
                }
                c.deployed.remove(u);
            }
        }
        return d;
    }

    public boolean hasSave() {
        return Company.parse(platform.load("company")) != null;
    }

    /** True if the saved campaign is a two-player one (for the menu's Continue label). */
    public boolean saveIsPvp() {
        Company c = Company.parse(platform.load("company"));
        return c != null && c.pvp;
    }

    /** True if the saved company is a mercenary one (for the menu's Continue label). */
    public boolean saveIsMerc() {
        Company c = Company.parse(platform.load("company"));
        return c != null && c.merc;
    }

    /** True if a battle was left in progress (campaign or quick battle). */
    public boolean hasBattle() {
        String s = platform.load(BattleSave.KEY);
        return s != null && s.length() > 0;
    }

    public void saveBattle(Battle b) {
        if (company != null) platform.save(BattleSave.KEY, BattleSave.write(company, b));
    }

    public void clearBattle() {
        platform.save(BattleSave.KEY, "");
    }

    /** Back into the battle that was in progress, exactly where it was left. */
    public void resumeBattle() {
        Battle b = BattleSave.read(this, platform.load(BattleSave.KEY));
        if (b == null) {
            clearBattle();
            if (hasSave()) continueCampaign();
            return;
        }
        setScreen(new BattleScreen(this, b));
    }

    // =====================================================================
    // Flow
    // =====================================================================

    public void newCampaign(int difficulty, int funds) {
        newCampaign(difficulty, funds, Scenario.ATTACKER);
    }

    public void newCampaign(int difficulty, int funds, int side) {
        clearBattle();
        company = new Company();
        company.difficulty = difficulty;
        company.side = side;
        // the attacker lands with more money; the defender's income grows faster (see Scenario)
        company.cash = Math.round(funds * Scenario.startFunds(side));
        company.rollContract(rng);
        company.rival = Rival.create(difficulty, Math.round(funds * Scenario.startFunds(1 - side)), rng);
        saveCompany();
        setScreen(new HqScreen(this));
    }

    /** After a campaign ends: the same company moves on to a new planet against a fresh enemy. */
    public void nextPlanet(int side) {
        Company c = campaignMain();
        company = c;
        pvpMain = null;
        c.over = 0;
        c.side = side;
        c.front = Scenario.FRONT_START;
        c.scenario = Scenario.INITIAL_DROP;
        c.lastScenario = -1;
        c.lastOutcome = Scenario.OUT_NONE;
        c.edge = -1;
        c.planets++;
        c.planetBattles = 0;
        if (c.pvp && c.rival != null) {
            // two players: the same two companies meet on the next planet, Player 1 on the chosen side
            c.rollContract(rng);
            c.pvpTurn = 0;
            syncRival(c);
            c.fitDeployment();
            c.rival.fitDeployment();
            saveCompany();
            if (online != null && online.campaign) {
                online.sendCampaign();   // both go to their HQs for the new planet
                setScreen(new HqScreen(this));
                return;
            }
            setScreen(pvpHandoff(0));
            return;
        }
        int worth = c.cash;
        for (Unit u : c.roster) worth += u.value();
        // a fresh enemy matched to your company's worth, scaled by which side has the bigger starting purse
        c.rival = Rival.create(c.difficulty, Math.round(worth * Scenario.startFunds(1 - side) / Scenario.startFunds(side)), rng);
        c.rollContract(rng);
        c.fitDeployment();
        saveCompany();
        setScreen(new HqScreen(this));
    }

    /** A mercenary company on its first planet, with a board of contracts waiting. */
    public void newMercenary(int difficulty, int funds) {
        clearBattle();
        company = new Company();
        company.merc = true;
        company.difficulty = difficulty;
        company.cash = funds;
        company.planet = Merc.planet(rng);
        Merc.fillBoard(company, rng);
        company.rollContract(rng);
        saveCompany();
        setScreen(new HqScreen(this));
    }

    /** Takes a contract from the board: its scenario sets the limits for deployment. */
    public void acceptContract(int index) {
        Company c = company;
        if (index < 0 || index >= c.contracts.size()) return;
        c.contract = index;
        c.side = c.contracts.get(index).role;
        c.rollContract(rng);
        c.fitDeployment();
        saveCompany();
        setScreen(new HqScreen(this));
    }

    /** Moves the company to another planet: new factions, a new board, new markets. */
    public void travel(Merc.Planet to) {
        Company c = company;
        int cost = Merc.travelCost(c);
        if (c.cash < cost) return;
        c.cash -= cost;
        c.planet = to;
        c.contracts.clear();
        c.contract = -1;
        Merc.fillBoard(c, rng);
        Merc.rollOffers(c, rng);
        c.rollContract(rng);
        c.fitDeployment();
        saveCompany();
        setScreen(new ContractsScreen(this));
    }

    // ---------------- hotseat: two players on one device ----------------

    /** Hotseat: the two players' companies while they build their forces. */
    public Company[] seats;
    /** Hotseat: Player 2's colors, for drawing their side in battle. */
    public Scheme hotseatEnemy;

    /** Two players, one device: each builds a force with the same budget, Player 1 first. */
    public void newHotseat(int budget) {
        clearBattle();
        seats = new Company[2];
        for (int i = 0; i < 2; i++) {
            Company c = new Company();
            c.quick = true;
            c.hotseat = true;
            c.seatNo = i;
            c.difficulty = 1;
            c.cash = budget;
            c.rollContract(rng);
            if (i == 1) c.scheme = Scheme.ENEMY_DEFAULT;   // Player 2 starts in red, as their side looks in battle
            seats[i] = c;
        }
        company = seats[0];
        setScreen(new HandoffScreen(this, 0, "BUILD YOUR FORCE",
                "Player 1 builds first. Buy units in the Market with your $" + Ui.money(budget) + ", customize them, tick the ones to deploy, then press Done.",
                new HqScreen(this)));
    }

    /** Hotseat: Player 1 is done building; Player 2 builds next without seeing Player 1's force. */
    public void hotseatNext() {
        company = seats[1];
        setScreen(new HandoffScreen(this, 1, "BUILD YOUR FORCE",
                "Pass the device to Player 2. Buy units in the Market with your $" + Ui.money(seats[1].cash)
                        + ", customize them, tick the ones to deploy, then press Fight.", new HqScreen(this)));
    }

    /** Hotseat: both forces are ready. A map for them, then the briefing; the battle sees it from Player 1's side. */
    public void prepareHotseat() {
        Company p1 = seats[0], p2 = seats[1];
        company = p1;
        hotseatEnemy = p2.scheme;
        Random r = new Random(p1.seed ^ (p2.seed * 31));
        int biome = r.nextInt(HexMap.BIOME_NAMES.length);
        HexMap map = new HexMap(20, 13, biome, Weather.random(biome, r));
        map.generate(r);
        p1.biome = biome;
        setScreen(new BriefingScreen(this, map, new ArrayList<Unit>(p2.deployed)));
    }

    /** Hotseat: the battle is over. */
    public void finishHotseat(Battle b) {
        if (!b.online()) clearBattle();   // an online battle is never saved, so any saved battle stays
        setScreen(new HotseatEndScreen(this, b));
    }

    /** Hotseat: the same two forces, fully repaired, on a new map. */
    public void hotseatRematch(Battle b) {
        if (seats == null) {   // a battle resumed from a save: rebuild the seats from the battle's units
            seats = new Company[2];
            for (int i = 0; i < 2; i++) {
                Company c = new Company();
                c.quick = true;
                c.hotseat = true;
                c.seatNo = i;
                c.difficulty = 1;
                c.scheme = i == 0 ? (company != null ? company.scheme : null) : hotseatEnemy;
                seats[i] = c;
            }
        }
        for (int i = 0; i < 2; i++) {
            Company c = seats[i];
            c.roster.clear();
            c.deployed.clear();
            for (Unit u : b.units) {
                if (u.team != i || u.tag != null) continue;
                Unit fresh = u.copyLoadout();
                fresh.name = u.name;
                c.roster.add(fresh);
                c.deployed.add(fresh);
            }
            c.rollContract(rng);
        }
        prepareHotseat();
    }

    public void quickBattle(int difficulty, int budget) {
        clearBattle();
        company = new Company();
        company.quick = true;
        company.difficulty = difficulty;
        company.cash = budget;
        company.rollContract(rng);
        setScreen(new HqScreen(this));
    }

    public void continueCampaign() {
        Company c = Company.parse(platform.load("company"));
        if (c == null) return;
        company = c;
        pvpMain = null;
        if (c.pvp) {
            c.rival = Company.parse(platform.load("rival"));
            if (c.rival == null) return;
            syncRival(c);
            c.fitDeployment();
            c.rival.fitDeployment();
            if (c.onlineCampaign) {
                hostOnlineCampaign();   // a new room: the friend joins with its code and picks up Player 2's company
                return;
            }
            if (c.over != 0) setScreen(new CampaignEndScreen(this));
            else if (c.pvpTurn == 1) {
                pvpMain = c;
                company = c.rival;
                setScreen(pvpHandoff(1));
            } else setScreen(pvpHandoff(0));
            return;
        }
        if (c.merc) {
            c.fitDeployment();
            setScreen(new HqScreen(this));
            return;
        }
        c.rival = Company.parse(platform.load("rival"));
        if (c.rival == null) {
            // saves from before rival companies existed: start one matched to this company's current worth
            int worth = c.cash;
            for (Unit u : c.roster) worth += u.value();
            c.rival = Rival.create(c.difficulty, worth, rng);
            saveCompany();
        }
        c.fitDeployment();
        if (c.over != 0) setScreen(new CampaignEndScreen(this));
        else setScreen(new HqScreen(this));
    }

    /** The enemy force for the next mission, built to a budget from stock designs. */
    public List<Unit> makeEnemyForce(Random r) {
        Company c = company;
        float mult = c.difficulty == 0 ? 0.75f : (c.difficulty == 1 ? 0.95f : 1.2f);
        if (!c.quick) mult *= 1f + Math.min(0.5f, (c.mission - 1) * 0.04f);
        int budget = Math.max(1500, Math.round(c.deployedValue() * mult));
        int count = Math.min(Limits.FULL.total(), Math.max(2, c.deployed.size() + (c.difficulty == 2 ? 1 : 0) + (r.nextBoolean() ? 1 : 0) - 1));
        List<Unit> out = new ArrayList<Unit>();
        int[] roleCount = new int[Chassis.ROLES.length];
        int remaining = budget;
        for (int slot = 0; slot < count; slot++) {
            int per = remaining / (count - slot);
            Unit pick = null;
            float bestScore = -1;
            for (int tries = 0; tries < 60; tries++) {
                Chassis ch = Chassis.SHOP[r.nextInt(Chassis.SHOP.length)];
                Unit u = new Unit(ch);
                int v = u.value();
                if (v > remaining || !Limits.FULL.fits(out, u)) continue;
                float fit = 1f - Math.abs(v - per) / (float) Math.max(1, per);
                if (roleCount[ch.role] >= 2) fit -= 0.6f;
                if (slot == 0 && ch.role != Chassis.R_LINE && ch.role != Chassis.R_BRAWLER) fit -= 0.3f;
                fit += r.nextFloat() * 0.25f;
                if (fit > bestScore) {
                    bestScore = fit;
                    pick = u;
                }
            }
            if (pick == null) break;
            roleCount[pick.ch.role]++;
            remaining -= pick.value();
            out.add(pick);
        }
        if (out.isEmpty()) out.add(new Unit(Chassis.ALL[0]));
        return out;
    }

    /** Builds everything for the next battle and shows the briefing. */
    /** The next mission's map: the contract's region, the scenario's weather, river and town. */
    private HexMap missionMap(Company c, Random r) {
        Scenario sc = c.next();
        Weather wx = sc != null && sc.weather >= 0 ? new Weather(sc.weather) : Weather.random(c.biome, r);
        HexMap map = new HexMap(20, 13, c.biome, wx);
        if (sc != null) {
            map.forceRiver = sc.river;
            map.forceTown = sc.town;
        }
        map.generate(r);
        return map;
    }

    public void prepareMission() {
        Company c = company;
        Random r = new Random(c.seed);
        Scenario sc = c.next();
        HexMap map = missionMap(c, r);
        List<Unit> enemy;
        List<Unit> allies = new ArrayList<Unit>();
        boolean militia = false;
        if (sc != null && c.merc && c.job() != null) {
            // the target faction's own force, in its colors, with the scenario's garrisons on each side
            Merc.Contract k = c.job();
            boolean attacking = c.side == Scenario.ATTACKER;
            enemy = new ArrayList<Unit>(Merc.enemyForce(k, sc));
            allies.addAll(Scenario.garrison(attacking ? sc.attackerGarrison : sc.defenderGarrison));
            for (Unit g : Scenario.garrison(attacking ? sc.defenderGarrison : sc.attackerGarrison)) {
                g.paint = Faction.ALL[k.target].scheme;
                enemy.add(g);
            }
        } else if (sc != null && c.pvp && c.rival != null) {
            // two players: Player 2's chosen force, with each side's garrison
            boolean attacking = c.side == Scenario.ATTACKER;
            enemy = new ArrayList<Unit>(c.rival.deployed);
            allies.addAll(Scenario.garrison(attacking ? sc.attackerGarrison : sc.defenderGarrison));
            enemy.addAll(Scenario.garrison(attacking ? sc.defenderGarrison : sc.attackerGarrison));
        } else if (sc != null && c.rival != null) {
            militia = Rival.ensureForce(c.rival);
            boolean attacking = c.side == Scenario.ATTACKER;
            enemy = new ArrayList<Unit>(Rival.chooseForce(c.rival, c.deployedValue(), c.difficulty, sc, !attacking, c.planetBattles, r));
            allies.addAll(Scenario.garrison(attacking ? sc.attackerGarrison : sc.defenderGarrison));
            enemy.addAll(Scenario.garrison(attacking ? sc.defenderGarrison : sc.attackerGarrison));
        } else {
            enemy = makeEnemyForce(r);
        }
        BriefingScreen bs = new BriefingScreen(this, map, enemy, allies);
        bs.militia = militia;
        setScreen(bs);
    }

    public void startBattle(HexMap map, List<Unit> enemy) {
        startBattle(map, enemy, new ArrayList<Unit>());
    }

    public void startBattle(HexMap map, List<Unit> enemy, List<Unit> allies) {
        Company c = company;
        setScreen(new BattleScreen(this, newBattle(c, map, enemy, allies, new Random(c.seed ^ 0x5DEECE66DL), c.twoPlayer())));
    }

    private Battle newBattle(Company c, HexMap map, List<Unit> enemy, List<Unit> allies, Random dice, boolean twoPlayer) {
        List<Unit> mine = new ArrayList<Unit>(c.deployed);
        mine.addAll(allies);
        Battle b = new Battle(this, mine, enemy, map, dice, c.difficulty, twoPlayer);
        Scenario sc = c.next();
        if (sc != null) {
            boolean attacking = c.side == Scenario.ATTACKER;
            b.maxRounds = sc.rounds;
            b.playerInitiative = attacking ? sc.attackerInitiative : 1f - sc.attackerInitiative;
            b.timeEdge = attacking ? -sc.timeLean : sc.timeLean;
            if (c.edge >= 0) b.firstInitiative = c.edge == c.side ? Battle.PLAYER : Battle.ENEMY;
        }
        return b;
    }

    // ---------------- online: two players on their own devices ----------------

    /** The online game in progress, or null. */
    public Online online;

    /** Opens an online room for a quick battle with this budget each. */
    public void hostOnline(int budget) {
        leaveOnline();
        online = Online.hostGame(this, budget);
        setScreen(new OnlineScreen(this));
    }

    public void joinOnline(String code) {
        leaveOnline();
        online = Online.joinGame(this, code);
        setScreen(new OnlineScreen(this));
    }

    /** Leaves any online game (the other player is told). */
    public void leaveOnline() {
        if (online != null) {
            online.leave();
            online = null;
        }
    }

    /** A two-player campaign against a friend online: this device is Player 1, keeps the campaign and hosts. */
    public void newOnlineCampaign(int funds, int side) {
        newPvpCampaign(funds, side);
        company.onlineCampaign = true;
        saveCompany();
        hostOnlineCampaign();
    }

    /** Opens a room for the campaign in {@link #company} (Player 1's, with Player 2's as its rival). */
    public void hostOnlineCampaign() {
        leaveOnline();
        pvpMain = null;
        online = Online.hostCampaign(this);
        setScreen(new OnlineScreen(this));
    }

    public boolean saveIsOnlineCampaign() {
        Company c = Company.parse(platform.load("company"));
        return c != null && c.pvp && c.onlineCampaign;
    }

    /** Online campaign, host: Player 1's turn at the HQ (or the choice of the next planet), Player 2's on their device. */
    void onlineCampaignTurn() {
        Company c = campaignMain();
        pvpMain = null;
        company = c;
        c.fitDeployment();
        setScreen(c.over != 0 ? new CampaignEndScreen(this) : new HqScreen(this));
    }

    /** Online campaign, guest: the campaign as the host sent it; this device plays Player 2's company. */
    void onlineCampaignGuest(Company p1, Company p2) {
        p1.rival = p2;
        p2.rival = p1;
        p1.pvp = p2.pvp = true;
        pvpMain = p1;
        company = p2;
        setScreen(p1.over != 0 ? new CampaignEndScreen(this) : new HqScreen(this));
    }

    /** Online campaign, host: both forces are in; the battle on the scenario's map, with each side's garrison. */
    Battle onlineCampaignBattle() {
        Company c = campaignMain();
        pvpMain = null;
        company = c;
        Random r = new Random(c.seed);
        Scenario sc = c.next();
        HexMap map = missionMap(c, r);
        List<Unit> enemy = new ArrayList<Unit>(c.rival.deployed);
        List<Unit> allies = new ArrayList<Unit>();
        if (sc != null) {
            boolean attacking = c.side == Scenario.ATTACKER;
            allies.addAll(Scenario.garrison(attacking ? sc.attackerGarrison : sc.defenderGarrison));
            enemy.addAll(Scenario.garrison(attacking ? sc.defenderGarrison : sc.attackerGarrison));
        }
        return newBattle(c, map, enemy, allies, new Rng(c.seed ^ 0x5DEECE66DL), true);
    }

    /** Online: both players are in the room. Build a force with the host's budget, as in a quick battle. */
    void onlineBuild() {
        Company c = new Company();
        c.quick = true;
        c.hotseat = true;
        c.online = true;
        c.seatNo = online.team;
        c.difficulty = 1;
        c.cash = online.budget;
        c.rollContract(rng);
        if (online.team == Battle.ENEMY) c.scheme = Scheme.ENEMY_DEFAULT;   // Player 2 starts in red, as in hotseat
        company = c;
        online.mine = c;
        setScreen(new HqScreen(this));
    }

    /** Online host: a map for the two forces and the battle on it, with dice both devices can roll alike. */
    Battle onlineBattle(Company p1, List<Unit> theirs) {
        Random r = new Random(rng.nextLong());
        int biome = r.nextInt(HexMap.BIOME_NAMES.length);
        HexMap map = new HexMap(20, 13, biome, Weather.random(biome, r));
        map.generate(r);
        p1.biome = biome;
        return newBattle(p1, map, theirs, new ArrayList<Unit>(), new Rng(r.nextLong()), true);
    }

    /** No unit on the roster may fight in the next scenario: the mission counts as a loss without a battle. */
    public void forfeitMission() {
        Company c = company;
        Debrief d = new Debrief();
        d.result = Battle.RESULT_WITHDRAW;
        d.forfeit = true;
        d.pay = c.reward / 4;
        c.cash += d.pay;
        settle(d, Battle.RESULT_WITHDRAW, 0, null);
    }

    /** Settles money, salvage, and losses after a battle, then shows the debrief. */
    public void finishBattle(Battle b) {
        clearBattle();
        Company c = company;
        if (c.pvp && c.rival != null) {
            // two players: both companies are settled as the player's is
            Debrief d = settleSide(c, b, Battle.PLAYER, b.result);
            d.p2 = settleSide(c.rival, b, Battle.ENEMY, flip(b.result));
            settle(d, b.result, 0, b);
            return;
        }
        Debrief d = new Debrief();
        d.result = b.result;
        int pay;
        switch (b.result) {
            case Battle.RESULT_WIN: pay = c.reward; break;
            case Battle.RESULT_DRAW: pay = c.reward / 2; break;
            default: pay = c.reward / 4; break;
        }
        d.pay = pay;
        int destroyedValue = 0;
        for (Unit u : b.units) {
            if (u.team == Battle.ENEMY && u.destroyed) {
                destroyedValue += u.value();
                d.enemyLost.add(u.name);
                if (b.result == Battle.RESULT_WIN || b.result == Battle.RESULT_DRAW) {
                    // salvage: one intact weapon, sometimes a piece of equipment
                    List<Integer> intact = new ArrayList<Integer>();
                    for (int m = 0; m < u.weapon.length; m++) if (u.weapon[m] >= 0 && !u.wpnBroken[m]) intact.add(u.weapon[m]);
                    if (!intact.isEmpty() && b.rnd.nextFloat() < 0.6f) {
                        int wid = intact.get(b.rnd.nextInt(intact.size()));
                        c.armoryW[wid]++;
                        d.salvage.add(Wpn.ALL[wid].name);
                    }
                    for (int e = 0; e < u.equip.length; e++) {
                        if (u.equip[e] >= 0 && !u.eqBroken[e] && b.rnd.nextFloat() < 0.25f) {
                            c.armoryE[u.equip[e]]++;
                            d.salvage.add(Equip.ALL[u.equip[e]].name);
                            break;
                        }
                    }
                }
            }
        }
        if (b.result == Battle.RESULT_WIN) d.bonus = destroyedValue * 15 / 100;
        else if (b.result == Battle.RESULT_DRAW) d.bonus = destroyedValue * 8 / 100;
        c.cash += d.pay + d.bonus;
        boolean recover = b.result == Battle.RESULT_WIN || b.result == Battle.RESULT_DRAW;
        for (Unit u : new ArrayList<Unit>(c.deployed)) {
            u.shutdown = false;
            u.staggered = false;
            u.ionized = false;
            u.heat = 0;
            if (u.destroyed) {
                if (recover) d.wrecked.add(u.name);
                else {
                    d.lost.add(u.name);
                    c.roster.remove(u);
                    c.deployed.remove(u);
                }
            }
            if (u.destroyed) c.deployed.remove(u);
        }
        int mineLost = 0;
        for (Unit u : b.units) if (u.team == Battle.PLAYER && u.destroyed && u.tag == null) mineLost += u.value();
        Company rv = c.rival;
        if (!c.quick && rv != null) {
            boolean rivalHeld = b.result != Battle.RESULT_WIN;
            for (Unit u : new ArrayList<Unit>(rv.roster)) {
                u.shutdown = false;
                u.staggered = false;
                u.ionized = false;
                u.heat = 0;
                if (u.destroyed && !rivalHeld) {
                    rv.roster.remove(u);   // the field is yours: its wrecks are gone
                    d.rivalLost++;
                }
            }
        }
        settle(d, b.result, mineLost, b);
    }

    /** The campaign moves on: the front, the next scenario, the rival's pay and shopping, and the record. */
    private void settle(Debrief d, int result, int mineLost, Battle b) {
        Company c = company;
        Company rv = c.rival;
        int battle = c.planetBattles;
        if (c.merc && c.job() != null) {
            // standing with every faction moves; the contract leaves the board and a new one takes its place
            Merc.Contract k = c.job();
            int[] dr = Merc.repChanges(k, result);
            for (int f = 0; f < Faction.COUNT; f++) c.rep[f] = Math.max(Merc.REP_MIN, Math.min(Merc.REP_MAX, c.rep[f] + dr[f]));
            d.rep = dr;
            d.employer = k.employer;
            d.target = k.target;
            d.scenario = Scenario.get(k.scenario).name;
            c.contracts.remove(c.contract);
            c.contract = -1;
            Merc.fillBoard(c, rng);
            Merc.rollOffers(c, rng);
        } else if (!c.quick) {
            Scenario sc = c.next();
            int out = c.outcome(result);
            d.scenario = sc.name;
            d.outcome = out;
            d.frontBefore = c.front;
            if (sc.id == Scenario.BASE_ASSAULT && out == Scenario.OUT_ATTACKER) c.over = Scenario.OUT_ATTACKER;
            else if (sc.id == Scenario.EVACUATION && out == Scenario.OUT_DEFENDER) c.over = Scenario.OUT_DEFENDER;
            else c.front = sc.frontAfter(c.front, out);
            c.edge = sc.intel && (out == Scenario.OUT_ATTACKER || out == Scenario.OUT_DEFENDER)
                    ? (out == Scenario.OUT_ATTACKER ? Scenario.ATTACKER : Scenario.DEFENDER) : -1;
            c.lastScenario = sc.id;
            c.lastOutcome = out;
            c.planetBattles++;
            if (c.over == 0) c.scenario = Scenario.roll(c.front, out, sc.id, rng);
            d.frontAfter = c.front;
            d.over = c.over;
            boolean wonPlanet = c.over == (c.side == Scenario.ATTACKER ? Scenario.OUT_ATTACKER : Scenario.OUT_DEFENDER);
            if (c.over != 0 && wonPlanet) {
                d.campaignBonus = 3000 + 1500 * c.difficulty;
                c.cash += d.campaignBonus;
            } else if (c.over != 0 && c.pvp && rv != null && d.p2 != null) {
                d.p2.campaignBonus = 3000 + 1500 * c.difficulty;   // Player 2 took the planet
                rv.cash += d.p2.campaignBonus;
            }
        }
        if (!c.quick && rv != null && !c.pvp) {
            float mult = Rival.incomeMult(c.difficulty);
            // The rival's backers pay a base budget every contract, plus a bonus when it wins or draws.
            // Your contract pay carries your side's income rate; the rival is paid at its own side's rate.
            int base = c.reward;
            if (!c.quick) base = Math.round(base * Scenario.income(1 - c.side, battle) / Scenario.income(c.side, battle));
            int rpay = base * 4 / 5;
            switch (result) {
                case Battle.RESULT_LOSS:
                case Battle.RESULT_WITHDRAW: rpay += base * 3 / 5; break;
                case Battle.RESULT_DRAW: rpay += base * 3 / 10; break;
                default: break;
            }
            if (result != Battle.RESULT_WIN) rpay += mineLost * 15 / 100;
            rpay = Math.round(rpay * mult);
            rv.cash += rpay;
            d.rivalPay = rpay;
            rv.deployed.clear();
            d.rivalBought.addAll(Rival.upkeep(rv, rng, c.over == 0 ? c.next() : null));
            d.rivalCash = rv.cash;
            d.rivalUnits = rv.roster.size();
        }
        if (result == Battle.RESULT_WIN) c.wins++;
        else if (result == Battle.RESULT_LOSS || result == Battle.RESULT_WITHDRAW) c.losses++;
        c.mission++;
        c.rollContract(rng);
        c.fitDeployment();
        d.repairCost = c.repairAllCost();
        if (c.pvp && rv != null && d.p2 != null) {
            // Player 2's record and next pay; then Player 1 has the next turn at the HQ
            if (result == Battle.RESULT_WIN) rv.losses++;
            else if (result == Battle.RESULT_LOSS) rv.wins++;
            syncRival(c);
            rv.fitDeployment();
            d.p2.repairCost = rv.repairAllCost();
            c.pvpTurn = 0;
            pvpMain = null;
            saveCompany();
            if (online != null && online.campaign) online.campaignSettled(d);   // Player 2 sees it on their device
            setScreen(new PvpDebriefScreen(this, b, d));
            return;
        }
        saveCompany();
        setScreen(new DebriefScreen(this, b, d));
    }

    public void toHq() {
        setScreen(new HqScreen(this));
    }

    public void toMenu() {
        leaveOnline();
        setScreen(new MenuScreen(this));
    }

    public static final class Debrief {
        public int result, pay, bonus, repairCost;
        public final List<String> enemyLost = new ArrayList<String>();
        public final List<String> salvage = new ArrayList<String>();
        public final List<String> lost = new ArrayList<String>();
        public final List<String> wrecked = new ArrayList<String>();
        public int rivalPay, rivalLost, rivalCash, rivalUnits;
        // campaign
        public boolean forfeit;
        public String scenario;
        public int outcome, frontBefore, frontAfter, over, campaignBonus;
        // mercenary play: standing changes by faction, and who the job was for and against
        public int[] rep;
        public int employer = -1, target = -1;
        // two-player campaign: Player 2's share, and who forfeited (if a player had nothing that could fight)
        public Debrief p2;
        public int forfeitTeam = -1;

        /** One player's side of a two-player debrief as one line of text (an online campaign sends it to Player 2). */
        String serialize() {
            StringBuilder b = new StringBuilder();
            int[] v = {result, pay, bonus, repairCost, campaignBonus, forfeit ? 1 : 0, forfeitTeam, outcome, frontBefore, frontAfter, over};
            for (int i = 0; i < v.length; i++) b.append(i > 0 ? "," : "").append(v[i]);
            b.append('\t').append(scenario == null ? "" : scenario.replace('\t', ' '));
            for (List<String> l : new List[]{salvage, lost, wrecked, enemyLost}) {
                b.append('\t');
                for (int i = 0; i < l.size(); i++) b.append(i > 0 ? "|" : "").append(l.get(i).replace('|', '/').replace('\t', ' '));
            }
            return b.toString();
        }

        static Debrief parse(String s) {
            String[] f = Text.split(s, '\t', true);
            String[] v = Text.split(f[0], ',');
            Debrief d = new Debrief();
            d.result = Integer.parseInt(v[0]);
            d.pay = Integer.parseInt(v[1]);
            d.bonus = Integer.parseInt(v[2]);
            d.repairCost = Integer.parseInt(v[3]);
            d.campaignBonus = Integer.parseInt(v[4]);
            d.forfeit = v[5].equals("1");
            d.forfeitTeam = Integer.parseInt(v[6]);
            d.outcome = Integer.parseInt(v[7]);
            d.frontBefore = Integer.parseInt(v[8]);
            d.frontAfter = Integer.parseInt(v[9]);
            d.over = Integer.parseInt(v[10]);
            d.scenario = f[1].length() == 0 ? null : f[1];
            List[] lists = {d.salvage, d.lost, d.wrecked, d.enemyLost};
            for (int k = 0; k < lists.length; k++) {
                if (f.length > 2 + k && f[2 + k].length() > 0) for (String x : Text.split(f[2 + k], '|')) lists[k].add(x);
            }
            return d;
        }
        public final List<String> rivalBought = new ArrayList<String>();
    }
}
