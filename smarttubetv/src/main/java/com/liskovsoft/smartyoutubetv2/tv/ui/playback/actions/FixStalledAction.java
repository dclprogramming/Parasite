package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class FixStalledAction extends Action {
    public FixStalledAction(Context context) {
        super(R.id.action_fix_stalled);
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.action_fix_stalled);

        setIcon(icon);
        setLabel1(context.getString(R.string.fix_stalled_client));
    }
}
