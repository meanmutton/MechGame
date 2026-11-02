package com.meanmutton.mechgame;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.meanmutton.mechgame.core.Game;

/** Hosts the game: a dedicated thread updates and draws at up to 60 fps; touches are queued to it. */
final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final Game game;
    private final AndroidGfx gfx = new AndroidGfx();
    private Thread thread;
    private volatile boolean running;

    GameView(Context ctx, Game game) {
        super(ctx);
        this.game = game;
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this, "game");
        thread.start();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (thread != null) {
            try {
                thread.join(1500);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    public void run() {
        long last = System.nanoTime();
        SurfaceHolder holder = getHolder();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c == null) {
                    sleep(16);
                    continue;
                }
                game.tick(dt, c.getWidth(), c.getHeight());
                gfx.begin(c);
                game.render(gfx);
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (IllegalStateException ignored) {
                    }
                }
            }
            long spent = (System.nanoTime() - now) / 1000000L;
            if (spent < 15) sleep(15 - spent);
        }
    }

    private static void sleep(long ms) {
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
