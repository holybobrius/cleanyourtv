package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * minimal_header: the header as one row. The time and date sit on the left, on the tiles' edge; on the right, side by
 * side, a round menu button that opens a side panel with what the pills used to hold, and the Google account's
 * picture at the far edge, where Google TV keeps it. A focused button turns white and opens out into its label; both
 * sit in one row pinned to the right, so a button opening out pushes its neighbour along. The palette stays neutral.
 */
final class MinimalHeader {

    interface Host {
        /** Down from the header: back to the tiles. */
        void onHeaderDown();
        /** OK on the account button. */
        void onAccountButton();
    }

    /** A row of the side panel. */
    static final class Item {
        final int icon;
        final String label;
        /** The row's state on its right ("Вкл."), or null. Read each time the panel opens. */
        final Supplier<String> value;
        final Runnable action;
        /** A thin line above the row. */
        final boolean divided;
        /** Set for a row that opens a list to pick from in the panel, instead of running an action. */
        final Chooser chooser;

        Item(int icon, String label, Supplier<String> value, Runnable action, boolean divided) {
            this(icon, label, value, action, divided, null);
        }

        Item(int icon, String label, Supplier<String> value, Runnable action, boolean divided, Chooser chooser) {
            this.icon = icon;
            this.label = label;
            this.value = value;
            this.action = action;
            this.divided = divided;
            this.chooser = chooser;
        }
    }

    /** A list a menu row opens in the panel: OK on one of its names picks it and closes the menu. */
    interface Chooser {
        List<String> names();
        int selected();
        void pick(int index);
    }

    private static final int REST_FILL = 0x1FFFFFFF, ON_FILL = Color.WHITE, ON_INK = 0xFF121316;
    private static final int PANEL_FILL = 0xFA141519;
    private static final Interpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final int FOCUS_MS = 200, OPEN_MS = 260, CLOSE_MS = 180;
    /** Button height (dp), and how small the picture gets inside the white pill. */
    private static final float BUTTON_DP = 32f, AVATAR_IN_PILL = 0.8125f;
    /** How far right the closed panel sits: its width and its margin. */
    private static final float PANEL_AWAY_DP = 300f + 20f;

    private final Activity activity;
    private final ViewGroup overlayParent;
    private final Host host;
    private final boolean animate;
    private final float density;
    private final ArgbEvaluator argb = new ArgbEvaluator();

    private final LinearLayout menuButton, accountButton, buttons;
    private final TextView accountName;
    private final ImageView avatar;
    private List<Item> items;

    private FrameLayout overlay;
    private View scrim;
    private LinearLayout panel;
    private boolean open;
    /** The menu row whose list the panel shows, or null for the menu itself. */
    private Item choosing;

    MinimalHeader(Activity activity, RelativeLayout header, ViewGroup overlayParent, Host host, boolean animate) {
        this.activity = activity;
        this.overlayParent = overlayParent;
        this.host = host;
        this.animate = animate;
        density = activity.getResources().getDisplayMetrics().density;

        FrameLayout row = new FrameLayout(activity);
        row.setClipChildren(false);
        RelativeLayout.LayoutParams rowLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        rowLp.topMargin = dp(5);
        header.addView(row, rowLp);

        // The two buttons, pinned to the right edge: whichever opens out, the row grows leftwards and the other moves.
        buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setClipChildren(false);
        row.addView(buttons, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(BUTTON_DP), Gravity.END | Gravity.TOP));

