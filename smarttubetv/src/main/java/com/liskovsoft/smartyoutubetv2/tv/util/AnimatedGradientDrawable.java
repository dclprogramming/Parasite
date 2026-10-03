package com.liskovsoft.smartyoutubetv2.tv.util;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/**
 * Rounded rectangle filled with a lavender gradient that slowly sweeps sideways.
 * Used as the background of the focused sidebar entry and the focused settings card.
 * The gradient starts and ends with the same colour, so the loop has no visible seam.
 */
public class AnimatedGradientDrawable extends Drawable implements Animatable {
    private static final int COLOR_DEEP = 0xFF4B3BD0;
    private static final int COLOR_LIGHT = 0xFF8C7DFF;
    private static final long PERIOD_MS = 2400;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Matrix mMatrix = new Matrix();
    private final int[] mColors;
    private final float mRadius;
    private final int mInsetLeft;
    private final int mInsetTop;
    private final int mInsetRight;
    private final int mInsetBottom;
    private LinearGradient mShader;
    private ValueAnimator mAnimator;
    private float mProgress;
    private float mSweep = 1f;

    public AnimatedGradientDrawable(int[] colors, float radiusPx, int insetLeft, int insetTop, int insetRight, int insetBottom) {
        mColors = colors;
        mRadius = radiusPx;
        mInsetLeft = insetLeft;
        mInsetTop = insetTop;
        mInsetRight = insetRight;
        mInsetBottom = insetBottom;
    }

    /** The standard Parasite focus look. Insets are in dp. */
    public static AnimatedGradientDrawable accent(Context context, float radiusDp, float insetHorizontalDp, float insetVerticalDp) {
        int ih = Math.round(UiStyle.dp(context, insetHorizontalDp));
        int iv = Math.round(UiStyle.dp(context, insetVerticalDp));
        return new AnimatedGradientDrawable(new int[]{COLOR_DEEP, COLOR_LIGHT, COLOR_DEEP}, UiStyle.dp(context, radiusDp), ih, iv, ih, iv);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mRect.set(bounds.left + mInsetLeft, bounds.top + mInsetTop, bounds.right - mInsetRight, bounds.bottom - mInsetBottom);
        mSweep = Math.max(1f, mRect.width());
        mShader = new LinearGradient(0, 0, mSweep, Math.max(1f, mRect.height()) * 0.5f, mColors, null, Shader.TileMode.REPEAT);
        mPaint.setShader(mShader);
    }

    @Override
    public void draw(Canvas canvas) {
        if (mShader == null || mRect.isEmpty()) {
            return;
        }
        mMatrix.setTranslate(mRect.left + mProgress * mSweep, mRect.top);
        mShader.setLocalMatrix(mMatrix);
        canvas.drawRoundRect(mRect, mRadius, mRadius, mPaint);
    }

    @Override
    public void start() {
        if (mAnimator != null && mAnimator.isRunning()) {
            return;
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(PERIOD_MS);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        mAnimator.start();
    }

    @Override
    public void stop() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    public boolean isRunning() {
        return mAnimator != null && mAnimator.isRunning();
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
