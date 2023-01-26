package com.zhushuli.recordipin.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.SensorEvent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ImuDynamicView extends View {

    private static final String TAG = ImuDynamicView.class.getSimpleName();

    private float xScale;
    private float yScale;
    private int xLength;
    private int yLength;
    private int xStart;
    private int yStart;

    private int screenWidth;
    private int screenHeight;

    private int maxDataSize;
    private List<Float> mDatas = new ArrayList<>();
    private List<List<Float>> mSensorDatas = new ArrayList<>();
    private long addCount = 0;
    private float mAbsMax = 0;

    private Paint coordPaint;
    private Paint textPaint;
    private List<Paint> dataPaints = new ArrayList<>();

    private final Handler handler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case 0x1234:
                    ImuDynamicView.this.invalidate();
                    break;
                default:
                    break;
            }
        }
    };

    public ImuDynamicView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        getScreenSize(context);
        initCoordParam();
        initPaint();

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    try {
//                        Thread.sleep(10);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    if (mDatas.size() >= maxDataSize) {
//                        mDatas.remove(0);
//                    }
//                    float data = (float) (-30.0 + Math.random() * 40.0);
//                    mDatas.add(data);
//                    if (Math.abs(data) > mAbsMax) {
//                        mAbsMax = Math.round(Math.abs(data));
//                    }
//                    handler.sendEmptyMessage(0x1234);
//                }
//            }
//        }).start();
    }

    public void addImuValue(SensorEvent event) {
//        float[] values = event.values;
//        if (mDatas.size() >= maxDataSize) {
//            mDatas.remove(0);
//        }
//        mDatas.add(values[1]);
//        if (Math.abs(values[1]) > mAbsMax) {
//            mAbsMax = Math.round(Math.abs(values[1]));
//        }
        float[] values = event.values;
        if (mSensorDatas.size() >= maxDataSize) {
            mSensorDatas.remove(0);
        }
        for (int i = 0; i < values.length; i ++) {
            if (Math.abs(values[i]) > mAbsMax) {
                mAbsMax = Math.abs(values[i]);
            }
        }
        mSensorDatas.add(new ArrayList<Float>(){{
            add(values[0]);
            add(values[1]);
            add(values[2]);
        }});
        handler.sendEmptyMessage(0x1234);
        addCount ++;

        // 两秒更新一次
        if (addCount > 100) {
            mAbsMax = 0;
            for (int i = 0; i < mSensorDatas.size(); i ++) {
                for (int j = 0; j < mSensorDatas.get(0).size(); j ++) {
                    if (Math.abs(mSensorDatas.get(i).get(j)) > mAbsMax) {
                        mAbsMax = Math.abs(mSensorDatas.get(i).get(j));
                    }
                }
            }
            addCount = 0;
        }
    }

    private void getScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(outMetrics);
        screenWidth = outMetrics.widthPixels;
        screenHeight = outMetrics.heightPixels;
        Log.d(TAG, "screen width:" + screenWidth + "," + "height:" + screenHeight);

        int marginTop = this.getTop();
        int marginLeft = this.getLeft();
        Log.d(TAG, "marginTop:" + marginTop + "," + "marginLeft:" + marginLeft);
    }

    /**
     * 初始化直角坐标系参数
     */
    private void initCoordParam() {
        xLength = screenWidth / 12 * 10;
        yLength = screenHeight / 20 * 4;
        xScale = 2.0F;
        yScale = 5.0F;
        xStart = xLength / 10;
        yStart = yLength / 10;
        maxDataSize = (int) (xLength / xScale);
        Log.d(TAG, "xStart:" + xStart + "," + "yStart:" + yStart + "," + "maxDataSize:" + maxDataSize);
    }

    private void initPaint() {
        coordPaint = new Paint();
        coordPaint.setStrokeWidth(3);  // 线条粗细
        coordPaint.setStyle(Paint.Style.STROKE);
        coordPaint.setAntiAlias(true);  // 去锯齿
        coordPaint.setColor(Color.BLACK);

        textPaint = new Paint();
        textPaint.setStrokeWidth(1);
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(36);

        Paint xPaint = new Paint();
        xPaint.setStrokeWidth(5);
        xPaint.setStyle(Paint.Style.FILL);
        xPaint.setAntiAlias(true);
        xPaint.setColor(Color.RED);
        dataPaints.add(xPaint);

        Paint yPaint = new Paint();
        yPaint.setStrokeWidth(5);
        yPaint.setStyle(Paint.Style.FILL);
        yPaint.setAntiAlias(true);
        yPaint.setColor(Color.GREEN);
        dataPaints.add(yPaint);

        Paint zPaint = new Paint();
        zPaint.setStrokeWidth(5);
        zPaint.setStyle(Paint.Style.FILL);
        zPaint.setAntiAlias(true);
        zPaint.setColor(Color.BLUE);
        dataPaints.add(zPaint);
    }

    /**
     * 动态调整直角坐标系参数
     */
    private void adaptCoordParam(Canvas canvas) {
        if (mAbsMax > 0) {
            if (Math.round(mAbsMax) < 0.0001F) {
                yScale = yLength;
            }
            else {
                yScale = yLength / mAbsMax;
            }

            canvas.drawText(String.format("%.2f", mAbsMax), xStart - 24, yStart, textPaint);
            canvas.drawText("-" + String.format("%.2f", mAbsMax), xStart - 24, yStart + yLength * 2, textPaint);
        }
    }

    /**
     * 绘制直角坐标系
     * @param canvas
     */
    private void drawRectCoordSys(Canvas canvas) {
        // 绘制X轴
        canvas.drawLine(xStart, yStart + yLength, xLength + xStart, yStart + yLength, coordPaint);
        canvas.drawLine(xLength + xStart - 12, yStart + yLength - 6, xLength + xStart, yStart + yLength, coordPaint);
        canvas.drawLine(xLength + xStart - 12, yStart + yLength + 6, xLength + xStart, yStart + yLength, coordPaint);

        // 绘制Y轴
        canvas.drawLine(xStart, yStart + yLength, xStart, yStart, coordPaint);
        canvas.drawLine(xStart, yStart + yLength, xStart, yStart + yLength * 2, coordPaint);
        canvas.drawLine(xStart - 6, yStart + 12, xStart, yStart, coordPaint);
        canvas.drawLine(xStart + 6, yStart + 12, xStart, yStart, coordPaint);

        canvas.drawText(String.valueOf(0), xStart - 24, yStart + yLength, textPaint);

        drawAnnotation(canvas);
    }

    private void drawAnnotation(Canvas canvas) {
        canvas.drawRect(xStart + xLength - 40, yStart + yLength * 2,
                xStart + xLength, yStart + yLength * 2 + 40, dataPaints.get(0));
        canvas.drawText("X", xStart + xLength, yStart + yLength * 2 + 40, textPaint);

        canvas.drawRect(xStart + xLength - 40, yStart + yLength * 2 + 50,
                xStart + xLength, yStart + yLength * 2 + 90, dataPaints.get(1));
        canvas.drawText("Y", xStart + xLength, yStart + yLength * 2 + 90, textPaint);

        canvas.drawRect(xStart + xLength - 40, yStart + yLength * 2 + 100,
                xStart + xLength, yStart + yLength * 2 + 140, dataPaints.get(2));
        canvas.drawText("Z", xStart + xLength, yStart + yLength * 2 + 140, textPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawRectCoordSys(canvas);
        adaptCoordParam(canvas);

        if (mSensorDatas.size() > 0) {
            for (int i = 1; i < mSensorDatas.size(); i ++) {
                for (int j = 0; j < mSensorDatas.get(0).size(); j ++) {
                    canvas.drawLine(xScale * (i - 1) + xStart, yStart + yLength - yScale * mSensorDatas.get(i - 1).get(j),
                            xScale * i + xStart, yStart + yLength - yScale * mSensorDatas.get(i).get(j), dataPaints.get(j));
                }
            }
        }
    }
}
