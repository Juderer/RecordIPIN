package com.zhushuli.recordipin.utils;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileUtils {

    private static String TAG;

    static {
        TAG = "My" + FileUtils.class.getSimpleName();
    }

    public static BufferedWriter initWriter(String dirPath, String fileName) {
        File file = new File(dirPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        FileWriter fileWriter = null;
        BufferedWriter bufferedWriter = null;
        try {
            fileWriter = new FileWriter(dirPath + File.separator + fileName);
            bufferedWriter = new BufferedWriter(fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "initWriter");
        }
        return bufferedWriter;
    }

    public static void closeBufferedWriter(BufferedWriter writer) {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
