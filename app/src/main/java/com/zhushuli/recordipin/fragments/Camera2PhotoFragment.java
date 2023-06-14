package com.zhushuli.recordipin.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
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
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.LongDef;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.utils.Camera2Utils;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.views.AutoFitTextureView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

/**
 * @author      : zhushuli
 * @createDate  : 2023/04/18 17:47
 * @description : 使用Camera2 API实现图像的连续采集
 */
public class Camera2PhotoFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "Camera2PhotoFragment";

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

    private int mSamplingPeriodMillis;

    private AutoFitTextureView mTextureView;

    private ImageButton btnPhotoRecord;

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

    private CaptureRequest.Builder mCaptureRequestBuilder;

    private CameraCaptureSession mCaptureSession;

    private ImageReader mJpegImageReader;

    private boolean mNoAFRun;

    private HandlerThread mPreviewThread;

    private Handler mPreviewHandler;

    private HandlerThread mCaptureThread;

    private Handler mCaptureHandler;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private final AtomicBoolean recording = new AtomicBoolean(false);

    private String mRecordRootDir;

    private String mRecordAbsDir;

    private ImageRecorder mImageRecorder;

    private FrameInfoRecorder mFrameInfoRecorder;

    private final Queue<String> mFrameInfos = new LinkedList<>();

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
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private final CameraCaptureSession.CaptureCallback mPreCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            // TODO::相机对焦锁定参考https://github.com/A3DV/VIRec/blob/main/app/src/main/java/io/a3dv/VIRec/Camera2Proxy.java#L388
            Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
            Log.d(TAG, String.valueOf(afMode));
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            Log.d(TAG, String.valueOf(afState));
            Integer aeMode = result.get(CaptureResult.CONTROL_AE_MODE);
            Log.d(TAG, String.valueOf(aeMode));
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            Log.d(TAG, String.valueOf(aeState));
            Integer awbMode = result.get(CaptureResult.CONTROL_AWB_MODE);
            Log.d(TAG, String.valueOf(awbMode));
            Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
            Log.d(TAG, String.valueOf(awbState));
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            Log.d(TAG, "onCaptureProgressed Preview:" + ThreadUtils.threadID());
            process(partialResult);
        }

                @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d(TAG, "onCaptureCompleted Preview:" + ThreadUtils.threadID());
            process(result);
        }
    };

    private final CameraCaptureSession.CaptureCallback mCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     long timestamp,
                                     long frameNumber) {
            Log.d(TAG, "onCaptureStarted:" + ThreadUtils.threadID());
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            Log.d(TAG, "onCaptureCompleted:" + ThreadUtils.threadID());
            /**
             * 时间戳参考CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE
             * Camera/FrameMetadata.csv
             * sysClockTime(nanos),sysTime(millis),sensorTimestamp(nanos),lensFocalLength,
             * fx,fy,cx,cy,s,frameNumber
             */
            StringBuilder sb = new StringBuilder();
            // 手机系统时间戳
            sb.append(SystemClock.elapsedRealtimeNanos()).append(",");
            // UTC时间戳
            sb.append(System.currentTimeMillis()).append(",");
            // 图像传感器时间戳
            sb.append(result.get(TotalCaptureResult.SENSOR_TIMESTAMP)).append(",");
            // 焦距
            sb.append(result.get(TotalCaptureResult.LENS_FOCAL_LENGTH)).append(",");
            // 内参
            try {
                float[] intrinsics = result.get(TotalCaptureResult.LENS_INTRINSIC_CALIBRATION);
                for (float x : intrinsics) {
                    sb.append(x).append(",");
                }
            } catch (NullPointerException e) {
                // 比如xiaomi8就没有内参信息
                // TODO::需要手动计算
                for (float x : new float[]{0, 0, 0, 0, 0}) {
                    sb.append(x).append(",");
                }
            }
            // 帧数编号？
            sb.append(result.getFrameNumber()).append("\n");
            mFrameInfos.offer(sb.toString());
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure failure) {
            Log.d(TAG, "onCaptureFailed Capture:" + ThreadUtils.threadID());
        }
    };

    private final CameraCaptureSession.StateCallback mSessionStateCallback =
            new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigured Preview");
            if (mCameraDevice == null) {
                return;
            }
            try {
                setup3AControlsLocked(mPreviewRequestBuilder);
                session.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mPreviewHandler);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            mCaptureSession = session;
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigureFailed Preview");
        }
    };

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA);

    private final SimpleDateFormat recordFormatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.CHINA);

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(8);

    private final ImageReader.OnImageAvailableListener mImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable:" + ThreadUtils.threadID());
