package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every app as a poster-like list: a square icon as tall as the capitals, then the name in large Onest capitals.
 * The selected row is white and a step bolder and stays in the middle of the screen; the others are dimmed. It opens
 * over Home's wallpaper, darkened almost to black, on Down from the dock (rows_below_dock 0).
 */
final class AppListOverlay {

    interface Host {
        void onListLaunch(int position);

        void onListMenu(int position, View anchor);

        void onListClosed();
    }

    /** Names too long for the list, shortened so the rest can stay large. */
    private static final Map<String, String> SHORT_NAMES = new HashMap<>();

    static {
        SHORT_NAMES.put("Медиапроигрыватель", "Медиаплеер");
    }

    private static final int REST_COLOR = 0xA6FFFFFF, ON_COLOR = Color.WHITE;
    private static final int REST_WEIGHT = 400, ON_WEIGHT = 500;
    private static final float REST_ICON = 0.5f;
    /** Onest's capital height, as a share of the font size. */
    private static final float CAP = 0.73f;
    /** Row pitch as a share of the font size, and the gap between the icon and the name (dp). */
    private static final float ROW = 1.12f, GAP_DP = 15f;

    private final Activity activity;
    private final AppAdapter apps;
    private final Host host;
    private final float density;
    private final boolean animate;
    private final Typeface onest;
    /** Onest at the rest and the selected weight, built once: switching a typeface is cheap, building one is not. */
    private final Typeface restFace, onFace;
    private final FrameLayout root;
    private final RecyclerView list;
    private final List<Integer> positions = new ArrayList<>();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private float textPx;
    private int rowPx, iconPx;
    /** Moves the name so the middle of its capitals lines up with the middle of the icon. */
    private float capShift;
    /** The name's natural height, from ascent to descent: kept even where it is taller than a row. */
    private int textBoxPx;

