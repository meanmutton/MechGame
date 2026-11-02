package com.meanmutton.mechgame.core;

import java.util.Random;

/** Generates every sound effect procedurally (16-bit mono PCM), so the game ships no audio files. */
public final class SoundSynth {
    public static final int RATE = 22050;

    private SoundSynth() {}

    public static short[] generate(int id) {
        Random r = new Random(4321 + id);
        switch (id) {
            case Platform.SND_LASER: return laser(r);
            case Platform.SND_CANNON: return thump(r, 0.35f, 160, 50, 0.6f);
            case Platform.SND_MISSILE: return whoosh(r);
            case Platform.SND_BOOM: return boom(r, 0.6f, 0.85f, 900);
            case Platform.SND_BIG_BOOM: return boom(r, 1.5f, 1.0f, 420);
            case Platform.SND_STEP: return thump(r, 0.16f, 70, 40, 0.5f);
            case Platform.SND_CLICK: return tone(0.04f, 1400, 1100, 0.3f, false);
            case Platform.SND_BUY: return chord(new float[]{988, 1319}, 0.08f, 0.4f);
            case Platform.SND_ALERT: return chord(new float[]{660, 880}, 0.09f, 0.35f);
            case Platform.SND_WIN: return chord(new float[]{523, 659, 784, 1047}, 0.14f, 0.5f);
            case Platform.SND_LOSE: return chord(new float[]{392, 330, 262, 196}, 0.2f, 0.45f);
            case Platform.SND_MELEE: return clang(r);
            case Platform.SND_RAIL: return rail(r);
            case Platform.SND_ARTY: return thump(r, 0.6f, 90, 30, 0.7f);
            case Platform.SND_HOVER: return tone(0.25f, 220, 320, 0.25f, true);
            default: return new short[100];
        }
    }

    private static short[] laser(Random r) {
        int n = (int) (0.2f * RATE);
        float[] f = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            ph += 2 * Math.PI * (2200 - 1700 * t) / RATE;
            float s = (float) Math.sin(ph) + 0.4f * (float) Math.sin(ph * 2.01);
            f[i] = s * (1 - t) * 0.35f;
        }
        return out(f);
    }

    private static short[] whoosh(Random r) {
        int n = (int) (0.45f * RATE);
        float[] f = new float[n];
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float noise = r.nextFloat() * 2 - 1;
            float a = 0.05f + 0.25f * t;
            lp += (noise - lp) * a;
            float env = (float) Math.sin(Math.PI * t);
            f[i] = lp * env * 0.9f;
        }
        return out(f);
    }

    private static short[] clang(Random r) {
        int n = (int) (0.5f * RATE);
        float[] f = new float[n];
        float[] fr = {420, 1130, 1870, 2650};
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float v = 0;
            for (int k = 0; k < fr.length; k++) v += Math.sin(2 * Math.PI * fr[k] * t) * Math.exp(-t * (6 + k * 3)) / (k + 1);
            v += (r.nextFloat() - 0.5f) * Math.exp(-t * 40);
            f[i] = v * 0.5f;
        }
        return out(f);
    }

    private static short[] rail(Random r) {
        int n = (int) (0.35f * RATE);
        float[] f = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            ph += 2 * Math.PI * (300 + 3000 * t * t) / RATE;
            float crack = t < 0.08f ? (r.nextFloat() - 0.5f) * 1.6f : 0;
            f[i] = ((float) Math.sin(ph) * 0.3f * (1 - t) + crack) * 0.8f;
        }
        return out(f);
    }

    private static short[] out(float[] f) {
        short[] s = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            float v = Math.max(-1f, Math.min(1f, f[i]));
            s[i] = (short) (v * 32000);
        }
        return s;
    }

    private static short[] thump(Random r, float dur, float f0, float f1, float vol) {
        int n = (int) (dur * RATE);
        float[] f = new float[n];
        double ph = 0;
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float freq = f0 + (f1 - f0) * t;
            ph += 2 * Math.PI * freq / RATE;
            float env = (float) Math.exp(-t * 6);
            float noise = r.nextFloat() * 2 - 1;
            lp += (noise - lp) * 0.3f;
            f[i] = (float) (Math.sin(ph) * 0.8 + lp * 0.6 * Math.exp(-t * 14)) * env * vol;
        }
        return out(f);
    }

    private static short[] boom(Random r, float dur, float vol, float cutoff) {
        int n = (int) (dur * RATE);
        float[] f = new float[n];
        float lp1 = 0, lp2 = 0;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float noise = r.nextFloat() * 2 - 1;
            float a = (float) (cutoff * (1 - t * 0.7) / RATE * 6.28);
            lp1 += (noise - lp1) * a;
            lp2 += (lp1 - lp2) * a;
            ph += 2 * Math.PI * (60 - 30 * t) / RATE;
            float attack = Math.min(1f, i / (RATE * 0.004f));
            float env = (float) Math.exp(-t * 4.5) * attack;
            f[i] = (lp2 * 3.2f + (float) Math.sin(ph) * 0.5f * (float) Math.exp(-t * 8)) * env * vol;
        }
        return out(f);
    }

    private static short[] tone(float dur, float f0, float f1, float vol, boolean wobble) {
        int n = (int) (dur * RATE);
        float[] f = new float[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float freq = f0 + (f1 - f0) * t;
            if (wobble) freq *= 1 + 0.08f * (float) Math.sin(t * 60);
            ph += 2 * Math.PI * freq / RATE;
            float env = Math.min(1f, i / (RATE * 0.003f)) * (1 - t);
            f[i] = (float) Math.sin(ph) * env * vol;
        }
        return out(f);
    }

    private static short[] chord(float[] notes, float step, float vol) {
        int n = (int) ((step * notes.length + 0.25f) * RATE);
        float[] f = new float[n];
        for (int k = 0; k < notes.length; k++) {
            int start = (int) (k * step * RATE);
            double ph = 0;
            for (int i = start; i < n; i++) {
                float t = (i - start) / (float) RATE;
                ph += 2 * Math.PI * notes[k] / RATE;
                float env = (float) Math.exp(-t * 7) * Math.min(1f, t / 0.005f);
                f[i] += ((float) Math.sin(ph) + 0.3f * (float) Math.sin(ph * 2)) * env * vol / 1.6f;
            }
        }
        return out(f);
    }

    /** Wraps PCM in a WAV container. */
    public static byte[] wav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        writeStr(b, 0, "RIFF");
        writeInt(b, 4, 36 + dataLen);
        writeStr(b, 8, "WAVE");
        writeStr(b, 12, "fmt ");
        writeInt(b, 16, 16);
        writeShort(b, 20, 1);
        writeShort(b, 22, 1);
        writeInt(b, 24, RATE);
        writeInt(b, 28, RATE * 2);
        writeShort(b, 32, 2);
        writeShort(b, 34, 16);
        writeStr(b, 36, "data");
        writeInt(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) writeShort(b, 44 + i * 2, pcm[i]);
        return b;
    }

    private static void writeStr(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void writeInt(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static void writeShort(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }
}
