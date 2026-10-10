package com.horizon.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.Locale;

/**
 * Explicitly user-enabled Accessibility Service.
 * No keylogging, event storage, automated messaging, or screen capture.
 * Operations are started only by a voice/text command.
 */
public final class JarvisAccessibilityService extends AccessibilityService {
    private static volatile JarvisAccessibilityService current;

    static boolean isConnected() { return current != null; }

    static boolean execute(String command, String argument) {
        JarvisAccessibilityService service = current;
        if (service == null) return false;
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return false;
        switch (command) {
            case "back":
                return service.performGlobalAction(GLOBAL_ACTION_BACK);
            case "home":
                return service.performGlobalAction(GLOBAL_ACTION_HOME);
            case "recents":
                return service.performGlobalAction(GLOBAL_ACTION_RECENTS);
            case "notifications":
                return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            case "quick_settings":
                return service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
            case "scroll_down":
                return service.swipe(true);
            case "scroll_up":
                return service.swipe(false);
            case "tap_text":
                return service.tapText(argument);
            default:
                return false;
        }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        current = this;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally do not collect or forward the contents of other apps.
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    private boolean swipe(boolean down) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        Path gesture = new Path();
        float fromY = down ? .75f : .31f;
        float toY = down ? .32f : .77f;
        gesture.moveTo(width * .52f, height * fromY);
        gesture.lineTo(width * .52f, height * toY);
        GestureDescription description = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(gesture, 0, 420))
            .build();
        return dispatchGesture(description, null, null);
    }

    private boolean tapText(String requested) {
        if (requested == null || requested.trim().isEmpty()) return false;
        String target = requested.toLowerCase(Locale.ROOT).trim();
        // Risky actions must never be auto-confirmed through voice-only navigation.
        for (String blocked : new String[] {
            "плат", "перевест", "отправить", "купить", "удалить",
            "стереть", "подтвер", "разрешить", "заказ", "оплат"
        }) if (target.contains(blocked)) return false;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(requested);
            if (matches == null || matches.isEmpty()) return false;
            for (AccessibilityNodeInfo found : matches) {
                CharSequence text = found.getText();
                CharSequence description = found.getContentDescription();
                String t = (text == null ? "" : text.toString()).toLowerCase(Locale.ROOT);
                String d = (description == null ? "" : description.toString()).toLowerCase(Locale.ROOT);
                if (!t.contains(target) && !d.contains(target)) continue;
                AccessibilityNodeInfo node = found;
                for (int n = 0; n < 5 && node != null; n++) {
                    if (node.isVisibleToUser() && node.isClickable())
                        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    node = node.getParent();
                }
            }
            return false;
        } finally {
            root.recycle();
        }
    }
}
