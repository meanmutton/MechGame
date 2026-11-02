package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/** The battlefield: map on the left (pan and pinch-zoom), unit panel on the right. */
public final class BattleScreen extends Screen {
    private static final int END = 1, FIRE = 2, TURBO = 3, ROT_L = 4, ROT_R = 5, UNDO = 6, SMOKE = 7, COOLANT = 8,
            REPAIR = 9, PAUSE = 10, CANCEL = 11, NEXT = 12, WROW = 100;
    private static final int P_RESUME = 201, P_WITHDRAW = 202, P_SOUND = 203, P_SPEED = 204, P_QUIT = 205, P_CONFIRM = 206;

    private final Battle b;
    private float t;
    // camera
    private float cs, fit, camX, camY, mapW, topBar, panelX, panelW;
    private boolean camInit;
    // gestures
    private final int[] pid = {-1, -1};
    private final float[] px = new float[2], py = new float[2];
    private float downX, downY, pinchDist, pinchScale;
    private boolean dragging, pinching;
    // selection
    private Unit view, target;
    private boolean[] wsel = new boolean[0];
    private float[] reach;
    private int[] prev;
    private int reachFor = -1;   // hash of state the reach was computed for
    private int pendingDest = -1;
    private int[] pendingPath;
    private int infoHex = -1;
    private float endTimer;
    private boolean finished;
    // overlay
    private int pauseMode; // 0 none, 1 pause, 2 confirm withdraw
    private final List<Btn> overlay = new ArrayList<Btn>();
    private Btn bEnd, bFire, bTurbo, bRotL, bRotR, bUndo, bSmoke, bCool, bRepair, bNext, bCancel;
    private float dollX, dollY, dollW, dollH, listY, rowH;
    private float flash;

    public BattleScreen(Game game, Battle b) {
        super(game);
        this.b = b;
    }

    public Battle battle() {
        return b;
    }

    // =====================================================================
    // Layout
    // =====================================================================

    protected void layout() {
        panelW = Math.max(w * 0.31f, u * 62);
        panelX = w - panelW;
        mapW = panelX;
        topBar = u * 8;
        float width = 1.5f * (b.map.cols - 1) + 2, height = 1.732f * (b.map.rows + 0.5f);
        float newFit = Math.min(mapW / width, (h - topBar) / height);
        if (!camInit || Math.abs(newFit - fit) > 0.01f) {
            fit = newFit;
            boolean first = !camInit;
            cs = fit;
            camX = (mapW - width * cs) / 2 + cs;
            camY = topBar + (h - topBar - height * cs) / 2 + cs * 0.866f;
            camInit = true;
            if (first) {
                // start zoomed in a little, showing your own deployment zone on the left
                cs = fit * 1.3f;
                camX = u * 2 + cs;
                camY = topBar + (h - topBar - height * cs) / 2 + cs * 0.866f;
                clampCam();
            }
        }
        buttons.clear();
        float x0 = panelX + u * 2, pw = panelW - u * 4;
        dollX = x0;
        dollY = u * 11;
        dollW = Math.min(pw * 0.46f, h * 0.3f);
        dollH = dollW;
        listY = dollY + dollH + u * 2;
        Unit su = panelUnit();
        int nw = su == null ? 0 : su.weapon.length;
        float btnArea = u * 24;
        rowH = nw == 0 ? 0 : Math.min(u * 6.4f, (h - listY - btnArea - u * 8) / nw);
        for (int m = 0; m < nw; m++) {
            Btn r = add(new Btn(WROW + m, "").set(x0, listY + m * rowH, pw, rowH - u * 0.5f));
            r.color = 0xFF1A2434;
        }
        float by2 = h - u * 11, by1 = by2 - u * 11;
        float bw5 = (pw - u * 4) / 5;
        bTurbo = add(new Btn(TURBO, "Turbo").set(x0, by1, bw5, u * 9.5f));
        bSmoke = add(new Btn(SMOKE, "Smoke").set(x0 + (bw5 + u), by1, bw5, u * 9.5f));
        bRotL = add(new Btn(ROT_L, "↺").set(x0 + 2 * (bw5 + u), by1, bw5, u * 9.5f));
        bRotR = add(new Btn(ROT_R, "↻").set(x0 + 3 * (bw5 + u), by1, bw5, u * 9.5f));
        bUndo = add(new Btn(UNDO, "Undo").set(x0 + 4 * (bw5 + u), by1, bw5, u * 9.5f));
        bCool = add(new Btn(COOLANT, "Coolant").set(x0 + (bw5 + u), by1, bw5, u * 9.5f));
        bRepair = add(new Btn(REPAIR, "Patch").set(x0 + (bw5 + u), by1, bw5, u * 9.5f));
        float bw2 = (pw - u) / 2;
        bFire = add(new Btn(FIRE, "FIRE").set(x0, by2, bw2, u * 9.5f));
        bFire.color = 0xFFB8322A;
        bEnd = add(new Btn(END, "End Turn").set(x0 + bw2 + u, by2, bw2, u * 9.5f));
        bEnd.color = 0xFF2E5D8F;
        bNext = add(new Btn(NEXT, "Next Unit").set(x0, by2, bw2, u * 9.5f));
        bCancel = add(new Btn(CANCEL, "Cancel").set(x0, by2, bw2, u * 9.5f));
        for (Btn bb : buttons) if (bb.id < WROW) bb.textSize = u * 3f;
        bRotL.textSize = bRotR.textSize = u * 4.5f;
        add(new Btn(PAUSE, "II").set(mapW - u * 9, u * 0.8f, u * 7.5f, u * 6.5f)).color = 0xAA1A2232;
        layoutOverlay();
        refreshButtons();
    }

