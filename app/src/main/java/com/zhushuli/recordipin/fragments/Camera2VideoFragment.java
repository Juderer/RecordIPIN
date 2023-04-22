package com.zhushuli.recordipin.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.os.Build;
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
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.utils.Camera2Utils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.views.AutoFitTextureView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/04/20 16：26
 * @description : 使用Camera2 API实现录像
 */
public class Camera2VideoFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "Camera2VideoFragment";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private SharedPreferences mSharedPreferences;

    private int mPreferenceWidth;

    private int mPreferenceHeight;

    private AutoFitTextureView mTextureView;

    private ImageButton btnVideoRecord;

    private ImageView ivHint;

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

    private CameraDevice mCameraDevice;

    private CameraCharacteristics mCharacteristics;

    private String mCameraId;

    private Size mPreviewSize;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CameraCaptureSession mPreviewSession;

    private MediaRecorder mMediaRecorder;

    private final static Surface mRecorderSurface = MediaCodec.createPersistentInputSurface();

    private boolean mNoAFRun;

    private HandlerThread mPreviewThread;

    private Handler mPreviewHandler;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private final AtomicBoolean recording = new AtomicBoolean(false);

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            mCameraDevice = camera;
            mCameraOpenCloseLock.release();
            if (mTextureView != null && mTextureView.isAvailable()) {
                createCameraPreviewSession();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            mCameraOpenCloseLock.release();
            camera.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "onError");
            mCameraOpenCloseLock.release();
            mCameraDevice.close();
            mCameraDevice = null;
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
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            Log.d(TAG, "onCaptureProgressed Preview:" + ThreadUtils.threadID());
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d(TAG, "onCaptureCompleted Preview:" + ThreadUtils.threadID());
        }
    };

    private final CameraCaptureSession.StateCallback mPreviewSessionStateCb =
            new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigured Preview:" + ThreadUtils.threadID());
            if (mCameraDevice == null) {
                return;
            }
            try {
                setup3AControls(mPreviewRequestBuilder);
                session.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mPreviewHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            mPreviewSession = session;
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigureFailed Preview");
        }
    };

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA);

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(8);

    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0x2000:
                    ivHint.setVisibility(View.INVISIBLE);
                    break;
                case 0x2001:
                    if (ivHint.getVisibility() == View.INVISIBLE) {
                        ivHint.setVisibility(View.VISIBLE);
                    } else if (ivHint.getVisibility() == View.VISIBLE) {
                        ivHint.setVisibility(View.INVISIBLE);
                    }
                    break;
                case 0x2002:
                    Activity activity = getActivity();
                    if (activity != null) {
                        Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public Camera2VideoFragment() {
        // Required empty public constructor
    }

    public static Camera2VideoFragment newInstance() {
        Log.d(TAG, "newInstance");
        return new Camera2VideoFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        String imageSize = mSharedPreferences.getString("prefCameraVideoSize", "1920x1080");
        mPreferenceWidth = Integer.parseInt(imageSize.split("x")[0]);
        mPreferenceHeight = Integer.parseInt(imageSize.split("x")[1]);
        Log.d(TAG, "mPreferenceWidth = " + mPreferenceWidth + ", " + "mPreferenceHeight = " + mPreferenceHeight);

        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

        mOrientationListener = new OrientationEventListener(getActivity(), SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
//                Log.d(TAG, "onOrientationChanged, " + orientation);
                mOrientationListenerValue = Camera2Utils.discretizeOrientation(orientation);
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
        return inflater.inflate(R.layout.fragment_camera2_video, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.ttvCamera);
        btnVideoRecord = (ImageButton) view.findViewById(R.id.btnVideoRecord);
        ivHint = (ImageView) view.findViewById(R.id.ivHint);

        btnVideoRecord.setOnClickListener(this::onClick);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        startBackgroundThread();
        openCamera();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mMediaRecorder = new MediaRecorder(getContext());
        } else {
            mMediaRecorder = new MediaRecorder();
        }

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

        if (recording.get()) {
            recordVideo();
        }

        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        stopBackgroundThread();
        closeCamera();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnVideoRecord:
                recordVideo();
                break;
            default:
                break;
        }
    }

    private void recordVideo() {
        Log.d(TAG, "recordVideo");
        recording.set(!recording.get());
        if (recording.get()) {
            btnVideoRecord.setImageResource(R.drawable.baseline_stop_24);
            startRecordingVideo();
        } else {
            btnVideoRecord.setImageResource(R.drawable.baseline_fiber_manual_record_24);
            stopRecordingVideo();
        }
    }

    private void configureTransform(int width, int height) {
        if (null == mTextureView) {
            return;
        }

        Log.d(TAG, "width = " + width + ", " + "height = " + height);

        StreamConfigurationMap map = mCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

//        Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
//                new Camera2Utils.CompareSizesByArea());
        Size largestJpeg = new Size(mPreferenceWidth, mPreferenceHeight);
        Log.d(TAG, "largestJpeg:" + largestJpeg.toString());

        int deviceRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        mDisplayRotation = deviceRotation;

        int totalRotation = sensorToDeviceRotation(mCharacteristics, deviceRotation);
        Log.d(TAG, "totalRotation:" + totalRotation);

        boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;

        int rotatedViewWidth;
        int rotatedViewHeight;

        if (swappedDimensions) {
            rotatedViewWidth = height;
            rotatedViewHeight = width;
        } else {
            rotatedViewWidth = width;
            rotatedViewHeight = height;
        }

        Size previewSize = Camera2Utils.chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedViewWidth, rotatedViewHeight, largestJpeg);
        Log.d(TAG, "previewSize:" + previewSize.toString());

        if (swappedDimensions) {
            mTextureView.setAspectRation(previewSize.getHeight(), previewSize.getWidth());
        } else {
            mTextureView.setAspectRation(previewSize.getWidth(), previewSize.getHeight());
        }

        if (mPreviewSize == null || !Camera2Utils.checkAspectsEqual(previewSize, mPreviewSize)) {
            mPreviewSize = previewSize;
            createCameraPreviewSession();
        }
    }

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
        mPreviewHandler = new Handler(mPreviewThread.getLooper());
    }

    private void stopBackgroundThread() {
        mPreviewThread.quitSafely();
        try {
            mPreviewThread.join();
            mPreviewThread = null;
            mPreviewHandler = null;
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
            if (null != mPreviewSession) {
                mPreviewSession.close();
                mPreviewSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
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
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
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

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            // TODO::Harmony失真

            Surface surface = new Surface(texture);

            setupMediaRecorder();

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mRecorderSurface);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Log.d(TAG, "SDK >= P");
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(new OutputConfiguration(surface));
                outputs.add(new OutputConfiguration(mRecorderSurface));
                SessionConfiguration config = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        outputs, mExecutorService, mPreviewSessionStateCb);
                mCameraDevice.createCaptureSession(config);
            } else {
                mCameraDevice.createCaptureSession(Arrays.asList(surface, mRecorderSurface),
                        mPreviewSessionStateCb, mPreviewHandler);
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * use auto-focus, auto-exposure, and auto-white-balance controls if available
     * 自动对焦、自动曝光、自动白平衡
     */
    private void setup3AControls(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        Float minFocusDist = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

        mNoAFRun = (minFocusDist == null || minFocusDist == 0);

        if (!mNoAFRun) {
            if (Camera2Utils.contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

//        if (!mNoAFRun) {
//            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//        }
//
//        if (!Camera2Utils.isLegacyLocked(mCharacteristics)) {
//            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
//                    CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
//        }

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
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
        }
    }

    private File getVideoFile() {
        File videoFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()
                + File.separator + "RecordIPIN",
                "MP4_" + formatter.format(new Date(System.currentTimeMillis())) + ".mp4");
        return videoFile;
    }

    private File getVideoFileBeforeRecording() {
        File dftFile = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(),
                "MP4_BeforeRecording.mp4");
        return dftFile;
    }

    private void setupMediaRecorder() {
        if (null == getActivity()) {
            return;
        }
        if (null == mMediaRecorder) {
            return;
        } else {
            mMediaRecorder.reset();
        }
        try {
            if (mRecorderSurface != null) {
                mMediaRecorder.setInputSurface(mRecorderSurface);
            }
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            if (recording.get()) {
                mMediaRecorder.setOutputFile(getVideoFile());
            } else {
                mMediaRecorder.setOutputFile(getVideoFileBeforeRecording());
            }
            // TODO::重点了解，影响最终视频大小
            mMediaRecorder.setVideoEncodingBitRate(8 * mPreferenceWidth * mPreferenceHeight);
            mMediaRecorder.setCaptureRate(30.0D);
            mMediaRecorder.setVideoFrameRate(30);
            mMediaRecorder.setVideoSize(mPreferenceWidth, mPreferenceHeight);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
//            mMediaRecorder.setOrientationHint(sensorToDeviceRotation(mCharacteristics, rotation));
            mMediaRecorder.setOrientationHint(sensorToDeviceRotationByListener(mCharacteristics, mOrientationListenerValue));
            mMediaRecorder.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startRecordingVideo() {
        if (mCameraDevice == null) {
            return;
        }

        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                setupMediaRecorder();
                mMediaRecorder.start();
            }
        });

        mExecutorService.execute(new VideoHinter());
    }

    private void stopRecordingVideo() {
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                mMediaRecorder.stop();
            }
        });
    }

    private class VideoHinter implements Runnable {
        @Override
        public void run() {
            while (recording.get()) {
                ThreadUtils.sleep(100);
                mMessageHandler.sendEmptyMessage(0x2001);
            }
            mMessageHandler.sendEmptyMessage(0x2000);
        }
    }
}