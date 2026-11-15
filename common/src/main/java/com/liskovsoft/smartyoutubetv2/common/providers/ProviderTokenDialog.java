package com.liskovsoft.smartyoutubetv2.common.providers;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * Fallback sign-in for Twitch: the person pastes the "auth-token" cookie value copied from a desktop browser where
 * they are already signed in. Used when Twitch refuses to sign in inside the app's WebView.
 */
public final class ProviderTokenDialog {
    public interface OnToken {
        void onToken(String token);
    }

    private static final String HELP = "On a computer: sign in at twitch.tv, press F12, open the Console tab and run:\n\n"
            + "document.cookie.split('; ').find(c => c.startsWith('auth-token=')).split('=')[1]\n\n"
            + "Type the result below (about 30 letters and digits). Keep it private: it works like your password.";

    private ProviderTokenDialog() {
    }

    public static void show(Context context, OnToken onToken) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint("auth-token value");

        String clip = clipboardText(context);

        if (clip != null && clip.matches("[A-Za-z0-9]{20,40}")) { // looks like a token: save some typing
            input.setText(clip);
        }

        FrameLayout box = new FrameLayout(context);
        box.setPadding(48, 8, 48, 0);
        box.addView(input);

        new AlertDialog.Builder(context)
                .setTitle("Sign in to Twitch with a token")
                .setMessage(HELP)
                .setView(box)
                .setPositiveButton("Sign in", (d, w) -> {
                    String token = input.getText().toString().trim();

                    if (!token.isEmpty() && onToken != null) {
                        onToken.onToken(token);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String clipboardText(Context context) {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

            if (manager != null && manager.hasPrimaryClip()) {
                ClipData clip = manager.getPrimaryClip();

                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).coerceToText(context);
                    return text != null ? text.toString().trim() : null;
                }
            }
        } catch (Exception e) {
            // NOP: no clipboard access
        }

        return null;
    }
}
