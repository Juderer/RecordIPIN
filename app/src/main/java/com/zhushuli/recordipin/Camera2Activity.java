package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.impl.utils.CompareSizesByArea;
import androidx.preference.PreferenceManager;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;

import com.zhushuli.recordipin.utils.Camera2Utils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.views.AutoFitTextureView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2Activity extends AppCompatActivity {
    private static final String TAG = Camera2Activity.class.getSimpleName();

    private SharedPreferences mSharePreferences;

    private int mPreferenceWidth;

    private int mPreferenceHeight;

    private AutoFitTextureView mTextureView;

    private CameraManager mCameraManager;

    private final Object mCameraStateLock = new Object();

    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private OrientationEventListener mOrientationListener;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            Log.d(TAG, String.valueOf(mTextureView.isAvailable()));
            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private String mCameraId;

    private CameraCaptureSession mCaptureSession;

    private CameraDevice mCameraDevice;

    private Size mPreviewSize;

    private CameraCharacteristics mCharacteristics;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            synchronized (mCameraStateLock) {
                mCameraOpenCloseLock.release();
                mCameraDevice = camera;

                if (mPreviewSize != null && mTextureView.isAvailable()) {
                    createCameraPreviewSessionLocked();
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError");
        }
    };

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        Log.d(TAG, "onCreate");

        mTextureView = (AutoFitTextureView) findViewById(R.id.ttvCamera);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        mOrientationListener = new OrientationEventListener(getApplicationContext(), 1_000_000) {
            @Override
            public void onOrientationChanged(int orientation) {
                Log.d(TAG, "onOrientationChanged");
                if (mTextureView != null && mTextureView.isAvailable()) {
                    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                }
            }
        };

        mSharePreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String imageSize = mSharePreferences.getString("prefCameraSizeRaw", "1920x1080");
        Log.d(TAG, "imageSize:" + imageSize);
        mPreferenceWidth = Integer.parseInt(imageSize.split("x")[0]);
        mPreferenceHeight = Integer.parseInt(imageSize.split("x")[1]);
        Log.d(TAG, "Setting:" + mPreferenceWidth + "x" + mPreferenceHeight);
    }

    private void startBackgroundThread() {
        Log.d(TAG, "startBackgroundThread");
        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        synchronized (mCameraStateLock) {
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (!setUpCameraOutputs()) {
            return;
        }

        try {
            // 等待之前运行的Session结束
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String cameraId;
            Handler backgroundHandler;
            synchronized (mCameraStateLock) {
                cameraId = mCameraId;
                backgroundHandler = mBackgroundHandler;
            }

            mCameraManager.openCamera(cameraId, mStateCallback, backgroundHandler);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean setUpCameraOutputs() {
        if (mCameraManager == null) {
            return false;
        }
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                synchronized (mCameraStateLock) {
                    mCharacteristics = characteristics;
                    mCameraId = cameraId;
                }
                return true;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private void configureTransform(int width, int height) {
        Log.d(TAG, "width:" + width + "," + "height:" + height);
        synchronized (mCameraStateLock) {
            if (null == mTextureView) {
                return;
            }

            StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new Camera2Utils.CompareSizesByArea());
//            Size largestJpeg = new Size(mPreferenceWidth, mPreferenceHeight);
            Log.d(TAG, "largestJpeg:" + largestJpeg.toString());

            int deviceRotation = this.getWindowManager().getDefaultDisplay().getRotation();
            Point displaySize = new Point();
            this.getWindowManager().getDefaultDisplay().getSize(displaySize);

            int totalRotation = sensorToDeviceRotation(mCharacteristics, deviceRotation);
            Log.d(TAG, "totalRotation:" + totalRotation);

            boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;

            int rotatedViewWidth = width;
            int rotatedViewHeight = height;

            if (swappedDimensions) {
                rotatedViewWidth = height;
                rotatedViewHeight = width;
            }

            Size previewSize = Camera2Utils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedViewWidth, rotatedViewHeight, largestJpeg);
            Log.d(TAG, "previewSize:" + previewSize.toString());

            if (swappedDimensions) {
                mTextureView.setAspectRation(previewSize.getHeight(), previewSize.getWidth());
            } else {
                mTextureView.setAspectRation(previewSize.getWidth(), previewSize.getHeight());
            }
        }

        if (mPreviewSize == null) {
            mPreviewSize = new Size(mPreferenceWidth, mPreferenceHeight);
            createCameraPreviewSessionLocked();
        }
    }

    private void createCameraPreviewSessionLocked() {
        Log.d(TAG, "createCameraPreviewSessionLocked");

        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "onConfigured");
                    synchronized (mCameraStateLock) {
                        if (null == mCameraDevice) {
                            return;
                        }
                        try {
                            session.setRepeatingRequest(previewRequestBuilder.build(),
                                    new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                                @NonNull CaptureRequest request,
                                                                @NonNull CaptureResult partialResult) {
//                                    Log.d(TAG, "onCaptureProgressed");
                                }

                                @Override
                                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                               @NonNull CaptureRequest request,
                                                               @NonNull TotalCaptureResult result) {
//                                    Log.d(TAG, "onCaptureCompleted");
                                }
                            }, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.d(TAG, "sensorOrientation:" + sensorOrientation);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        Log.d(TAG, "deviceOrientation:" + deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation - deviceOrientation + 360) % 360;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        startBackgroundThread();
        openCamera();

        if (mTextureView.isAvailable()) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            synchronized (mCameraStateLock) {
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}