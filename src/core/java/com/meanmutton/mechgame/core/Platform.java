package com.meanmutton.mechgame.core;

/** Services the host (Android or desktop harness) provides to the game. */
public interface Platform {
    int SND_LASER = 0, SND_CANNON = 1, SND_MISSILE = 2, SND_BOOM = 3, SND_BIG_BOOM = 4, SND_STEP = 5,
            SND_CLICK = 6, SND_BUY = 7, SND_ALERT = 8, SND_WIN = 9, SND_LOSE = 10, SND_MELEE = 11, SND_RAIL = 12,
            SND_ARTY = 13, SND_HOVER = 14, SND_COUNT = 15;

    void play(int sound, float volume);

    String load(String key);

    void save(String key, String value);

    /** Ask the user for a line of text. The callback may run on another thread; use Game.post. */
    void promptText(String title, String initial, TextCallback callback);

    void exit();

    /** False where there is nothing to quit to (a browser tab): the menu then has no Quit button. */
    boolean canExit();

    /** Saves right away (used when the app is about to die, so it must not be deferred). */
    void saveNow(String key, String value);

    /** Offers text to other apps (email, messages, notes) through the system share sheet. */
    void share(String subject, String text);

    /** Which build this is and what it runs on, for crash reports. */
    String buildInfo();

    /** Opens a WebSocket to the online relay, or returns null where this build has no online play. */
    Link openLink(String url);

    interface TextCallback {
        void done(String text);
    }
}
