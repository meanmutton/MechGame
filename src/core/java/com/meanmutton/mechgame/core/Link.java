package com.meanmutton.mechgame.core;

/**
 * A connection to the online relay (a WebSocket). Text messages only. Received messages queue up until the game
 * reads them once per frame, so the game never has to deal with other threads.
 */
public interface Link {
    int CONNECTING = 0, OPEN = 1, CLOSED = 2;

    int state();

    /** Sends one message; ignored unless the link is open. */
    void send(String text);

    /** The next message received, or null if there is none waiting. */
    String poll();

    void close();
}
