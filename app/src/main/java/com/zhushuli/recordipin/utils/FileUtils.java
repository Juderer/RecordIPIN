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

    public static boolean checkFileExists(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            return true;
        }
        return false;
    }

    public static BufferedWriter initWriter(String fileName) {
        File file = new File(fileName);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileName));
            return bufferedWriter;
        } catch (IOException e) {
            Log.d(TAG, "initWriter");
            throw new RuntimeException(e);
        }
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

    public static BufferedWriter initAppendWriter(String dirPath, String fileName) {
        FileWriter fileWriter;
        BufferedWriter bufferedWriter = null;
        try {
            fileWriter = new FileWriter(dirPath + File.separator + fileName, true);
            bufferedWriter = new BufferedWriter(fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "initAppendWriter");
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
