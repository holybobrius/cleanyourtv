package com.gathin.tvhome;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * The minimal header's account button: which Google account is signed in, and the name and picture the theme gives
 * it (account_profiles). Android does not hand a launcher the account's own photo, so the theme carries it.
 */
final class Profiles {
    static final String GOOGLE = "com.google";

    static final class Profile {
        /** The account's address, or null when no account could be read (the first profile line stands in). */
        final String account;
        final String name;
        /** The theme's file for it in avatars/, or null. */
        final String picture;

        Profile(String account, String name, String picture) {
            this.account = account;
            this.name = name;
            this.picture = picture;
        }
    }

    private Profiles() {}

    /**
     * The profile to show. A launcher sees a Google account only once the person picked it in Android's account
     * chooser (the account button opens it), so with none in sight the theme's first line is shown.
     */
    static Profile current(Context context, List<String> lines, String chosen) {
        Account[] accounts;
        try {
            accounts = AccountManager.get(context).getAccountsByType(GOOGLE);
        } catch (RuntimeException e) {
            accounts = new Account[0];
        }
        String account = null;
        for (Account a : accounts) {
            if (a.name.equalsIgnoreCase(chosen)) account = a.name;
        }
        if (account == null && accounts.length > 0) account = accounts[0].name;
        if (account == null) {
            String[] first = lines.isEmpty() ? null : split(lines.get(0));
            return first != null ? new Profile(null, first[1], first[2]) : new Profile(null, "", null);
        }
        for (String line : lines) {
            String[] p = split(line);
            if (p != null && matches(p[0], account)) return new Profile(account, p[1], p[2]);
        }
        return new Profile(account, localPart(account), null);
    }

    /** "account|name|picture" into its three parts (picture may be empty); null for a line without a name. */
    static String[] split(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 2 || p[1].trim().isEmpty()) return null;
        String picture = p.length > 2 && !p[2].trim().isEmpty() ? p[2].trim() : null;
        return new String[]{p[0].trim(), p[1].trim(), picture};
    }

    /** A profile line's account matches the full address or the part before the @, in any case. */
    static boolean matches(String key, String account) {
        if (key.isEmpty() || account == null) return false;
        String k = key.toLowerCase(Locale.ROOT), a = account.toLowerCase(Locale.ROOT);
        return k.equals(a) || k.equals(localPart(a));
    }

    static String localPart(String account) {
        int at = account.indexOf('@');
        return at > 0 ? account.substring(0, at) : account;
    }

    /** The theme's picture cut to a circle of this size, or null when there is none or it does not decode. */
    static Bitmap circle(File themeDir, String picture, int size) {
        if (themeDir == null || picture == null || !ThemeFiles.collectionImage(picture)) return null;
        File file = new File(new File(themeDir, ThemeFiles.AVATARS), picture);
        if (!file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        int side = Math.min(bounds.outWidth, bounds.outHeight);
        opts.inSampleSize = 1;
        while (side / (opts.inSampleSize * 2) >= size) opts.inSampleSize *= 2;
        Bitmap source = BitmapFactory.decodeFile(file.getPath(), opts);
        if (source == null) return null;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int crop = Math.min(source.getWidth(), source.getHeight());
        Rect src = new Rect((source.getWidth() - crop) / 2, (source.getHeight() - crop) / 2, 0, 0);
        src.right = src.left + crop;
        src.bottom = src.top + crop;
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float scale = size / (float) crop;
        m.setScale(scale, scale);
        m.preTranslate(-src.left, -src.top);
        shader.setLocalMatrix(m);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        new Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, paint);
        source.recycle();
        return out;
    }
}
