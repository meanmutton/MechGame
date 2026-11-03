package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Top level: current screen, input queue, settings, the company, and the flow between screens. */
public final class Game {
    public static final int TOUCH_DOWN = 0, TOUCH_MOVE = 1, TOUCH_UP = 2, BACK = 3;
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
        this.screen = new MenuScreen(this);
    }

    public Screen screen() {
        return screen;
    }

    public void setScreen(Screen s) {
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

    public void touch(int type, int pointer, float x, float y) {
        synchronized (input) {
            input.add(new float[]{type, pointer, x, y});
        }
    }

    public void tick(float dt, int width, int height) {
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
                    case BACK:
                        if (!s.closeOverlay() && !s.back()) platform.exit();
                        break;
                }
            }
        }
        time += dt;
        screen.update(dt);
    }

    public void render(Gfx g) {
        screen.resize(w, h);
        screen.render(g);
    }

    public void sfx(int id, float vol) {
        if (settings.sound && !headless) platform.play(id, vol);
    }

    public void saveSettings() {
        platform.save("settings", settings.serialize());
    }

    public void saveCompany() {
        if (company != null && !company.quick) {
            platform.save("company", company.serialize());
            if (company.rival != null) platform.save("rival", company.rival.serialize());
        }
    }

    public boolean hasSave() {
        return Company.parse(platform.load("company")) != null;
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
        Company c = company;
        c.over = 0;
        c.side = side;
        c.front = Scenario.FRONT_START;
        c.scenario = Scenario.INITIAL_DROP;
        c.lastScenario = -1;
        c.lastOutcome = Scenario.OUT_NONE;
        c.edge = -1;
        c.planets++;
        c.planetBattles = 0;
        int worth = c.cash;
        for (Unit u : c.roster) worth += u.value();
        // a fresh enemy matched to your company's worth, scaled by which side has the bigger starting purse
        c.rival = Rival.create(c.difficulty, Math.round(worth * Scenario.startFunds(1 - side) / Scenario.startFunds(side)), rng);
        c.rollContract(rng);
        c.fitDeployment();
        saveCompany();
        setScreen(new HqScreen(this));
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
    public void prepareMission() {
        Company c = company;
        Random r = new Random(c.seed);
        Scenario sc = c.next();
        Weather wx = sc != null && sc.weather >= 0 ? new Weather(sc.weather) : Weather.random(c.biome, r);
        HexMap map = new HexMap(20, 13, c.biome, wx);
        if (sc != null) {
            map.forceRiver = sc.river;
            map.forceTown = sc.town;
        }
        map.generate(r);
        List<Unit> enemy;
        List<Unit> allies = new ArrayList<Unit>();
        boolean militia = false;
        if (sc != null && c.rival != null) {
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
        List<Unit> mine = new ArrayList<Unit>(c.deployed);
        mine.addAll(allies);
        Battle b = new Battle(this, mine, enemy, map, new Random(c.seed ^ 0x5DEECE66DL), c.difficulty);
        Scenario sc = c.next();
        if (sc != null) {
            boolean attacking = c.side == Scenario.ATTACKER;
            b.maxRounds = sc.rounds;
            b.playerInitiative = attacking ? sc.attackerInitiative : 1f - sc.attackerInitiative;
            b.timeEdge = attacking ? -sc.timeLean : sc.timeLean;
            if (c.edge >= 0) b.firstInitiative = c.edge == c.side ? Battle.PLAYER : Battle.ENEMY;
        }
        setScreen(new BattleScreen(this, b));
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
        if (!c.quick) {
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
            }
        }
        if (!c.quick && rv != null) {
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
        saveCompany();
        setScreen(new DebriefScreen(this, b, d));
    }

    public void toHq() {
        setScreen(new HqScreen(this));
    }

    public void toMenu() {
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
        public final List<String> rivalBought = new ArrayList<String>();
    }
}
