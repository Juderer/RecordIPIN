package com.zhushuli.recordipin.videocapture;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.zhushuli.recordipin.utils.Camera2Utils;
import com.zhushuli.recordipin.utils.FileUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera2Proxy {

    private static final String TAG = "Camera2Proxy";

    private final Activity mActivity;

    private static SharedPreferences mSharedPreferences;

    private int mPreferenceWidth;

    private int mPreferenceHeight;

    private String mCameraIdStr = "0";  // default lens facing back

    private Size mPreviewSize;

    private Size mVideoSize;

    private final CameraManager mCameraManager;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mCaptureSession;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private Integer mTimeSourceValue;

    private CaptureRequest mPreviewRequest;

    private Handler mBackgroundHandler;

    private HandlerThread mBackgroundThread;

    private Surface mPreviewSurface;

    private SurfaceTexture mPreviewSurfaceTexture = null;

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Wait until the CONTROL_AF_MODE is in auto.
     */
    private static final int STATE_WAITING_AUTO = 1;

    /**
     * Trigger auto focus algorithm.
     */
    private static final int STATE_TRIGGER_AUTO = 2;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 3;

    /**
     * Camera state: Focus distance is locked.
     */
    private static final int STATE_FOCUS_LOCKED = 4;
    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mFocusCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    private Queue<String> mFrameInfos = new LinkedList<>();

    private FrameMetadataRecorder mFrameMetadataRecorder;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private volatile boolean mRecordingMetadata = false;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            mCameraDevice = camera;
            initPreviewRequest();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            releaseCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.w(TAG, "Camera Open failed with error " + error);
            releaseCamera();
        }
    };

    public Integer getTimeSourceValue() {
        return mTimeSourceValue;
    }

    public Size getVideoSize() {
        return mVideoSize;
    }

    public void startRecordingCaptureResult(String captureResultFile) {
        mRecordingMetadata = true;
        mFrameMetadataRecorder = new FrameMetadataRecorder(captureResultFile, null);
        mExecutorService.execute(mFrameMetadataRecorder);
    }

