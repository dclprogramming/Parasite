package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

public class BufferingDetector {
    private static final long BUFFERING_WINDOW_MS = 60_000;
    private static final long BUFFERING_DURATION_MS = 20_000;
    /** A stall of this length in the middle of playback (not at start, not after a seek) triggers the fix. */
    private static final long STALL_DURATION_MS = 5_000;
    
    private long mBeginTimeMs;
    private long mStartTimeMs;
    private long mTotalDurationMs;
    private final Runnable mOnLongBuffering = this::onLongBuffering;
    private final OnLongBuffering mCallback;
    private boolean mIsPlayable;

    public interface OnLongBuffering {
        void onLongBuffering();
    }

    public BufferingDetector(@NonNull OnLongBuffering callback) {
        mCallback = callback;
    }

    public void onStartBuffering() {
        onStartBuffering(mIsPlayable);
    }

    /**
     * @param quick true: one continuous stall of {@link #STALL_DURATION_MS} triggers the callback (video already plays).
     *              false: the old, patient behavior (video start, seeks): 20 sec of buffering within a minute.
     */
    public void onStartBuffering(boolean quick) {
        long currentTimeMs = System.currentTimeMillis();

        if (quick) {
            mStartTimeMs = currentTimeMs;
            Utils.removeCallbacks(mOnLongBuffering);
            Utils.postDelayed(mOnLongBuffering, STALL_DURATION_MS);
            return;
        }

        if (currentTimeMs - mBeginTimeMs > BUFFERING_WINDOW_MS) {
            mBeginTimeMs = currentTimeMs;
            mTotalDurationMs = 0;
        }
        mStartTimeMs = currentTimeMs;
        Utils.postDelayed(mOnLongBuffering, Math.max(0, BUFFERING_DURATION_MS - mTotalDurationMs));
    }

    public void onStopBuffering() {
        Utils.removeCallbacks(mOnLongBuffering);
        if (mStartTimeMs > 0) {
            long stopTimeMs = System.currentTimeMillis();
            mTotalDurationMs += (stopTimeMs - mStartTimeMs);
            mStartTimeMs = 0;
        }
        mIsPlayable = true;
    }

    public void start() {
        mIsPlayable = false;
        reset();
    }

    /**
     * Reset buffering stats
     */
    public void reset() {
        mBeginTimeMs = mStartTimeMs = mTotalDurationMs = 0;
        Utils.removeCallbacks(mOnLongBuffering);
    }

    public boolean isPlayable() {
        return mIsPlayable;
    }

    private void onLongBuffering() {
        reset();
        mCallback.onLongBuffering();
    }
}
