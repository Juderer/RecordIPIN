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
import android.view.Menu;
import android.view.MenuItem;
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
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};
    private boolean isAllGranted = false;

    private TextView tvTest;
    private Button btn2LocationActivity;
    private Button btn2ImuActivity;
    private Button btn2CollActivity;
    private long mExitTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        tvTest = (TextView) findViewById(R.id.tvTest);

        btn2LocationActivity = (Button) findViewById(R.id.btn2LocationActivity);
        btn2LocationActivity.setOnClickListener(this);

        btn2ImuActivity = (Button) findViewById(R.id.btn2ImuActivity);
        btn2ImuActivity.setOnClickListener(this);

        btn2CollActivity = (Button) findViewById(R.id.btn2CollActivity);
        btn2CollActivity.setOnClickListener(this);

        isAllGranted = checkPermissionAllGranted(permissions);
        if (!isAllGranted) {
            Log.d(TAG, "未授权");
            ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn2LocationActivity:
                isAllGranted = checkPermissionAllGranted(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION});
                if (!isAllGranted) {
                    Log.d(TAG, "未授权");
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
                    break;
                }
                startActivity(new Intent(this, LocationActivity.class));
                break;
            case R.id.btn2ImuActivity:
                // 注意！不授予外部读写权限仍能保存IMU数据，因为保存的路径算是内部存储
                isAllGranted = checkPermissionAllGranted(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                });
                if (!isAllGranted) {
                    Log.d(TAG, "外部存储读写权限未授权");
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
                }
                startActivity(new Intent(this, ImuActivity.class));
                break;
            case R.id.btn2CollActivity:
                isAllGranted = checkPermissionAllGranted(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION});
                if (!isAllGranted) {
                    Log.d(TAG, "未授权");
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
                    break;
                }
                startActivity(new Intent(this, CollectionActivity.class));
                break;
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.showAbout:
                // TODO::加入RecordIPIN的开发说明(如: 设计静态文字页面)
                Toast.makeText(this, "juderer.github.io", Toast.LENGTH_SHORT).show();
                break;
            case R.id.showIMU:
                startActivity(new Intent(this, ImuActivity.class));
                break;
            case R.id.checkPermission:
                isAllGranted = checkPermissionAllGranted(permissions);
                if (!isAllGranted) {
                    Log.d(TAG, "未授权");
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
                } else {
                    Toast.makeText(this, "权限正常", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.exit:
                finish();
                break;
            default:
                break;
        }
        return true;
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