    AppListOverlay(Activity activity, ViewGroup parent, AppAdapter apps, Host host, boolean animate) {
        this.activity = activity;
        this.apps = apps;
        this.host = host;
        this.animate = animate;
        density = activity.getResources().getDisplayMetrics().density;
        onest = activity.getResources().getFont(R.font.onest);
        restFace = Onest.face(activity, REST_WEIGHT);
        onFace = Onest.face(activity, ON_WEIGHT);

        root = new FrameLayout(activity);
        // Opaque: open() paints the wallpaper, darkened, into it, and hides everything under it while the list shows.
        root.setBackgroundColor(Color.BLACK);
        root.setVisibility(View.GONE);
        root.setClickable(true);

        list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity) {
            // The selected row is centred by centre(); the stock "just bring it on screen" scroll would fight it.
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull android.graphics.Rect rect, boolean immediate,
                                                         boolean focusedChildVisible) {
                return false;
            }
        });
        list.setItemAnimator(null);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setHasFixedSize(true);
        list.setClipChildren(false);
        FrameLayout.LayoutParams listLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        listLp.topMargin = dp(60);
        root.addView(list, listLp);
        // The ends of the list fade into the dark: two plain gradients drawn over it. A fading edge would draw the
        // whole list into an offscreen layer on every frame of a scroll.
        root.addView(fade(true), edgeLp(true));
        root.addView(fade(false), edgeLp(false));

        // A quiet line on top, as in a portfolio: what this is on the left, the time on the right.
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(57), 0, dp(57), 0);
        View dot = new View(activity);
        android.graphics.drawable.GradientDrawable round = new android.graphics.drawable.GradientDrawable();
        round.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        round.setColor(Color.WHITE);
        dot.setBackground(round);
        top.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView title = smallText();
        title.setText(R.string.all_apps);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(7);
        top.addView(title, titleLp);
        TextClock clock = new TextClock(activity);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        styleSmall(clock);
        top.addView(clock);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        topLp.topMargin = dp(24);
        root.addView(top, topLp);

        parent.addView(root, parent.indexOfChild(parent.findViewById(R.id.glass)),
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View fade(boolean top) {
        View v = new View(activity);
        v.setBackground(new android.graphics.drawable.GradientDrawable(top
                ? android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                : android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                top ? new int[]{0xF2000000, 0xF2000000, 0x00000000} : new int[]{0xF2000000, 0x00000000}));
        return v;
    }

    private FrameLayout.LayoutParams edgeLp(boolean top) {
        // The top one starts at the screen's edge, behind the title, so it has no step where the list begins.
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(top ? 116 : 56),
                top ? Gravity.TOP : Gravity.BOTTOM);
    }

    boolean isOpen() {
        return root.getVisibility() == View.VISIBLE;
    }

    void open() {
        positions.clear();
        for (int i = 0; i < apps.appCount(); i++) {
            if (apps.isListableAt(i)) positions.add(i);
        }
        if (positions.isEmpty()) return;
        backdrop();
        root.setVisibility(View.VISIBLE);
        root.post(() -> {
            fitText();
            // No padding: the fading edges are drawn from the padding lines, and the first and last rows should rest at
            // the list's ends; centre() keeps the selected row in the middle wherever there is room to scroll.
            list.setPadding(0, 0, 0, 0);
            list.setAdapter(new Rows());
            list.scrollToPosition(0);
            list.post(() -> {
                RecyclerView.ViewHolder first = list.findViewHolderForAdapterPosition(0);
                if (first != null) first.itemView.requestFocus();
            });
        });
        root.animate().cancel();
        list.animate().cancel();
        if (animate) {
            root.setAlpha(0f);
            list.setTranslationY(dp(24));
            root.animate().alpha(1f).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
            list.animate().translationY(0f).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            root.setAlpha(1f);
            list.setTranslationY(0f);
        }
    }

    void close(boolean quick) {
        if (!isOpen()) return;
        showUnder();
        root.animate().cancel();
        if (!animate || quick) {
            root.setVisibility(View.GONE);
            list.setAdapter(null);
        } else {
            root.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                root.setVisibility(View.GONE);
                list.setAdapter(null);
            }).start();
        }
        host.onListClosed();
    }

    /** Views under the list hidden while it shows, with the visibility each had. */
    private final Map<View, Integer> hidden = new HashMap<>();
    private final Runnable hideUnder = this::hideUnder;

    /**
     * The wallpaper, darkened, as the list's own opaque background: a quarter-size copy drawn once. The views under
     * the list are hidden while it shows, so a scroll redraws one picture and the names, not the wallpaper, a dark
     * veil over it and the rest of Home, which this TV's GPU could not do in a frame.
     */
    private void backdrop() {
        ViewGroup parent = (ViewGroup) root.getParent();
        // The wallpaper: the root's first ImageView holding a picture. With a video wallpaper that is the video's
        // first frame (gone while the video plays, but still holding it); with the picture wallpaper, the picture.
        View wallpaper = null;
        for (int i = 0; i < parent.getChildCount() && wallpaper == null; i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView && ((ImageView) child).getDrawable()
                    instanceof android.graphics.drawable.BitmapDrawable) wallpaper = child;
        }
        int w = Math.max(1, parent.getWidth() / 4), h = Math.max(1, parent.getHeight() / 4);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        if (wallpaper != null && ((ImageView) wallpaper).getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            try {
                // The wallpaper is a GPU bitmap, which a software canvas cannot draw: read it back first (once per opening).
                android.graphics.Bitmap source = ((android.graphics.drawable.BitmapDrawable) ((ImageView) wallpaper).getDrawable()).getBitmap();
                if (source.getConfig() == android.graphics.Bitmap.Config.HARDWARE) {
                    source = source.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                }
                if (source != null) {
                    canvas.scale(0.25f, 0.25f);
                    canvas.concat(((ImageView) wallpaper).getImageMatrix());
                    canvas.drawBitmap(source, 0f, 0f, new Paint(Paint.FILTER_BITMAP_FLAG));
                    canvas.setMatrix(null);
                }
            } catch (RuntimeException e) {
                canvas.setMatrix(null);
                canvas.drawColor(Color.BLACK);
            }
        }
        canvas.drawColor(0xDB000000);
        android.graphics.drawable.BitmapDrawable bg = new android.graphics.drawable.BitmapDrawable(activity.getResources(), bitmap);
        bg.setFilterBitmap(true);
        root.setBackground(bg);
        // Hidden only once the list has faded in, so the fade shows Home underneath.
        root.postDelayed(hideUnder, animate ? 260 : 0);
    }

    private void hideUnder() {
        if (!isOpen()) return;
        ViewGroup parent = (ViewGroup) root.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == root || hidden.containsKey(child)) continue;
            hidden.put(child, child.getVisibility());
            child.setVisibility(View.INVISIBLE);
        }
    }

    private void showUnder() {
        root.removeCallbacks(hideUnder);
        for (Map.Entry<View, Integer> e : hidden.entrySet()) e.getKey().setVisibility(e.getValue());
        hidden.clear();
    }

    /** The largest size, up to 67dp, at which the longest name fits beside its icon. */
    private void fitText() {
        Paint paint = new Paint();
        paint.setTypeface(restFace);
        paint.setLetterSpacing(-0.01f);
        paint.setTextSize(100f);
        float widest = 0f;
        for (int p : positions) widest = Math.max(widest, paint.measureText(name(p)));
        // Measured at the regular weight; the selected row is a step bolder, so leave it a little room.
        widest *= 1.04f;
        float avail = root.getWidth() - 2 * dp(57) - dp(GAP_DP);
        // The icon is as wide as it is tall, CAP of the text size, so it takes its share of the width too.
        float size = 100f * avail / (widest + 100f * CAP);
        textPx = Math.min(dp(60), size);
        rowPx = Math.round(textPx * ROW);
        iconPx = Math.round(textPx * CAP);
        // The name's box runs from the font's ascent to its descent, so centring the box leaves the capitals low.
        // A row shorter than that box would squeeze it and push the text to its top, so the box keeps its natural
        // height (TextView's integer metrics) and may reach past the row; the rows do not clip it.
        paint.setTextSize(textPx);
        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        textBoxPx = fm.descent - fm.ascent;
        android.graphics.Rect caps = new android.graphics.Rect();
        paint.getTextBounds("H", 0, 1, caps);
        float capMiddle = -fm.ascent - caps.height() / 2f;   // from the box's top to the middle of the capitals
        capShift = textBoxPx / 2f - capMiddle;
    }

    private String name(int position) {
        String label = apps.labelAt(position);
        String shorter = SHORT_NAMES.get(label);
        return (shorter != null ? shorter : label).toUpperCase(Locale.getDefault());
    }

    private TextView smallText() {
        TextView t = new TextView(activity);
        styleSmall(t);
        return t;
    }

    private void styleSmall(TextView t) {
        t.setTypeface(onest);
        t.setFontVariationSettings("'wght' 500");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLetterSpacing(0.02f);
        t.setAllCaps(true);
        t.setTextColor(Color.WHITE);
        t.setIncludeFontPadding(false);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private final class Rows extends RecyclerView.Adapter<Row> {
        @NonNull
        @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(57), 0, dp(57), 0);
            row.setFocusable(true);
            row.setClickable(true);
            row.setLongClickable(true);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx));
            ImageView icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
            iconLp.rightMargin = dp(GAP_DP);
            row.addView(icon, iconLp);
            TextView text = new TextView(activity);
            text.setTypeface(restFace);
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx);
            text.setLetterSpacing(-0.01f);
            text.setIncludeFontPadding(false);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setTranslationY(capShift);
            row.setClipChildren(false);
            row.addView(text, new LinearLayout.LayoutParams(0, textBoxPx, 1f));
            return new Row(row, icon, text);
        }

        @Override
        public void onBindViewHolder(@NonNull Row holder, int index) {
            int position = positions.get(index);
            holder.text.setText(name(position));
            Drawable icon = apps.iconAt(position);
            holder.icon.setImageDrawable(icon);
            holder.setSelected(holder.itemView.hasFocus(), false);
        }

        @Override
        public int getItemCount() {
            return positions.size();
        }
    }

    private final class Row extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView text;
        float amount = -1f;
        ValueAnimator anim;
        /** The weight last given to the name, so it is only rebuilt when it changes. */
        int weight = REST_WEIGHT;

        Row(View view, ImageView icon, TextView text) {
            super(view);
            this.icon = icon;
            this.text = text;
            view.setOnFocusChangeListener((v, focused) -> {
                setSelected(focused, animate);
                if (focused) centre(v);
            });
            view.setOnClickListener(v -> {
                int index = getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) host.onListLaunch(positions.get(index));
            });
            view.setOnLongClickListener(v -> {
                int index = getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) host.onListMenu(positions.get(index), icon);
                return true;
            });
            view.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int index = getBindingAdapterPosition();
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && index == 0) {
                    close(false);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && index == positions.size() - 1) return true;
                return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            });
        }

        /** Colour, weight and icon between rest (0) and selected (1), animated or at once. */
        void setSelected(boolean on, boolean animated) {
            float target = on ? 1f : 0f;
            if (anim != null) anim.cancel();
            // A step bolder at once: a new weight builds a new typeface, too much work for every frame.
            int w = on ? ON_WEIGHT : REST_WEIGHT;
            if (w != weight) {
                weight = w;
                text.setTypeface(on ? onFace : restFace);
            }
            if (!animated || amount < 0f) {
                apply(target);
                return;
            }
            anim = ValueAnimator.ofFloat(amount, target);
            anim.setDuration(150);
            anim.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
            anim.start();
        }

        private void apply(float t) {
            amount = t;
            text.setTextColor((int) argb.evaluate(t, REST_COLOR, ON_COLOR));
            icon.setAlpha(REST_ICON + (1f - REST_ICON) * t);
        }
    }

    /** Scrolls so the selected row sits in the middle of the list. */
    private void centre(View row) {
        int dy = (row.getTop() + row.getBottom()) / 2 - list.getHeight() / 2;
        if (dy == 0) return;
        if (animate) list.smoothScrollBy(0, dy, new DecelerateInterpolator(), 180);
        else list.scrollBy(0, dy);
    }
}
