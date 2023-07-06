package com.zhushuli.recordipin;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.zhushuli.recordipin.fragments.IGDFragment;
import com.zhushuli.recordipin.fragments.VIGDFragment;

public class CollectionActivity extends AppCompatActivity {

    private static final String TAG = CollectionActivity.class.getSimpleName();

    private SharedPreferences mSharePreferences;

    private boolean videoRecording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);
        Log.d(TAG, "onCreate");

        mSharePreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        videoRecording = mSharePreferences.getBoolean("prefCameraCollected", false);

        if (null == savedInstanceState) {
            if (videoRecording) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.collectContainer, VIGDFragment.newInstance())
                        .commit();
            } else {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.collectContainer, IGDFragment.newInstance())
                        .commit();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}