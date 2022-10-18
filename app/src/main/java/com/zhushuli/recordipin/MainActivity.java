package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.zhushuli.recordipin.utils.DialogUtils;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "My" + MainActivity.class.getSimpleName();

    private static final int MY_PERMISSION_REQUEST_CODE = 2024;

    private final String[] permissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE};
    private boolean isAllGranted = false;

    private TextView tvTest;
    private Button btnPermissionTest;
    private Button btn2ThreadActivity;
    private Button btn2LocationActivity;
    private Button btn2ImuActivity;
    private long mExitTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        tvTest = (TextView) findViewById(R.id.tvTest);

        btnPermissionTest = (Button) findViewById(R.id.btnPermissionTest);
        btnPermissionTest.setOnClickListener(this);

        btn2ThreadActivity = (Button) findViewById(R.id.btn2TheardActivity);
        btn2ThreadActivity.setOnClickListener(this);

        btn2LocationActivity = (Button) findViewById(R.id.btn2LocationActivity);
        btn2LocationActivity.setOnClickListener(this);

        btn2ImuActivity = (Button) findViewById(R.id.btn2ImuActivity);
        btn2ImuActivity.setOnClickListener(this);

        isAllGranted = checkPermissionAllGranted(permissions);
        if (!isAllGranted) {
            Log.d(TAG, "定位未授权");
            ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnPermissionTest:
                isAllGranted = checkPermissionAllGranted(permissions);
                if (!isAllGranted) {
                    Log.d(TAG, "定位未授权");
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
                } else {
                    Toast.makeText(this, "GPS正常", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btn2TheardActivity:
                Intent threadIntent = new Intent(this, ThreadTestActivity.class);
                startActivity(threadIntent);
                break;
            case R.id.btn2LocationActivity:
                startActivity(new Intent(this, LocationActivity.class));
//                overridePendingTransition(R.anim.bottom_to_center, R.anim.center_to_top);
                break;
            case R.id.btn2ImuActivity:
                startActivity(new Intent(this, ImuActivity.class));
            default:
                break;
        }
    }

    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSION_REQUEST_CODE) {
            isAllGranted = true;
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }
            if (isAllGranted) {
                Log.d(TAG, "用户已授权");
            } else {
                Log.d(TAG, "用户未授权");
                DialogUtils.openAppDetails(MainActivity.this);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((System.currentTimeMillis() - mExitTime) > 5000) {
                Toast.makeText(this, "再按一次退出RecordIPIN", Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }
}