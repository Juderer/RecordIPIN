package com.zhushuli.recordipin.videocapture;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author      : zhushuli
 * @createDate  : 2023/05/30 09:45
 * @description : MediaCodec视频编码
 */
public class VideoEncoderCore2 {

    private static final String TAG = "VideoEncoderCore2";

    private static final int FRAME_RATE = 30;

    private static final int IFRAME_INTERVAL = 1;

    private MediaCodec mEncoder;

    private MediaFormat mFormat;

    private MediaMuxer mMuxer;

    private boolean mMuxerStarted;

    private int mTrackIndex;

    private MediaCodec.BufferInfo mBufferInfo;

    private Surface mInputSurface;

    private boolean mEncoderInExecutingState;

    private final MediaCodec.Callback mCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            Log.d(TAG, "onInputBufferAvailable");
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            Log.d(TAG, "onOutputBufferAvailable");
            ByteBuffer encodedData = codec.getOutputBuffer(index);
            mBufferInfo = info;
            if (encodedData == null) {
                throw new RuntimeException("encoderOutputBuffer " + index + " was null");
            }

            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                mBufferInfo.size = 0;
            }

            if (mBufferInfo.size != 0) {
                if (!mMuxerStarted) {
                    throw new RuntimeException("muxer hasn't started");
                }

                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                encodedData.position(mBufferInfo.offset);
                encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
            }
            mEncoder.releaseOutputBuffer(index, false);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.d(TAG, "onError");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            Log.d(TAG, "onOutputFormatChanged");
            mFormat = format;
            mTrackIndex = mMuxer.addTrack(mFormat);
            // TODO::设置视频朝向
            mMuxer.setOrientationHint(0);
            mMuxer.start();
            mMuxerStarted = true;
        }
    };

    private HandlerThread mCallbackThread;

    private Handler mCallbackHandler;

    public VideoEncoderCore2(int width, int height, int bitRate, String outputFile, String metaFile)
            throws IOException {
        Log.d(TAG, "VideoEncoderCore2");
        mBufferInfo = new MediaCodec.BufferInfo();

        mFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

        mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mCallbackThread = new HandlerThread("EncoderCallback");
        mCallbackThread.start();
        mCallbackHandler = new Handler(mCallbackThread.getLooper());

        mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        // TODO::Harmony系统不兼容最新API，需使用VideoEncoderCore类
        mEncoder.setCallback(mCallback, mCallbackHandler);
        try {
            mEncoder.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (MediaCodec.CodecException e) {
            Log.d(TAG, e.toString());
        }
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        mMuxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    public VideoEncoderCore2(int width, int height, int bitRate, @Nullable Surface surface) throws IOException {
        Log.d(TAG, "VideoEncoderCore2");
        mFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

        // TODO::Harmony系统出现闪屏问题!!!可尝试使用VideoEncoderCore类解决。
        mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        mFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
//        mFormat.setInteger(MediaFormat.KEY_BITRATE_MODE,
//                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        mFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        mFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mCallbackThread = new HandlerThread("EncoderCallback");
        mCallbackThread.start();
        mCallbackHandler = new Handler(mCallbackThread.getLooper());

        mEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        mEncoder.setCallback(mCallback, mCallbackHandler);
        mEncoder.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (surface == null) {
            mInputSurface = mEncoder.createInputSurface();
        } else {
            mEncoder.setInputSurface(surface);
        }
        mEncoder.start();

        String outputFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() +
                File.separator + "test.mp4";
        mMuxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void release() {
        Log.d(TAG, "release");
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
        if (mCallbackThread != null) {
            mCallbackThread.quitSafely();
        }
    }
}
