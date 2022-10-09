package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.transition.Explode;
import android.transition.Fade;
import android.transition.Slide;
import android.transition.TransitionInflater;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class ThreadTestActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "My" + ThreadTestActivity.class.getSimpleName();
    private static final int MY_THREAD_STOP_CODE = 2020;

    private MyThread myThread = null;
    public boolean isMyThreadOver = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread_test);
        Log.d(TAG, "onCreate");

        TextView tvThreadMsg = (TextView) findViewById(R.id.tvThreadMsg);
        Button btnTheardStart = (Button) findViewById(R.id.btnThreadStart);
        btnTheardStart.setOnClickListener(this);
        Button btnThreadStop = (Button) findViewById(R.id.btnThreadStop);
        btnThreadStop.setOnClickListener(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.left_to_center, R.anim.center_to_right);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnThreadStart:
                if (myThread != null && !isMyThreadOver) {
                    Log.d(TAG, String.valueOf(myThread) + " running");
                } else {
                    myThread = new MyThread(this);
                    new Thread(myThread).start();
                    isMyThreadOver = false;
                }
                break;
            case R.id.btnThreadStop:
                if (myThread == null || isMyThreadOver) {
                    Log.d(TAG, String.valueOf(myThread) + " running");
                } else {
                    Message message = Message.obtain();
                    message.what = MY_THREAD_STOP_CODE;
                    myThread.mHandler.sendMessage(message);
                    isMyThreadOver = true;
                }
                break;
            default:
                break;
        }
    }

    @SuppressLint("HandlerLeak")
    public class MyThread implements Runnable {
        public Handler mHandler = null;
        private Activity activity;

        public MyThread(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            Looper.prepare();
            Log.d(TAG, "MyThread Start");
            TextView tv = (TextView) activity.findViewById(R.id.tvThreadMsg);
            tv.setText("Start");
            mHandler = new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    super.handleMessage(msg);
                    Log.d(TAG, "MyThread Handler");
                    switch (msg.what) {
                        case MY_THREAD_STOP_CODE:
                            Log.d(TAG, "MyThread Message");
                            tv.setText("Handler");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            Looper.myLooper().quit();
                            break;
                        default:
                            break;
                    }
                }
            };
            Looper.loop();
            Log.d(TAG, "MyThread End");
            tv.setText("Stop");
        }
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