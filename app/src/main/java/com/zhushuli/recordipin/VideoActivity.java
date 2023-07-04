package com.zhushuli.recordipin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.zhushuli.recordipin.gles.FullFrameRect;
import com.zhushuli.recordipin.gles.Texture2dProgram;
import com.zhushuli.recordipin.utils.DeviceUtils;
import com.zhushuli.recordipin.videocapture.Camera2Proxy;
import com.zhushuli.recordipin.videocapture.TextureMovieEncoder;
import com.zhushuli.recordipin.views.SampleGLView;

import timber.log.Timber;

class VideoActivityBase extends Activity implements SurfaceTexture.OnFrameAvailableListener {

    private static final String TAG = VideoActivityBase.class.getSimpleName();

    protected int mCameraPreviewWidth;

    protected int mCameraPreviewHeight;

    protected int mVideoFrameWidth;

    protected int mVideoFrameHeight;

    protected Camera2Proxy mCamera2Proxy = null;

    protected SampleGLView mGLView;

    protected TextureMovieEncoder sVideoEncoder = new TextureMovieEncoder();

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
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

    protected String renewOutputDir() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US);
        String folderName = dateFormat.format(new Date());

        String dataDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        String outputDir = dataDir + File.separator + folderName + File.separator + "Camera";

        (new File(outputDir)).mkdirs();
        return outputDir;
    }

    // updates mCameraPreview Width/Height
    protected void setLayoutAspectRatio(Size cameraPreviewSize) {
        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
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

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        Log.d(TAG, "onFrameAvailable");
        mGLView.requestRender();
    }
}

public class VideoActivity extends VideoActivityBase {

    private static final String TAG = VideoActivity.class.getSimpleName();

    private CameraSurfaceRenderer mRenderer = null;

    private CameraHandler mCameraHandler;

    private boolean mRecordingEnabled;      // controls button state

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        setContentView(R.layout.activity_video);

        Timber.d("onCreate");

        if (DeviceUtils.isHarmonyOs() && BuildConfig.DEBUG) {
            Timber.d("Harmony OS" + "\t" + DeviceUtils.getPhoneBrand() + "\t" + DeviceUtils.getPhoneModel());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Timber.d("onStart");

        // Configure the GLSurfaceView.  This will start the Renderer thread, with an
        // appropriate EGL context.
        mGLView = findViewById(R.id.cameraPreview_surfaceView);

        mCamera2Proxy = new Camera2Proxy(this);
        Size previewSize = mCamera2Proxy.configureCamera();
        setLayoutAspectRatio(previewSize);  // updates mCameraPreviewWidth/Height
        Size videoSize = mCamera2Proxy.getVideoSize();
        mVideoFrameWidth = videoSize.getWidth();
        mVideoFrameHeight = videoSize.getHeight();

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraHandler(this);

        mRecordingEnabled = sVideoEncoder.isRecording();

        if (mRenderer == null) {
            mRenderer = new CameraSurfaceRenderer(mCameraHandler, sVideoEncoder, 0);
            mGLView.setEGLContextClientVersion(2);  // select GLES 2.0
            mGLView.setRenderer(mRenderer);
            mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
    }

    @Override
    protected void onResume() {
        Timber.d("onResume -- acquiring camera");
        super.onResume();

        if (mCamera2Proxy == null) {
            mCamera2Proxy = new Camera2Proxy(this);
            Size previewSize = mCamera2Proxy.configureCamera();
            setLayoutAspectRatio(previewSize);
            Size videoSize = mCamera2Proxy.getVideoSize();
            mVideoFrameWidth = videoSize.getWidth();
            mVideoFrameHeight = videoSize.getHeight();
        }

        mGLView.onResume();
        mGLView.queueEvent(() -> {
            mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
            mRenderer.setVideoFrameSize(mVideoFrameWidth, mVideoFrameHeight);
        });
    }

    @Override
    protected void onPause() {
        Timber.d("onPause -- releasing camera");
        super.onPause();

        // no more frame metadata will be saved during pause
        if (mCamera2Proxy != null) {
            mCamera2Proxy.releaseCamera();
            mCamera2Proxy = null;
        }

        mGLView.queueEvent(() -> {
            // Tell the renderer that it's about to be paused so it can clean up.
            mRenderer.notifyPausing();
        });
        mGLView.onPause();
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy");
        super.onDestroy();
        mCameraHandler.invalidateHandler();
    }

    public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        Timber.d("clickToggleRecording");
        mRecordingEnabled = !mRecordingEnabled;
        if (mRecordingEnabled) {
            String outputDir = renewOutputDir();
            String outputFile = outputDir + File.separator + "Video" + File.separator + "Movie.mp4";
            String frameTimeFile = outputDir + File.separator + "FrameTimestamp.csv";
            String frameMetaFile = outputDir + File.separator + "FrameMetadata.csv";

            Timber.d(outputFile);
            Timber.d(frameTimeFile);

//            mRenderer.resetOutputFiles(outputFile, frameTimeFile); // this will not cause sync issues
            mRenderer.resetOutputFiles(outputDir, outputDir);
            mCamera2Proxy.startRecordingCaptureResult(frameMetaFile);
        } else {
            mCamera2Proxy.stopRecordingCaptureResult();
        }

        mGLView.queueEvent(() -> {
            // notify the renderer that we want to change the encoder's state
            mRenderer.changeRecordingState(mRecordingEnabled);
        });
    }
}


class CameraHandler extends Handler {
    public static final int MSG_SET_SURFACE_TEXTURE = 0;
    public static final int MSG_SET_SURFACE_TEXTURE2 = 2;

    // Weak reference to the Activity; only access this from the UI thread.
    private final WeakReference<Activity> mWeakActivity;

    public CameraHandler(Activity activity) {
        mWeakActivity = new WeakReference<>(activity);
    }

    /**
     * Drop the reference to the activity. Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    public void invalidateHandler() {
        mWeakActivity.clear();
    }


    @Override  // runs on UI thread
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;
        Object obj = inputMessage.obj;

        Timber.d("CameraHandler [%s]: what=%d", this.toString(), what);

        Activity activity = mWeakActivity.get();
        if (activity == null) {
            Timber.w("CameraHandler.handleMessage: activity is null");
            return;
        }

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                ((VideoActivityBase) activity).handleSetSurfaceTexture(
                        (SurfaceTexture) inputMessage.obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }
}

class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "CameraSurfaceRenderer";

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
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
        } else {
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                    CameraHandler.MSG_SET_SURFACE_TEXTURE2, mSurfaceTexture));
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
        GLES20.glScissor(0, 0, 50, 50);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}