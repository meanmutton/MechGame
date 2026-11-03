package com.meanmutton.mechgame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.meanmutton.mechgame.core.Game;

/**
 * Hosts the game: one dedicated thread updates and draws at up to 60 fps; touches are queued to it.
 *
 * Rotating the phone (or leaving and coming back) destroys the drawing surface and makes a new one. The loop is
 * written so that can never leave two drawing threads running or the main thread waiting on the game:
 * each thread has its own stop flag, a new one starts only once the old one has finished, the game updates
 * outside the surface lock (which the main thread needs during rotation), and a surface that can't be locked
 * mid-change is simply skipped for a frame instead of killing the thread.
 */
final class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private final Game game;
    private final AndroidGfx gfx = new AndroidGfx();
    private Loop loop;
    /** When the drawing thread last finished a frame, and whether one should be running (for the watchdog). */
    volatile long lastFrame;
    volatile boolean drawing;

    GameView(Context ctx, Game game) {
        super(ctx);
        this.game = game;
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    private final class Loop extends Thread {
        volatile boolean run = true;

        Loop() {
            super("game");
        }

        @Override
        public void run() {
            long last = System.nanoTime();
            SurfaceHolder holder = getHolder();
            while (run) {
                long now = System.nanoTime();
                float dt = Math.min(0.05f, (now - last) / 1e9f);
                last = now;
                Rect frame = holder.getSurfaceFrame();
                if (frame.width() > 0 && frame.height() > 0) game.tick(dt, frame.width(), frame.height());
                if (!run) break;
                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c != null) {
                        gfx.begin(c);
                        game.render(gfx);
                    }
                } catch (IllegalArgumentException e) {
                    c = null;   // the surface is being replaced: skip this frame
                } catch (IllegalStateException e) {
                    c = null;
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c);
                        } catch (IllegalArgumentException ignored) {
                        } catch (IllegalStateException ignored) {
                        }
                    }
                }
                lastFrame = SystemClock.uptimeMillis();
                long spent = (System.nanoTime() - now) / 1000000L;
                pause(c == null ? 16 : Math.max(1, 15 - spent));
            }
        }
    }

    public synchronized void surfaceCreated(SurfaceHolder holder) {
        stopLoop();
        lastFrame = SystemClock.uptimeMillis();
        drawing = true;
        loop = new Loop();
        loop.start();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    public synchronized void surfaceDestroyed(SurfaceHolder holder) {
        drawing = false;
        stopLoop();
    }

    /** Stops the drawing thread and waits for it, so the surface is never drawn on after it is gone. */
    private void stopLoop() {
        Loop l = loop;
        loop = null;
        if (l == null) return;
        l.run = false;
        // A frame takes milliseconds; the bound only matters if the game itself is stuck, and then the thread
        // has been told to stop, so it can't come back and draw on the next surface.
        try {
            l.join(2000);
        } catch (InterruptedException ignored) {
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.touch(Game.TOUCH_DOWN, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) {
                    game.touch(Game.TOUCH_MOVE, e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int i = e.getActionIndex();
                game.touch(Game.TOUCH_UP, e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
        }
        return true;
    }
}
