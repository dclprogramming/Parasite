package com.liskovsoft.smartyoutubetv2.tv.ui.browse.providers;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.BrowseSupportFragment;
import com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.interfaces.Section;
import com.liskovsoft.smartyoutubetv2.tv.util.UiStyle;

/**
 * Sidebar "Providers" page: three big cards (YouTube, Rumble, Odysee) side by side.
 */
public class ProvidersFragment extends Fragment implements BrowseSupportFragment.MainFragmentAdapterProvider, Section {
    private static final int BASE_COLOR = 0xFF17142E;
    private static final int[] BRAND_COLORS = {0xFFFF0033, 0xFF85C742, 0xFFEF1970};
    private static final int[] NAMES = {R.string.provider_youtube, R.string.provider_rumble, R.string.provider_odysee};
    private static final String[] LETTERS = {"Y", "R", "O"};

    private final BrowseSupportFragment.MainFragmentAdapter<Fragment> mMainFragmentAdapter =
            new BrowseSupportFragment.MainFragmentAdapter<Fragment>(this);
    private final View[] mCards = new View[ProviderData.COUNT];
    private final boolean[] mFocused = new boolean[ProviderData.COUNT];

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mMainFragmentAdapter.getFragmentHost() != null) {
            mMainFragmentAdapter.getFragmentHost().notifyDataReady(mMainFragmentAdapter);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_providers, container, false);
        LinearLayout row = root.findViewById(R.id.providers_row);

        for (int i = 0; i < ProviderData.COUNT; i++) {
            final int provider = i;
            View card = inflater.inflate(R.layout.provider_card, row, false);

            card.setOnClickListener(v -> onProviderClicked(provider));
            card.setOnFocusChangeListener((v, hasFocus) -> {
                mFocused[provider] = hasFocus;
                bindCard(provider);
                UiStyle.zoomOnFocus(v, hasFocus);
            });

            mCards[i] = card;
            row.addView(card);
            bindCard(i);
        }

        return root;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mMainFragmentAdapter.getFragmentHost() != null) {
            mMainFragmentAdapter.getFragmentHost().notifyViewCreated(mMainFragmentAdapter);
        }
    }

    @Override
    public BrowseSupportFragment.MainFragmentAdapter<Fragment> getMainFragmentAdapter() {
        return mMainFragmentAdapter;
    }

    @Override
    public void clear() {
        // NOP. Static page
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    private void onProviderClicked(int provider) {
        if (!ProviderData.isAvailable(provider)) {
            Toast.makeText(getContext(), getString(R.string.provider_soon_toast, getString(NAMES[provider])), Toast.LENGTH_LONG).show();
            return;
        }

        if (ProviderData.getSelected(getContext()) == provider) {
            return; // already active
        }

        ProviderData.setSelected(getContext(), provider);

        for (int i = 0; i < ProviderData.COUNT; i++) {
            bindCard(i);
        }

        // The provider is fixed per process: restart so that every screen reloads from the new one
        Toast.makeText(getContext(), getString(R.string.provider_switching_toast, getString(NAMES[provider])), Toast.LENGTH_LONG).show();
        Utils.restartTheApp(getContext());
    }

    private void bindCard(int provider) {
        View card = mCards[provider];

        if (card == null) {
            return;
        }

        int brand = BRAND_COLORS[provider];
        boolean focused = mFocused[provider];
        boolean available = ProviderData.isAvailable(provider);
        boolean selected = available && ProviderData.getSelected(card.getContext()) == provider;

        // Card background
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(UiStyle.dp(card.getContext(), 22));
        bg.setColor(ColorUtils.blendARGB(BASE_COLOR, brand, focused ? 0.45f : 0.22f));
        if (focused) {
            bg.setStroke((int) UiStyle.dp(card.getContext(), 3), Color.WHITE);
        } else if (selected) {
            bg.setStroke((int) UiStyle.dp(card.getContext(), 3), brand);
        } else {
            bg.setStroke((int) UiStyle.dp(card.getContext(), 1), ColorUtils.setAlphaComponent(brand, 0x55));
        }
        card.setBackground(bg);

        // Round emblem with the provider's initial
        TextView badge = card.findViewById(R.id.provider_badge);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(brand);
        badge.setBackground(badgeBg);
        badge.setText(LETTERS[provider]);
        badge.setGravity(Gravity.CENTER);

        TextView name = card.findViewById(R.id.provider_name);
        name.setText(NAMES[provider]);

        // Status pill
        TextView status = card.findViewById(R.id.provider_status);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(UiStyle.dp(card.getContext(), 20));
        if (selected) {
            status.setText(R.string.provider_status_active);
            pill.setColor(brand);
            status.setTextColor(Color.WHITE);
        } else if (available) {
            status.setText(R.string.provider_status_select);
            pill.setColor(0x33FFFFFF);
            status.setTextColor(Color.WHITE);
        } else {
            status.setText(R.string.provider_status_soon);
            pill.setColor(0x1AFFFFFF);
            status.setTextColor(0xB3FFFFFF);
        }
        status.setBackground(pill);
    }
}
