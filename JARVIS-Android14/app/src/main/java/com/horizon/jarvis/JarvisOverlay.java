package com.horizon.jarvis;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

final class JarvisOverlay {
    interface Listener {
        void onCommand(String query);
        void onStop();
    }
    private static final int CYAN = 0xFF26E5FF;
    private final Context ctx;
    private final WindowManager manager;
    private final Listener listener;
    private View current;
    private WindowManager.LayoutParams params;
    private boolean expanded;
    private TextView latest;
    private String lastText = "Система активна. Скажите «Джарвис».";
    private int x, y;
    private float downX, downY;
    private int originX, originY;
    private boolean moved;

    JarvisOverlay(Context context, Listener callback) {
        ctx = context;
        listener = callback;
        manager = (WindowManager)ctx.getSystemService(Context.WINDOW_SERVICE);
        x = ctx.getResources().getDisplayMetrics().widthPixels - dp(88);
        y = ctx.getResources().getDisplayMetrics().heightPixels / 3;
    }

    private int dp(float value) {
        return (int)(value * ctx.getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private TextView label(String s, int size, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        return t;
    }

    void show() {
        if (!Settings.canDrawOverlays(ctx)) return;
        if (current == null) draw(false);
    }

    private WindowManager.LayoutParams layout(boolean full) {
        int width = full ? Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(24), dp(430)) : dp(74);
        int height = full ? dp(312) : dp(74);
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | (full ? WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags, android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = full ? dp(12) : Math.max(0, x);
        lp.y = full ? dp(75) : Math.max(0, y);
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        lp.setTitle("JARVIS AI floating assistant");
        return lp;
    }

    private void draw(boolean full) {
        if (!Settings.canDrawOverlays(ctx)) return;
        remove();
        expanded = full;
        current = full ? panel() : bubble();
        params = layout(full);
        try { manager.addView(current, params); }
        catch (Exception e) { current = null; params = null; }
    }

    private View bubble() {
        FrameLayout shell = new FrameLayout(ctx);
        shell.setBackground(shape(0xE90A263D, CYAN, 100));
        shell.setElevation(dp(14));
        TextView orb = label("◎", 52, CYAN);
        orb.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        orb.setGravity(Gravity.CENTER);
        shell.addView(orb, new FrameLayout.LayoutParams(-1, -1));
        TextView j = label("J", 18, Color.WHITE);
        j.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        j.setGravity(Gravity.CENTER);
        shell.addView(j, new FrameLayout.LayoutParams(-1, -1));
        shell.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX();
                downY = event.getRawY();
                originX = x; originY = y;
                moved = false;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int dx = (int)(event.getRawX() - downX);
                int dy = (int)(event.getRawY() - downY);
                if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                if (moved && params != null) {
                    x = Math.max(0, Math.min(ctx.getResources().getDisplayMetrics().widthPixels - dp(74), originX + dx));
                    y = Math.max(dp(18), Math.min(ctx.getResources().getDisplayMetrics().heightPixels - dp(110), originY + dy));
                    params.x = x; params.y = y;
                    try { manager.updateViewLayout(current, params); } catch (Exception ignored) {}
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (!moved) draw(true);
                return true;
            }
            return true;
        });
        return shell;
    }

    private Button button(String s) {
        Button b = new Button(ctx);
        b.setText(s);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(shape(0xFF12374E, 0xFF24779E, 9));
        return b;
    }

    private View panel() {
        LinearLayout shell = new LinearLayout(ctx);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(17), dp(12), dp(17), dp(13));
        shell.setBackground(shape(0xF6081A2A, CYAN, 18));
        shell.setElevation(dp(18));
        LinearLayout heading = new LinearLayout(ctx);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label("◉  J.A.R.V.I.S.", 21, CYAN);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(name, new LinearLayout.LayoutParams(0, dp(43), 1f));
        Button close = button("—");
        heading.addView(close, new LinearLayout.LayoutParams(dp(49), dp(42)));
        close.setOnClickListener(v -> draw(false));
        shell.addView(heading);

        TextView sub = label("ГОЛОСОВОЙ ПОМОЩНИК  •  ONLINE", 10, 0xFF78A6C2);
        sub.setLetterSpacing(.06f);
        shell.addView(sub);

        TextView decorative = label("◉       ─────    AI CORE ACTIVE    ─────       ◉", 13, CYAN);
        decorative.setGravity(Gravity.CENTER);
        decorative.setPadding(0, dp(11), 0, dp(11));
        shell.addView(decorative);

        ScrollView scroll = new ScrollView(ctx);
        latest = label(lastText, 14, Color.WHITE);
        latest.setPadding(dp(10), dp(12), dp(10), dp(12));
        latest.setBackground(shape(0xFF092C42, 0xFF1A526F, 10));
        scroll.addView(latest);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, dp(77)));

        EditText ask = new EditText(ctx);
        ask.setTextColor(Color.WHITE);
        ask.setHintTextColor(0xFF6D91A8);
        ask.setTextSize(13);
        ask.setSingleLine(true);
        ask.setHint("Спросите что-нибудь...");
        shell.addView(ask, new LinearLayout.LayoutParams(-1, dp(49)));

        LinearLayout row = new LinearLayout(ctx);
        Button send = button("➤  Спросить");
        Button stop = button("■  Стоп");
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(47), 2f);
        left.rightMargin = dp(8);
        row.addView(send, left);
        row.addView(stop, new LinearLayout.LayoutParams(0, dp(47), 1f));
        shell.addView(row);

        send.setOnClickListener(v -> {
            String question = ask.getText().toString().trim();
            if (!question.isEmpty()) {
                ask.setText("");
                ((InputMethodManager)ctx.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(ask.getWindowToken(), 0);
                ask.clearFocus();
                listener.onCommand(question);
            }
        });
        stop.setOnClickListener(v -> listener.onStop());
        return shell;
    }

    void update(String message) {
        lastText = message;
        if (expanded && latest != null) latest.setText(message);
    }

    void remove() {
        if (current != null) {
            try { manager.removeView(current); } catch (Exception ignored) {}
            current = null;
        }
        latest = null;
    }
}
