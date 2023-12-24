/*
 * Copyright 2023 SHULI ZHU

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhushuli.recordipin.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
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

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.location.SatelliteInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * @author      : zhushuli
 * @createDate  : 2023/10/23 21:21
 * @description : GNSS星空图
 */
public class GnssSkyView extends View {

    private static final String TAG = GnssSkyView.class.getSimpleName();

    private static final int BORDER_SIZE = 5;

    private final int BITMAP_WIDTH = 48;

    private final int BITMAP_HEIGHT = BITMAP_WIDTH / 4 * 3;

    private int mViewWidth;

    private int mViewHeight;

    private int mScreenWidth;

    private int mScreenHeight;

    private Paint mOuterCirclePaint;

    private Paint mInnerCirclePaint;

    private Paint mTextPaint;

    private float mCircleCenterX;

    private float mCircleCenterY;

    private float mMaxRadius;

    private List<SatelliteInfo> mSatellites = new ArrayList<>();

    private final List<Bitmap> mFlags = new ArrayList<>();

    private final Semaphore mSemaphore = new Semaphore(1);

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
                GnssSkyView.this.invalidate();
        }
    };

    public GnssSkyView(Context context) {
        super(context);
    }

    public GnssSkyView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Log.d(TAG, "GnssSkyView");

        getScreenSize(context);
        mViewWidth = mScreenWidth;
        mViewHeight = mScreenHeight / 2;
        requestLayout();

        initPaints();
        initCircle();
    }

    public void updateSatellite(List<SatelliteInfo> satellites) {
        if (satellites.size() == 0) {
            return;
        }

        try {
            mSemaphore.acquire();
            mSatellites.clear();
            mSatellites = satellites;

            mFlags.clear();
            for (SatelliteInfo satellite : mSatellites) {
                mFlags.add(genSatelliteBitmap(satellite));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mSemaphore.release();
        }

        mHandler.sendEmptyMessage(0);
    }

    public void clearSatellite() {
        try {
            mSemaphore.acquire();
            mSatellites.clear();
            mFlags.clear();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mSemaphore.release();
        }
        mHandler.sendEmptyMessage(0);
    }

    private void initPaints() {
        mOuterCirclePaint = new Paint();
        mOuterCirclePaint.setStrokeWidth(5);
        mOuterCirclePaint.setStyle(Paint.Style.STROKE);
        mOuterCirclePaint.setAntiAlias(true);
        mOuterCirclePaint.setColor(Color.BLACK);

        mInnerCirclePaint = new Paint();
        mInnerCirclePaint.setStrokeWidth(1.5F);
        mInnerCirclePaint.setStyle(Paint.Style.STROKE);
        mInnerCirclePaint.setAntiAlias(true);
        mInnerCirclePaint.setColor(Color.GRAY);

        mTextPaint = new Paint();
        mTextPaint.setStrokeWidth(3);
        mTextPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextSize(40);
    }

    private void initCircle() {
        mCircleCenterX = mViewWidth * 0.5F;
        mCircleCenterY = mViewHeight * 0.5F;
        mMaxRadius = Math.min(mCircleCenterX, mCircleCenterY) * 0.98F;
    }

    private Bitmap addBorderBitmap(Bitmap source) {
        Bitmap bmpWithBorder = Bitmap.createBitmap(source.getWidth() + BORDER_SIZE * 2,
                source.getHeight() + BORDER_SIZE * 2,
                source.getConfig());
        Canvas canvas = new Canvas(bmpWithBorder);
        canvas.drawColor(getResources().getColor(R.color.GoldenYellow, getContext().getTheme()));
        canvas.drawBitmap(source, BORDER_SIZE,BORDER_SIZE, null);
        return bmpWithBorder;
    }

    private Bitmap genSatelliteBitmap(SatelliteInfo satellite) {
        Bitmap flag = BitmapFactory.decodeResource(getResources(), satellite.getPngFlag());
        Matrix matrix = new Matrix();
        float scaleWidth = (float) BITMAP_WIDTH / flag.getWidth();
        float scaleHeight = (float) BITMAP_HEIGHT / flag.getHeight();

        matrix.postScale(scaleWidth, scaleHeight);

        Bitmap newFlag = Bitmap.createBitmap(flag, 0, 0, flag.getWidth(), flag.getHeight(), matrix, false);

        if (satellite.isUsed()) {
            return addBorderBitmap(newFlag);
        }
        return newFlag;
    }

    private void getScreenSize(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(outMetrics);
        mScreenWidth = outMetrics.widthPixels;
        mScreenHeight = outMetrics.heightPixels;
        Log.d(TAG, "screen width:" + mScreenWidth + "," + "height:" + mScreenHeight);

        int marginTop = this.getTop();
        int marginLeft = this.getLeft();
        Log.d(TAG, "marginTop:" + marginTop + "," + "marginLeft:" + marginLeft);
    }

    private void drawFlags(Canvas canvas) {
        try {
            mSemaphore.acquire();
            for (int i = 0; i < mFlags.size(); i++) {
                canvas.drawBitmap(mFlags.get(i),
                        (float) (mCircleCenterX + mMaxRadius * (1 - mSatellites.get(i).getElevation() / 90) * Math.sin(Math.toRadians(mSatellites.get(i).getAzimuth()))),
                        (float) (mCircleCenterY + mMaxRadius * (1 - mSatellites.get(i).getElevation() / 90) * Math.cos(Math.toRadians(mSatellites.get(i).getAzimuth()))),
                        null);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mSemaphore.release();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Log.d(TAG, "onDraw");

        canvas.drawCircle(mCircleCenterX, mCircleCenterY, mMaxRadius, mOuterCirclePaint);
        canvas.drawCircle(mCircleCenterX, mCircleCenterY, mMaxRadius / 3 * 2, mInnerCirclePaint);
        canvas.drawCircle(mCircleCenterX, mCircleCenterY, mMaxRadius / 3, mInnerCirclePaint);

        canvas.drawLine(mCircleCenterX - mMaxRadius, mCircleCenterY,
                mCircleCenterX + mMaxRadius, mCircleCenterY,
                mInnerCirclePaint);
        canvas.drawLine(mCircleCenterX, mCircleCenterY - mMaxRadius,
                mCircleCenterX, mCircleCenterY + mMaxRadius,
                mInnerCirclePaint);

        canvas.drawText("N", mCircleCenterX - 20, mCircleCenterY - mMaxRadius + 40, mTextPaint);

        drawFlags(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Log.d(TAG, "onMeasure");
        setMeasuredDimension(mViewWidth, mViewHeight);
    }
}