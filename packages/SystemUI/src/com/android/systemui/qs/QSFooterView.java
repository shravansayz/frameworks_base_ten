/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.settingslib.net.DataUsageController;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.res.R;

/**
 * Footer of expanded Quick Settings, tiles page indicator, (optionally) build number and
 * {@link FooterActionsView}
 */
public class QSFooterView extends FrameLayout {
    private static final String TAG = "QSFooterView";

    private PageIndicator mPageIndicator;
    private TextView mUsageText;
    private View mEditButton;
    private Drawable mIcon;

    private LinearLayout mDataUsagePanel;

    @Nullable
    protected TouchAnimator mFooterAnimator;

    private boolean mQsDisabled;
    private boolean mExpanded;
    private float mExpansionAmount;

    private boolean mShouldShowDataUsage;
    private boolean mShouldShowDataUsageIcon;
    private boolean mShouldShowUsageText;

    private final Handler mHandler = new Handler();

    @Nullable
    private OnClickListener mExpandClickListener;

    private DataUsageController mDataController;
    private SubscriptionManager mSubManager;

    private boolean mHasNoSims;
    private boolean mIsWifiConnected;
    private String mWifiSsid;
    private int mSubId;
    private int mCurrentDataSubId;

    public QSFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDataController = new DataUsageController(context);
        mSubManager = context.getSystemService(SubscriptionManager.class);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPageIndicator = findViewById(R.id.footer_page_indicator);
        mUsageText = findViewById(R.id.build);
        mEditButton = findViewById(android.R.id.edit);
        mDataUsagePanel = findViewById(R.id.qs_data_usage);

        updateResources();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setClickable(false);
        setUsageText();

        mUsageText.setOnClickListener(v -> {
            if (mSubManager.getActiveSubscriptionInfoCount() > 1) {
                // Get opposite slot 2 ^ 3 = 1, 1 ^ 3 = 2
                mSubId = mSubId ^ 3;
                setUsageText();
                mUsageText.setSelected(false);
                postDelayed(() -> mUsageText.setSelected(true), 1000);
            }
        });

