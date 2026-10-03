package com.liskovsoft.smartyoutubetv2.tv.util;

import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Small helpers for the Parasite look: rounded cards, round avatars, gentle focus zoom. */
public final class UiStyle {
    private UiStyle() {
    }

    public static float dp(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    /** Clips the view and everything inside it to rounded corners. */
    public static void roundCorners(View view, float radiusDp) {
        final float radius = dp(view.getContext(), radiusDp);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    /** Clips the view to a circle (the largest centered square inside it). */
    public static void circle(View view) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                int size = Math.min(v.getWidth(), v.getHeight());
                int left = (v.getWidth() - size) / 2;
                int top = (v.getHeight() - size) / 2;
                outline.setOval(left, top, left + size, top + size);
            }
        });
        view.setClipToOutline(true);
    }

    /** Small zoom when a card gets focus. */
    public static void zoomOnFocus(View view, boolean hasFocus) {
        float scale = hasFocus ? 1.05f : 1f;
        view.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
    }
}
