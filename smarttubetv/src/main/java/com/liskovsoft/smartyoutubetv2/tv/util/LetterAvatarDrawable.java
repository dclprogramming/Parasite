package com.liskovsoft.smartyoutubetv2.tv.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Stand-in channel picture: the channel's first letter in white on a colour chosen from its name.
 */
public class LetterAvatarDrawable extends Drawable {
    private static final int[] COLORS = {0xFF5545D6, 0xFF2E7D6B, 0xFFB5543C, 0xFF3C6EB5, 0xFF8E4FA8, 0xFF9A7B1E};
    private final Paint mBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String mLetter;

    public LetterAvatarDrawable(CharSequence name) {
        String text = name == null ? "" : name.toString().trim();
        mLetter = text.isEmpty() ? "?" : text.substring(0, text.offsetByCodePoints(0, 1)).toUpperCase();
        mBackground.setColor(COLORS[Math.abs(text.hashCode()) % COLORS.length]);
        mText.setColor(0xFFFFFFFF);
        mText.setTextAlign(Paint.Align.CENTER);
        mText.setFakeBoldText(true);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();

        if (b.isEmpty()) {
            return;
        }

        canvas.drawRect(b, mBackground);
        mText.setTextSize(b.height() * 0.55f);
        float y = b.exactCenterY() - (mText.descent() + mText.ascent()) / 2f;
        canvas.drawText(mLetter, b.exactCenterX(), y, mText);
    }

    @Override
    public void setAlpha(int alpha) {
        mBackground.setAlpha(alpha);
        mText.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBackground.setColorFilter(colorFilter);
        mText.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
