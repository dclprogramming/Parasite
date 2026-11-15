package com.liskovsoft.smartyoutubetv2.common.providers.twitch;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.providers.HttpStatusException;
import com.liskovsoft.smartyoutubetv2.common.providers.ProviderHttp;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Twitch sign-in with a code, like the Twitch TV app: the screen shows a short code, the person enters it at
 * twitch.tv/activate on a phone or computer, and the app picks up the token. No browser needed on this device.
 */
public final class TwitchDeviceLogin {
    private static final String DEVICE_URL = "https://id.twitch.tv/oauth2/device";
    private static final String TOKEN_URL = "https://id.twitch.tv/oauth2/token";

    public interface Callback {
        void onToken(String token);

        void onFailed(String message);
    }

    private TwitchDeviceLogin() {
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", TwitchApi.ANDROID_USER_AGENT);
        headers.put("Accept", "application/json");
        return headers;
    }

    public static void start(Context context, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog[] dialog = new AlertDialog[1];

        new Thread(() -> {
            try {
                String answer = ProviderHttp.postForm(DEVICE_URL, "client_id=" + TwitchApi.ANDROID_CLIENT_ID + "&scopes=", headers());
                JSONObject json = new JSONObject(answer);
                String deviceCode = json.getString("device_code");
                String userCode = json.getString("user_code");
                int interval = Math.max(2, json.optInt("interval", 5));
                long deadline = System.currentTimeMillis() + json.optInt("expires_in", 600) * 1000L;

                main.post(() -> {
                    dialog[0] = new AlertDialog.Builder(context)
                            .setTitle("Sign in to Twitch")
                            .setMessage("On your phone or computer, open\n\ntwitch.tv/activate\n\nand enter this code:\n\n"
                                    + userCode + "\n\nThis screen continues by itself once you finish.")
                            .setNegativeButton("Cancel", (d, w) -> cancelled.set(true))
                            .setOnCancelListener(d -> cancelled.set(true))
                            .show();
                });

                String form = "client_id=" + TwitchApi.ANDROID_CLIENT_ID + "&device_code=" + deviceCode
                        + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

                while (!cancelled.get() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L);

                    if (cancelled.get()) {
                        return;
                    }

                    try {
                        String token = new JSONObject(ProviderHttp.postForm(TOKEN_URL, form, headers())).optString("access_token", null);

                        if (token != null && !token.isEmpty()) {
                            main.post(() -> {
                                if (dialog[0] != null) {
                                    dialog[0].dismiss();
                                }
                                callback.onToken(token);
                            });
                            return;
                        }
                    } catch (HttpStatusException e) {
                        String body = e.body != null ? e.body : "";

                        if (body.contains("slow_down")) {
                            interval += 5;
                        } else if (!body.contains("authorization_pending")) {
                            throw e; // expired, denied, ...
                        }
                    }
                }

                if (!cancelled.get()) {
                    fail(main, dialog, callback, "the code expired. Try again.");
                }
            } catch (Exception e) {
                fail(main, dialog, callback, e.getMessage());
            }
        }).start();
    }

    private static void fail(Handler main, AlertDialog[] dialog, Callback callback, String message) {
        main.post(() -> {
            if (dialog[0] != null) {
                dialog[0].dismiss();
            }
            callback.onFailed(message);
        });
    }
}
