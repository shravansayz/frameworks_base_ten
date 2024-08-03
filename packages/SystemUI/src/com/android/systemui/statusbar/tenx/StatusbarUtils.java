/*
* Copyright (C) 2019-2024 TenX-OS
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.statusbar.tenx;

import android.content.Context;
import android.provider.Settings;

import com.android.systemui.res.R;

public class StatusbarUtils {

    public static boolean useCustomStatusbarPaddingStart(Context context) {
        return Settings.System.getInt(
            context.getContentResolver(),
            Settings.System.USE_CUSTOM_STATUSBAR_PADDING_START, 0) != 0;
    }

    public static int getCustomStatusBarPaddingStart(Context context, int defaultValue) {
        return Settings.System.getInt(
            context.getContentResolver(),
            Settings.System.CUSTOM_STATUSBAR_PADDING_START, defaultValue
        );
    }

    public static boolean useCustomStatusBarPaddingEnd(Context context) {
        return Settings.System.getInt(
            context.getContentResolver(),
            Settings.System.USE_CUSTOM_STATUSBAR_PADDING_END, 0) != 0;
    }

    public static int getCustomStatusBarPaddingEnd(Context context, int defaultValue) {
        return Settings.System.getInt(
            context.getContentResolver(),
            Settings.System.CUSTOM_STATUSBAR_PADDING_END, defaultValue
        );
    }

    public static boolean useCustomStatusBarHeight(Context context) {
        return Settings.System.getInt(
            context.getContentResolver(),
            Settings.System.USE_CUSTOM_STATUSBAR_HEIGHT, 0) != 0;
    }

    public static int getCustomStatusBarHeight(Context context, int defaultValue) {
        return Settings.System.getInt(
            context.getContentResolver(),
            Settings.System.CUSTOM_STATUSBAR_HEIGHT, defaultValue);
    }

    public static int dpToPixels(int dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
