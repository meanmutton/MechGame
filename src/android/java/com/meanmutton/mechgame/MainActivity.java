package com.meanmutton.mechgame;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import com.meanmutton.mechgame.core.Game;
import com.meanmutton.mechgame.core.Platform;
import com.meanmutton.mechgame.core.SoundSynth;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** The single activity: full-screen landscape, hosts GameView and provides platform services. */
public class MainActivity extends Activity implements Platform {
    private Game game;
    private GameView view;
    private SoundPool pool;
    private final int[] soundIds = new int[SND_COUNT];
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            // Draw into the display cutout area too (attribute value 1 = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES).
            try {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.getClass().getField("layoutInDisplayCutoutMode").setInt(lp, 1);
                getWindow().setAttributes(lp);
            } catch (Exception ignored) {
            }
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = getSharedPreferences("mechgame", MODE_PRIVATE);
        initSounds();
        game = new Game(this, System.nanoTime());
        view = new GameView(this, game);
        setContentView(view);
        hideSystemUi();
    }

    @SuppressWarnings("deprecation")
    private void initSounds() {
        if (Build.VERSION.SDK_INT >= 21) {
            pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        } else {
            pool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        }
        File dir = new File(getCacheDir(), "sfx");
        dir.mkdirs();
        for (int i = 0; i < SND_COUNT; i++) {
            File f = new File(dir, "s" + i + "_v2.wav");
            try {
                if (!f.exists()) {
                    FileOutputStream out = new FileOutputStream(f);
                    out.write(SoundSynth.wav(SoundSynth.generate(i)));
                    out.close();
                }
                soundIds[i] = pool.load(f.getAbsolutePath(), 1);
            } catch (IOException e) {
                soundIds[i] = 0;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onBackPressed() {
        game.touch(Game.BACK, 0, 0, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pool != null) pool.release();
    }

    // ---------------- Platform ----------------

    public void play(int sound, float volume) {
        if (pool != null && sound >= 0 && sound < SND_COUNT && soundIds[sound] != 0) {
            pool.play(soundIds[sound], volume, volume, 1, 0, 1f);
        }
    }

    public String load(String key) {
        return prefs.getString(key, null);
    }

    public void save(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public void promptText(final String title, final String initial, final TextCallback cb) {
        runOnUiThread(new Runnable() {
            public void run() {
                final EditText input = new EditText(MainActivity.this);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
                input.setText(initial);
                input.setSelectAllOnFocus(true);
                AlertDialog d = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title)
                        .setView(input)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                cb.done(input.getText().toString());
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .create();
                d.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        hideSystemUi();
                    }
                });
                d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                d.show();
            }
        });
    }

    public void exit() {
        runOnUiThread(new Runnable() {
            public void run() {
                finish();
            }
        });
    }
}
