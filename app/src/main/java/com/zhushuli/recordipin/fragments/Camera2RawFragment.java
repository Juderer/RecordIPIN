package com.zhushuli.recordipin.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.utils.Camera2Utils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.views.AutoFitTextureView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author      : zhushuli
 * @createDate  : 2023/04/18 17:47
 * @description : 使用Camera2 API实现单张拍摄
 */
public class Camera2RawFragment extends Fragment {

    private final static String TAG = "Camera2RawFragment";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static final int STATE_CLOSED = 0;

    private static final int STATE_OPENED = 1;

    private static final int STATE_PREVIEW = 2;

    private static final int STATE_WAITING_FOR_3A_CONVERGENCE = 3;

    private static final long PRECAPTURE_TIMEOUT_MS = 2000;

    private SharedPreferences mSharedPreferences;

    private int mPreferenceWidth;

    private int mPreferenceHeight;

    private int mPreferenceLenFacing;

    private AutoFitTextureView mTextureView;

    /**
     * 可参考官方API
     * https://developer.android.com/reference/android/view/Display#getRotation()
     */
    private int mDisplayRotation = -1;

    /**
     * 可参考官方API
     * https://developer.android.com/reference/android/view/OrientationEventListener
     */
    private OrientationEventListener mOrientationListener;

    private int mOrientationListenerValue;

    private CameraManager mCameraManager;

    private ImageReader mJpegImageReader;

    private CameraDevice mCameraDevice;

    private CameraCharacteristics mCharacteristics;

    private String mCameraId;

    private Size mPreviewSize;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CaptureRequest.Builder mCaptureRequestBuilder;

    private CameraCaptureSession mCaptureSession;

    private int mState;

    private boolean mNoAFRun;

    private long mCaptureTimer;

    private HandlerThread mPreviewThread;

    private HandlerThread mCaptureThread;

    private Handler mPreviewHandler;

    private Handler mCaptureHandler;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private final Object mCameraStateLock = new Object();

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            synchronized (mCameraStateLock) {
                Log.d(TAG, "onOpened");
                mState = STATE_OPENED;
                mCameraDevice = camera;
                mCameraOpenCloseLock.release();
                if (mTextureView != null && mTextureView.isAvailable()) {
                    createCameraPreviewSession();
                }
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            synchronized (mCameraStateLock) {
                Log.d(TAG, "onDisconnected");
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                camera.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            synchronized (mCameraStateLock) {
                Log.e(TAG, "onError");
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (getActivity() != null) {
                getActivity().finish();
            }
        }
    };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            mPreviewSize = null;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private final CameraCaptureSession.CaptureCallback mPreCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            synchronized (mCameraStateLock) {
                switch (mState) {
                    case STATE_PREVIEW:
                        // We have nothing to do when the camera preview is running normally.
                        break;
                    case STATE_WAITING_FOR_3A_CONVERGENCE:
                        boolean readyToCapture = false;
                        if (!mNoAFRun) {
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == null) {
                                break;
                            }

                            // If auto-focus has reached locked state, we are ready to capture
                            readyToCapture = (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                        }

                        // If we are running on an non-legacy device, we should also wait until
                        // auto-exposure and auto-white-balance have converged as well before
                        // taking a picture.
                        if (!Camera2Utils.isLegacyLocked(mCharacteristics)) {
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
                            if (aeState == null || awbState == null) {
                                break;
                            }

                            readyToCapture = readyToCapture &&
                                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                                    awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED;
                        }

                        // If we haven't finished the pre-capture sequence but have hit our maximum
                        // wait timeout, too bad! Begin capture anyway.
                        if (!readyToCapture && hitTimeoutLocked()) {
                            Log.w(TAG, "Timed out waiting for pre-capture sequence to complete.");
                            readyToCapture = true;
                        }

                        if (readyToCapture) {
                            captureStillPicture();
                            mState = STATE_PREVIEW;
                        }
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
//            Log.d(TAG, "onCaptureProgress Preview:" + ThreadUtils.threadID());
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
//            Log.d(TAG, "onCaptureCompleted Preview:" + ThreadUtils.threadID());
            process(result);
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     long timestamp,
                                     long frameNumber) {
            Log.d(TAG, "onCaptureStarted Capture:" + ThreadUtils.threadID());
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d(TAG, "onCaptureCompleted Capture:" + ThreadUtils.threadID());
            Log.d(TAG, String.valueOf(result.get(TotalCaptureResult.LENS_FOCAL_LENGTH)));
            Log.d(TAG, String.valueOf(result.get(TotalCaptureResult.LENS_FOCUS_DISTANCE)));
            Log.d(TAG, String.valueOf(result.get(TotalCaptureResult.SENSOR_SENSITIVITY)));
            finishedCapture();
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure failure) {
            Log.d(TAG, "onCaptureFailed Capture:" + ThreadUtils.threadID());
            finishedCapture();
        }
    };

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA);

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(4);

