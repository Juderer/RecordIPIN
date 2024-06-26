package com.zhushuli.recordipin.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.location.GnssStatus;
import android.location.Location;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.gles.FullFrameRect;
import com.zhushuli.recordipin.gles.Texture2dProgram;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.models.location.SatelliteInfo;
import com.zhushuli.recordipin.services.ImuService2;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.videocapture.Camera2Proxy;
import com.zhushuli.recordipin.videocapture.TextureMovieEncoder;
import com.zhushuli.recordipin.views.SampleGLView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * @author      : zhushuli
 * @createDate  : 2023/07/06 15:29
 * @description : Smartphone-based Visual-Inertial-GNSS Dataset
 */
class VIGDFragmentBase extends Fragment {

    private static final String TAG = "VIGDFragmentBase";

    protected TextView tvGnssInfo;

    protected TextView tvImuInfo;

    // 定位服务相关类
    protected LocationService2.LocationBinder mLocationBinder;

    protected LocationService2 mLocationService2;

    protected final ServiceConnection mLocationServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected Location");
            mLocationBinder = (LocationService2.LocationBinder) service;
            mLocationService2 = mLocationBinder.getLocationService2();

            mLocationService2.startLocationRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected Location");
        }
    };

    // IMU服务相关类
    protected ImuService2.ImuBinder mImuBinder;

    protected ImuService2 mImuService2;

    protected final ServiceConnection mImuServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected IMU");
            mImuBinder = (ImuService2.ImuBinder) service;
            mImuService2 = mImuBinder.getImuService2();

            mImuService2.startImuRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected IMU");
        }
    };

    protected final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Location:" + ThreadUtils.threadID());

            String action = intent.getAction();
            if (action.equals(LocationService2.GNSS_LOCATION_CHANGED_ACTION)) {
                Location location = intent.getParcelableExtra("Location");
                Message msg = Message.obtain();
                msg.what = LocationService2.GNSS_LOCATION_CHANGED_CODE;
                msg.obj = location;
                mMainHandler.sendMessage(msg);
            } else if (action.equals(LocationService2.GNSS_PROVIDER_DISABLED_ACTION)) {
                mMainHandler.sendEmptyMessage(LocationService2.GNSS_PROVIDER_DISABLED_CODE);
            }
        }
    };

    protected final HandlerThread mLocationReceiveThread = new HandlerThread("Location Receiver");

    protected final BroadcastReceiver mSatelliteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Satellite");
            String action = intent.getAction();
            Log.d(TAG, action);

            if (action.equals(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION)) {
                List<SatelliteInfo> satellites = intent.getParcelableArrayListExtra("Satellites");

                int beidouSatelliteCount = 0;
                int gpsSatelliteCount = 0;
                for (SatelliteInfo satellite : satellites) {
                    if (satellite.isUsed()) {
                        switch (satellite.getConstellationType()) {
                            case GnssStatus.CONSTELLATION_BEIDOU:
                                beidouSatelliteCount += 1;
                                break;
                            case GnssStatus.CONSTELLATION_GPS:
                                gpsSatelliteCount += 1;
                                break;
                            default:
                                break;
                        }
                    }
                }

                Message msg = Message.obtain();
                msg.what = LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE;
                msg.obj = String.format("%02d Beidou; %02d GPS", beidouSatelliteCount, gpsSatelliteCount);
                mMainHandler.sendMessage(msg);
            }
        }
    };

    protected final HandlerThread mSatelliteReceiverThread = new HandlerThread("Satellite Receiver");

    protected final BroadcastReceiver mImuReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive IMU:" + ThreadUtils.threadID());

            String action = intent.getAction();
            if (action.equals(ImuService2.IMU_SENSOR_CHANGED_ACTION)) {
                ImuInfo imuInfo = JSON.parseObject(intent.getStringExtra("IMU"), ImuInfo.class);
                Message msg = Message.obtain();
                msg.what = imuInfo.getType();
                msg.obj = imuInfo;
                mMainHandler.sendMessage(msg);
            }
        }
    };

    protected final HandlerThread mImuReceiverThread = new HandlerThread("IMU Receiver");

    // 数据存储路径
    protected final SimpleDateFormat storageFormatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    protected final SimpleDateFormat displayFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    protected String mRecordRootDir;

    protected String mRecordAbsDir;

    protected Handler mMainHandler;

    protected VIGDFragmentBase() {
        // Required empty public constructor
    }

    protected static VIGDFragmentBase newInstance() {
        VIGDFragmentBase fragment = new VIGDFragmentBase();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        if (getActivity() == null) {
            onDestroy();
        }

        mRecordRootDir = getActivity().getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        mLocationReceiveThread.start();
        mSatelliteReceiverThread.start();
        mImuReceiverThread.start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_vigd, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");
        tvGnssInfo = (TextView) view.findViewById(R.id.gnssInfo);
        tvImuInfo = (TextView) view.findViewById(R.id.imuInfo);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        IntentFilter locationIntent = new IntentFilter();
        locationIntent.addAction(LocationService2.GNSS_LOCATION_CHANGED_ACTION);
        locationIntent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        getActivity().registerReceiver(
                mLocationReceiver,
                locationIntent,
                null,
                new Handler(mLocationReceiveThread.getLooper()));

        IntentFilter satelliteIntent = new IntentFilter();
        satelliteIntent.addAction(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION);
        getActivity().registerReceiver(
                mSatelliteReceiver,
                satelliteIntent,
                null,
                new Handler(mSatelliteReceiverThread.getLooper()));

        IntentFilter imuIntent = new IntentFilter();
        imuIntent.addAction(ImuService2.IMU_SENSOR_CHANGED_ACTION);
        getActivity().registerReceiver(
                mImuReceiver,
                imuIntent,
                null,
                new Handler(mImuReceiverThread.getLooper()));
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        getActivity().unregisterReceiver(mLocationReceiver);
        getActivity().unregisterReceiver(mSatelliteReceiver);
        getActivity().unregisterReceiver(mImuReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        ThreadUtils.interrupt(mLocationReceiveThread);
        ThreadUtils.interrupt(mSatelliteReceiverThread);
        ThreadUtils.interrupt(mImuReceiverThread);
    }

    protected void bindServices() {
        // 绑定定位服务
        Intent locationIntent = new Intent(getActivity(), LocationService2.class);
        getActivity().bindService(locationIntent, mLocationServiceConn, Context.BIND_AUTO_CREATE);
        // 绑定IMU服务
        Intent imuIntent = new Intent(getActivity(), ImuService2.class);
        getActivity().bindService(imuIntent, mImuServiceConn, Context.BIND_AUTO_CREATE);
    }

    protected void unbindServices() {
        // 服务解绑
        getActivity().unbindService(mLocationServiceConn);
        getActivity().unbindService(mImuServiceConn);
    }
}

