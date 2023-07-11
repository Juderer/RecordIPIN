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
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/07/11 14:02
 * @description : MediaCodec视频编码，修复issue-002的另一种方法
 */
public class VideoEncoderCore2Fixed {

    private static final String TAG = "VideoEncoderCore2Fixed";

    private static final int FRAME_RATE = 30;

    private static final int IFRAME_INTERVAL = 1;

    private MediaCodec mEncoder;

    private MediaFormat mFormat;

    private MediaMuxer mMuxer;

    private boolean mMuxerStarted;

    private int mTrackIndex;

    private MediaCodec.BufferInfo mBufferInfo;

    private Surface mInputSurface;

    private VideoTimeRecorder mVideoTimeRecorder;

    private final Queue<String> mVideoTimes = new LinkedList<>();

    private final AtomicBoolean recording = new AtomicBoolean(false);

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);

    private final Semaphore mSemaphore = new Semaphore(1);

    private boolean checkRecording() {
        return recording.get();
    }

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

                try {
                    mSemaphore.acquire();
                    mVideoTimes.add(String.format("%d,%d,%d\n",
                            SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis(), mBufferInfo.presentationTimeUs));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    mSemaphore.release();
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
//            mMuxer.setOrientationHint(90);
            mMuxer.start();
            mMuxerStarted = true;
        }
    };

    private HandlerThread mCallbackThread;

    private Handler mCallbackHandler;

    public VideoEncoderCore2Fixed(int width, int height, int bitRate, String streamDir, String metaDir)
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

        mVideoTimeRecorder = new VideoTimeRecorder(null, metaDir);
        recording.set(true);
        mExecutorService.execute(mVideoTimeRecorder);
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
            mCallbackThread = null;
            mCallbackHandler = null;
        }
        recording.set(false);
    }

    private class VideoTimeRecorder implements Runnable {

        private String mRecordFile;

        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        public VideoTimeRecorder(@Nullable String recordFile, @Nullable String recordDir) {
            mRecordFile = recordFile;
            mRecordDir = recordDir;
        }

        private void initWriter() {
            if (mRecordFile == null) {
                mBufferedWriter = FileUtils.initWriter(mRecordDir + File.separator + "Video",
                        "FrameTimestamp.csv");
            } else {
                mBufferedWriter = FileUtils.initWriter(mRecordFile);
            }
            try {
                mBufferedWriter.write("sysClockTime[nanos],sysTime[millis],frameTime[micros]\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            Log.d(TAG, "VideoTimeRecorder");
            initWriter();
            int writeCount = 0;
            while (VideoEncoderCore2Fixed.this.checkRecording() || mVideoTimes.size() > 0) {
                try {
                    mSemaphore.acquire();
                    if (mVideoTimes.size() > 0) {
                        try {
                            mBufferedWriter.write(mVideoTimes.poll());
                            writeCount += 1;
                            if (writeCount > 30 * 3) {
                                mBufferedWriter.flush();
                                writeCount = 0;
                                Log.d(TAG, "VideoTime Recorder Write");
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    mSemaphore.release();
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "VideoTime Recorder End");
        }
    }
}