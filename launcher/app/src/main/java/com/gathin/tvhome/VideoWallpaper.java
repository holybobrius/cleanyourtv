package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A theme's video wallpaper, looping without sound behind Home: one of the videos/ collection (or a lone
 * wallpaper.mp4). It plays on a SurfaceView under the window, so the video's frames go straight to the display and
 * Home itself is not redrawn for them. The video's first frame, taken from the file once and kept in the cache,
 * covers it until it is playing, and again each time the player opens (Home shown again, the app list closed). It
 * plays only while Home is shown.
 */
final class VideoWallpaper implements SurfaceHolder.Callback {
    static final String FILE_NAME = "wallpaper.mp4";
    static final String FOLDER = "videos";
    private static final String TAG = "TvHome.Video";

    private final Context context;
    private final SurfaceView surface;
    private final ImageView poster;
    private final View shade;
    private final boolean dimmed;
    private final ExecutorService frames = Executors.newSingleThreadExecutor();
    /** The video on screen; null shows nothing (the picture wallpaper is up instead). */
    private File file;
    private MediaPlayer player;
    private boolean prepared, active, surfaceReady, closed;
    /** Where the loop was when the player was let go, so it picks up there instead of from the start. */
    private int position;

    VideoWallpaper(Context context, FrameLayout root, int dimPercent) {
        this.context = context;
        dimmed = dimPercent > 0;
        surface = new SurfaceView(context);
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface.getHolder().addCallback(this);
        surface.setVisibility(View.GONE);
        root.addView(surface, 0, full());
        poster = new ImageView(context);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        poster.setVisibility(View.GONE);
        root.addView(poster, 1, full());
        // wallpaper_dim, as a still shade over the video and its poster: drawn into Home's window once, not per frame.
        shade = new View(context);
        shade.setBackgroundColor(Color.argb(dimPercent * 255 / 100, 0, 0, 0));
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setVisibility(View.GONE);
        root.addView(shade, 2, full());
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /** The theme's videos: the videos/ collection by name, or else its lone wallpaper.mp4. Empty without a wallpaper. */
    static List<File> list(Context context, Config config) {
        List<File> videos = new ArrayList<>();
        File dir = Config.dir(context);
        if (!config.wallpaperEnabled || dir == null) return videos;
        File[] found = new File(dir, FOLDER).listFiles((d, name) -> isVideo(name));
        if (found != null) {
            Arrays.sort(found, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : found) if (f.length() > 0) videos.add(f);
        }
        File single = new File(dir, FILE_NAME);
        if (videos.isEmpty() && single.isFile() && single.length() > 0) videos.add(single);
        return videos;
    }

    static boolean isVideo(String name) {
        return name != null && !name.startsWith(".") && name.matches("[A-Za-z0-9_.-]+\\.(mp4|MP4)");
    }

    /** Plays this video, or with null takes the video wallpaper away. */
    void show(File video) {
        if (video == null ? file == null : video.equals(file)) return;
        release();
        file = video;
        position = 0;
        int visibility = video == null ? View.GONE : View.VISIBLE;
        surface.setVisibility(visibility);
        shade.setVisibility(dimmed ? visibility : View.GONE);
        if (video == null) {
            poster.setVisibility(View.GONE);
            poster.setImageDrawable(null);
            return;
        }
        loadPoster(video, null);
        if (active && surfaceReady) open();
    }

    /** How long one background takes to fade into the next. */
    static final int FADE_MS = 450;
    /** How long the first frame stays over the video once it is said to be playing. */
    private static final int POSTER_HOLD_MS = 250;

    /**
     * From the video on screen to another: the new one's first frame fades in over the old video, then the new video
     * starts under that frame, which is the same picture, and the frame fades away. One decoder at a time.
     */
    void crossfadeTo(File video) {
        if (video == null || video.equals(file) || file == null) {
            show(video);
            return;
        }
        File from = file;
        loadPoster(video, () -> {
            if (!from.equals(file) || closed) return;
            poster.animate().cancel();
            poster.setAlpha(0f);
            poster.setVisibility(View.VISIBLE);
            poster.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MS).withEndAction(() -> {
                if (!from.equals(file) || closed) return;
                release();
                file = video;
                position = 0;
                // A fresh surface for the new video: the old one keeps the last frame of the old video, which showed
                // for a frame between the poster going and the new video's first frame reaching the screen. Gone for
                // one frame under the poster, then back: surfaceCreated opens the new video on the new surface.
                surface.setVisibility(View.GONE);
                surface.postDelayed(() -> {
                    if (video.equals(file) && !closed) surface.setVisibility(View.VISIBLE);
                }, 50);
            }).start();
        });
    }