public class VIGDFragment extends VIGDFragmentBase implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = "VIGDFragment";

    private SampleGLView mGLView;

    private ImageButton btnRecord;

    private int mCameraPreviewWidth;

    private int mCameraPreviewHeight;

    private int mVideoFrameWidth;

    private int mVideoFrameHeight;

    private CameraSurfaceRenderer mRenderer = null;

    private Camera2Proxy mCamera2Proxy = null;

    private final TextureMovieEncoder mVideoEncoder = new TextureMovieEncoder();

    private CameraHandler mCameraHandler = null;

    private final AtomicBoolean recording = new AtomicBoolean(false);

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LocationService2.GNSS_LOCATION_CHANGED_CODE:
                    Location location = (Location) msg.obj;
                    StringBuffer sb = new StringBuffer();
                    sb.append(displayFormatter.format(new Date(System.currentTimeMillis()))).append("\n");
                    sb.append(String.format("%.6f,%.6f,%.2fm",
                            location.getLongitude(), location.getLatitude(), location.getAccuracy())).append("\n");
                    sb.append(String.format("%.2fm/s,%.2f\u00B0",
                            location.getSpeed(), location.getBearing()));
                    tvGnssInfo.setText(sb.toString());
                    break;
                case LocationService2.GNSS_PROVIDER_DISABLED_CODE:
                    DialogUtils.showLocationSettingsAlert(getActivity());
                    clickToggleRecording(null);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                    ImuInfo accelInfo = (ImuInfo) msg.obj;
                    tvImuInfo.setText(String.format("%.4f,%.4f,%.4f",
                            accelInfo.values[0], accelInfo.values[1], accelInfo.values[2]));
                    break;
                default:
                    break;
            }
        }
    };

