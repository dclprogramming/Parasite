package com.liskovsoft.smartyoutubetv2.common.providers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;
import com.liskovsoft.smartyoutubetv2.common.providers.odysee.OdyseeAccount;
import com.liskovsoft.smartyoutubetv2.common.providers.rumble.RumbleApi;

/**
 * Sign in / sign out for the active non-YouTube provider.
 */
public final class ProviderLogin {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private ProviderLogin() {
    }

    /** Called (on the main thread) whenever signing in or out of the active provider finishes. */
    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static Runnable notifying(Runnable onChanged) {
        return () -> {
            if (onChanged != null) {
                onChanged.run();
            }

            for (Runnable listener : LISTENERS) {
                listener.run();
            }
        };
    }

    /**
     * The sign-in icon at the top of the app, for Rumble / Odysee: opens their sign-in page, or (when already signed
     * in) offers to sign out.
     */
    public static void onTopIconClicked(Context context) {
        int provider = ProviderData.getSelected();

        if (provider == ProviderData.YOUTUBE) {
            return;
        }

        if (!isSignedIn(context)) {
            start(context, null);
            return;
        }

        AppDialogPresenter dialog = AppDialogPresenter.instance(context);
        dialog.appendSingleButton(UiOptionItem.from("Sign out of " + nameOf(provider), option -> {
            dialog.closeDialog();
            signOut(context, null);
        }));
        dialog.appendSingleButton(UiOptionItem.from("Cancel", option -> dialog.closeDialog()));
        dialog.showDialog(nameOf(provider) + " account");
    }

    public static String nameOf(int provider) {
        return provider == ProviderData.RUMBLE ? "Rumble" : "Odysee";
    }

    public static boolean isSignedIn(Context context) {
        return ProviderAuth.isSignedIn(context, ProviderData.getSelected());
    }

    /**
     * Opens the site's sign-in page. {@code onChanged} runs on the main thread once the sign-in state is known.
     */
    public static void start(Context context, Runnable onChanged) {
        int provider = ProviderData.getSelected();

        if (provider == ProviderData.YOUTUBE) {
            return;
        }

        String hint = "Sign in to " + nameOf(provider) + ", then press Back to finish";

        try {
            ProviderLoginDialog.show(context, provider, hint, finalUrl -> finish(context, provider, finalUrl, notifying(onChanged)));
        } catch (RuntimeException e) { // e.g. no WebView on this device
            MessageHelpers.showLongMessage(context, "Can't open the " + nameOf(provider) + " sign-in page: " + e.getMessage());
        }
    }

    public static void signOut(Context context, Runnable onChanged) {
        int provider = ProviderData.getSelected();
        ProviderAuth.signOut(context, provider);
        MessageHelpers.showMessage(context, "Signed out of " + nameOf(provider));
        notifying(onChanged).run();
    }

    private static void finish(Context context, int provider, String finalUrl, Runnable onChanged) {
        new Thread(() -> {
            String message;

            try {
                message = provider == ProviderData.ODYSEE ? finishOdysee(context) : finishRumble(context, finalUrl);
            } catch (Exception e) {
                message = nameOf(provider) + ": could not check the sign-in (" + e.getMessage() + ")";
            }

            String text = message;
            Handler main = new Handler(Looper.getMainLooper());
            main.post(() -> {
                MessageHelpers.showLongMessage(context, text);

                if (onChanged != null) {
                    onChanged.run();
                }
            });
        }).start();
    }

    private static String finishOdysee(Context context) throws Exception {
        String token = ProviderAuth.cookie(ProviderAuth.ODYSEE_SITE, "auth_token");

        if (token == null) {
            ProviderAuth.setSignedIn(context, ProviderData.ODYSEE, false);
            return "Odysee sign-in not detected. Sign in on the page, then press Back.";
        }

        ProviderAuth.setOdyseeToken(context, token);
        ProviderAuth.setSignedIn(context, ProviderData.ODYSEE, true);

        try {
            int count = OdyseeAccount.importFollows(new ProviderStore(context, "odysee"), token);
            return "Signed in to Odysee. Imported " + count + " followed channels.";
        } catch (Exception e) {
            return "Signed in to Odysee, but your followed channels could not be loaded (" + e.getMessage() + ")";
        }
    }

    private static String finishRumble(Context context, String finalUrl) throws Exception {
        boolean leftLoginPage = finalUrl != null && finalUrl.contains("rumble.com") && !finalUrl.contains("login") && !finalUrl.contains("register");
        boolean signedIn = leftLoginPage;

        try {
            signedIn = signedIn || RumbleApi.looksSignedIn();
        } catch (Exception e) {
            // Keep the page-based guess
        }

        ProviderAuth.setSignedIn(context, ProviderData.RUMBLE, signedIn);
        return signedIn ? "Signed in to Rumble." : "Rumble sign-in not detected. Sign in on the page, then press Back.";
    }
}