    private void layoutOverlay() {
        overlay.clear();
        if (pauseMode == 0) return;
        float bw = u * 52, bh = u * 9.5f, x = (w - bw) / 2, y = h * 0.22f;
        if (pauseMode == 1) {
            overlay.add(new Btn(P_RESUME, "Resume").set(x, y, bw, bh).tint(0xFF2E7D4F));
            overlay.add(new Btn(P_SOUND, "Sound: " + (game.settings.sound ? "On" : "Off")).set(x, y + bh * 1.2f, bw, bh));
            overlay.add(new Btn(P_SPEED, "Enemy turns: " + Settings.SPEED_NAMES[game.settings.aiSpeed]).set(x, y + bh * 2.4f, bw, bh));
            overlay.add(new Btn(P_WITHDRAW, "Withdraw from battle").set(x, y + bh * 3.6f, bw, bh).tint(0xFF8F5A2E));
            overlay.add(new Btn(P_QUIT, "Quit to menu (battle lost unsaved)").set(x, y + bh * 4.8f, bw, bh).tint(0xFF7A2A2A));
        } else {
            overlay.add(new Btn(P_CONFIRM, "Yes, withdraw").set(w / 2f - u * 32, h * 0.55f, u * 30, bh).tint(0xFF8F3A2E));
            overlay.add(new Btn(P_RESUME, "Keep fighting").set(w / 2f + u * 2, h * 0.55f, u * 30, bh).tint(0xFF2E7D4F));
        }
    }

    protected List<Btn> activeButtons() {
        return pauseMode != 0 ? overlay : buttons;
    }

    private Unit panelUnit() {
        if (b.active != null) return b.active;
        return view;
    }

    private boolean myActivation() {
        return b.playerTurn() && b.active != null && b.active.team == Battle.PLAYER && !b.busy();
    }

    private void refreshButtons() {
        Unit a = b.active;
        boolean mine = myActivation();
        boolean targeting = mine && target != null;
        for (Btn bb : buttons) {
            if (bb.id >= WROW) {
                int m = bb.id - WROW;
                bb.enabled = targeting && a != null && m < a.weapon.length && b.hitChance(a, m, target, null) >= 0;
                bb.selected = bb.enabled && m < wsel.length && wsel[m];
            }
        }
        bTurbo.visible = mine && a.equipSlot(Equip.TURBO) >= 0;
        bTurbo.enabled = mine && b.canTurbo(a);
        if (bTurbo.visible) bTurbo.sub = a.eqUses[a.equipSlot(Equip.TURBO)] + " left";
        bSmoke.visible = mine && a.equipSlot(Equip.SMOKE) >= 0;
        bSmoke.enabled = mine && b.canUse(a, Equip.SMOKE);
        bCool.visible = mine && !bSmoke.visible && a.equipSlot(Equip.COOLANT) >= 0;
        bCool.enabled = mine && b.canUse(a, Equip.COOLANT);
        bRepair.visible = mine && !bSmoke.visible && !bCool.visible && a.equipSlot(Equip.REPAIR_KIT) >= 0;
        bRepair.enabled = mine && b.canUse(a, Equip.REPAIR_KIT);
        bRotL.visible = bRotR.visible = mine;
        bUndo.visible = mine;
        bUndo.enabled = mine && b.canUndo(a);
        boolean anySel = false;
        for (int m = 0; m < wsel.length; m++) if (wsel[m]) anySel = true;
        bFire.visible = targeting;
        bFire.enabled = targeting && anySel && !a.fired;
        bEnd.visible = mine;
        bEnd.label = a != null && !a.moved && !a.fired ? "Hold / Skip" : "End Turn";
        bCancel.visible = false;
        bNext.visible = b.playerTurn() && b.active == null && !b.busy();
        bEnd.enabled = mine;
    }

    // =====================================================================
    // Update
    // =====================================================================

    public void update(float dt) {
        super.update(dt);
        t += dt;
        if (flash > 0) flash -= dt;
        if (pauseMode != 0) return;
        b.update(dt);
        if (b.active != null && b.active.team == Battle.ENEMY) view = b.active;
        if (target != null && (target.destroyed || b.active == null || b.active.team != Battle.PLAYER)) target = null;
        if (b.result != Battle.RESULT_NONE && !b.busy()) {
            endTimer += dt;
            if (endTimer > 2f && !finished) {
                finished = true;
                game.finishBattle(b);
                return;
            }
        }
        // the panel layout depends on which unit is shown
        Unit pu = panelUnit();
        int nw = pu == null ? 0 : pu.weapon.length;
        int rows = 0;
        for (Btn bb : buttons) if (bb.id >= WROW) rows++;
        if (rows != nw) layoutKeepCamera();
        refreshButtons();
    }

    private void layoutKeepCamera() {
        int ow = w;
        w = -1;
        resize(ow, h);
    }

    private void computeReach() {
        Unit a = b.active;
        if (a == null) {
            reach = null;
            return;
        }
        int key = a.q * 1000 + a.r * 10 + (a.turboOn ? 1 : 0) + (a.moved ? 5 : 0) + a.hashCode() * 31;
        if (reach != null && key == reachFor) return;
        reachFor = key;
        prev = new int[b.map.cols * b.map.rows];
        reach = a.moved ? null : b.reach(a, prev, b.mpAvailable(a));
    }

    // =====================================================================
    // Input
    // =====================================================================

    protected void onClick(Btn bt) {
        Unit a = b.active;
        switch (bt.id) {
            case PAUSE: pauseMode = 1; layoutOverlay(); return;
            case P_RESUME: pauseMode = 0; return;
            case P_SOUND: game.settings.sound = !game.settings.sound; game.saveSettings(); layoutOverlay(); return;
            case P_SPEED: game.settings.aiSpeed = (game.settings.aiSpeed + 1) % 3; game.saveSettings(); layoutOverlay(); return;
            case P_WITHDRAW: pauseMode = 2; layoutOverlay(); return;
            case P_CONFIRM: pauseMode = 0; b.withdraw(); endTimer = 1.5f; return;
            case P_QUIT: game.toMenu(); return;
            case NEXT: selectNextReady(); return;
            default: break;
        }
        if (!myActivation()) return;
        switch (bt.id) {
            case END:
                b.endActivation(a);
                target = null;
                pendingDest = -1;
                reach = null;
                return;
            case FIRE:
                if (target != null) {
                    b.fire(a, target, wsel);
                    flash = 0.15f;
                }
                return;
            case TURBO: b.turbo(a); reach = null; pendingDest = -1; return;
            case SMOKE: b.useEquip(a, Equip.SMOKE); return;
            case COOLANT: b.useEquip(a, Equip.COOLANT); return;
            case REPAIR: b.useEquip(a, Equip.REPAIR_KIT); return;
            case ROT_L: b.rotate(a, -1); recomputeSelection(); return;
            case ROT_R: b.rotate(a, 1); recomputeSelection(); return;
            case UNDO: b.undoMove(a); reach = null; pendingDest = -1; recomputeSelection(); return;
            default:
                if (bt.id >= WROW && bt.id < WROW + 50) {
                    int m = bt.id - WROW;
                    if (m < wsel.length) wsel[m] = !wsel[m];
                }
        }
    }

