package com.meanmutton.mechgame.core;

/** Shown at launch after a crash: the saved report, with buttons to share it or put it away. */
public final class CrashScreen extends Screen {
    private static final int SHARE = 1, CLOSE = 2;
    private final String[] lines;
    private final String report;
    private float scroll, dragY = -1, dragStart;
    private int dragPointer = -1;

    public CrashScreen(Game game, String report) {
        super(game);
        this.report = report;
        this.lines = report.split("\n");
    }

    protected void layout() {
        buttons.clear();
        Btn s = add(new Btn(SHARE, "Share report").set(w - u * 43, h - u * 12, u * 40, u * 9));
        s.color = 0xFF2E6D8F;
        add(new Btn(CLOSE, "Close").set(u * 3, h - u * 12, u * 28, u * 9));
    }

    protected void onClick(Btn b) {
        if (b.id == SHARE) game.platform.share(report.startsWith("Hexfront freeze") ? "Hexfront freeze report" : "Hexfront crash report", report);
        else close();
    }

    private void close() {
        game.platform.save(CrashReport.KEY, "");
        game.toMenu();
    }

    public boolean back() {
        close();
        return true;
    }

    private float lineH() {
        return u * 2.6f;
    }

    private float maxScroll() {
        float area = h - u * 16 - h * 0.17f;
        return Math.max(0, lines.length * lineH() - area);
    }

    protected void onTouchDown(int pointer, float x, float y) {
        dragPointer = pointer;
        dragY = y;
        dragStart = scroll;
    }

    protected void onTouchMove(int pointer, float x, float y) {
        if (pointer != dragPointer) return;
        scroll = Math.max(0, Math.min(maxScroll(), dragStart - (y - dragY)));
    }

    protected void onTouchUp(int pointer, float x, float y) {
        if (pointer == dragPointer) dragPointer = -1;
    }

    public void render(Gfx g) {
        background(g);
        boolean froze = report.startsWith("Hexfront freeze");
        g.text(froze ? "HEXFRONT FROZE LAST TIME" : "HEXFRONT CRASHED LAST TIME", u * 3, h * 0.055f, u * 4.6f, Col.BAD, Gfx.ALIGN_LEFT, true);
        g.text("Sorry about that. Share this report (email it, or paste it into a chat) so the problem can be found and fixed."
                + (game.hasBattle() ? " Your battle was saved as you played: Resume Battle is on the menu." : ""),
                u * 3, h * 0.11f, u * 2.4f, Col.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        float top = h * 0.15f, bottom = h - u * 14;
        Ui.panel(g, u * 2, top, w - u * 4, bottom - top, u * 1.5f);
        g.save();
        g.clipRect(u * 2, top + u, w - u * 4, bottom - top - u * 2);
        float y = top + u * 2.5f - scroll;
        for (String line : lines) {
            if (y > top - lineH() && y < bottom + lineH()) {
                boolean head = line.startsWith("Hexfront") || line.contains("Exception") || line.contains("Error") || line.startsWith("Thread \"");
                g.text(Ui.fit(g, line.replace('\t', ' '), u * 2f, w - u * 9), u * 4, y, u * 2f, head ? Col.ACCENT : Col.TEXT, Gfx.ALIGN_LEFT, false);
            }
            y += lineH();
        }
        g.restore();
        if (maxScroll() > 0) g.text("Drag to scroll", w / 2f, h - u * 7.5f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        renderButtons(g);
    }
}
