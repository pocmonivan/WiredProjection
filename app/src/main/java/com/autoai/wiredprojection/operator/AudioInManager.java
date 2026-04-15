package com.autoai.wiredprojection.operator;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Reusable audio-input manager with focus + render pipeline.
 */
public final class AudioInManager {
    private static final String TAG = "[APP_AudioIn]_AudioInManager";

    public enum ErrorCode {
        FOCUS_REQUEST_FAILED,
        FOCUS_STILL_MISSING,
        RENDER_INIT_FAILED
    }

    private final AudioManager mAudioManager;
    private final Handler mHandler;
    private final AudioInManagerConfig mConfig;
    private final AudioInManagerListener mListener;

    private boolean mDesiredOpenStatus = false;
    private boolean mCurrentOpenStatus = false;
    private boolean mIsAudioFocusHeld = false;
    private int mLastAudioFocus = AudioManager.AUDIOFOCUS_NONE;
    private boolean mStoppedByTransicientLoss = false;
    private int mCurrentPort;

    private boolean mIsRendering = false;
    private Thread mReadThread = null;
    private Thread mWriteThread = null;
    private AudioRecord mAudioRecord = null;
    private AudioTrack mAudioTrack = null;

    private final int mRecordBufferSize;
    private final Object mRenderLock = new Object();
    private final Object mRenderingLock = new Object();
    private final Object mIoLock = new Object();

    public AudioInManager(Context context) {
        this(context, AudioInManagerConfig.defaultConfig(), null);
    }

    public AudioInManager(Context context, AudioInManagerListener listener) {
        this(context, AudioInManagerConfig.defaultConfig(), listener);
    }

    public AudioInManager(Context context, AudioInManagerConfig config,
            AudioInManagerListener listener) {
        Context appContext = context.getApplicationContext();
        mAudioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        mConfig = config == null ? AudioInManagerConfig.defaultConfig() : config;
        mListener = listener;
        mCurrentPort = mConfig.defaultPort;
        mRecordBufferSize = AudioRecord.getMinBufferSize(
                mConfig.sampleRate, mConfig.channelConfig, mConfig.audioFormat)
                * mConfig.recordBufferMultiplier;
    }

    public void initialize() {
        createRenderThread();
    }

    public void release() {
        abandonAudioFocus();
        exitRenderThread();
    }

    public int getCurrentPort() {
        return mCurrentPort;
    }

    public boolean isRendering() {
        return mIsRendering;
    }

    public boolean currenAudioInState() {
        return mCurrentOpenStatus;
    }

    public boolean isAudioFocusHeld() {
        return mIsAudioFocusHeld;
    }

    public boolean getDesiredOpenStatus() {
        return mDesiredOpenStatus;
    }

    public void setDesiredOpenStatus(boolean desiredOpenStatus) {
        mDesiredOpenStatus = desiredOpenStatus;
    }

    public boolean openAudioIn(int port) {
        Log.d(TAG, "openAudioIn");
        if (requestAudioFocus()) {
            Log.d(TAG, "audiofocus request successfully!");
            mDesiredOpenStatus = true;
            switchAudioIn(port);
            notifyState();
            return true;
        }
        Log.e(TAG, "audiofocus request failed!");
        mCurrentOpenStatus = false;
        mDesiredOpenStatus = false;
        notifyError(ErrorCode.FOCUS_REQUEST_FAILED, "audio focus request failed");
        notifyState();
        return false;
    }

    public boolean openAudioIn() {
        return openAudioIn(mCurrentPort);
    }

    public void closeAudioIn() {
        Log.d(TAG, "closeAudioIn");
        mDesiredOpenStatus = false;
        stopAudioInSession();
    }

    public void resumeAudioInIfNotRendering() {
        if (!isRendering()) {
            resumeAudioIn(getCurrentPort());
        }
    }

    public void resumeAudioIn(int port) {
        Log.d(TAG, "resumeAudioIn");
        if (mDesiredOpenStatus) {
            if (requestAudioFocus()) {
                Log.d(TAG, "resumeAudioIn:request audiofocus successfully!");
                mCurrentOpenStatus = true;
                switchAudioIn(port);
            } else {
                mHandler.postDelayed(mHasAudioFocus, 1500);
            }
        }
        notifyState();
    }

    public void resumeAudioIn() {
        resumeAudioIn(mCurrentPort);
    }

    private void stopAudioInSession() {
        Log.i(TAG, "stopAudioInSession, mCurrentPort:" + mCurrentPort);
        stopRendering();
        mCurrentOpenStatus = false;
        notifyState();
    }