//    private boolean mRecordingEnabled;

    public VIGDFragment() {
        // Required empty public constructor
    }

    public static VIGDFragment newInstance() {
        VIGDFragment fragment = new VIGDFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        super.mMainHandler = mMainHandler;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "onViewCreated");

        btnRecord = (ImageButton) view.findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(this::clickToggleRecording);

        mGLView = (SampleGLView) view.findViewById(R.id.cameraPreview_surfaceView);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        mCamera2Proxy = new Camera2Proxy(getActivity());
        Size previewSize = mCamera2Proxy.configureCamera();
        setLayoutAspectRatio(previewSize);
        Size videoSize = mCamera2Proxy.getVideoSize();
        mVideoFrameWidth = videoSize.getWidth();
        mVideoFrameHeight = videoSize.getHeight();

        mCameraHandler = new CameraHandler(this);

        if (mRenderer == null) {
            mRenderer = new CameraSurfaceRenderer(mCameraHandler, mVideoEncoder, 0);
            mGLView.setEGLContextClientVersion(2);
            mGLView.setRenderer(mRenderer);
            mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        DisplayMetrics dm = getActivity().getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        mGLView.onResume();
        mGLView.queueEvent(() -> {
            mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
            mRenderer.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight);
            mRenderer.setScreenSize(screenWidth, screenHeight);
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        if (recording.get()) {
            clickToggleRecording(null);
        }

        if (mCamera2Proxy != null) {
            mCamera2Proxy.releaseCamera();
            mCamera2Proxy = null;
        }

        mGLView.queueEvent(() -> {
            mRenderer.notifyPausing();
        });
        mGLView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mCameraHandler.invalidateHandler();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onFrameAvailable");
        mGLView.requestRender();
    }

    private void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        Log.d(TAG, "clickToggleRecording");
        recording.set(!recording.get());
        if (recording.get()) {
            btnRecord.setImageResource(R.drawable.baseline_stop_24);

            mRecordAbsDir = mRecordRootDir + File.separator + storageFormatter.format(new Date(System.currentTimeMillis()));
            String cameraFile = mRecordAbsDir + File.separator + "Camera";
            String frameMetaFile = cameraFile + File.separator + "FrameMetadata.csv";
            (new File(cameraFile)).mkdirs();

            bindServices();

            mRenderer.resetOutputFiles(cameraFile, cameraFile);
            mCamera2Proxy.startRecordingCaptureResult(frameMetaFile);
        } else {
            unbindServices();
            mCamera2Proxy.stopRecordingCaptureResult();
            btnRecord.setImageResource(R.drawable.baseline_fiber_manual_record_24);
            tvGnssInfo.setText("GNSS Information...");
            tvImuInfo.setText("IMU Information...");
        }

        mGLView.queueEvent(() -> {
            mRenderer.changeRecordingState(recording.get());
        });
    }

    public void handleSetSurfaceTexture(SurfaceTexture st) {
        st.setOnFrameAvailableListener(this);

        if (mCamera2Proxy != null) {
            mCamera2Proxy.setPreviewSurfaceTexture(st);
            mCamera2Proxy.openCamera();
        } else {
            throw new RuntimeException(
                    "Try to set surface texture while camera2proxy is null");
        }
    }

    protected void setLayoutAspectRatio(Size cameraPreviewSize) {
        Display display = ((WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mCameraPreviewWidth = cameraPreviewSize.getWidth();
        mCameraPreviewHeight = cameraPreviewSize.getHeight();
        if (display.getRotation() == Surface.ROTATION_0) {
            mGLView.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth);
        } else if (display.getRotation() == Surface.ROTATION_180) {
            mGLView.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth);
        } else {
            mGLView.setAspectRatio((double) mCameraPreviewWidth / mCameraPreviewHeight);
        }
    }
}

class CameraHandler extends Handler {

    private static final String TAG = "VIGDCameraHandler";

    public static final int MSG_SET_SURFACE_TEXTURE = 0;

    // Weak reference to the Activity; only access this from the UI thread.
    private final WeakReference<Fragment> mWeakFragment;

    public CameraHandler(Fragment fragment) {
        mWeakFragment = new WeakReference<>(fragment);
    }

