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

    /**
     * Hides the cookie-consent pop-up of the site (it covers the sign-in fields). Picks "reject / necessary only" when the
     * pop-up offers it. The sign-in itself only needs the site's own session cookie, not the optional ones.
     */
    private static final String HIDE_CONSENT_JS = ""
            + "(function () {\n"
            + "  if (window.__parasiteConsent) return;\n"
            + "  window.__parasiteConsent = true;\n"
            + "  var REJECT = /^(reject( all| cookies| non-essential)?|decline( all| cookies)?|deny( all)?|refuse( all)?|necessary( cookies)?( only)?|only (necessary|essential)( cookies)?|essential( cookies)?( only)?|no,? thanks|disagree|continue without accepting)$/i;\n"
            + "  var ACCEPT = /^(accept( all| cookies| all cookies)?|allow( all| cookies| all cookies)?|agree|i agree|got it|ok|okay|i understand|understood|close)$/i;\n"
            + "  var HINTS = '[id*=\"cookie\" i],[class*=\"cookie\" i],[id*=\"consent\" i],[class*=\"consent\" i],[id*=\"gdpr\" i],[class*=\"gdpr\" i],[id*=\"cmp\" i],[class*=\"cmp-\" i],[role=\"dialog\"],[aria-modal=\"true\"],[id*=\"onetrust\" i],[id*=\"usercentrics\" i],[id*=\"cookiebot\" i]';\n"
            + "  function words(el) { return (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim(); }\n"
            + "  function isOverlay(el) {\n"
            + "    var st = window.getComputedStyle(el), pos = st.position;\n"
            + "    return pos === 'fixed' || pos === 'sticky' || pos === 'absolute' || el.getAttribute('role') === 'dialog' || el.getAttribute('aria-modal') === 'true';\n"
            + "  }\n"
            + "  function isLoginForm(el) { return !!el.querySelector('input[type=\"password\"],input[type=\"email\"],input[name=\"username\"]'); }\n"
            + "  function hide(el) { el.style.setProperty('display', 'none', 'important'); el.setAttribute('data-parasite-hidden', '1'); }\n"
            + "  function clickFirst(el, re) {\n"
            + "    var items = el.querySelectorAll('button,a,[role=\"button\"],input[type=\"button\"],input[type=\"submit\"]');\n"
            + "    for (var i = 0; i < items.length; i++) {\n"
            + "      var label = (items[i].value || words(items[i])).replace(/[.!]+$/, '');\n"
            + "      if (label.length < 40 && re.test(label)) { items[i].click(); return true; }\n"
            + "    }\n"
            + "    return false;\n"
            + "  }\n"
            + "  function sweep() {\n"
            + "    var found = false, list = document.querySelectorAll(HINTS);\n"
            + "    for (var i = 0; i < list.length; i++) {\n"
            + "      var el = list[i];\n"
            + "      if (el.getAttribute('data-parasite-hidden') || el === document.body || el === document.documentElement) continue;\n"
            + "      var text = words(el);\n"
            + "      if (text.length < 8 || text.length > 3000 || !/cookie|consent|gdpr/i.test(text)) continue;\n"
            + "      if (isLoginForm(el) || !isOverlay(el)) continue;\n"
            + "      if (!clickFirst(el, REJECT)) clickFirst(el, ACCEPT);\n"
            + "      hide(el);\n"
            + "      found = true;\n"
            + "    }\n"
            + "    if (found) {\n"
            + "      var back = document.querySelectorAll('[class*=\"backdrop\" i],[class*=\"overlay\" i],[class*=\"modal-bg\" i]');\n"
            + "      for (var j = 0; j < back.length; j++) {\n"
            + "        var b = back[j];\n"
            + "        if (!isLoginForm(b) && window.getComputedStyle(b).position === 'fixed' && words(b).length < 20) hide(b);\n"
            + "      }\n"
            + "      document.body.style.setProperty('overflow', 'auto', 'important');\n"
            + "      document.documentElement.style.setProperty('overflow', 'auto', 'important');\n"
            + "      var field = document.querySelector('input[type=\"text\"],input[type=\"email\"],input[name=\"username\"]');\n"
            + "      if (field && (!document.activeElement || document.activeElement === document.body)) field.focus();\n"
            + "    }\n"
            + "  }\n"
            + "  var timer = null;\n"
            + "  function later() { if (timer) return; timer = setTimeout(function () { timer = null; sweep(); }, 150); }\n"
            + "  new MutationObserver(later).observe(document.documentElement, { childList: true, subtree: true });\n"
            + "  sweep();\n"
            + "})();\n";

    public static void show(Context context, int provider, String hint, OnClosed onClosed) {
        String loginUrl = provider == ProviderData.RUMBLE ? "https://rumble.com/login.php" : "https://www.twitch.tv/login";
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

        boolean rumble = provider == ProviderData.RUMBLE;
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (rumble) {
                    view.evaluateJavascript(HIDE_CONSENT_JS, null);
                }
            }
        });
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