        // Menu: a hamburger in a faint circle; focused, a white pill reading "Menu".
        menuButton = button();
        ImageView menuIcon = new ImageView(activity);
        menuIcon.setImageResource(R.drawable.ic_menu);
        menuIcon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        menuButton.addView(menuIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView menuLabel = label(activity.getString(R.string.menu));
        menuButton.addView(menuLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        menuButton.setContentDescription(activity.getString(R.string.menu));
        menuButton.setPadding(dp(7), 0, dp(7), 0);
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(BUTTON_DP));
        menuLp.rightMargin = dp(10);
        buttons.addView(menuButton, menuLp);
        wire(menuButton, menuLabel, menuIcon, null, REST_FILL, dp(7), dp(7), dp(7), dp(14));
        menuButton.setOnClickListener(v -> openMenu());

        // The date over the time, on the left: a quiet line, then the time large.
        LinearLayout clock = new LinearLayout(activity);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock date = new CapitalizedClock(activity);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        text(date, 400, 13, 0xA8FFFFFF);
        // Wrap, not the vertical layout's default match_parent: a match_parent TextView does not ask for a new
        // layout when its text grows, and the date stayed as wide as the time it was first measured beside.
        clock.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        text(time, 500, 28, Color.WHITE);
        time.setLetterSpacing(-0.01f);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(5);
        clock.addView(time, timeLp);
        row.addView(clock, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP));
        // The buttons sit on the middle of the two lines as they are seen, from the top of the date's capitals down
        // to the time's baseline (its digits have nothing below it), so both sides of the header share one height.
        clock.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            android.graphics.Rect caps = new android.graphics.Rect();
            date.getPaint().getTextBounds("H", 0, 1, caps);
            float inkTop = date.getTop() + date.getBaseline() + caps.top;
            float inkBottom = time.getTop() + time.getBaseline();
            buttons.setTranslationY((inkTop + inkBottom) / 2f - dp(BUTTON_DP) / 2f);
        });

        // Account: the picture alone; focused, a white pill with the name and the picture inset in it.
        accountButton = button();
        accountName = label("");
        accountButton.addView(accountName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        avatar = new ImageView(activity);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        accountButton.addView(avatar, new LinearLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP)));
        buttons.addView(accountButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(BUTTON_DP)));
        wire(accountButton, accountName, null, avatar, Color.TRANSPARENT, 0, dp(14), 0, 0);
        accountButton.setOnClickListener(v -> host.onAccountButton());
    }

    View menuButton() {
        return menuButton;
    }

    /** The menu and account buttons together, for the idle fade: the date and time stay. */
    View buttons() {
        return buttons;
    }

    void setItems(List<Item> items) {
        this.items = items;
    }

    /** The account's name and picture; without a picture, the name's first letter on a neutral disc. */
    void setProfile(String name, Bitmap picture) {
        accountName.setText(name);
        accountButton.setContentDescription(name);
        if (picture != null) {
            avatar.setImageBitmap(picture);
            avatar.setBackground(null);
        } else {
            avatar.setImageDrawable(new Monogram(name, density));
        }
        accountName.setTag(null);   // measure the new name on the next focus
    }

    boolean isMenuOpen() {
        return open;
    }

    // ---- header buttons ---------------------------------------------------------------------------

    private LinearLayout button() {
        LinearLayout b = new LinearLayout(activity);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setClickable(true);
        b.setClipToPadding(false);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(BUTTON_DP) / 2f);
        b.setBackground(fill);
        return b;
    }

    private TextView label(String text) {
        TextView t = new TextView(activity);
        text(t, 500, 13, ON_INK);
        t.setText(text);
        t.setSingleLine(true);
        t.setAlpha(0f);
        return t;
    }

    private void text(TextView t, int weight, int sp, int color) {
        t.setTypeface(Onest.face(activity, weight));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(0.01f);
    }

    /**
     * Focus opens the button out: the fill turns white, the label grows in from nothing, the icon inks dark and the
     * picture shrinks into the pill. One animator drives it all, so the parts never drift apart.
     */
    private void wire(LinearLayout b, TextView label, ImageView icon, ImageView picture, int restFill,
                      int restStart, int onStart, int restEnd, int onEnd) {
        GradientDrawable fill = (GradientDrawable) b.getBackground();
        fill.setColor(restFill);
        float[] state = {0f};
        ValueAnimator[] running = {null};
        Runnable apply = () -> {
            float t = state[0];
            fill.setColor((int) argb.evaluate(t, restFill, ON_FILL));
            if (icon != null) icon.setImageTintList(ColorStateList.valueOf((int) argb.evaluate(t, Color.WHITE, ON_INK)));
            if (picture != null) {
                float s = 1f + (AVATAR_IN_PILL - 1f) * t;
                picture.setScaleX(s);
                picture.setScaleY(s);
            }
            if (label.getTag() == null) {
                label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                label.setTag(label.getMeasuredWidth());
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) label.getLayoutParams();
            lp.width = Math.round((Integer) label.getTag() * t);
            int gap = Math.round(dp(8) * t);
            if (icon != null) lp.leftMargin = gap;
            else lp.rightMargin = gap;
            label.setLayoutParams(lp);
            label.setAlpha(t * t);
            b.setPadding(Math.round(restStart + (onStart - restStart) * t), 0,
                    Math.round(restEnd + (onEnd - restEnd) * t), 0);
        };
        apply.run();
        b.setOnFocusChangeListener((v, focused) -> {
            if (running[0] != null) running[0].cancel();
            float target = focused ? 1f : 0f;
            if (!animate) {
                state[0] = target;
                apply.run();
                return;
            }
            ValueAnimator a = ValueAnimator.ofFloat(state[0], target);
            a.setDuration(FOCUS_MS);
            a.setInterpolator(EASE);
            a.addUpdateListener(an -> {
                state[0] = (float) an.getAnimatedValue();
                apply.run();
            });
            running[0] = a;
            a.start();
        });
        b.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.onHeaderDown();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return b == menuButton;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return b == accountButton;
                default:
                    return false;
            }
        });
    }

    // ---- side panel -------------------------------------------------------------------------------

    void openMenu() {
        if (open || items == null || items.isEmpty()) return;
        open = true;
        if (overlay == null) buildOverlay();
        choosing = null;
        View first = fillRows(activity.getString(R.string.menu), items, 0);
        overlay.bringToFront();
        overlay.setVisibility(View.VISIBLE);
        float away = PANEL_AWAY_DP * density;
        if (animate) {
            scrim.setAlpha(0f);
            panel.setTranslationX(away);
            scrim.animate().alpha(1f).setDuration(OPEN_MS).setInterpolator(EASE).start();
            panel.animate().translationX(0f).setDuration(OPEN_MS).setInterpolator(EASE).start();
        } else {
            scrim.setAlpha(1f);
            panel.setTranslationX(0f);
        }
        first.requestFocus();
    }

    /** Back in the panel: from a list to the menu, from the menu out. */
    void back() {
        if (choosing == null) {
            closeMenu(null);
            return;
        }
        Item from = choosing;
        choosing = null;
        fillRows(activity.getString(R.string.menu), items, items.indexOf(from)).requestFocus();
    }

    private void showChoices(Item item) {
        choosing = item;
        List<String> names = item.chooser.names();
        int selected = item.chooser.selected();
        List<Item> rows = new java.util.ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int index = i;
            rows.add(new Item(i == selected ? R.drawable.ic_check : 0, names.get(i), null,
                    () -> item.chooser.pick(index), false));
        }
        fillRows(item.label, rows, Math.max(0, selected)).requestFocus();
    }

    /** Closes the panel, then runs after (or nothing); focus goes back to the menu button. */
    void closeMenu(Runnable after) {
        if (!open) return;
        open = false;
        menuButton.requestFocus();
        Runnable done = () -> {
            overlay.setVisibility(View.GONE);
            if (after != null) after.run();
        };
        if (!animate) {
            done.run();
            return;
        }
        scrim.animate().alpha(0f).setDuration(CLOSE_MS).setInterpolator(EASE).start();
        panel.animate().translationX(PANEL_AWAY_DP * density).setDuration(CLOSE_MS).setInterpolator(EASE)
                .withEndAction(done).start();
    }

    /** Home: shut at once, with no animation and nothing run after. */
    void closeNow() {
        if (!open) return;
        open = false;
        scrim.animate().cancel();
        panel.animate().cancel();
        overlay.setVisibility(View.GONE);
    }

    private void buildOverlay() {
        overlay = new FrameLayout(activity);
        overlay.setVisibility(View.GONE);
        scrim = new View(activity);
        scrim.setBackgroundColor(0x8C000000);
        overlay.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL_FILL);
        bg.setCornerRadius(dp(18));
        panel.setBackground(bg);
        panel.setPadding(dp(12), dp(20), dp(12), dp(20));
        // On the right, under the menu button, the side Google TV's own settings panel comes in from.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END);
        lp.setMargins(0, dp(20), dp(20), dp(20));
        overlay.addView(panel, lp);
        overlayParent.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Fills the panel with a title and these rows; returns the row at focusIndex. */
    private View fillRows(String heading, List<Item> rows, int focusIndex) {
        panel.removeAllViews();
        TextView title = new TextView(activity);
        text(title, 500, 12, 0x80FFFFFF);
        title.setText(heading);
        title.setAllCaps(true);
        title.setLetterSpacing(0.08f);
        title.setPadding(dp(13), 0, dp(13), dp(12));
        panel.addView(title);
        List<View> views = new java.util.ArrayList<>();
        for (Item item : rows) {
            if (item.divided) {
                View line = new View(activity);
                line.setBackgroundColor(0x1AFFFFFF);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, dp(1)));
                lp.setMargins(dp(13), dp(6), dp(13), dp(6));
                panel.addView(line, lp);
            }
            View row = row(item);
            panel.addView(row);
            views.add(row);
        }
        View first = views.get(0), last = views.get(views.size() - 1);
        for (View child : views) {
            child.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        return v == first;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        return v == last;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        back();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;
                    default:
                        return false;
                }
            });
        }
        return views.get(Math.min(Math.max(0, focusIndex), views.size() - 1));
    }

    private View row(Item item) {
        LinearLayout r = new LinearLayout(activity);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setPadding(dp(13), 0, dp(15), 0);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(21));
        fill.setColor(Color.TRANSPARENT);
        r.setBackground(fill);
        ImageView icon = new ImageView(activity);
        if (item.icon != 0) icon.setImageResource(item.icon);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        r.addView(icon, new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView label = new TextView(activity);
        text(label, 400, 14, Color.WHITE);
        label.setText(item.label);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.leftMargin = dp(12);
        r.addView(label, labelLp);
        String value = item.value != null ? item.value.get() : null;
        TextView valueView = null;
        if (value != null && !value.isEmpty()) {
            valueView = new TextView(activity);
            text(valueView, 400, 12, 0x80FFFFFF);
            valueView.setText(value);
            valueView.setSingleLine(true);
            valueView.setMaxWidth(dp(110));
            valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(valueView);
        }
        TextView v = valueView;
        r.setOnFocusChangeListener((view, focused) -> {
            fill.setColor(focused ? ON_FILL : Color.TRANSPARENT);
            int ink = focused ? ON_INK : Color.WHITE;
            icon.setImageTintList(ColorStateList.valueOf(ink));
            label.setTextColor(ink);
            if (v != null) v.setTextColor(focused ? 0x8C000000 : 0x80FFFFFF);
        });
        r.setOnClickListener(view -> {
            if (item.chooser != null) showChoices(item);
            else closeMenu(item.action);
        });
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        return r;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    /** A TextClock whose text starts with a capital: "Понедельник, 2 ноября", not "понедельник". */
    static final class CapitalizedClock extends TextClock {
        CapitalizedClock(Context context) {
            super(context);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            if (text != null && text.length() > 0 && Character.isLowerCase(text.charAt(0))) {
                String s = text.toString();
                text = s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
            }
            super.setText(text, type);
        }
    }

    /** The account's first letter on a neutral disc, for a profile without a picture. */
    private static final class Monogram extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint disc = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint ink = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final String letter;

        Monogram(String name, float density) {
            letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.getDefault());
            disc.setColor(0xFF3A3C42);
            ink.setColor(Color.WHITE);
            ink.setTextAlign(android.graphics.Paint.Align.CENTER);
            ink.setTextSize(15 * density);
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, disc);
            android.graphics.Paint.FontMetrics fm = ink.getFontMetrics();
            canvas.drawText(letter, b.exactCenterX(), b.exactCenterY() - (fm.ascent + fm.descent) / 2f, ink);
        }

        @Override public void setAlpha(int alpha) { disc.setAlpha(alpha); ink.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { ink.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
