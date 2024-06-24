package com.zhushuli.recordipin;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.zhushuli.recordipin.fragments.Camera2PhotoFragment;
import com.zhushuli.recordipin.fragments.Camera2RawFragment;
import com.zhushuli.recordipin.fragments.Camera2VideoFragment;

/**
 * @author : zhushuli
 * @createDate : 2023/04/14 01:05
 * @description : Camera2实现图像采集、视频记录
 */
public class Camera2Activity extends AppCompatActivity {
    private static final String TAG = Camera2Activity.class.getSimpleName();

    private SharedPreferences mSharePreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        Log.d(TAG, "onCreate");

        mSharePreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Log.d(TAG, Build.BRAND);
        Log.d(TAG, Build.MODEL);
        Log.d(TAG, Build.DEVICE);
        Log.d(TAG, Build.BOARD);

        // TODO::识别不同手机品牌（Android机：xiaomi、OPPO、honor Vs Harmony机：huawei）
        if (null == savedInstanceState) {
            int recordMode = Integer.valueOf(mSharePreferences.getString("prefCameraRecordMode", "0"));
            Log.d(TAG, "recordMode:" + recordMode);
            if (recordMode == 0) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, Camera2RawFragment.newInstance())
                        .commit();
            } else if (recordMode == 1) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, Camera2PhotoFragment.newInstance())
                        .commit();
            } else if (recordMode == 2) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, Camera2VideoFragment.newInstance())
                        .commit();
            } else {
                Toast.makeText(this, "Illegal Record Mode", Toast.LENGTH_SHORT).show();
            }
        }
    }
}