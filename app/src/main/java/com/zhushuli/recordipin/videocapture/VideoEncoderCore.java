package com.zhushuli.recordipin.videocapture;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class wraps up the core components used for surface-input video encoding.
 * <p>
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 * <p>
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
public class VideoEncoderCore {

    private static final String TAG = "VideoEncoderCore";

    private static final String MIME_TYPE = "video/avc";  // H.264 Advanced Video Coding

    public static final int FRAME_RATE = 30;  // 30fps

    private static final int IFRAME_INTERVAL = 1;  // seconds between I-frames

    private static final int TIMEOUT_US = 10000;

    private final Surface mInputSurface;

    private MediaMuxer mMuxer;

    private MediaCodec mEncoder;

    private boolean mEncoderInExecutingState;

    private final MediaCodec.BufferInfo mBufferInfo;

    private int mTrackIndex;

    private boolean mMuxerStarted;

    private BufferedWriter mTimeWriter;

    private int mTimeCount = 0;

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    public VideoEncoderCore(int width, int height, int bitRate,
                            String streamDir, String timeDir)
            throws IOException {
        Log.d(TAG, "VideoEncoderCore");
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        try {
            mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            mEncoderInExecutingState = true;
        } catch (IllegalStateException ise) {
            // This exception occurs with certain devices e.g., Nexus 9 API 22.
            Log.e(TAG, String.valueOf(ise));
            mEncoderInExecutingState = false;
        }

        // Create a MediaMuxer. We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
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

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Releases encoder resources.
     */
    public void release() {
        Log.d(TAG, "releasing encoder objects");
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
        if (mTimeWriter != null) {
            FileUtils.closeBufferedWriter(mTimeWriter);
            mTimeWriter = null;
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     * <p>
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    public void drainEncoder(boolean endOfStream) {
        Log.d("VideoEncoderCore", "drainEncoder");

        if (endOfStream) {
            Log.d(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        while (mEncoderInExecutingState) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break;      // out of while
                } else {
                    Log.d(TAG, "no output available, spinning to await EOS");
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);

                // now that we have the Magic Goodies, start the muxer
                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = mEncoder.getOutputBuffer(encoderStatus);
                // bufferFormat is identical to newFormat
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
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

                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        Log.d(TAG, "end of stream reached");
                    }
                    break;  // out of while
                }
            }
        }
    }
}