    /**
     * Gets this video ready under whatever covers the wallpaper (the picture), showing its first frame, and runs
     * ready once that frame is in place, so the cover can fade away onto it. The video itself starts with start().
     */
    void prepareUnder(File video, Runnable ready) {
        release();
        file = video;
        position = 0;
        surface.setVisibility(View.VISIBLE);
        shade.setVisibility(dimmed ? View.VISIBLE : View.GONE);
        loadPoster(video, () -> {
            if (!video.equals(file)) return;
            showPoster();
            ready.run();
        });
        if (active && surfaceReady) open();
    }

    void start() {
        active = true;
        if (file == null) return;
        if (player == null) {
            if (surfaceReady) open();
        } else if (prepared && !player.isPlaying()) {
            player.start();
        }
    }

    void stop() {
        active = false;
        if (player != null && prepared && player.isPlaying()) player.pause();
    }

    /** Frees the decoder; the next start opens it again where it left off. */
    void release() {
        if (player == null) return;
        if (prepared) position = player.getCurrentPosition();
        player.release();
        player = null;
        prepared = false;
        // The poster is left as it is: a release while the app list hides Home must not bring a full-screen picture
        // back under the list. open() puts it up again before the next first frame.
    }

    void close() {
        closed = true;
        active = false;
        release();
        frames.shutdownNow();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        if (active && player == null && file != null) open();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        release();
    }

    private void open() {
        // Once only: a second player cannot connect to the surface the first one holds ("already connected"), and
        // a switch from the picture can ask twice (the switch itself, then start or the new surface).
        if (closed || file == null || player != null) return;
        showPoster();
        MediaPlayer p = new MediaPlayer();
        player = p;
        try {
            p.setDataSource(file.getPath());
            p.setDisplay(surface.getHolder());
            p.setLooping(true);
            p.setVolume(0f, 0f);
            p.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            p.setOnPreparedListener(mp -> {
                if (mp != player) return;
                prepared = true;
                if (position > 0) mp.seekTo(position);
                if (active) mp.start();
            });
            p.setOnInfoListener((mp, what, extra) -> {
                if (mp == player && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) hidePoster();
                return false;
            });
            p.setOnErrorListener((mp, what, extra) -> {
                Log.w(TAG, "Video wallpaper failed: " + what + "/" + extra);
                if (mp == player) {
                    release();
                    showPoster();
                }
                return true;
            });
            p.prepareAsync();
        } catch (Exception e) {
            Log.w(TAG, "Video wallpaper failed to open", e);
            p.release();
            player = null;
            showPoster();
        }
    }

    private void hidePoster() {
        poster.animate().cancel();
        // A short wait first: the first frame is reported rendered a little before the TV's video plane shows it.
        poster.animate().alpha(0f).setStartDelay(POSTER_HOLD_MS).setDuration(300).withEndAction(() -> {
            // Gone, not just clear: a transparent full-screen picture would still be drawn on every Home frame.
            if (player != null) poster.setVisibility(View.GONE);
        }).start();
    }

    private void showPoster() {
        poster.animate().cancel();
        poster.animate().setStartDelay(0);
        poster.setAlpha(1f);
        poster.setVisibility(View.VISIBLE);
    }

    /**
     * The video's first frame: from the cache at once, else taken from the file in the background and kept. With
     * loaded, the frame goes up only when that runs (on the main thread, frame or no frame); without, at once.
     */
    private void loadPoster(File video, Runnable loaded) {
        if (loaded == null) poster.setImageDrawable(null);
        File cached = posterFile(video);
        if (cached.isFile()) {
            poster.setImageBitmap(decode(cached));
            if (loaded != null) loaded.run();
            return;
        }
        frames.execute(() -> {
            Bitmap frame = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(video.getPath());
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame != null) {
                    cached.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(cached)) {
                        frame.compress(Bitmap.CompressFormat.JPEG, 88, out);
                    }
                    frame.recycle();
                }
            } catch (Exception e) {
                Log.w(TAG, "No first frame for " + video.getName(), e);
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }
            Bitmap bitmap = cached.isFile() ? decode(cached) : null;
            poster.post(() -> {
                if (bitmap != null && (loaded != null || video.equals(file))) poster.setImageBitmap(bitmap);
                if (loaded != null) loaded.run();
            });
        });
    }

    /** Keyed by name, size and date, so a replaced video gets a new frame. */
    private File posterFile(File video) {
        String key = String.format(Locale.ROOT, "%s-%d-%d.jpg", video.getName(), video.length(), video.lastModified());
        return new File(new File(context.getCacheDir(), "video-posters"), key);
    }

    private static Bitmap decode(File jpeg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.HARDWARE;
        return BitmapFactory.decodeFile(jpeg.getPath(), o);
    }
}
