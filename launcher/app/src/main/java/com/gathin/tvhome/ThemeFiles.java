package com.gathin.tvhome;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** Profile pictures for the header's account button (Profiles), one per Google account. */
    static final String AVATARS = "avatars";
    /** Tile pictures that replace an app's own banner, named by its package: banners/com.spotify.tv.android.png. */
    static final String BANNERS = "banners";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    || VideoWallpaper.FILE_NAME.equals(name) ? name : null;
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        if (segments.size() == 2 && (AVATARS.equals(segments.get(0)) || BANNERS.equals(segments.get(0)))
                && collectionImage(segments.get(1))) {
            return segments.get(0) + "/" + segments.get(1);
        }
        if (segments.size() == 2 && VideoWallpaper.FOLDER.equals(segments.get(0))
                && (VideoWallpaper.isVideo(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return VideoWallpaper.FOLDER + "/" + segments.get(1);
        }
        return null;
    }

    /** "wallpapers", "avatars", "banners" or "videos" alone: a delete of it clears that folder's files. Null for anything else. */
    static String collection(List<String> segments) {
        if (segments == null || segments.size() != 1) return null;
        String name = segments.get(0);
        return WALLPAPERS.equals(name) || AVATARS.equals(name) || BANNERS.equals(name)
                || VideoWallpaper.FOLDER.equals(name) ? name : null;
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
