package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import com.liskovsoft.smartyoutubetv2.tv.util.AnimatedGradientDrawable;

/**
 * Sidebar entry container: while it has focus its background is the animated lavender gradient,
 * otherwise it falls back to whatever background the layout XML declared.
 */
public class FocusGlowLinearLayout extends LinearLayout {
    private AnimatedGradientDrawable mGlow;
    private int mGlowProvider = -1;
    private Drawable mRestBackground;

    public FocusGlowLinearLayout(Context context) {
        super(context);
    }

    public FocusGlowLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusGlowLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // The sidebar fades entries in and out; same hint the original leanback container gives.
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        setGlow(gainFocus);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setGlow(false);
    }

    private void setGlow(boolean on) {
        if (on) {
            if (mRestBackground == null) {
                mRestBackground = getBackground();
            }
            int provider = com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData.getSelected();
            if (mGlow == null || mGlowProvider != provider) { // colour follows the active provider
                mGlow = AnimatedGradientDrawable.accent(getContext(), 12, 0, 0);
                mGlowProvider = provider;
            }
            setBackground(mGlow);
            mGlow.start();
        } else {
            if (mGlow != null) {
                mGlow.stop();
            }
            if (mRestBackground != null && getBackground() == mGlow) {
                setBackground(mRestBackground);
            }
        }
    }
}
