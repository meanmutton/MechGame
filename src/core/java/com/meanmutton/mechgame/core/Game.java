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
                        if (!s.back()) platform.exit();
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
        if (company != null && !company.quick) platform.save("company", company.serialize());
    }

    public boolean hasSave() {
        return Company.parse(platform.load("company")) != null;
    }

    // =====================================================================
    // Flow
    // =====================================================================

    public void newCampaign(int difficulty, int funds) {
        company = new Company();
        company.difficulty = difficulty;
        company.cash = funds;
        company.rollContract(rng);
        saveCompany();
        setScreen(new HqScreen(this));
    }

    public void quickBattle(int difficulty, int budget) {
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
        setScreen(new HqScreen(this));
    }

    /** The enemy force for the next mission, built to a budget from stock designs. */
    public List<Unit> makeEnemyForce(Random r) {
        Company c = company;
        float mult = c.difficulty == 0 ? 0.75f : (c.difficulty == 1 ? 0.95f : 1.2f);
        if (!c.quick) mult *= 1f + Math.min(0.5f, (c.mission - 1) * 0.04f);
        int budget = Math.max(1500, Math.round(c.deployedValue() * mult));
        int count = Math.min(Company.MAX_DEPLOY, Math.max(2, c.deployed.size() + (c.difficulty == 2 ? 1 : 0) + (r.nextBoolean() ? 1 : 0) - 1));
        List<Unit> out = new ArrayList<Unit>();
        int[] roleCount = new int[Chassis.ROLES.length];
        int remaining = budget;
        for (int slot = 0; slot < count; slot++) {
            int per = remaining / (count - slot);
            Unit pick = null;
            float bestScore = -1;
            for (int tries = 0; tries < 60; tries++) {
                Chassis ch = Chassis.ALL[r.nextInt(Chassis.ALL.length)];
                Unit u = new Unit(ch);
                int v = u.value();
                if (v > remaining) continue;
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
        Random r = new Random(company.seed);
        Weather wx = Weather.random(company.biome, r);
        HexMap map = new HexMap(20, 13, company.biome, wx);
        map.generate(r);
        List<Unit> enemy = makeEnemyForce(r);
        setScreen(new BriefingScreen(this, map, enemy));
    }

    public void startBattle(HexMap map, List<Unit> enemy) {
        List<Unit> mine = new ArrayList<Unit>(company.deployed);
        Battle b = new Battle(this, mine, enemy, map, new Random(company.seed ^ 0x5DEECE66DL), company.difficulty);
        setScreen(new BattleScreen(this, b));
    }

    /** Settles money, salvage, and losses after a battle, then shows the debrief. */
    public void finishBattle(Battle b) {
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
        if (b.result == Battle.RESULT_WIN) c.wins++;
        else if (b.result == Battle.RESULT_LOSS || b.result == Battle.RESULT_WITHDRAW) c.losses++;
        c.mission++;
        c.rollContract(rng);
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
    }
}