//            File jpegFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
//                    + File.separator + "RecordIPIN",
//                    "JPEG_" + formatter.format(new Date(System.currentTimeMillis())) + ".jpg");
            String path = mRecordAbsDir + File.separator + "Camera" + File.separator + "MultiPhotos";
            mExecutorService.execute(new ImageSaver(reader, null, path));
        }
    };

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

    public Camera2PhotoFragment() {
        // Required empty public constructor
    }

    public static Camera2PhotoFragment newInstance() {
        Log.d(TAG, "newInstance");
        return new Camera2PhotoFragment();
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

        mSamplingPeriodMillis = Integer.valueOf(mSharedPreferences.getString("prefCameraPhotoSamplingPeriod", "500"));
        Log.d(TAG, "mSamplingPeriodMillis = " + mSamplingPeriodMillis);

        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

        mOrientationListener = new OrientationEventListener(getActivity(), SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                Log.d(TAG, "onOrientationChanged, " + orientation);
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

        mRecordRootDir = getActivity().getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera2_photo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.ttvCamera);
        btnPhotoRecord = (ImageButton) view.findViewById(R.id.btnPhotoRecord);
        ivHint = (ImageView) view.findViewById(R.id.ivHint);

        btnPhotoRecord.setOnClickListener(this::onClick);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        startBackgroundThread();
        openCamera();

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

        if (recording.get()) {
            recordPhoto();
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
            case R.id.btnPhotoRecord:
                recordPhoto();
                break;
            default:
                break;
        }
    }

    private void recordPhoto() {
        Log.d(TAG, "recordPhoto");
        recording.set(!recording.get());
        if (recording.get()) {
            mImageRecorder = new ImageRecorder();
            mRecordAbsDir = mRecordRootDir + File.separator +
                    recordFormatter.format(new Date(System.currentTimeMillis()));
            Log.d(TAG, mRecordAbsDir);
            mFrameInfoRecorder = new FrameInfoRecorder(mRecordAbsDir);
            mExecutorService.execute(mImageRecorder);
            mExecutorService.execute(mFrameInfoRecorder);
            btnPhotoRecord.setImageResource(R.drawable.baseline_stop_24);
        } else {
            mImageRecorder = null;
            mFrameInfoRecorder = null;
            btnPhotoRecord.setImageResource(R.drawable.baseline_fiber_manual_record_24_photo);
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
//        Log.d(TAG, "largestJpeg:" + largestJpeg.toString());

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

            Surface surface = new Surface(texture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Log.d(TAG, "SDK >= P");
                List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(new OutputConfiguration(surface));
                outputs.add(new OutputConfiguration(mJpegImageReader.getSurface()));
                SessionConfiguration config = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        outputs, mExecutorService, mSessionStateCallback);
                mCameraDevice.createCaptureSession(config);
            } else {
                mCameraDevice.createCaptureSession(Arrays.asList(surface, mJpegImageReader.getSurface()),
                        mSessionStateCallback, mPreviewHandler);
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * use auto-focus, auto-exposure, and auto-white-balance controls if available
     * 自动对焦、自动曝光、自动白平衡
     */
    private void setup3AControlsLocked(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

        Float minFocusDist = mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

        mNoAFRun = (minFocusDist == null || minFocusDist == 0);

        if (!mNoAFRun) {
            if (Camera2Utils.contains(mCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

        // 不需要对焦锁定
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

            // TODO::加入该段显示应该会更流畅
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);
            mCaptureRequestBuilder.addTarget(surface);

            setup3AControlsLocked(mCaptureRequestBuilder);

//            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
//            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, sensorToDeviceRotation(mCharacteristics, rotation));
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    sensorToDeviceRotationByListener(mCharacteristics, mOrientationListenerValue));

            mCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mCaptureHandler);
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private class FrameInfoRecorder implements Runnable {
        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        public FrameInfoRecorder(String recordDir) {
            mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir + File.separator + "Camera", "FrameMetadata.csv");
            try {
                mBufferedWriter.write("sysClockTime(nanos),sysTime(millis),sensorTimestamp(nanos)," +
                        "lensFocalLength,fx,fy,cx,cy,s,frameNumber\n");
                mBufferedWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "FrameInfoRecorder Init");
        }

        @Override
        public void run() {
            Log.d(TAG, "FrameInfoRecorder Start:" + this.mRecordDir);
            initWriter();
            int count = 0;
            while (recording.get() || mFrameInfos.size() > 0) {
                if (mFrameInfos.size() > 0) {
                    try {
                        mBufferedWriter.write(mFrameInfos.poll());
                        count++;
                        if (count > 10) {
                            mBufferedWriter.flush();
                            Log.d(TAG, "FrameInfoRecorder Write");
                            count = 0;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
        }
    }

    private class ImageSaver implements Runnable {
        private ImageReader mImageReader;

        private File mFile;

        private String mPath;

        public ImageSaver(ImageReader imageReader, @Nullable File file, String path) {
            mImageReader = imageReader;
            mFile = file;
            mPath = path;
        }

        @Override
        public void run() {
            Image image = mImageReader.acquireNextImage();
            Log.d(TAG, image.getWidth() + "x" + image.getHeight());

            if (mFile == null) {
                long imageTime = image.getTimestamp();
                mFile = new File(mPath + File.separator + String.format("%d.jpg", imageTime));
            }
            if (!mFile.getParentFile().exists()) {
                mFile.getParentFile().mkdirs();
            }

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
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
        }
    }

    private class ImageRecorder implements Runnable {
        @Override
        public void run() {
            while (recording.get()) {
                captureStillPicture();
                ThreadUtils.sleep(mSamplingPeriodMillis);
                Log.d(TAG, "Sleep");
                mMessageHandler.sendEmptyMessage(0x2001);
            }
            mMessageHandler.sendEmptyMessage(0x2000);
        }
    }
}