    private void selectNextReady() {
        if (!b.playerTurn() || b.active != null) return;
        for (Unit un : b.units) {
            if (un.team == Battle.PLAYER && !un.destroyed && !un.activated) {
                activate(un);
                centerOn(un);
                return;
            }
        }
    }

    private void activate(Unit un) {
        b.beginActivation(un);
        view = un;
        target = null;
        pendingDest = -1;
        reach = null;
        wsel = new boolean[un.weapon.length];
        layoutKeepCamera();
    }

    private void centerOn(Unit un) {
        float sx = camX + HexMap.px(un.q, un.r) * cs, sy = camY + HexMap.py(un.q, un.r) * cs;
        if (sx < mapW * 0.15f || sx > mapW * 0.85f || sy < h * 0.15f || sy > h * 0.85f) {
            camX += mapW / 2 - sx;
            camY += h / 2 - sy;
            clampCam();
        }
    }

    /** Default weapon picks for a new target: everything that can fire, within a sensible heat budget. */
    private void recomputeSelection() {
        Unit a = b.active;
        if (a == null || target == null) return;
        wsel = new boolean[a.weapon.length];
        float budget = a.ch.heatCap * 0.9f - a.heat;
        boolean melee = false;
        Integer[] order = new Integer[a.weapon.length];
        final float[] e = new float[a.weapon.length];
        for (int m = 0; m < order.length; m++) {
            order[m] = m;
            e[m] = b.expected(a, m, target);
        }
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) {
                return Float.compare(e[y], e[x]);
            }
        });
        for (int m : order) {
            Wpn w = a.wpn(m);
            if (w == null || b.hitChance(a, m, target, null) < 0) continue;
            if (w.isMelee()) {
                if (melee) continue;
                melee = true;
            }
            if (a.type != UnitType.INFANTRY && w.heat > budget) continue;
            budget -= a.type == UnitType.INFANTRY ? 0 : w.heat;
            wsel[m] = true;
        }
    }

    private void tapHex(int c, int r) {
        Unit at = b.unitAt(c, r);
        int k = b.map.idx(c, r);
        infoHex = -1;
        if (b.busy()) {
            if (at != null) view = at;
            return;
        }
        if (b.playerTurn()) {
            Unit a = b.active;
            if (at != null && at.team == Battle.PLAYER) {
                if (a == null && !at.activated) {
                    activate(at);
                    return;
                }
                if (a != null && a != at && !at.activated && !a.moved && !a.fired && !a.turboOn) {
                    // switch to a different unit before doing anything
                    b.active = null;
                    activate(at);
                    return;
                }
                view = at;
                return;
            }
            if (at != null && at.team == Battle.ENEMY) {
                if (a != null && !a.fired) {
                    target = at;
                    recomputeSelection();
                    layoutKeepCamera();
                } else view = at;
                return;
            }
            if (a != null && !a.moved) {
                computeReach();
                if (reach != null && reach[k] < Float.MAX_VALUE && k != b.map.idx(a.q, a.r)) {
                    if (pendingDest == k) {
                        b.move(a, pendingPath);
                        pendingDest = -1;
                        reach = null;
                        if (target != null) recomputeSelection();
                    } else {
                        pendingDest = k;
                        pendingPath = b.pathTo(prev, b.map.idx(a.q, a.r), k);
                    }
                    return;
                }
            }
        } else if (at != null) {
            view = at;
            return;
        }
        pendingDest = -1;
        infoHex = k;
    }

    protected void onTouchDown(int pointer, float x, float y) {
        if (pauseMode != 0) return;
        if (pid[0] < 0) {
            if (x >= mapW) return;
            pid[0] = pointer;
            px[0] = x;
            py[0] = y;
            downX = x;
            downY = y;
            dragging = false;
        } else if (pid[1] < 0) {
            pid[1] = pointer;
            px[1] = x;
            py[1] = y;
            pinching = true;
            pinchDist = dist();
            pinchScale = cs;
        }
    }

    private float dist() {
        float dx = px[0] - px[1], dy = py[0] - py[1];
        return (float) Math.sqrt(dx * dx + dy * dy) + 1;
    }

    protected void onTouchMove(int pointer, float x, float y) {
        int i = pointer == pid[0] ? 0 : (pointer == pid[1] ? 1 : -1);
        if (i < 0) return;
        float ox = px[i], oy = py[i];
        px[i] = x;
        py[i] = y;
        if (pinching && pid[1] >= 0) {
            float mx = (px[0] + px[1]) / 2, my = (py[0] + py[1]) / 2;
            float ns = Math.max(fit, Math.min(fit * 2.8f, pinchScale * dist() / pinchDist));
            // zoom around the midpoint
            float wx = (mx - camX) / cs, wy = (my - camY) / cs;
            cs = ns;
            camX = mx - wx * cs;
            camY = my - wy * cs;
            if (i == 0) {
                camX += (x - ox) / 2;
                camY += (y - oy) / 2;
            }
            clampCam();
            dragging = true;
            return;
        }
        if (i == 0) {
            if (!dragging && Math.hypot(x - downX, y - downY) > u * 2.2f) dragging = true;
            if (dragging) {
                camX += x - ox;
                camY += y - oy;
                clampCam();
            }
        }
    }

    protected void onTouchUp(int pointer, float x, float y) {
        if (pointer == pid[1]) {
            pid[1] = -1;
            return;
        }
        if (pointer == pid[0]) {
            pid[0] = -1;
            if (pid[1] >= 0) {
                // promote the second finger
                pid[0] = pid[1];
                px[0] = px[1];
                py[0] = py[1];
                pid[1] = -1;
                return;
            }
            boolean wasPinch = pinching;
            pinching = false;
            if (!dragging && !wasPinch && x < mapW && y > topBar) {
                int[] hex = screenToHex(x, y);
                if (hex != null) tapHex(hex[0], hex[1]);
            }
            dragging = false;
        }
    }

    private void clampCam() {
        float width = (1.5f * (b.map.cols - 1) + 2) * cs, height = 1.732f * (b.map.rows + 0.5f) * cs;
        float margin = u * 4;
        float minX = mapW - width + cs - margin, maxX = cs + margin;
        float minY = h - height + cs * 0.866f - margin, maxY = topBar + cs * 0.866f + margin;
        if (width < mapW) minX = maxX = (mapW - width) / 2 + cs;
        if (height < h - topBar) minY = maxY = topBar + (h - topBar - height) / 2 + cs * 0.866f;
        camX = Math.max(minX, Math.min(maxX, camX));
        camY = Math.max(minY, Math.min(maxY, camY));
    }

    private int[] screenToHex(float sx, float sy) {
        float mx = (sx - camX) / cs, my = (sy - camY) / cs;
        int c0 = Math.round(mx / 1.5f);
        int[] best = null;
        float bd = Float.MAX_VALUE;
        for (int c = c0 - 1; c <= c0 + 1; c++) {
            int r0 = Math.round(my / 1.732f - 0.5f * (c & 1));
            for (int r = r0 - 1; r <= r0 + 1; r++) {
                if (!b.map.in(c, r)) continue;
                float dx = HexMap.px(c, r) - mx, dy = HexMap.py(c, r) - my;
                float d = dx * dx + dy * dy;
                if (d < bd) {
                    bd = d;
                    best = new int[]{c, r};
                }
            }
        }
        return bd < 1.1f ? best : null;
    }

    public boolean back() {
        if (pauseMode != 0) pauseMode = 0;
        else if (target != null) target = null;
        else {
            pauseMode = 1;
            layoutOverlay();
        }
        return true;
    }

    // =====================================================================
    // Rendering
    // =====================================================================

    public void render(Gfx g) {
        g.fillRect(0, 0, w, h, 0xFF070B12);
        g.save();
        g.clipRect(0, 0, mapW, h);
        g.translate(camX, camY);
        g.scale(cs, cs);
        HexRender.draw(g, b.map, t, cs > u * 5);
        renderOverlays(g);
        renderUnits(g);
        renderFx(g);
        g.restore();
        renderWeather(g);
        renderTop(g);
        renderLog(g);
        renderPanel(g);
        if (b.result != Battle.RESULT_NONE && !b.busy()) {
            String[] titles = {"", "VICTORY", "DEFEAT", "DRAW", "WITHDRAWING"};
            float k = Math.min(1f, endTimer * 2);
            g.fillRect(0, h * 0.38f, mapW, h * 0.16f, Col.alpha(0xCC000000, k));
            g.text(titles[b.result], mapW / 2, h * 0.46f, u * 9, Col.alpha(b.result == Battle.RESULT_WIN ? Col.GOOD : Col.ACCENT, k), Gfx.ALIGN_CENTER, true);
        }
        if (flash > 0) g.fillRect(0, 0, mapW, h, Col.alpha(0xFFFFFFFF, flash));
        if (pauseMode != 0) {
            g.fillRect(0, 0, w, h, 0xB0000000);
            if (pauseMode == 1) Ui.title(g, "PAUSED", w / 2f, h * 0.12f, u * 8);
            else {
                Ui.panel(g, w * 0.2f, h * 0.25f, w * 0.6f, h * 0.48f, u * 3);
                g.text("Withdraw from this battle?", w / 2f, h * 0.35f, u * 4.5f, Col.ACCENT, Gfx.ALIGN_CENTER, true);
                g.text("It counts as a loss. Units already destroyed are lost; the rest come home.", w / 2f, h * 0.44f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
            }
            for (Btn bb : overlay) Ui.button(g, bb, u);
        }
    }

    private void renderOverlays(Gfx g) {
        Unit a = b.active;
        boolean mine = myActivation();
        if (mine && !a.moved) {
            computeReach();
            if (reach != null) {
                for (int k = 0; k < reach.length; k++) {
                    if (reach[k] == Float.MAX_VALUE) continue;
                    int c = k % b.map.cols, r = k / b.map.cols;
                    if (c == a.q && r == a.r) continue;
                    HexRender.fillHex(g, c, r, 0.9f, 0x3366FF88);
                }
            }
        }
        if (mine && pendingDest >= 0 && pendingPath != null) {
            float lx = HexMap.px(a.q, a.r), ly = HexMap.py(a.q, a.r);
            for (int k : pendingPath) {
                float nx = HexMap.px(k % b.map.cols, k / b.map.cols), ny = HexMap.py(k % b.map.cols, k / b.map.cols);
                g.line(lx, ly, nx, ny, 0.12f, 0xCCFFFFFF);
                lx = nx;
                ly = ny;
            }
            int c = pendingDest % b.map.cols, r = pendingDest / b.map.cols;
            HexRender.strokeHex(g, c, r, 0.92f, 0.08f, 0xFFFFFFFF);
            g.fillRoundRect(HexMap.px(c, r) - 1.5f, HexMap.py(c, r) + 1.02f, 3f, 0.5f, 0.15f, 0xCC000000);
            g.text("tap again to move", HexMap.px(c, r), HexMap.py(c, r) + 1.27f, 0.36f, 0xFFFFFFFF, Gfx.ALIGN_CENTER, true);
        }
        // ready units pulse
        if (b.playerTurn() && b.active == null && !b.busy()) {
            float p = 0.5f + 0.5f * (float) Math.sin(t * 5);
            for (Unit un : b.units) {
                if (un.team == Battle.PLAYER && !un.destroyed && !un.activated) {
                    HexRender.strokeHex(g, un.q, un.r, 0.95f, 0.07f, Col.alpha(0xFF7AD8FF, 0.4f + 0.5f * p));
                }
            }
        }
        if (a != null && !a.destroyed) {
            float p = 0.6f + 0.4f * (float) Math.sin(t * 6);
            HexRender.strokeHex(g, a.q, a.r, 0.97f, 0.1f, Col.alpha(Col.ACCENT, p));
            // targetable enemies
            if (mine && !a.fired) {
                for (Unit e : b.units) {
                    if (e.destroyed || e.team == a.team) continue;
                    boolean can = false;
                    for (int m = 0; m < a.weapon.length && !can; m++) if (b.hitChance(a, m, e, null) >= 0) can = true;
                    if (can) HexRender.strokeHex(g, e.q, e.r, 0.9f, 0.06f, 0x99FF5A4A);
                }
            }
        }
        if (target != null && a != null) {
            float p = 0.6f + 0.4f * (float) Math.sin(t * 8);
            HexRender.strokeHex(g, target.q, target.r, 0.97f, 0.12f, Col.alpha(0xFFFF4A3A, p));
            boolean sees = b.losPoints(a, target) >= 0;
            g.line(HexMap.px(a.q, a.r), HexMap.py(a.q, a.r), HexMap.px(target.q, target.r), HexMap.py(target.q, target.r), 0.05f,
                    sees ? 0x99FFFFFF : 0x66FF9A3A);
        }
        if (infoHex >= 0) {
            int c = infoHex % b.map.cols, r = infoHex / b.map.cols;
            HexRender.strokeHex(g, c, r, 0.95f, 0.06f, 0xCCFFFFFF);
        }
    }

    private void renderUnits(Gfx g) {
        // wrecks
        for (Unit un : b.units) {
            if (!un.destroyed || !un.deathShown) continue;
            float x = HexMap.px(un.q, un.r), y = HexMap.py(un.q, un.r);
            if (b.unitAt(un.q, un.r) != null) continue;
            g.fillCircle(x, y, 0.55f, 0x66101010);
            for (int i = 0; i < 5; i++) {
                float a = i * 1.3f + un.ch.id;
                g.fillRect(x + (float) Math.cos(a) * 0.3f - 0.07f, y + (float) Math.sin(a) * 0.3f - 0.05f, 0.14f, 0.1f, 0xFF2A2A2A);
            }
            float sm = (t * 0.6f + un.ch.id) % 2f;
            g.fillCircle(x + sm * 0.2f, y - sm * 0.6f, 0.15f + sm * 0.2f, Col.alpha(0xFF555555, 0.5f * (1 - sm / 2)));
        }
        for (Unit un : b.units) {
            if (un.destroyed && un.deathShown) continue;
            float x = un.drawX, y = un.drawY;
            int tc = UnitArt.teamColor(un.team);
            g.fillCircle(x, y, 0.82f, Col.alpha(tc, 0.18f));
            g.strokeCircle(x, y, 0.82f, 0.05f, Col.alpha(tc, 0.85f));
            // facing tick
            float fa = (float) Math.toRadians(un.drawFacing);
            g.line(x + (float) Math.cos(fa) * 0.7f, y + (float) Math.sin(fa) * 0.7f, x + (float) Math.cos(fa) * 0.95f,
                    y + (float) Math.sin(fa) * 0.95f, 0.1f, tc);
            UnitArt.draw(g, un, x, y, un.drawFacing, un.team, 1.08f, t, un.condition());
            if (un.hitFlash > 0) g.fillCircle(x, y, 0.75f, Col.alpha(0xFFFFFFFF, un.hitFlash));
            boolean dim = un.activated && b.result == Battle.RESULT_NONE && un != b.active;
            if (dim) g.fillCircle(x, y, 0.82f, 0x66000000);
            // health bar
            float cond = un.condition();
            g.fillRect(x - 0.55f, y + 0.72f, 1.1f, 0.14f, 0xCC000000);
            g.fillRect(x - 0.53f, y + 0.74f, 1.06f * cond, 0.1f, cond > 0.6f ? Col.GOOD : (cond > 0.3f ? Col.ACCENT : Col.BAD));
            // heat bar for hot units
            if (un.heat > 0 && un.ch.heatCap > 0 && un.type != UnitType.INFANTRY) {
                g.fillRect(x - 0.55f, y + 0.88f, 1.1f * Math.min(1, un.heatFrac()), 0.07f, un.heatFrac() > 0.75f ? 0xFFFF3A2A : 0xFFFF9A3A);
            }
            String st = un.shutdown ? "OFF" : (un.baseMp() == 0 ? "IMMOBILE" : null);
            if (st != null) g.text(st, x, y - 0.95f, 0.3f, 0xFFFFD27A, Gfx.ALIGN_CENTER, true);
        }
    }

    private void renderFx(Gfx g) {
        for (Battle.Fx f : b.fx) {
            if (!f.started) continue;
            float k = Math.min(1f, f.age / f.dur);
            switch (f.kind) {
                case Battle.Fx.BEAM: {
                    float a = k < 0.6f ? 1f : (1 - k) / 0.4f;
                    float wdt = 0.06f + f.size * 0.05f;
                    g.line(f.x1, f.y1, f.x2, f.y2, wdt * 3, Col.alpha(f.color, 0.35f * a));
                    g.line(f.x1, f.y1, f.x2, f.y2, wdt, Col.alpha(f.color, a));
                    g.line(f.x1, f.y1, f.x2, f.y2, wdt * 0.4f, Col.alpha(0xFFFFFFFF, a));
                    if (f.hit) g.fillRadial(f.x2, f.y2, 0.35f, Col.alpha(0xFFFFFFFF, a), Col.alpha(f.color, 0));
                    break;
                }
                case Battle.Fx.TRACER: {
                    float travel = Math.max(0.01f, f.dur - 0.05f);
                    for (int i = 0; i < f.count; i++) {
                        float ff = (f.age - i * 0.04f) / travel;
                        if (ff < 0 || ff > 1) continue;
                        float x = f.x1 + (f.x2 - f.x1) * ff, y = f.y1 + (f.y2 - f.y1) * ff;
                        float bx = f.x1 + (f.x2 - f.x1) * Math.max(0, ff - 0.08f), by = f.y1 + (f.y2 - f.y1) * Math.max(0, ff - 0.08f);
                        g.line(bx, by, x, y, 0.05f + f.size * 0.03f, f.color);
                    }
                    if (f.hit && f.age > travel * 0.9f) g.fillRadial(f.x2, f.y2, 0.3f, 0xFFFFFFFF, 0x00FFAA44);
                    break;
                }
                case Battle.Fx.MISSILES: {
                    float travel = Math.max(0.01f, f.dur - 0.05f);
                    float dx = f.x2 - f.x1, dy = f.y2 - f.y1;
                    float len = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                    float nx = -dy / len, ny = dx / len;
                    for (int i = 0; i < f.count; i++) {
                        float ff = (f.age - i * 0.05f) / travel;
                        if (ff < 0 || ff > 1) continue;
                        float wob = (float) Math.sin(ff * Math.PI) * (0.3f + (i % 3) * 0.2f) * ((i & 1) == 0 ? 1 : -1);
                        float x = f.x1 + dx * ff + nx * wob, y = f.y1 + dy * ff + ny * wob;
                        g.fillCircle(x - dx / len * 0.15f, y - dy / len * 0.15f, 0.08f, 0x66CCCCCC);
                        g.fillCircle(x, y, 0.06f, f.color);
                    }
                    if (f.hit && f.age > travel * 0.9f) g.fillRadial(f.x2, f.y2, 0.45f, 0xFFFFE0A0, 0x00FF6A2A);
                    break;
                }
                case Battle.Fx.SHELL: {
                    float travel = Math.max(0.01f, f.dur - 0.05f);
                    float ff = Math.min(1f, f.age / travel);
                    float x = f.x1 + (f.x2 - f.x1) * ff, y = f.y1 + (f.y2 - f.y1) * ff;
                    float lift = (float) Math.sin(ff * Math.PI) * 2.5f;
                    g.fillCircle(x, y, 0.08f, 0x55000000);
                    g.fillCircle(x, y - lift, 0.12f, 0xFFFFE8A0);
                    break;
                }
                case Battle.Fx.MELEE: {
                    float a = 1 - k;
                    for (int i = 0; i < 3; i++) {
                        float ang = (float) (k * 3 + i * 2.1);
                        g.line(f.x2 - (float) Math.cos(ang) * 0.5f, f.y2 - (float) Math.sin(ang) * 0.5f,
                                f.x2 + (float) Math.cos(ang) * 0.5f, f.y2 + (float) Math.sin(ang) * 0.5f, 0.08f, Col.alpha(0xFFE0F0FF, a));
                    }
                    break;
                }
                case Battle.Fx.EXPLOSION:
                case Battle.Fx.DEATH: {
                    float r = f.size * (0.4f + 0.8f * k);
                    float a = 1 - k;
                    g.fillRadial(f.x1, f.y1, r * 1.4f, Col.alpha(0xFFFF8A2A, a * 0.7f), 0x00FF4A00);
                    g.fillRadial(f.x1, f.y1, r * 0.8f, Col.alpha(0xFFFFFFFF, a), Col.alpha(0xFFFFB02E, a * 0.6f));
                    if (f.kind == Battle.Fx.DEATH) {
                        for (int i = 0; i < 8; i++) {
                            float ang = i * 0.785f;
                            float d = k * 1.6f;
                            g.fillRect(f.x1 + (float) Math.cos(ang) * d - 0.05f, f.y1 + (float) Math.sin(ang) * d - 0.05f, 0.1f, 0.1f, Col.alpha(0xFF333333, a));
                        }
                    }
                    break;
                }
                case Battle.Fx.TEXT: {
                    float a = Math.min(1f, (1 - k) * 2.5f);
                    float y = f.y1 - k * 0.9f;
                    g.text(f.text, f.x1 + 0.03f, y + 0.03f, 0.36f, Col.alpha(0xFF000000, a * 0.8f), Gfx.ALIGN_CENTER, true);
                    g.text(f.text, f.x1, y, 0.36f, Col.alpha(f.color, a), Gfx.ALIGN_CENTER, true);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void renderWeather(Gfx g) {
        int id = b.map.weather.id;
        g.save();
        g.clipRect(0, 0, mapW, h);
        switch (id) {
            case Weather.RAIN:
            case Weather.STORM: {
                int n = id == Weather.STORM ? 140 : 80;
                for (int i = 0; i < n; i++) {
                    float x = (i * 97.13f + t * 260) % (mapW + 100) - 50, y = (i * 53.7f + t * 900) % h;
                    g.line(x, y, x - u * 0.8f, y + u * 2.5f, Math.max(1, u * 0.18f), 0x55BBD8FF);
                }
                g.fillRect(0, 0, mapW, h, 0x22102030);
                if (id == Weather.STORM && (t % 7) < 0.12f) g.fillRect(0, 0, mapW, h, 0x88FFFFFF);
                break;
            }
            case Weather.SNOW:
                for (int i = 0; i < 110; i++) {
                    float x = (i * 97.13f + (float) Math.sin(t + i) * u * 3 + t * 30) % mapW, y = (i * 53.7f + t * 70 * (1 + i % 3)) % h;
                    g.fillCircle(x, y, u * (0.25f + (i % 3) * 0.12f), 0xBBFFFFFF);
                }
                break;
            case Weather.FOG:
                g.fillRectVGradient(0, 0, mapW, h, 0x66C8D0D8, 0x33C8D0D8);
                for (int i = 0; i < 6; i++) {
                    float x = (i * 311 + t * 12) % (mapW + u * 60) - u * 30;
                    g.fillRadial(x, h * (0.15f + i * 0.14f), u * 30, 0x33E0E6EC, 0x00E0E6EC);
                }
                break;
            case Weather.NIGHT:
                g.fillRect(0, 0, mapW, h, 0x88000818);
                break;
            case Weather.HEAT_WAVE:
                g.fillRect(0, 0, mapW, h, 0x22FF8A2A);
                break;
            case Weather.SANDSTORM:
                g.fillRect(0, 0, mapW, h, 0x44C89A5A);
                for (int i = 0; i < 70; i++) {
                    float x = (i * 97.13f + t * 500) % (mapW + 100) - 50, y = (i * 53.7f + (float) Math.sin(t * 2 + i) * u * 2) % h;
                    g.line(x, y, x + u * 3, y + u * 0.3f, Math.max(1, u * 0.2f), 0x66E8C890);
                }
                break;
            case Weather.MAGNETIC:
                for (int i = 0; i < 3; i++) {
                    float y = h * (0.05f + i * 0.06f) + (float) Math.sin(t * 0.7f + i) * u * 2;
                    g.fillRectVGradient(0, y, mapW, u * 6, 0x0044FFAA, Col.alpha(i == 1 ? 0xFFAA66FF : 0xFF44FFAA, 0.18f));
                }
                break;
            default:
                break;
        }
        g.restore();
    }

    private void renderTop(Gfx g) {
        g.fillRectVGradient(0, 0, mapW, topBar, 0xDD0A0F18, 0x880A0F18);
        String turn;
        if (b.result != Battle.RESULT_NONE) turn = "Battle over";
        else if (b.side == Battle.PLAYER) {
            int ready = 0;
            for (Unit un : b.units) if (un.team == Battle.PLAYER && !un.destroyed && !un.activated) ready++;
            turn = b.active != null ? "Your activation: " + b.active.name : "Your turn: pick a unit (" + ready + " ready)";
        } else turn = "Enemy activation" + (b.active != null ? ": " + b.active.name : "...");
        g.text("Round " + Math.min(b.round, Battle.MAX_ROUNDS) + "/" + Battle.MAX_ROUNDS, u * 2, topBar / 2, u * 3, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(Weather.NAMES[b.map.weather.id], u * 24, topBar / 2, u * 2.8f, Col.SHIELD, Gfx.ALIGN_LEFT, false);
        g.text(turn, mapW / 2 + u * 8, topBar / 2, u * 3, b.side == Battle.PLAYER ? Col.ACCENT : 0xFFFF8A7A, Gfx.ALIGN_CENTER, true);
        g.text(b.alive(Battle.PLAYER) + " vs " + b.alive(Battle.ENEMY), mapW - u * 11, topBar / 2, u * 3, Col.TEXT, Gfx.ALIGN_RIGHT, true);
        if (infoHex >= 0) {
            int c = infoHex % b.map.cols, r = infoHex / b.map.cols;
            String s = b.map.describe(c, r);
            float tw = g.textWidth(s, u * 2.6f, false) + u * 4;
            g.fillRoundRect(mapW / 2 - tw / 2, topBar + u, tw, u * 5, u * 1.5f, 0xDD000000);
            g.text(s, mapW / 2, topBar + u * 3.5f, u * 2.6f, Col.TEXT, Gfx.ALIGN_CENTER, false);
        }
    }

    private void renderLog(Gfx g) {
        int n = Math.min(4, b.log.size());
        float y = h - u * 2.5f;
        for (int i = 0; i < n; i++) {
            String s = b.log.get(b.log.size() - 1 - i);
            float a = 1f - i * 0.2f;
            float tw = Math.min(mapW - u * 4, g.textWidth(s, u * 2.3f, false) + u * 2);
            g.fillRoundRect(u * 1.5f, y - u * 2, tw, u * 4, u, Col.alpha(0xAA000000, a));
            g.text(Ui.fit(g, s, u * 2.3f, mapW - u * 6), u * 2.5f, y, u * 2.3f, Col.alpha(0xFFE8EEF8, a), Gfx.ALIGN_LEFT, false);
            y -= u * 4.4f;
        }
    }

    private void renderPanel(Gfx g) {
        g.fillRect(panelX, 0, panelW, h, 0xFF101826);
        g.fillRect(panelX, 0, u * 0.4f, h, 0xFF3D4E6E);
        Unit su = panelUnit();
        float x0 = panelX + u * 2, pw = panelW - u * 4;
        if (su == null) {
            g.text(b.playerTurn() ? "Tap one of your units (pulsing blue)" : "Enemy is moving...", panelX + panelW / 2, h * 0.3f, u * 2.8f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
            g.text("Drag to pan, pinch to zoom", panelX + panelW / 2, h * 0.36f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
            renderButtons(g);
            return;
        }
        int tc = UnitArt.teamColor(su.team);
        g.fillRect(panelX + u * 0.4f, 0, u * 1.2f, u * 10, tc);
        g.text(Ui.fit(g, su.name, u * 3.6f, pw - u * 10), x0 + u, u * 3.5f, u * 3.6f, Col.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(UnitType.NAMES[su.type] + " · " + Chassis.ROLES[su.ch.role] + (su.team == Battle.ENEMY ? " · ENEMY" : ""),
                x0 + u, u * 7.5f, u * 2.3f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        UnitArt.draw(g, su, x0 + pw - u * 4, u * 5, -90, su.team, u * 3.5f, t, 1);
        UnitArt.doll(g, su, dollX, dollY, dollW, dollH, u * 3, -1);

        float sx = dollX + dollW + u * 2, sw = x0 + pw - sx;
        if (target != null && su == b.active) {
            int arc = b.attackArc(su, target);
            g.text("Target", sx, dollY + u * 1.5f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            g.text(Ui.fit(g, target.name, u * 2.8f, sw), sx, dollY + u * 5, u * 2.8f, 0xFFFF8A7A, Gfx.ALIGN_LEFT, true);
            g.text("hitting its " + UnitType.ARC_NAMES[arc].toUpperCase(), sx, dollY + u * 8.5f, u * 2.2f, Col.ACCENT, Gfx.ALIGN_LEFT, true);
            float tdw = Math.min(sw, dollH - u * 11);
            UnitArt.doll(g, target, sx, dollY + u * 10.5f, tdw, tdw, u * 2.2f, -1);
        } else {
            float y = dollY + u * 1.5f;
            int mp = b.mpAvailable(su);
            g.text("Move", sx, y, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            g.text(su == b.active && su.moved ? "moved " + su.movedHexes : mp + " MP" + (su.turboOn ? " (turbo)" : ""),
                    sx + sw, y, u * 2.5f, Col.TEXT, Gfx.ALIGN_RIGHT, true);
            y += u * 4.2f;
            if (su.type != UnitType.INFANTRY) {
                g.text("Heat " + Math.round(su.heat) + "/" + su.ch.heatCap, sx, y, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                g.text("-" + su.dissipation() + "/rnd", sx + sw, y, u * 2.1f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
                y += u * 2.4f;
                Ui.bar(g, sx, y, sw, u * 1.6f, su.heatFrac(), su.heatFrac() > 0.75f ? 0xFFFF3A2A : (su.heatFrac() > 0.5f ? 0xFFFF9A3A : 0xFFFFD27A));
                g.fillRect(sx + sw * 0.5f, y - u * 0.3f, u * 0.3f, u * 2.2f, 0x88FFFFFF);
                g.fillRect(sx + sw * 0.75f, y - u * 0.3f, u * 0.3f, u * 2.2f, 0x88FF5A2A);
                y += u * 4f;
            } else {
                g.text("Strength " + Math.round(su.firepowerScale() * 100) + "%", sx, y, u * 2.3f, Col.TEXT, Gfx.ALIGN_LEFT, false);
                y += u * 4f;
            }
            String st = su.statusText();
            if (st.length() > 0) {
                g.text(st, sx, y, u * 2.2f, Col.BAD, Gfx.ALIGN_LEFT, true);
                y += u * 3.5f;
            }
            StringBuilder eq = new StringBuilder();
            for (int e = 0; e < su.equip.length; e++) {
                if (su.equip[e] < 0) continue;
                Equip ee = Equip.ALL[su.equip[e]];
                if (eq.length() > 0) eq.append(", ");
                eq.append(ee.shortName).append(su.eqBroken[e] ? "(x)" : (ee.uses > 0 ? "(" + su.eqUses[e] + ")" : ""));
            }
            if (eq.length() > 0) Ui.wrap(g, eq.toString(), sx, y, u * 2f, sw, Col.SHIELD);
            if (b.inEcm(su) && su.team == Battle.PLAYER) g.text("In ECM cover", sx, dollY + dollH - u, u * 2f, 0xFF7AD8FF, Gfx.ALIGN_LEFT, false);
        }

        // weapons
        renderButtons(g);
        boolean targeting = target != null && su == b.active;
        int heatSel = 0;
        StringBuilder why = null;
        int firstSel = -1;
        for (int m = 0; m < su.weapon.length; m++) {
            Btn row = null;
            for (Btn bb : buttons) if (bb.id == WROW + m) row = bb;
            if (row == null) continue;
            Wpn wp = su.wpn(m);
            float cy = row.y + row.h / 2;
            float ts = Math.min(u * 2.5f, row.h * 0.42f);
            if (wp == null) {
                g.text("(empty mount)", row.x + u * 1.5f, cy, ts, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
                continue;
            }
            boolean usable = su.weaponUsable(m);
            if (targeting && wsel.length > m && wsel[m]) {
                heatSel += wp.heat;
                if (firstSel < 0) firstSel = m;
            }
            if (targeting) {
                g.fillRoundRect(row.x + u * 0.8f, cy - ts * 0.55f, ts * 1.1f, ts * 1.1f, ts * 0.2f,
                        wsel.length > m && wsel[m] && row.enabled ? Col.ACCENT : 0xFF2A3346);
            }
            float tx = row.x + (targeting ? u * 1.5f + ts * 1.4f : u * 1.5f);
            g.text(Ui.fit(g, wp.name, ts, row.w * 0.48f), tx, cy - ts * 0.32f, ts, usable ? Col.TEXT : Col.BAD, Gfx.ALIGN_LEFT, true);
            String sub = Info.locOf(su, m) + "  " + wp.dmg + "d " + wp.heat + "h  " + wp.rangeText()
                    + (wp.usesAmmo() ? "  ammo " + su.ammo[m] : "");
            g.text(Ui.fit(g, sub, ts * 0.72f, row.w * 0.62f), tx, cy + ts * 0.62f, ts * 0.72f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            if (targeting) {
                int ch = b.hitChance(su, m, target, null);
                if (ch >= 0) {
                    int c = ch >= 60 ? Col.GOOD : (ch >= 35 ? Col.ACCENT : Col.BAD);
                    g.text(ch + "%", row.x + row.w - u * 1.5f, cy, ts * 1.25f, c, Gfx.ALIGN_RIGHT, true);
                } else {
                    g.text(b.lastReason, row.x + row.w - u * 1.5f, cy, ts * 0.75f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
                }
            } else if (!usable) {
                g.text(su.wpnBroken[m] || su.locDestroyed(su.ch.mounts[m].loc) ? "DESTROYED" : "NO AMMO", row.x + row.w - u * 1.5f, cy, ts * 0.8f, Col.BAD, Gfx.ALIGN_RIGHT, true);
            }
        }
        float infoY = listY + su.weapon.length * rowH + u * 1.5f;
        if (targeting) {
            if (su.type != UnitType.INFANTRY) {
                int after = Math.round(su.heat) + heatSel;
                int c = after >= su.ch.heatCap ? Col.BAD : (after >= su.ch.heatCap * 0.75f ? Col.ACCENT : Col.TEXT_DIM);
                g.text("Heat after firing: " + after + " / " + su.ch.heatCap + (after >= su.ch.heatCap ? "  SHUTDOWN!" : ""),
                        x0, infoY + u, u * 2.3f, c, Gfx.ALIGN_LEFT, true);
            }
            if (firstSel >= 0) {
                why = new StringBuilder();
                b.hitChance(su, firstSel, target, why);
                Ui.wrap(g, su.wpn(firstSel).name + ": " + why, x0, infoY + u * 4.5f, u * 1.9f, pw, Col.TEXT_DIM);
            }
        } else if (myActivation()) {
            String hint = !su.moved ? "Tap a green hex to move (tap twice), or tap an enemy to target it." :
                    (!su.fired ? "Tap an enemy to target it, rotate if needed, then End Turn." : "Done? Rotate or use equipment, then End Turn.");
            Ui.wrap(g, hint, x0, infoY + u, u * 2.2f, pw, Col.TEXT_DIM);
        }
    }
}
