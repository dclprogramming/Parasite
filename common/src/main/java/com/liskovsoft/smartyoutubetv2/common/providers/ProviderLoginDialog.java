package com.liskovsoft.smartyoutubetv2.common.providers;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;

/**
 * Full-screen browser showing the site's own sign-in page. Press Back when done.
 */
public final class ProviderLoginDialog {
    public interface OnClosed {
        void onClosed(String finalUrl);
    }

    private ProviderLoginDialog() {
    }

    public static void show(Context context, int provider, String hint, OnClosed onClosed) {
        String loginUrl = provider == ProviderData.RUMBLE ? "https://rumble.com/login.php" : "https://odysee.com/$/signin";
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(context);
        WebView web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString().replace("; wv", "")); // plain Chrome, like the page expects

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient());
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView bar = new TextView(context);
        bar.setText(hint);
        bar.setTextColor(Color.WHITE);
        bar.setBackgroundColor(0xCC000000);
        bar.setTextSize(15);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(24, 10, 24, 10);
        bar.setFocusable(false);
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        dialog.setContentView(root);
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                d.dismiss();
                return true;
            }

            return false;
        });
        dialog.setOnDismissListener(d -> {
            String finalUrl = web.getUrl();
            cookies.flush();
            root.removeAllViews();
            web.destroy();

            if (onClosed != null) {
                onClosed.onClosed(finalUrl);
            }
        });

        web.loadUrl(loginUrl);
        dialog.show();
        web.requestFocus();
    }
}
