package com.horizon.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit opt-in Accessibility actions. Never log window events, passwords or typed text.
 * All commands are initiated by the user. We do not automatically submit messages/payments.
 */
public final class JarvisAccessibilityService extends AccessibilityService {
    private static volatile JarvisAccessibilityService current;
    static boolean isConnected() { return current != null; }

    static boolean execute(String command, String argument) {
        JarvisAccessibilityService service = current;
        if (service == null || Looper.myLooper() != Looper.getMainLooper()) return false;
        switch (command) {
            case "back": return service.performGlobalAction(GLOBAL_ACTION_BACK);
            case "home": return service.performGlobalAction(GLOBAL_ACTION_HOME);
            case "recents": return service.performGlobalAction(GLOBAL_ACTION_RECENTS);
            case "notifications": return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
            case "quick_settings": return service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
            case "scroll_down": return service.swipe(true);
            case "scroll_up": return service.swipe(false);
            case "tap_text": return service.tapText(argument);
            case "type_text": return service.typeText(argument);
            case "clear_text": return service.editFocused("", false);
            case "backspace": return service.editFocused("", true);
            case "show_keyboard": return service.showKeyboard();
            case "hide_keyboard": return service.performGlobalAction(GLOBAL_ACTION_BACK);
            default: return false;
        }
    }

    static String describeScreen() {
        JarvisAccessibilityService service = current;
        if (service == null || Looper.myLooper() != Looper.getMainLooper()) return null;
        return service.screenText();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        current = this;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Never record what users type or what other apps display.
    }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    private boolean swipe(boolean down) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        Path gesture = new Path();
        gesture.moveTo(width * .52f, height * (down ? .76f : .32f));
        gesture.lineTo(width * .52f, height * (down ? .33f : .76f));
        GestureDescription description = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(gesture, 0, 410)).build();
        return dispatchGesture(description, null, null);
    }

    private static boolean riskyLabel(String text) {
        if (text == null) return true;
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String blocked : new String[] {
            "платеж", "оплати", "перевести", "отправить", "купить", "удалить",
            "стереть", "подтвердить", "разрешить", "заказ", "оплата",
            "подписать", "согласен", "transfer", "pay", "delete", "allow", "send"
        }) if (normalized.contains(blocked)) return true;
        return false;
    }

    private boolean tapText(String requested) {
        if (requested == null || requested.trim().isEmpty() || riskyLabel(requested)) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(requested);
            if (matches == null) return false;
            String target = requested.toLowerCase(Locale.ROOT).trim();
            for (AccessibilityNodeInfo found : matches) {
                String t = found.getText()==null ? "" : found.getText().toString().toLowerCase(Locale.ROOT);
                String d = found.getContentDescription()==null ? "" : found.getContentDescription().toString().toLowerCase(Locale.ROOT);
                if (!t.contains(target) && !d.contains(target)) continue;
                AccessibilityNodeInfo node = found;
                for (int n=0;n<6 && node!=null;n++) {
                    if (node.isVisibleToUser() && node.isClickable() && !node.isPassword())
                        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    node=node.getParent();
                }
            }
            return false;
        } finally { root.recycle(); }
    }

    private AccessibilityNodeInfo focusedEditable() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        try {
            AccessibilityNodeInfo node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (node != null && node.isEditable() && node.isVisibleToUser() && !node.isPassword()) return node;
            return null;
        } finally { root.recycle(); }
    }

    private boolean typeText(String text) {
        if (text == null || text.isEmpty() || text.length() > 800) return false;
        AccessibilityNodeInfo node = focusedEditable();
        if (node == null) return false;
        // ACTION_SET_TEXT intentionally replaces field contents. It never presses send/enter.
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private boolean editFocused(String replacement, boolean backspace) {
        AccessibilityNodeInfo node = focusedEditable();
        if (node == null) return false;
        String value = "";
        if (backspace) {
            CharSequence old = node.getText();
            value = old == null ? "" : old.toString();
            if (value.isEmpty()) return false;
            int end = value.offsetByCodePoints(value.length(), -1);
            value = value.substring(0, end);
        }
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, backspace ? value : replacement);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private boolean showKeyboard() {
        AccessibilityNodeInfo node = focusedEditable();
        return node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private String screenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "Не могу прочитать текущий экран.";
        try {
            String pkg = root.getPackageName()==null ? "" : root.getPackageName().toString();
            // Never announce secure credential interfaces, settings containing credentials, or banking apps.
            String p = pkg.toLowerCase(Locale.ROOT);
            if (p.contains("bank") || p.contains("finance") || p.contains("wallet") || p.contains("authenticator")
                || p.contains("password") || p.contains("keepass"))
                return "Не читаю содержимое финансовых приложений и хранилищ паролей.";
            Set<String> seen=new HashSet<>();
            ArrayDeque<AccessibilityNodeInfo> nodes=new ArrayDeque<>();
            nodes.add(root);
            StringBuilder output=new StringBuilder();
            int checked=0;
            while (!nodes.isEmpty() && checked++ < 120 && output.length() < 330) {
                AccessibilityNodeInfo node=nodes.removeFirst();
                if (node.isVisibleToUser() && !node.isPassword() && !node.isEditable()) {
                    CharSequence text=node.getText();
                    if (text==null || text.length()==0) text=node.getContentDescription();
                    if (text!=null) {
                        String value=text.toString().replaceAll("\\s+", " ").trim();
                        if (value.length()>2 && value.length()<120 && seen.add(value)) {
                            if (output.length()>0) output.append(". ");
                            output.append(value);
                        }
                    }
                }
                for (int i=0;i<node.getChildCount();i++) {
                    AccessibilityNodeInfo child=node.getChild(i);
                    if (child!=null) nodes.addLast(child);
                }
            }
            return output.length()==0 ? "На экране нет доступного для чтения текста." :
                "На экране: " + output.substring(0, Math.min(output.length(), 330));
        } finally { root.recycle(); }
    }
}