    private boolean requestAudioFocus() {
        Log.d(TAG, "requestAudioFocus");
        int audioFocus = mAudioManager.requestAudioFocus(
                mAudioFocusChangeListener,
                mConfig.focusStreamType,
                mConfig.focusGainType);
        mIsAudioFocusHeld = (AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioFocus);
        if (mIsAudioFocusHeld) {
            mCurrentOpenStatus = true;
            mLastAudioFocus = AudioManager.AUDIOFOCUS_GAIN;
        }
        return mIsAudioFocusHeld;
    }

    private void abandonAudioFocus() {
        Log.i(TAG, "abandonAudioFocus");
        mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
        mIsAudioFocusHeld = false;
    }

    private void switchAudioIn(int port) {
        Log.i(TAG, "switchAudioIn, port:" + port);
        if (port != mCurrentPort) {
            closeCurrentPort();
        }
        mCurrentPort = port;
        startRender();
    }

    private void stopRendering() {
        closeCurrentPort();
    }

    private void closeCurrentPort() {
        Log.i(TAG, "closeCurrentPort, mCurrentPort:" + mCurrentPort);
        stopRender();
    }

    private void stopRender() {
        Log.d(TAG, "stopRender");
        synchronized (mRenderingLock) {
            boolean localRender = isRenderingInternal();
            mIsRendering = false;
            if (localRender) {
                try {
                    mRenderingLock.wait(500);
                } catch (InterruptedException e) {
                    Log.w(TAG, "stopRender, thread interrupted");
                }
            }
        }
    }

    private boolean isRenderingInternal() {
        return mIsRendering;
    }

    private final Runnable mHasAudioFocus = new Runnable() {
        @Override
        public void run() {
            if (mLastAudioFocus != AudioManager.AUDIOFOCUS_GAIN) {
                mCurrentOpenStatus = false;
                notifyError(ErrorCode.FOCUS_STILL_MISSING, "audio focus still missing");
                notifyState();
            }
        }
    };

