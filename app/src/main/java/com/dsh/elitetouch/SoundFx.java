package com.dsh.elitetouch;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Tiny synthesized retro SFX set, generated once into the cache. */
public class SoundFx {

    public static final int LASER = 0, LASER_ENEMY = 1, EXPLODE = 2, HIT = 3,
            MISSILE = 4, DOCK = 5, JUMP = 6, CLICK = 7;
    private static final int SR = 22050;

    private SoundPool pool;
    private final int[] ids = new int[8];
    public boolean enabled = true;

    public SoundFx(Context ctx) {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build();
            File dir = new File(ctx.getCacheDir(), "sfx");
            if (!dir.exists()) dir.mkdirs();
            ids[LASER] = load(dir, "laser.wav", laser(false));
            ids[LASER_ENEMY] = load(dir, "laser2.wav", laser(true));
            ids[EXPLODE] = load(dir, "boom.wav", explosion());
            ids[HIT] = load(dir, "hit.wav", hit());
            ids[MISSILE] = load(dir, "missile.wav", missile());
            ids[DOCK] = load(dir, "dock.wav", dock());
            ids[JUMP] = load(dir, "jump.wav", jump());
            ids[CLICK] = load(dir, "click.wav", click());
        } catch (Throwable t) {
            pool = null;
        }
    }

    private int load(File dir, String name, short[] pcm) {
        try {
            File f = new File(dir, name);
            if (!f.exists() || f.length() < 44) writeWav(f, pcm);
            return pool.load(f.getPath(), 1);
        } catch (Throwable t) {
            return 0;
        }
    }

    public void play(int which) {
        if (!enabled || pool == null || which < 0 || which >= ids.length) return;
        int id = ids[which];
        if (id == 0) return;
        float v = which == CLICK ? 0.35f : 0.55f;
        pool.play(id, v, v, 1, 0, 1.0f);
    }

    public void release() {
        if (pool != null) pool.release();
        pool = null;
    }

    // ------------------------------------------------------------------ synths

    private static short[] laser(boolean enemy) {
        int n = (int) (SR * 0.13);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double f = (enemy ? 750 : 1350) * Math.exp(-t * 18) + (enemy ? 95 : 240);
            phase += 2 * Math.PI * f / SR;
            double env = Math.exp(-t * (enemy ? 16 : 20));
            double v = Math.sin(phase);
            v = Math.signum(v) * 0.55 + v * 0.45;
            out[i] = clamp(v * env * 15000);
        }
        return out;
    }

    private static short[] explosion() {
        int n = (int) (SR * 0.75);
        short[] out = new short[n];
        Random r = new Random(7);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double white = r.nextDouble() * 2 - 1;
            lp = lp * 0.90 + white * 0.10;
            double env = Math.exp(-t * 4.6);
            double thump = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-t * 8) * 0.5;
            out[i] = clamp((lp * 1.5 + thump) * env * 26000);
        }
        return out;
    }

    private static short[] hit() {
        int n = (int) (SR * 0.14);
        short[] out = new short[n];
        Random r = new Random(13);
        double lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double white = r.nextDouble() * 2 - 1;
            lp = lp * 0.7 + white * 0.3;
            out[i] = clamp(lp * Math.exp(-t * 26) * 18000);
        }
        return out;
    }

    private static short[] missile() {
        int n = (int) (SR * 0.55);
        short[] out = new short[n];
        Random r = new Random(21);
        double phase = 0, lp = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double f = 260 + 900 * (t / 0.55);
            phase += 2 * Math.PI * f / SR;
            double white = r.nextDouble() * 2 - 1;
            lp = lp * 0.85 + white * 0.15;
            double env = Math.sin(Math.PI * Math.min(1, t / 0.55));
            out[i] = clamp((Math.sin(phase) * 0.35 + lp * 0.9) * env * 14000);
        }
        return out;
    }

    private static short[] dock() {
        int n = (int) (SR * 0.65);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double v = 0;
            if (t < 0.22) {
                v = Math.sin(2 * Math.PI * 523 * t) * Math.exp(-t * 9);
            } else {
                double u = t - 0.22;
                v = Math.sin(2 * Math.PI * 784 * u) * Math.exp(-u * 7) * 0.9;
            }
            out[i] = clamp(v * 9000);
        }
        return out;
    }

    private static short[] jump() {
        int n = (int) (SR * 1.35);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double f = 120 + 1500 * Math.pow(t / 1.35, 1.7);
            phase += 2 * Math.PI * f / SR;
            double env = Math.sin(Math.PI * Math.min(1, t / 1.35));
            out[i] = clamp(Math.sin(phase) * env * 9000);
        }
        return out;
    }

    private static short[] click() {
        int n = (int) (SR * 0.04);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            out[i] = clamp(Math.sin(2 * Math.PI * 1150 * t) * Math.exp(-t * 90) * 8000);
        }
        return out;
    }

    private static short clamp(double v) {
        if (v > 32000) return 32000;
        if (v < -32000) return -32000;
        return (short) v;
    }

    private static void writeWav(File f, short[] pcm) throws Exception {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
        int dataBytes = pcm.length * 2;
        out.writeBytes("RIFF");
        le32(out, 36 + dataBytes);
        out.writeBytes("WAVEfmt ");
        le32(out, 16);
        le16(out, 1);
        le16(out, 1);
        le32(out, SR);
        le32(out, SR * 2);
        le16(out, 2);
        le16(out, 16);
        out.writeBytes("data");
        le32(out, dataBytes);
        for (short s : pcm) le16(out, s & 0xFFFF);
        out.close();
    }

    private static void le16(DataOutputStream o, int v) throws Exception {
        o.writeByte(v & 0xFF);
        o.writeByte((v >> 8) & 0xFF);
    }

    private static void le32(DataOutputStream o, int v) throws Exception {
        o.writeByte(v & 0xFF);
        o.writeByte((v >> 8) & 0xFF);
        o.writeByte((v >> 16) & 0xFF);
        o.writeByte((v >> 24) & 0xFF);
    }
}
