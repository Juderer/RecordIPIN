package com.zhushuli.recordipin;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public class LocationActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "My" + LocationActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        Log.d(TAG, "onCreate");
    }

//    @Override
//    public void onBackPressed() {
//        super.onBackPressed();
//        overridePendingTransition(R.anim.top_to_center, R.anim.center_to_bottom);
//    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            default:
                break;
        }
    }
}