//    public void resumeRecordingCaptureResult() {
//        mRecordingMetadata = true;
//    }
//
//    public void pauseRecordingCaptureResult() {
//        mRecordingMetadata = false;
//    }

    public void stopRecordingCaptureResult() {
        if (mRecordingMetadata) {
            mRecordingMetadata = false;
        }
        mFrameMetadataRecorder = null;
    }

    public Camera2Proxy(Activity activity) {
        mActivity = activity;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

        String imageSize = mSharedPreferences.getString("prefCameraVideoSize", "1920x1080");
        mPreferenceWidth = Integer.parseInt(imageSize.split("x")[0]);
        mPreferenceHeight = Integer.parseInt(imageSize.split("x")[1]);
        Log.d(TAG, "mPreferenceWidth = " + mPreferenceWidth + ", " + "mPreferenceHeight = " + mPreferenceHeight);
    }

    public Size configureCamera() {
        try {
            CameraCharacteristics mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraIdStr);
            StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics
                    .SCALER_STREAM_CONFIGURATION_MAP);

            mTimeSourceValue = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);

            mVideoSize = new Size(mPreferenceWidth, mPreferenceHeight);

            mPreviewSize = Camera2Utils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    mPreferenceWidth, mPreferenceHeight, mVideoSize);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        }
        return mPreviewSize;
    }

    @SuppressLint("MissingPermission")
    public void openCamera() {
        Log.v(TAG, "openCamera");
        startBackgroundThread();
        if (mCameraIdStr.isEmpty()) {
            configureCamera();
        }
        try {
            mCameraManager.openCamera(mCameraIdStr, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        }
    }

    public void releaseCamera() {
        Log.v(TAG, "releaseCamera");
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mPreviewSurfaceTexture = null;
        mCameraIdStr = "0";
        stopRecordingCaptureResult();
        stopBackgroundThread();
    }

    public void setPreviewSurfaceTexture(SurfaceTexture surfaceTexture) {
        mPreviewSurfaceTexture = surfaceTexture;
    }

    private static class NumExpoIso {
        public Long mNumber;
        public Long mExposureNanos;
        public Integer mIso;

        public NumExpoIso(Long number, Long expoNanos, Integer iso) {
            mNumber = number;
            mExposureNanos = expoNanos;
            mIso = iso;
        }
    }

    private final int kMaxExpoSamples = 10;
    private final ArrayList<NumExpoIso> expoStats = new ArrayList<>(kMaxExpoSamples);

    private void initPreviewRequest() {
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Set control elements, we want auto white balance
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);

            // We disable customizing focus distance by user input because
            // it is less flexible than tap to focus.
//            boolean manualControl = mSharedPreferences.getBoolean("switchManualControl", false);
//            if (manualControl) {
//                String focus = mSharedPreferences.getString("prefFocusDistance", "5.0");
//                Float focusDistance = Float.parseFloat(focus);
//                mPreviewRequestBuilder.set(
//                        CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
//                mPreviewRequestBuilder.set(
//                        CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance);
//                Log.d("Focus distance set to %f", focusDistance);
//            }

            List<Surface> surfaces = new ArrayList<>();

            if (mPreviewSurfaceTexture != null && mPreviewSurface == null) { // use texture view
                mPreviewSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),
                        mPreviewSize.getHeight());
                mPreviewSurface = new Surface(mPreviewSurfaceTexture);
            }
            surfaces.add(mPreviewSurface);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);

            mCameraDevice.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureSession = session;
                            mPreviewRequest = mPreviewRequestBuilder.build();
                            startPreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.w(TAG, "ConfigureFailed. session: mCaptureSession");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        }
    }

    public void startPreview() {
        Log.v(TAG, "startPreview");
        if (mCaptureSession == null || mPreviewRequestBuilder == null) {
            Log.w(TAG, "startPreview: mCaptureSession or mPreviewRequestBuilder is null");
            return;
        }
        try {
            mCaptureSession.setRepeatingRequest(
                    mPreviewRequest, mFocusCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, e.toString());
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to tap to focus.
     * https://stackoverflow.com/questions/42127464/how-to-lock-focus-in-camera2-api-android
     */
    private final CameraCaptureSession.CaptureCallback mFocusCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
//                    mState = STATE_WAITING_AUTO;
                    break;
                }
                case STATE_WAITING_AUTO: {
                    Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
//                    if (afMode != null && afMode == CaptureResult.CONTROL_AF_MODE_AUTO) {
//                        mState = STATE_TRIGGER_AUTO;
//
//                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                                CaptureRequest.CONTROL_AF_MODE_AUTO);
//                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
//                                CameraMetadata.CONTROL_AF_TRIGGER_START);
//                        try {
//                            mCaptureSession.capture(
//                                    mPreviewRequestBuilder.build(),
//                                    mFocusCaptureCallback, mBackgroundHandler);
//                        } catch (CameraAccessException e) {
//                            Log.e(e);
//                        }
//                    }
                    if (afMode != null && afMode == CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                        mState = STATE_TRIGGER_AUTO;

                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO);
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CameraMetadata.CONTROL_AF_TRIGGER_START);
                        try {
                            mCaptureSession.capture(
                                    mPreviewRequestBuilder.build(),
                                    mFocusCaptureCallback, mBackgroundHandler);
                        } catch (CameraAccessException e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                    break;
                }
                case STATE_TRIGGER_AUTO: {
                    mState = STATE_WAITING_LOCK;

                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                    try {
                        mCaptureSession.setRepeatingRequest(
                                mPreviewRequestBuilder.build(),
                                mFocusCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, e.toString());
                    }
                    Log.d(TAG, "Focus trigger auto");
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        mState = STATE_FOCUS_LOCKED;
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        mState = STATE_FOCUS_LOCKED;
                        Log.d(TAG, "Focus locked after waiting lock");
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

//        @Override
//        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
//                                       @NonNull CaptureRequest request,
//                                       @NonNull TotalCaptureResult result) {
//            long unixTime = System.currentTimeMillis();
//            process(result);
//
//            Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
//            Long number = result.getFrameNumber();
//            Long exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
//
//            Long frmDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION);
//            Long frmReadoutNs = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
//            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
//            if (expoStats.size() > kMaxExpoSamples) {
//                expoStats.subList(0, kMaxExpoSamples / 2).clear();
//            }
//            expoStats.add(new NumExpoIso(number, exposureTimeNs, iso));
//
//            Float fl = result.get(CaptureResult.LENS_FOCAL_LENGTH);
//
//            Float fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
//
//            Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
//
//            Rect rect = result.get(CaptureResult.SCALER_CROP_REGION);
//            String delimiter = ",";
//            String frame_info = timestamp +
//                    delimiter + number +
//                    delimiter + exposureTimeNs +
//                    delimiter + frmDurationNs +
//                    delimiter + frmReadoutNs +
//                    delimiter + iso +
//                    delimiter + fl +
//                    delimiter + fd +
//                    delimiter + afMode +
//                    delimiter + unixTime + "000000";
//            if (mRecordingMetadata) {
//                try {
//                    mFrameMetadataWriter.write(frame_info + "\n");
//                } catch (IOException err) {
//                    Log.e(TAG, "Error writing captureResult");
//                }
//            }
//        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d(TAG, "onCaptureCompleted");
            StringBuilder sb = new StringBuilder();
            // 手机系统时间戳
            sb.append(SystemClock.elapsedRealtimeNanos()).append(",");
            // UTC时间戳
            sb.append(System.currentTimeMillis()).append(",");
            // 图像传感器时间戳
            sb.append(result.get(TotalCaptureResult.SENSOR_TIMESTAMP)).append(",");
            // 焦距
            sb.append(result.get(TotalCaptureResult.LENS_FOCAL_LENGTH)).append(",");
            //
            sb.append(result.get(TotalCaptureResult.LENS_FOCUS_DISTANCE)).append(",");
            // ISO
            sb.append(result.get(TotalCaptureResult.SENSOR_SENSITIVITY)).append(",");
            // 帧数编号？
            sb.append(result.getFrameNumber()).append(",");
            // 曝光时间
            sb.append(result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME)).append(",");
            // 帧间隔
            sb.append(result.get(TotalCaptureResult.SENSOR_FRAME_DURATION)).append(",");
            sb.append(result.get(TotalCaptureResult.SENSOR_ROLLING_SHUTTER_SKEW)).append(",");
            // 内参
            try {
                float[] intrinsics = result.get(TotalCaptureResult.LENS_INTRINSIC_CALIBRATION);
                for (float x : intrinsics) {
                    sb.append(x).append(",");
                }
            } catch (NullPointerException e) {
                // 比如xiaomi8就没有内参信息
                // TODO::需要手动计算
                // TODO::Harmony系统有所差别
                for (float x : new float[]{0, 0, 0, 0, 0}) {
                    sb.append(x).append(",");
                }
            }
//            sb.replace(sb.length() - 1, sb.length(), "\n");
            sb.append("\n");
            if (mRecordingMetadata) {
                mFrameInfos.add(sb.toString());
            }
        }
    };

    private void startBackgroundThread() {
        if (mBackgroundThread == null || mBackgroundHandler == null) {
            Log.v(TAG, "startBackgroundThread");
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        Log.v(TAG, "stopBackgroundThread");
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
            }
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
    }

    private class FrameMetadataRecorder implements Runnable {

        private String mRecordFile;

        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        public FrameMetadataRecorder(@Nullable String recordFile, @Nullable String recordDir) {
            this.mRecordFile = recordFile;
            this.mRecordDir = recordDir;
        }

        private void initWriter() {
            if (mRecordFile == null) {
                mBufferedWriter = FileUtils.initWriter(mRecordDir, "FrameTimestamp.csv");
            } else {
                mBufferedWriter = FileUtils.initWriter(mRecordFile);
            }
            try {
                mBufferedWriter.write("sysClockTime(nanos),sysTime(millis),sensorTimestamp(nanos)," +
                        "lensFocalLength,lensFocusDistance,iso,frameNumber," +
                        "exposureTime(nanos),frameDuration(nanos),frameReadoutTime(nanos),fx,fy,cx,cy,s\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Log.d(TAG, "FrameMetadataRecorder");
            initWriter();
            int count = 0;
            while (mRecordingMetadata || mFrameInfos.size() > 0) {
                if (mFrameInfos.size() > 0) {
                    try {
                        mBufferedWriter.write(mFrameInfos.poll());
                        count++;
                        if (count > 30 * 3) {
                            mBufferedWriter.flush();
                            count = 0;
                            Log.d(TAG, "FrameMetadataRecorder Write");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
        }
    }
}