    /**
     * Drop the reference to the activity. Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    public void invalidateHandler() {
        mWeakFragment.clear();
    }


    @Override  // runs on UI thread
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;
        Object obj = inputMessage.obj;

        Log.d(TAG, String.format("CameraHandler [%s]: what=%d", this.toString(), what));

        Fragment fragment = mWeakFragment.get();
        if (fragment == null) {
            Log.w(TAG, "CameraHandler.handleMessage: fragment is null");
            return;
        }

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                ((VIGDFragment) fragment).handleSetSurfaceTexture(
                        (SurfaceTexture) inputMessage.obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }
}

class CameraSurfaceRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "VIGDCameraSurfaceRenderer";

    private static final int RECORDING_OFF = 0;

    private static final int RECORDING_ON = 1;

    private static final int RECORDING_RESUMED = 2;

    private final CameraHandler mCameraHandler;

    private final TextureMovieEncoder mVideoEncoder;

    private String mOutputFile;

    private String mMetadataFile;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];

    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;

    private boolean mRecordingEnabled;

    private int mRecordingStatus;

    private int mFrameCount;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;

    private int mIncomingWidth;

    private int mIncomingHeight;

    private int mVideoFrameWidth;

    private int mVideoFrameHeight;

    private int mScreenWidth;

    private int mScreenHeight;

    private final int mCameraId;

    private final Object mLockObj = new Object();

    public CameraSurfaceRenderer(CameraHandler cameraHandler,
                                 TextureMovieEncoder movieEncoder, int cameraId) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;
        mTextureId = -1;

        mRecordingStatus = -1;
        mRecordingEnabled = false;
        mFrameCount = -1;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;
        mVideoFrameWidth = mVideoFrameHeight = -1;

        mCameraId = cameraId;
    }

    public void resetOutputFiles(String outputFile, String metaFile) {
        mOutputFile = outputFile;
        mMetadataFile = metaFile;
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     * <p>
     * For best results, call this *after* disabling Camera preview.
     */
    public void notifyPausing() {
        mRecordingEnabled = false;
        if (mRecordingStatus != RECORDING_OFF) {
            mVideoEncoder.stopRecording();
        }
        if (mSurfaceTexture != null) {
            Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
        mVideoFrameWidth = mVideoFrameHeight = -1;
    }

    /**
     * Notifies the renderer that we want to stop or start recording.
     */
    public void changeRecordingState(boolean isRecording) {
        Log.d(TAG, String.format("changeRecordingState: was %b now %b", mRecordingEnabled, isRecording));
        mRecordingEnabled = isRecording;
    }

    /**
     * Records the size of the incoming camera preview frames.
     * <p>
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    public void setCameraPreviewSize(int width, int height) {
        Log.d(TAG, "setCameraPreviewSize");
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    public void setVideoFrameSize(int width, int height) {
        mVideoFrameWidth = width;
        mVideoFrameHeight = height;
    }

    public void setScreenSize(int width, int height) {
        mScreenWidth = width;
        mScreenHeight = height;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        // We're starting up or coming back. Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        mRecordingEnabled = mVideoEncoder.isRecording();
        if (mRecordingEnabled) {
            mRecordingStatus = RECORDING_RESUMED;
        } else {
            mRecordingStatus = RECORDING_OFF;
        }

        // Set up the texture glitter that will be used for on-screen display. This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

        mTextureId = mFullScreen.createTextureObject();

        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        // Tell the UI thread to enable the camera preview.
        if (mCameraId == 0) {
            // default lens facing back
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
        } else {
            // Pass
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.d(TAG, String.format("onSurfaceChanged %dx%d", width, height));
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        Log.d(TAG, "onDrawFrame");
        Log.d(TAG, "mRecordingEnabled:" + mRecordingEnabled);
        Log.d(TAG, "mRecordingStatus:" + mRecordingStatus);

        boolean showBox;

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        mSurfaceTexture.updateTexImage();

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        if (mRecordingEnabled) {
            switch (mRecordingStatus) {
                case RECORDING_OFF:
                    if (mVideoFrameWidth <= 0 || mVideoFrameHeight <= 0) {
                        Log.i(TAG,"Start recording before setting video frame size; skipping");
                        break;
                    }
                    Log.d(TAG, String.format("Start recording outputFile: %s", mOutputFile));
                    // The output video has a size e.g., 720x1280. Video of the same size is recorded in
                    // the portrait mode of the complex CameraRecorder-android at
                    // https://github.com/MasayukiSuda/CameraRecorder-android.
                    mVideoEncoder.startRecording(
                            new TextureMovieEncoder.EncoderConfig(
                                    mOutputFile, mVideoFrameHeight, mVideoFrameWidth,
                                    8 * mVideoFrameWidth * mVideoFrameHeight,
                                    EGL14.eglGetCurrentContext(),
                                    mFullScreen.getProgram(), mMetadataFile));
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_RESUMED:
                    Log.d(TAG, "Resume recording");
                    mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                    mRecordingStatus = RECORDING_ON;
                    break;
                case RECORDING_ON:
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        } else {
            switch (mRecordingStatus) {
                case RECORDING_ON:
                case RECORDING_RESUMED:
                    Log.d(TAG, "Stop recording");
                    synchronized (mLockObj) {
                        mVideoEncoder.stopRecording();
                        mRecordingStatus = RECORDING_OFF;
                    }
                    break;
                case RECORDING_OFF:
                    break;
                default:
                    throw new RuntimeException("unknown status " + mRecordingStatus);
            }
        }

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        mVideoEncoder.setTextureId(mTextureId);

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mVideoEncoder.frameAvailable(mSurfaceTexture);

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Log.i(TAG, "Drawing before incoming texture size set; skipping");
            return;
        }

        if (mIncomingSizeUpdated) {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }

        // Draw the video frame.
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix);

        // Draw a flashing box if we're recording. This only appears on screen.
        showBox = (mRecordingStatus == RECORDING_ON);
        if (showBox && (++mFrameCount & 0x04) == 0) {
            drawBox();
        }
    }

    /**
     * Draws a red box in the corner.
     */
    private void drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
//        GLES20.glScissor(0, 0, 50, 50);
        GLES20.glScissor( mScreenWidth - 50,  mScreenWidth * mVideoFrameWidth / mVideoFrameHeight - 50,
                50, 50);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}