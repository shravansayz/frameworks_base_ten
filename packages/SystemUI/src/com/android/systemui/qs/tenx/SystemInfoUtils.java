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
package com.android.systemui.qs.tenx;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class SystemInfoUtils {

    private static final String TAG = "SystemInfoUtils";

    public static String readOneLine(String fileName) {
        String line = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName), 512)) {
            line = reader.readLine();
        } catch (FileNotFoundException e) {
            Log.w(TAG, "No such file " + fileName + " for reading", e);
        } catch (IOException e) {
            Log.e(TAG, "Could not read from file " + fileName, e);
        }
        return line;
    }

    public static boolean fileExists(String fileName) {
        final File file = new File(fileName);
        return file.exists();
    }

    public static String getSystemInfo(String sysPath, int multiplier, String unit, boolean returnFormatted) {
        if (!sysPath.isEmpty() && fileExists(sysPath)) {
            String value = readOneLine(sysPath);
            if (value != null) {
                return returnFormatted ? String.format("%s", Integer.parseInt(value) / multiplier) + unit : value;
            } else {
                return "N/A";
            }
        }
        return null;
    }
}