    private final ImageReader.OnImageAvailableListener mImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable:" + ThreadUtils.threadID());
            File jpegFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "RecordIPIN",
                    "JPEG_" + formatter.format(new Date(System.currentTimeMillis())) + ".jpg");
            Log.d(TAG, jpegFile.getAbsolutePath());

            mExecutorService.execute(new Camera2RawFragment.ImageSaver(reader, jpegFile));
        }
    };

    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getActivity();
            if (activity != null) {
                Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
            }
        }
    };

    public Camera2RawFragment() {
        // Required empty public constructor
    }

    public static Camera2RawFragment newInstance() {
        return new Camera2RawFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        String imageSize = mSharedPreferences.getString("prefCameraFrameSize", "1920x1080");
        mPreferenceWidth = Integer.parseInt(imageSize.split("x")[0]);
        mPreferenceHeight = Integer.parseInt(imageSize.split("x")[1]);
        Log.d(TAG, "mPreferenceWidth = " + mPreferenceWidth + ", " + "mPreferenceHeight = " + mPreferenceHeight);

        mPreferenceLenFacing = Integer.parseInt(mSharedPreferences.getString("prefCameraLensFacing", "1"));
        Log.d(TAG, "mPreferenceLenFacing = " + mPreferenceLenFacing);

        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

        mOrientationListener = new OrientationEventListener(getActivity(), SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                mOrientationListenerValue = Camera2Utils.discretizeOrientation(orientation);
//                Log.d(TAG, "onOrientationChanged, " + mOrientationListenerValue);
                if (mDisplayRotation < 0) {
                    return;
                }
                int deviceRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                if (mDisplayRotation == deviceRotation) {
                    return;
                }
                if (mTextureView != null && mTextureView.isAvailable()) {
                    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                }
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera2_raw, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        view.findViewById(R.id.btnTakePicture).setOnClickListener(this::onClick);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.ttvCamera);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        startBackgroundThread();
        openCamera();
        // TODO::手动触屏对焦

        mJpegImageReader = ImageReader.newInstance(mPreferenceWidth, mPreferenceHeight, ImageFormat.JPEG, 5);
        mJpegImageReader.setOnImageAvailableListener(mImageAvailableListener, mCaptureHandler);

        if (mTextureView.isAvailable()) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        stopBackgroundThread();
        closeCamera();
    }

    private void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnTakePicture:
                takePicture();
                break;
        }
    }

    private void takePicture() {
        synchronized (mCameraStateLock) {
            Log.d(TAG, "takePicture");

            // If we already triggered a pre-capture sequence, or are in a state where we cannot
            // do this, return immediately.
            if (mState != STATE_PREVIEW) {
                return;
            }

            // Trigger an auto-focus run if camera is capable. If the camera is already focused,
            // this should do nothing.
            if (!mNoAFRun) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START);
            }

            // If this is not a legacy device, we can also trigger an auto-exposure metering run.
            if (!Camera2Utils.isLegacyLocked(mCharacteristics)) {
                // Tell the camera to lock focus.
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            }

            // Update state machine to wait for auto-focus, auto-exposure, and
            // auto-white-balance (aka, "3A") to converge.
            mState = STATE_WAITING_FOR_3A_CONVERGENCE;

            startTimerLocked();

            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback, mCaptureHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void configureTransform(int width, int height) {
        synchronized (mCameraStateLock) {
            if (null == mTextureView) {
                return;
            }

            Log.d(TAG, "width = " + width + ", " + "height = " + height);

            StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

//            Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
//                    new Camera2Utils.CompareSizesByArea());
            Size largestJpeg = new Size(mPreferenceWidth, mPreferenceHeight);
//            Log.d(TAG, "largestJpeg:" + largestJpeg.toString());

            int deviceRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            mDisplayRotation = deviceRotation;

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

            // Find rotation of device in degrees (reverse device orientation for front-facing
            // cameras).
            int rotation = (mCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT) ?
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360 :
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360;

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, width, height);
            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            // Initially, output stream images from the Camera2 API will be rotated to the native
            // device orientation from the sensor's orientation, and the TextureView will default to
            // scaling these buffers to fill it's view bounds.  If the aspect ratios and relative
            // orientations are correct, this is fine.
            //
            // However, if the device orientation has been rotated relative to its native
            // orientation so that the TextureView's dimensions are swapped relative to the
            // native device orientation, we must do the following to ensure the output stream
            // images are not incorrectly scaled by the TextureView:
            //   - Undo the scale-to-fill from the output buffer's dimensions (i.e. its dimensions
            //     in the native device orientation) to the TextureView's dimension.
            //   - Apply a scale-to-fill from the output buffer's rotated dimensions
            //     (i.e. its dimensions in the current device orientation) to the TextureView's
            //     dimensions.
            //   - Apply the rotation from the native device orientation to the current device
            //     rotation.
            if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) height / previewSize.getHeight(),
                        (float) width / previewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);

            }
            matrix.postRotate(rotation, centerX, centerY);

            mTextureView.setTransform(matrix);

            // Start or restart the active capture session if the preview was initialized or
            // if its aspect ratio changed significantly.
            if (mPreviewSize == null || !Camera2Utils.checkAspectsEqual(previewSize, mPreviewSize)) {
                mPreviewSize = previewSize;
                if (mState != STATE_CLOSED) {
                    createCameraPreviewSession();
                }
            }
        }
    }

    /**
     * 参考官方API
     * https://developer.android.com/training/camera2/camera-preview
     */
    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
