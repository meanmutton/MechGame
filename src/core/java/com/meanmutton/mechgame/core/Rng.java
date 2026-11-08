package com.meanmutton.mechgame.core;

import java.util.Random;

/**
 * java.util.Random's own generator, written out so that every device gives the same numbers and its state can be
 * saved and sent. An online battle runs on both players' devices at once and must roll the same dice on each: the
 * browser's Random isn't guaranteed to match Java's, and Random's state can't be read back.
 */
final class Rng extends Random {
    private static final long MULT = 0x5DEECE66DL, ADD = 0xBL, MASK = (1L << 48) - 1;
    private long s;

    Rng(long seed) {
        super(0);
        s = (seed ^ MULT) & MASK;
    }

    /** A generator in exactly the state another one was in (see {@link #state()}). */
    static Rng at(long state) {
        Rng r = new Rng(0);
        r.s = state & MASK;
        return r;
    }

    long state() {
        return s;
    }

    protected int next(int bits) {
        s = (s * MULT + ADD) & MASK;
        return (int) (s >>> (48 - bits));
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
        int bits, val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }

    public long nextLong() {
        return ((long) next(32) << 32) + next(32);
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }

    public float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public double nextDouble() {
        return (((long) next(26) << 27) + next(27)) * (1.0 / (1L << 53));
    }

    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1, v2, q;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            q = v1 * v1 + v2 * v2;
        } while (q >= 1 || q == 0);
        double m = Math.sqrt(-2 * Math.log(q) / q);
        nextNextGaussian = v2 * m;
        haveNextNextGaussian = true;
        return v1 * m;
    }
}
