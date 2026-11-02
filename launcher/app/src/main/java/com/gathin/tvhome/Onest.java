package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Onest, the variable font of the app list and the minimal header, at a fixed weight. */
final class Onest {
    /** Built faces by weight: switching a typeface is cheap, building one is not. */
    private static final SparseArray<Typeface> FACES = new SparseArray<>();

    private Onest() {}

    /** Onest at one weight of its variable axis, built from a copy of the font file; the plain font if that fails. */
    static synchronized Typeface face(Context context, int weight) {
        Typeface cached = FACES.get(weight);
        if (cached != null) return cached;
        Typeface plain = context.getResources().getFont(R.font.onest);
        File file = new File(context.getCacheDir(), "onest.ttf");
        Typeface face;
        try {
            if (!file.exists()) {
                try (InputStream in = context.getResources().openRawResource(R.font.onest);
                     OutputStream out = new FileOutputStream(file)) {
                    byte[] buffer = new byte[16384];
                    for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
                }
            }
            face = new Typeface.Builder(file).setFontVariationSettings("'wght' " + weight).build();
        } catch (IOException | RuntimeException e) {
            file.delete();
            face = null;
        }
        if (face == null) return plain;
        FACES.put(weight, face);
        return face;
    }
}
