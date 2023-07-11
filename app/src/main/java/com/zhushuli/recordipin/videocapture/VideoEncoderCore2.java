package com.zhushuli.recordipin.videocapture;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zhushuli.recordipin.utils.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author : zhushuli
 * @createDate : 2023/05/30 09:45
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

                /**
                 * <p>It is incorrect that we start a new thread to record timestamps like {@link com.zhushuli.recordipin.services.LocationService2}.
                 * The detailed error message is below:
                 *   java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference.
                 * We use {@link BufferedWriter} directly in {@link MediaCodec.Callback}</p>
                 * <p>If you want to know more details, you could see [issue002](https://github.com/Juderer/RecordIPIN/issues/2)</p>
                 */
                try {
                    mTimeWriter.write(genFrameTimeStr(mBufferInfo));
                    mTimeCount ++;
                    if (mTimeCount % 30 == 0) {
                        mTimeCount = 0;
                        mTimeWriter.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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

    private BufferedWriter mTimeWriter;

    private int mTimeCount = 0;

    public VideoEncoderCore2(int width, int height, int bitRate, String streamDir, String timeDir)
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

        String outputFile = streamDir + File.separator + "Video" + File.separator + "Movie.mp4";
        File file = new File(outputFile);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        mMuxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        mTrackIndex = -1;
        mMuxerStarted = false;

        mTimeWriter = initFrameTimeWriter(null, timeDir);
    }

    private BufferedWriter initFrameTimeWriter(@Nullable String recordFile, @Nullable String recordDir) {
        BufferedWriter writer;
        if (recordFile == null) {
            writer = FileUtils.initWriter(recordDir + File.separator + "Video",
                    "FrameTimestamp.csv");
        } else {
            writer = FileUtils.initWriter(recordFile);
        }
        try {
            writer.write("sysClockTime[nanos],sysTime[millis],frameTime[micros]\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer;
    }

    private String genFrameTimeStr(MediaCodec.BufferInfo bufferInfo) {
        StringBuffer sb = new StringBuffer();
        sb.append(SystemClock.elapsedRealtimeNanos()).append(",");
        sb.append(System.currentTimeMillis()).append(",");
        sb.append(bufferInfo.presentationTimeUs).append("\n");
        return sb.toString();
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
        if (mTimeWriter != null) {
            FileUtils.closeBufferedWriter(mTimeWriter);
        }
    }
}
