package com.zhushuli.recordipin.utils.log;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.zhushuli.recordipin.utils.FileUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author      : generallizhong, zhushuli
 * @createDate  : 2023/10/16 14:54
 * @description : Android全局异常日志记录（https://blog.51cto.com/u_14397532/2999055）
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "GlobalCrash";

    private Thread.UncaughtExceptionHandler mDefaultHandler;

    private static CrashHandler instance = new CrashHandler();

    private Context mContext;

    private Map<String, String> infos = new HashMap<>();

    private String mRootDir;

    private CrashHandler() {}

    /**
     * 单实例模式
     */
    public static CrashHandler getInstance() {
        return instance;
    }

    public void init(Context context) {
        mContext = context;
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mRootDir = mContext.getExternalFilesDir("crash").getAbsolutePath();
        Log.d(TAG, mRootDir);
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        if (!handleException(e) && mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(t, e);
        }
        else {
            SystemClock.sleep(1000);
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private boolean handleException(Throwable e) {
        if (e == null) {
            return false;
        }

        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(mContext, "程序出现异常，即将重启...", Toast.LENGTH_LONG).show();
                Looper.loop();
            }
        }.start();

        try {
            collectDeviceInfo(mContext);
            saveCrashInfoField(e);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return true;
    }

    private void collectDeviceInfo(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName + "";
                String versionCode = pi.getLongVersionCode() + "";
                infos.put("versionName", versionName);
                infos.put("versionCode", versionCode);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                infos.put(field.getName(), field.get(null).toString());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String saveCrashInfoField(Throwable e) throws Exception {
        StringBuffer sb = new StringBuffer();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = formatter.format(new Date());
        sb.append("\r\n" + date + "\r\n");
        for (Map.Entry<String, String> entry : infos.entrySet()) {
            sb.append(entry.getKey() + "=" + entry.getValue() + "\n");
        }

        Writer write = new StringWriter();
        PrintWriter printWriter = new PrintWriter(write);
        e.printStackTrace(printWriter);
        Throwable cause = e.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.flush();
        printWriter.close();
        String result = write.toString();
        sb.append(result);

        String fileName = writeFile(sb.toString());
        return fileName;
    }

    private String writeFile(String sb){
        String time = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        String fileName = "Crash_" + time + ".log";
        BufferedWriter bw = FileUtils.initWriter( mRootDir, fileName);
        try {
            bw.write(sb);
            bw.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        FileUtils.closeBufferedWriter(bw);
        return fileName;
    }
}