        mHandler.post(() -> {
            getInternetUsage();
        });
    }

    private void setUsageText() {
        if (mUsageText == null || !mShouldShowDataUsage || !mExpanded) return;
        DataUsageController.DataUsageInfo info;
        String suffix;
        if (mIsWifiConnected) {
            info = mDataController.getWifiDailyDataUsageInfo(true);
            mIcon = mContext.getResources().getDrawable(R.drawable.ic_data_usage_wifi);
            if (info == null) {
                info = mDataController.getWifiDailyDataUsageInfo(false);
                suffix = mContext.getResources().getString(R.string.usage_wifi_default_suffix);
            } else {
                suffix = getWifiSsid();
            }
        } else if (!mHasNoSims) {
            mDataController.setSubscriptionId(mSubId);
            info = mDataController.getDailyDataUsageInfo();
            suffix = getSlotCarrierName();
            mIcon = mContext.getResources().getDrawable(R.drawable.ic_data_usage_mobile);
        } else {
            mShouldShowUsageText = false;
            mUsageText.setText(null);
            updateVisibilities();
            return;
        }
        if (info == null) {
            Log.w(TAG, "setUsageText: DataUsageInfo is NULL.");
            return;
        }
        // Setting text actually triggers a layout pass (because the text view is set to
        // wrap_content width and TextView always relayouts for this). Avoid needless
        // relayout if the text didn't actually change.
        String text = formatDataUsage(info.usageLevel, suffix);
        if (!TextUtils.equals(text, mUsageText.getText())) {
            mUsageText.setText(formatDataUsage(info.usageLevel, suffix));
        }
        mShouldShowUsageText = true;
        updateVisibilities();
    }

    private String formatDataUsage(long byteValue, String suffix) {
        // Example: 1.23 GB used today (airtel)
        StringBuilder usage = new StringBuilder(Formatter.formatFileSize(getContext(),
                byteValue, Formatter.FLAG_IEC_UNITS))
                .append(" ")
                .append(mContext.getString(R.string.usage_data))
                .append(" (")
                .append(suffix)
                .append(")");
        return usage.toString();
    }

    private String getSlotCarrierName() {
        SubscriptionInfo subInfo = mSubManager.getActiveSubscriptionInfo(mSubId);
        if (subInfo != null) {
            return subInfo.getDisplayName().toString();
        }
        return mContext.getResources().getString(R.string.usage_data_default_suffix);
    }

    private String getWifiSsid() {
        if (mWifiSsid != null) {
            return mWifiSsid.replace("\"", "");
        }
        return mContext.getResources().getString(R.string.usage_wifi_default_suffix);
    }

    protected void setWifiSsid(String ssid) {
        if (mWifiSsid != ssid) {
            mWifiSsid = ssid;
            setUsageText();
        }
    }

    protected void setIsWifiConnected(boolean connected) {
        if (mIsWifiConnected != connected) {
            mIsWifiConnected = connected;
            setUsageText();
        }
    }

    protected void setNoSims(boolean hasNoSims) {
        if (mHasNoSims != hasNoSims) {
            mHasNoSims = hasNoSims;
            setUsageText();
        }
    }

    protected void setCurrentDataSubId(int subId) {
        if (mCurrentDataSubId != subId) {
            mSubId = mCurrentDataSubId = subId;
            setUsageText();
        }
    }

    protected void setShowDataUsage(boolean show) {
        if (mShouldShowDataUsage != show) {
            mShouldShowDataUsage = show;
            updateVisibilities();
        }
    }

    protected void setShowDataUsageIcon(boolean show) {
        if (mShouldShowDataUsageIcon != show) {
            mShouldShowDataUsageIcon = show;
            updateVisibilities();
        }
    }

    private void getInternetUsage() {
        TextView internetUp = findViewById(R.id.internet_up);
        TextView internetDown = findViewById(R.id.internet_down);
        TextView mobileUp = findViewById(R.id.mobile_up);
        TextView mobileDown = findViewById(R.id.mobile_down);
        TextView wifiUp = findViewById(R.id.wifi_up);
        TextView wifiDown = findViewById(R.id.wifi_down);

        long mobileUpload = TrafficStats.getMobileTxBytes();
        long mobileDownload = TrafficStats.getMobileRxBytes();

        long totalDownload = TrafficStats.getTotalRxBytes();
        long totalUpload = TrafficStats.getTotalTxBytes();

        long wifiUpload = totalUpload - mobileUpload;
        long wifiDownload = totalDownload - mobileDownload;

        internetDown.setText("Download: " + BytesToMb(totalDownload) + "MB");
        internetUp.setText("Upload: " + BytesToMb(totalUpload) + "MB");

        mobileDown.setText("Download: " + BytesToMb(mobileDownload) + "MB");
        mobileUp.setText("Upload: " + BytesToMb(mobileUpload) + "MB");

        wifiDown.setText("Download: " + BytesToMb(wifiDownload) + "MB");
        wifiUp.setText("Upload: " + BytesToMb(wifiUpload) + "MB");
    }

    public long BytesToMb(long usage) {
        return usage / 1048567;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    private void updateResources() {
        updateFooterAnimator();
        updateEditButtonResources();
        updateBuildTextResources();
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.height = !showUsagePanel() ?
                getResources().getDimensionPixelSize(R.dimen.qs_footer_height) :
                ViewGroup.LayoutParams.WRAP_CONTENT;
        int sideMargin = getResources().getDimensionPixelSize(R.dimen.qs_footer_margin);
        lp.leftMargin = sideMargin;
        lp.rightMargin = sideMargin;
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.qs_footers_margin_bottom);
        setLayoutParams(lp);
    }

    private void updateEditButtonResources() {
        int size = getResources().getDimensionPixelSize(R.dimen.qs_footer_action_button_size);
        int padding = getResources().getDimensionPixelSize(R.dimen.qs_footer_icon_padding);
        MarginLayoutParams lp = (MarginLayoutParams) mEditButton.getLayoutParams();
        lp.height = size;
        lp.width = size;
        mEditButton.setLayoutParams(lp);
        mEditButton.setPadding(padding, padding, padding, padding);
    }

    private void updateBuildTextResources() {
        FontSizeUtils.updateFontSizeFromStyle(mUsageText, R.style.TextAppearance_QS_Status_Build);
    }

    private void updateFooterAnimator() {
        mFooterAnimator = createFooterAnimator();
    }

    @Nullable
    private TouchAnimator createFooterAnimator() {
        TouchAnimator.Builder builder = new TouchAnimator.Builder()
                .addFloat(mPageIndicator, "alpha", 0, 1)
                .addFloat(mUsageText, "alpha", 0, 1)
                .addFloat(mEditButton, "alpha", 0, 1)
                .addFloat(mDataUsagePanel, "alpha", 0, 1)
                .setStartDelay(0.9f);
        return builder.build();
    }

    /** */
    public void setKeyguardShowing() {
        setExpansion(mExpansionAmount);
    }

    public void setExpandClickListener(OnClickListener onClickListener) {
        mExpandClickListener = onClickListener;
    }

    void setExpanded(boolean expanded) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();
    }

    /** */
    public void setExpansion(float headerExpansionFraction) {
        mExpansionAmount = headerExpansionFraction;
        if (mFooterAnimator != null) {
            mFooterAnimator.setPosition(headerExpansionFraction);
        }

        if (mUsageText == null || !mShouldShowDataUsage) return;
        if (headerExpansionFraction == 1.0f) {
            postDelayed(() -> mUsageText.setSelected(true), 1000);
        } else if (headerExpansionFraction == 0.0f) {
            mUsageText.setSelected(false);
            mSubId = mCurrentDataSubId;
        }
    }

    void disable(int state2) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        updateEverything();
    }

    void updateEverything() {
        post(() -> {
            updateVisibilities();
            setUsageText();
        });
    }

    private boolean showUsagePanel() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QS_DATA_USAGE_PANEL, 0,
                UserHandle.USER_CURRENT) == 1;
    }

    private void updateVisibilities() {
        mUsageText.setVisibility(mShouldShowDataUsage && mExpanded && mShouldShowUsageText
                ? View.VISIBLE : View.INVISIBLE);

        if (mUsageText.getVisibility() == View.VISIBLE && mShouldShowDataUsageIcon) {
            mUsageText.setCompoundDrawablesWithIntrinsicBounds(mIcon, null, null, null);
        } else {
            mUsageText.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }

        if (mExpanded && showUsagePanel()) {
            mDataUsagePanel.setVisibility(View.VISIBLE);
            getInternetUsage();
        } else {
            mDataUsagePanel.setVisibility(View.GONE);
        }
    }
}