    private final AudioManager.OnAudioFocusChangeListener mAudioFocusChangeListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (mListener != null) {
                        mListener.onFocusChanged(focusChange);
                    }
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            mLastAudioFocus = AudioManager.AUDIOFOCUS_LOSS;
                            if (mCurrentOpenStatus) {
                                mCurrentOpenStatus = false;
                                mStoppedByTransicientLoss = false;
                                stopAudioInSession();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            mLastAudioFocus = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                            if (mCurrentOpenStatus) {
                                mStoppedByTransicientLoss = true;
                                mCurrentOpenStatus = false;
                                stopAudioInSession();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            if (mStoppedByTransicientLoss) {
                                mStoppedByTransicientLoss = false;
                                mLastAudioFocus = AudioManager.AUDIOFOCUS_GAIN;
                                resumeAudioInIfNotRendering();
                            }
                            break;
                        default:
                            break;
                    }
                }
            };

    private synchronized void startRender() {
        releaseAudioRecord();
        releaseAudioTrack();
        if (initAudioRecordSink()) {
            mIsRendering = true;
            synchronized (mRenderLock) {
                mRenderLock.notify();
            }
        } else {
            notifyError(ErrorCode.RENDER_INIT_FAILED, "init audio record/track failed");
        }
    }

    private synchronized boolean initAudioRecordSink() {
        mAudioRecord = new AudioRecord(
                mConfig.audioSourceType,
                mConfig.sampleRate,
                mConfig.channelConfig,
                mConfig.audioFormat,
                mRecordBufferSize);
        if (mAudioRecord == null) {
            return false;
        }

        mAudioTrack = new AudioTrack(
                mConfig.outputStreamType,
                mConfig.sampleRate,
                mConfig.channelConfig,
                mConfig.audioFormat,
                mRecordBufferSize,
                AudioTrack.MODE_STREAM);
        if (mAudioTrack == null) {
            mAudioRecord = null;
            return false;
        }
        return true;
    }

    private void releaseAudioRecord() {
        synchronized (mIoLock) {
            if (mAudioRecord == null) {
                return;
            }
            try {
                if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mAudioRecord.stop();
                }
                mAudioRecord.release();
                mAudioRecord = null;
            } catch (IllegalStateException e) {
                Log.e(TAG, "releaseAudioRecord, IllegalStateException");
            } catch (NullPointerException e) {
                Log.e(TAG, "releaseAudioRecord, NullPointerException");
            }
        }
    }

    private void releaseAudioTrack() {
        synchronized (mIoLock) {
            if (mAudioTrack == null) {
                return;
            }
            try {
                if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    mAudioTrack.flush();
                    mAudioTrack.stop();
                }
                mAudioTrack.release();
                mAudioTrack = null;
            } catch (IllegalStateException e) {
                Log.e(TAG, "releaseAudioTrack, IllegalStateException");
            } catch (NullPointerException e) {
                Log.e(TAG, "releaseAudioTrack, NullPointerException");
            }
        }
    }

    private synchronized void createRenderThread() {
        ReadRunnable readRunnable = new ReadRunnable();
        WriteRunnable writeRunnable = new WriteRunnable();
        mReadThread = new Thread(readRunnable);
        mWriteThread = new Thread(writeRunnable);
        try {
            writeRunnable.getPipedOutputStream().connect(readRunnable.getPipedInputStream());
            mWriteThread.start();
            mReadThread.start();
        } catch (IOException e) {
            Log.e(TAG, "PipedOutputStream connect error");
        }
    }

    private synchronized void exitRenderThread() {
        stopRender();
        if (mReadThread != null) {
            mReadThread.interrupt();
            mReadThread = null;
        }
        if (mWriteThread != null) {
            mWriteThread.interrupt();
            mWriteThread = null;
        }
    }

    private void notifyState() {
        if (mListener != null) {
            mListener.onStateChanged(mCurrentOpenStatus);
        }
    }

    private void notifyError(ErrorCode code, String message) {
        if (mListener != null) {
            mListener.onError(code, message);
        }
    }

    private class ReadRunnable implements Runnable {
        private final PipedInputStream mReadPis;

        ReadRunnable() {
            mReadPis = new PipedInputStream();
        }

        PipedInputStream getPipedInputStream() {
            return mReadPis;
        }

        @Override
        public void run() {
            int len;
            byte[] buffer = new byte[mConfig.pipeBufferSize];
            try {
                while ((len = mReadPis.read(buffer)) != -1) {
                    if (isRenderingInternal() && mAudioTrack != null) {
                        mAudioTrack.write(buffer, 0, len);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "PipedInputStream read error:" + e);
            } catch (IllegalStateException e) {
                Log.e(TAG, "AudioTrack write error:" + e);
            } finally {
                try {
                    mReadPis.close();
                } catch (IOException e) {
                    Log.e(TAG, "close read pipe error:" + e);
                }
            }
        }
    }

    private class WriteRunnable implements Runnable {
        private final PipedOutputStream mWritePos;
        private int mCurrentFrame = 0;

        WriteRunnable() {
            mWritePos = new PipedOutputStream();
        }

        PipedOutputStream getPipedOutputStream() {
            return mWritePos;
        }

        private boolean isAudioFrameNeedIgnore() {
            return mCurrentFrame < mConfig.ignoreFramesCount;
        }

        @Override
        public void run() {
            try {
                byte[] readBuf = new byte[mConfig.pipeBufferSize];
                byte[] writeBuf = new byte[mConfig.pipeBufferSize];
                boolean lastRendering = false;
                while (!Thread.interrupted()) {
                    if (lastRendering != isRenderingInternal()) {
                        lastRendering = isRenderingInternal();
                    }
                    if (isRenderingInternal()) {
                        if (mAudioRecord != null
                                && mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED
                                && mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED) {
                            mAudioRecord.startRecording();
                        }
                        if (mAudioTrack != null
                                && mAudioTrack.getState() == AudioTrack.STATE_INITIALIZED
                                && mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_STOPPED) {
                            mAudioTrack.play();
                        }
                        int size = 0;
                        if (mAudioRecord != null) {
                            size = mAudioRecord.read(readBuf, 0, mConfig.pipeBufferSize);
                        }
                        if (isAudioFrameNeedIgnore()) {
                            mCurrentFrame += 1;
                            continue;
                        }
                        if (size <= 0) {
                            continue;
                        }
                        System.arraycopy(readBuf, 0, writeBuf, 0, size);
                        if (isRenderingInternal()) {
                            try {
                                mWritePos.write(writeBuf, 0, size);
                            } catch (IOException e) {
                                Log.e(TAG, "PipedOutputStream write error:" + e);
                            }
                        }
                    } else {
                        mCurrentFrame = 0;
                        try {
                            try {
                                mWritePos.flush();
                            } catch (IOException e) {
                                Log.e(TAG, "flush pipe error:" + e);
                            }
                            releaseAudioTrack();
                            releaseAudioRecord();
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "render thread state error");
                        } finally {
                            synchronized (mRenderLock) {
                                mRenderLock.wait();
                            }
                            synchronized (mRenderingLock) {
                                mRenderingLock.notify();
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "render thread interrupted");
            } finally {
                releaseAudioRecord();
                releaseAudioTrack();
                try {
                    mWritePos.close();
                } catch (IOException e) {
                    Log.e(TAG, "close write pipe error:" + e);
                }
            }
        }
    }
}