//        Log.d(TAG, "sensorOrientation:" + sensorOrientation);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
//        Log.d(TAG, "deviceOrientation:" + deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation - deviceOrientation + 360) % 360;
    }

    private static int sensorToDeviceRotationByListener(CameraCharacteristics c, int orientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.d(TAG, "sensorOrientation:" + sensorOrientation);

        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
            orientation = -orientation;
        }

        return (sensorOrientation - orientation + 360) % 360;
    }

    private void startBackgroundThread() {
        mPreviewThread = new HandlerThread("PreviewThread");
        mPreviewThread.start();
        mCaptureThread = new HandlerThread("CaptureThread");
        mCaptureThread.start();
        mPreviewHandler = new Handler(mPreviewThread.getLooper());
        mCaptureHandler = new Handler(mCaptureThread.getLooper());
    }

    private void stopBackgroundThread() {
        mPreviewThread.quitSafely();
        mCaptureThread.quitSafely();
        try {
            mPreviewThread.join();
            mPreviewThread = null;
            mPreviewHandler = null;
            mCaptureThread.join();
            mCaptureThread = null;
            mCaptureHandler = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        if (!setupCameraOutputs()) {
            return;
        }

        try {
            // 等待之前运行的Session结束
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            mCameraManager.openCamera(mCameraId, mStateCallback, mPreviewHandler);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (null != mJpegImageReader) {
                    mJpegImageReader.close();
                    mJpegImageReader = null;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private boolean setupCameraOutputs() {
        if (mCameraManager == null) {
            return false;
        }

        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != mPreferenceLenFacing) {
                    continue;
                }

                mCharacteristics = characteristics;
                mCameraId = cameraId;
                return true;
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface, mJpegImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            synchronized (mCameraStateLock) {
                                Log.d(TAG, "onConfigured Preview");
                                if (mCameraDevice == null) {
                                    return;
                                }
                                try {
                                    setup3AControlsLocked(mPreviewRequestBuilder);
                                    session.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mPreviewHandler);
                                    mState = STATE_PREVIEW;
                                } catch (CameraAccessException e) {
                                    throw new RuntimeException(e);
                                }
                                mCaptureSession = session;
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed Preview");
                        }
                    }, mPreviewHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void setup3AControlsLocked(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        Float minFocusDist = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        Log.d(TAG, "mNoAFRun = " + String.valueOf(minFocusDist));

        mNoAFRun = (minFocusDist == null || minFocusDist == 0);

        if (!mNoAFRun) {
            if (Camera2Utils.contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

        if (Camera2Utils.contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }

        if (Camera2Utils.contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
    }

    private void finishedCapture() {
        // Reset the auto-focus trigger in case AF didn't run quickly enough.
        if (!mNoAFRun) {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        }
    }

    private void captureStillPicture() {
        if (mCameraDevice == null) {
            return;
        }

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(mJpegImageReader.getSurface());

//            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
//            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, sensorToDeviceRotation(mCharacteristics, rotation));

            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    sensorToDeviceRotationByListener(mCharacteristics, mOrientationListenerValue));
            // 调试学习
//            Log.d(TAG, String.valueOf(sensorToDeviceRotationByListener(mCharacteristics, mOrientationListenerValue)));
//            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);

            mCaptureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, Integer.valueOf(100).byteValue());

            setup3AControlsLocked(mCaptureRequestBuilder);

            mCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mCaptureHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    private class ImageSaver implements Runnable {
        private ImageReader mImageReader;

        private File mFile;

        public ImageSaver(ImageReader imageReader, File file) {
            mImageReader = imageReader;
            mFile = file;

            if (!mFile.getParentFile().exists()) {
                mFile.getParentFile().mkdirs();
            }
        }

        @Override
        public void run() {
            Image image = mImageReader.acquireNextImage();
            Log.d(TAG, image.getWidth() + "x" + image.getHeight());
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            Log.d(TAG, String.valueOf(buffer.capacity()));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                image.close();
                try {
                    output.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            showToast("Saving JPEG as: " + mFile.getAbsolutePath());
        }
    }
}