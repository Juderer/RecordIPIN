package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.impl.utils.CompareSizesByArea;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
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
import android.view.Surface;
import android.view.TextureView;

import com.zhushuli.recordipin.views.AutoFitTextureView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera2Activity extends AppCompatActivity {
    private static final String TAG = Camera2Activity.class.getSimpleName();

    // camera state
    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPENED = 1;
    private static final int STATE_PREVIEW = 2;
    private static final int STATE_WAITTING_FOR_3A_CONVERGENCE = 3;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private ImageReader mPreviewReader;

    private HandlerThread mImageReaderThread;
    private HandlerThread mSessionThread;

    private AutoFitTextureView ttvCamera;

    private Size previewSize;
    private Range<Integer>[] fpsRanges;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            mCameraDevice = camera;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            releaseCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.d(TAG, "onError");
            releaseCamera();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        Log.d(TAG, "onCreate");

        ttvCamera = (AutoFitTextureView) findViewById(R.id.ttvCamera);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//        initCameraInfo();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        ttvCamera.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable");
                configCamera();
                configImageReader();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    private void configCamera() {
        try {
            // 遍历相机列表，使用前置相机
            for (String cid : mCameraManager.getCameraIdList()) {
                // 获取相机配置
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cid);
                // 使用后置相机
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);  // 获取相机朝向
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                // 获取相机输出格式/尺寸参数
                StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                Log.d(TAG, "" + Arrays.toString(fpsRanges));
                // 设定最佳预览尺寸
//                previewSize = setOptimalPreviewSize(configs.getOutputSizes(ImageReader.class), ttvCamera.getWidth(), ttvCamera.getHeight());
                previewSize = new Size(960, 1280);
                ttvCamera.setAspectRation(previewSize.getWidth(), previewSize.getHeight());
                Size[] sizes = configs.getOutputSizes(MediaRecorder.class);
                for (int i = 0; i < sizes.length; i ++) {
                    Log.d(TAG, String.valueOf(sizes[i]));
                }
                // 打印最佳预览尺寸
                Log.d(TAG, "最佳预览尺寸（w-h）：" + previewSize.getWidth() + "-" + previewSize.getHeight());
                mCameraId = cid;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configImageReader() {
        mImageReaderThread = new HandlerThread("Reader");
        mImageReaderThread.start();
        Handler handler = new Handler(mImageReaderThread.getLooper());
        mPreviewReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 2);
        mPreviewReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "onImageAvailable");
                //获取预览帧数据
                Image image = reader.acquireNextImage();
                Log.d(TAG, "image:" + image.getWidth() + "," + image.getHeight());
                image.close();
            }
        }, handler);
    }

    @SuppressLint("RestrictedApi")
    private Size setOptimalPreviewSize(Size[] sizes, int previewViewWidth, int previewViewHeight) {
        List<Size> bigEnoughSizes = new ArrayList<>();
        List<Size> notBigEnoughSizes = new ArrayList<>();

        for (Size size : sizes) {
            if (size.getWidth() >= previewViewWidth && size.getHeight() >= previewViewHeight) {
                bigEnoughSizes.add(size);
            } else {
                notBigEnoughSizes.add(size);
            }
        }

        if (bigEnoughSizes.size() > 0) {
            return Collections.min(bigEnoughSizes, new CompareSizesByArea());
        } else if (notBigEnoughSizes.size() > 0) {
            return Collections.max(notBigEnoughSizes, new CompareSizesByArea());
        } else {
            Log.d(TAG, "未找到合适的预览尺寸");
            return sizes[0];
        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            mCameraManager.openCamera(mCameraId, mExecutorService, mStateCallback);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void initCameraInfo() {
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            Log.d(TAG, cameraIdList[0]);

            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraIdList[0]);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size[] sizes = map.getOutputSizes(ImageReader.class);
//            Size[] sizes = map.getOutputSizes(map.getOutputFormats()[0]);
            Log.d(TAG, String.valueOf(sizes.length));
            for (int i = 0; i < sizes.length; i ++) {
                Log.d(TAG, String.valueOf(sizes[i]));
            }
            previewSize = sizes[-8];
            mCameraManager.openCamera(cameraIdList[0], mExecutorService, mStateCallback);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createPreviewSession() {
        //根据TextureView 和 选定的 previewSize 创建用于显示预览数据的Surface
        SurfaceTexture surfaceTexture = ttvCamera.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());//设置SurfaceTexture缓冲区大小
        final Surface previewSurface = new Surface(surfaceTexture);

        final Surface readerSurface = mPreviewReader.getSurface();

        mSessionThread = new HandlerThread("Session");
        mSessionThread.start();
        Handler handler = new Handler(mSessionThread.getLooper());

        try {
            //创建预览session
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, readerSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        // 构建预览捕获请求
                        CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(previewSurface);  // 设置 previewSurface 作为预览数据的显示界面
                        builder.addTarget(readerSurface);
                        CaptureRequest captureRequest = builder.build();
                        // 设置重复请求，以获取连续预览数据
                        session.setRepeatingRequest(captureRequest, new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                                super.onCaptureProgressed(session, request, partialResult);
                            }

                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }
                        }, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void releaseCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
            mSessionThread.quitSafely();
        }
        if (mPreviewReader != null) {
            mPreviewReader.close();
            mPreviewReader = null;
            mImageReaderThread.quitSafely();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}