package com.zhushuli.recordipin;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;

import java.text.DecimalFormat;

public class ImuActivity extends AppCompatActivity {

    private static final String TAG = "My" + ImuActivity.class.getSimpleName();

    private TextView tvAcceX;
    private TextView tvAcceY;
    private TextView tvAcceZ;
    private TextView tvGyroX;
    private TextView tvGyroY;
    private TextView tvGyroZ;

    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private Sensor mAcceSensor;
    private Sensor mGyroSensor;

    private DecimalFormat dfSensor = new DecimalFormat("#0.00000");  // 传感器数据显示精度

    private HandlerThread mSensorThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imu);
        Log.d(TAG, "onCreate");

        tvAcceX = (TextView) findViewById(R.id.tvAcceX);
        tvAcceY = (TextView) findViewById(R.id.tvAcceY);
        tvAcceZ = (TextView) findViewById(R.id.tvAcceZ);
        tvGyroX = (TextView) findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) findViewById(R.id.tvGyroZ);

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        mAcceSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mSensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                switch (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        tvAcceX.setText(dfSensor.format(event.values[0]));
                        tvAcceY.setText(dfSensor.format(event.values[1]));
                        tvAcceZ.setText(dfSensor.format(event.values[2]));
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        tvGyroX.setText(String.format("%.5f", event.values[0]));
                        tvGyroY.setText(String.format("%.5f", event.values[1]));
                        tvGyroZ.setText(String.format("%.5f", event.values[2]));
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        mSensorThread = new HandlerThread("Sensor Thread");
        mSensorThread.start();
        Handler mHandler = new Handler(mSensorThread.getLooper());

        mSensorManager.registerListener(mSensorEventListener, mAcceSensor, SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_NORMAL, mHandler);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        mSensorManager.unregisterListener(mSensorEventListener, mAcceSensor);
        mSensorManager.unregisterListener(mSensorEventListener, mGyroSensor);
        mSensorManager.unregisterListener(mSensorEventListener);
        mSensorThread.quitSafely();
    }
}