package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraxActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = CameraxActivity.class.getSimpleName();

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture mImageCapture;
    private VideoCapture mVideoCapture;
    private Preview mPreview;
    private CameraSelector mCameraSelector;

    private PreviewView pvTest;
    private Button btnTakePhoto;
    private Button btnVideo;

    private SimpleDateFormat formatter;
    private String mRecordingDir;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camerax);
        Log.d(TAG, "onCreate");

        pvTest = (PreviewView) findViewById(R.id.pvTest);
        btnTakePhoto = (Button) findViewById(R.id.btnTakePhoto);
        btnTakePhoto.setOnClickListener(this);
        btnVideo = (Button) findViewById(R.id.btnVideo);
        btnVideo.setOnClickListener(this);

        formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        executorService = Executors.newFixedThreadPool(4);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        Log.d(TAG, Environment.getExternalStorageDirectory().getAbsolutePath());
        Log.d(TAG, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
        Log.d(TAG, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        Log.d(TAG, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * TODO::java.lang.NullPointerException:
     * Attempt to invoke virtual method 'int android.view.Display.getRotation()' on a null object reference
     */
    @SuppressLint("RestrictedApi")
    private void bindPreview(ProcessCameraProvider cameraProvider) {
        mPreview = new Preview.Builder().build();

        mCameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        mPreview.setSurfaceProvider(pvTest.getSurfaceProvider());

        mImageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(pvTest.getDisplay().getRotation())
                .setTargetResolution(new Size(1080, 1920))
                .build();

        mVideoCapture = new VideoCapture.Builder()
                .setTargetRotation(pvTest.getDisplay().getRotation())
                .setTargetResolution(new Size(720, 1280))
                .build();

        cameraProvider.bindToLifecycle((LifecycleOwner) this, mCameraSelector, mImageCapture, mPreview);
        cameraProvider.bindToLifecycle((LifecycleOwner) this, mCameraSelector, mVideoCapture, mPreview);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        executorService.shutdown();
    }

    @SuppressLint({"MissingPermission", "RestrictedApi"})
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnTakePhoto:
                ImageCapture.OutputFileOptions outputFileOptions =
                        new ImageCapture.OutputFileOptions
                                .Builder(new File(mRecordingDir + File.separator +
                                formatter.format(new Date(System.currentTimeMillis())) + ".jpeg")).build();
                mImageCapture.takePicture(outputFileOptions, executorService, new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Log.d(TAG, "onImageSaved");
                        Log.d(TAG, String.valueOf(outputFileResults.getSavedUri()));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.d(TAG, "onError");
                        Toast.makeText(CameraxActivity.this, "拍照失败！", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case R.id.btnVideo:
                if (btnVideo.getText().equals("Video")) {
                    btnVideo.setText("Stop");
                    VideoCapture.OutputFileOptions videoOutputOpts = new VideoCapture.OutputFileOptions
                            .Builder(new File(mRecordingDir + File.separator +
                            formatter.format(new Date(System.currentTimeMillis())) + ".mp4")).build();
                    mVideoCapture.startRecording(videoOutputOpts, executorService, new VideoCapture.OnVideoSavedCallback() {
                        @Override
                        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                            Log.d(TAG, "onVideoSaved");
                            Log.d(TAG, String.valueOf(outputFileResults.getSavedUri()));
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            Log.d(TAG, "onError");
                            Toast.makeText(CameraxActivity.this, "录像失败！", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                else {
                    btnVideo.setText("Video");
                    mVideoCapture.stopRecording();
                }
                break;
            default:
                break;
        }
    }
}