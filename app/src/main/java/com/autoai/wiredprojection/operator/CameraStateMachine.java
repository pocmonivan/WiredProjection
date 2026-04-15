package com.autoai.wiredprojection.operator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Size;
import android.view.Surface;

import com.autoai.wiredprojection.common.State;
import com.autoai.wiredprojection.common.StateMachine;
import com.autoai.wiredprojection.util.Constants;
import com.autoai.wiredprojection.util.Constants.*;
import com.autoai.wiredprojection.util.LogUtil;

import java.util.List;

public class CameraStateMachine extends StateMachine {
    private String TAG = "CameraStateMachine";

    public static final int MSG_OPEN_CAMERA = 1;
    public static final int MSG_CLOSE_CAMERA = -1;
    public static final int MSG_START_PREVIEW = 2;
    public static final int MSG_STOP_PREVIEW = -2;
    public static final int MSG_CAMERA_OPENED = 3;
    public static final int MSG_CAMERA_CLOSED = 4;
    public static final int MSG_SET_RESOLUTION = 5;
    public static final int MSG_PREVIEWING = 6;
    public static final int MSG_PREVIEW_STOPPED = 7;
    public static final int MSG_QB_ON = 8;
    public static final int MSG_QB_OFF = -8;
    public static final int MSG_RESTORE_CAMERA = 9;

    private State mClosedState;
    private State mOpeningState;
    private State mOpenedState;
    private State mClosingState;
    private State mPreviewingState;
    private State mQBOffState;

    private StateMachineCallback mCallback;
    private final CameraOperation mActiveCamera;
    private final String mCameraId;
    private PowerManager.WakeLock mWakeLock;
    private Context mContext;

    public CameraStateMachine(Context context, String cameraId) {
        super("CameraStateMachine");
        mContext = context;
        mCameraId = cameraId;
        TAG = TAG + mCameraId;
        mActiveCamera = new CameraOperation(context, cameraId, getHandler().getLooper());
        mActiveCamera.registerCameraOperationCallback(mOperationCallback);
        initStates();
    }

    private void initStates() {
        mClosedState = new ClosedState();
        mOpeningState = new OpeningState();
        mOpenedState = new OpenedState();
        mClosingState = new ClosingState();
        mQBOffState = new QBOffState();
        mPreviewingState = new PreviewingState();

        addState(mClosedState);
        addState(mOpeningState);
        addState(mOpenedState);
        addState(mClosingState);
        addState(mQBOffState);
        addState(mPreviewingState, mOpenedState);

        setInitialState(mClosedState);
    }

    private void handleSetParamMessage(Message msg) {
        switch (msg.what) {
            case MSG_SET_RESOLUTION:
                mActiveCamera.setResolution(msg.arg1, msg.arg2);
                break;
        }
    }

    private class ClosedState extends State {
        @Override
        public void enter() {
            LogUtil.d(TAG, "enter ClosedState" + mCameraId);
        }

        @Override
        public boolean processMessage(Message msg) {
            LogUtil.d(TAG, getClass().getSimpleName() + mCameraId + " processMessage " + msgToString(msg.what));

            if (hasTheOppositeMessage(msg.what)
                    || hasTheSameMessage(msg.what)) {
                return HANDLED;
            }
            switch (msg.what) {
                case MSG_START_PREVIEW:
                    if (!hasMessages(msg.what)) {
                        transitionTo(mOpeningState);
                        mActiveCamera.open();
                        deferMessage(msg);
                    }
                    break;
                case MSG_OPEN_CAMERA:
                    mActiveCamera.open();
                    transitionTo(mOpeningState);
                    break;
                case MSG_STOP_PREVIEW:
                    break;
                case MSG_SET_RESOLUTION:
                    handleSetParamMessage(msg);
                    break;
                case MSG_QB_OFF:
                    transitionTo(mQBOffState);
                    ((QBOffState)mQBOffState).mIsCameraClosed = true;
                    break;
                case MSG_RESTORE_CAMERA:
                    if (mActiveCamera.isNeedRestoreCamera()) {
                        mActiveCamera.open();
                        transitionTo(mOpeningState);
                        deferMessage(msg);
                    }
                    break;
                default:
                    LogUtil.d(TAG, "illegal message " + msgToString(msg.what) + " appear in " + getClass().getSimpleName());
                    break;
            }
            return HANDLED;
        }
    }

    private class OpeningState extends State {
        @Override
        public void enter() {
            LogUtil.d(TAG, "enter OpeningState" + mCameraId);
        }

        @Override
        public boolean processMessage(Message msg) {
            LogUtil.d(TAG, getClass().getSimpleName() + mCameraId + " processMessage " + msgToString(msg.what));

            if (hasTheOppositeMessage(msg.what)
                    || hasTheSameMessage(msg.what)) {
                return HANDLED;
            }
            switch (msg.what) {
                case MSG_START_PREVIEW:
                case MSG_STOP_PREVIEW:
                case MSG_CLOSE_CAMERA:
                    deferMessage(msg);
                    break;
                case MSG_CAMERA_OPENED:
                    transitionTo(mOpenedState);
                    break;
                case MSG_CAMERA_CLOSED:
                    removeTheSameMessages(MSG_START_PREVIEW);
                    removeTheSameMessages(MSG_STOP_PREVIEW);
                    removeTheSameMessages(MSG_RESTORE_CAMERA);
                    transitionTo(mClosedState);
                    break;
                case MSG_QB_OFF:
                case MSG_RESTORE_CAMERA:
                    deferMessage(msg);
                    break;
                case MSG_SET_RESOLUTION:
                    handleSetParamMessage(msg);
                    break;
                default:
                    LogUtil.d(TAG, "illegal message " + msgToString(msg.what) + " appear in " + getClass().getSimpleName());
                    break;
            }
            return HANDLED;
        }
    }

    private class OpenedState extends State {
        @Override
        public void enter() {
            LogUtil.d(TAG, "enter OpenedState" + mCameraId);
        }

        @Override
        public boolean processMessage(Message msg) {
            LogUtil.d(TAG, getClass().getSimpleName() + mCameraId + " processMessage " + msgToString(msg.what));

            if (hasTheOppositeMessage(msg.what)
                    || hasTheSameMessage(msg.what)) {
                return HANDLED;
            }
            switch (msg.what) {
                case MSG_CAMERA_CLOSED:
                    LogUtil.w(TAG, "camera closed when opened!");
                    handleCameraException();
                    break;
                case MSG_START_PREVIEW:
                    mActiveCamera.startPreview(msg.arg1 == 1);
                    break;
                case MSG_STOP_PREVIEW:
                    mActiveCamera.stopPreview(msg.arg1 == 1);
                    break;
                case MSG_PREVIEWING:
                    transitionTo(mPreviewingState);
                    break;
                case MSG_QB_OFF:
                    if (mActiveCamera.isNeedRestoreCamera()) {
                        sendMessage(MSG_RESTORE_CAMERA);
                    }
                    mActiveCamera.close();
                    transitionTo(mQBOffState);
                    break;
                case MSG_SET_RESOLUTION:
                    handleSetParamMessage(msg);
                    break;
                case MSG_RESTORE_CAMERA:
                    mayRestoreCamera();
                    break;
                case MSG_PREVIEW_STOPPED:
                    boolean previewRequest = mActiveCamera.isPreviewRequest();
                    LogUtil.d(TAG, "previewRequest = " + previewRequest);
                    if (!previewRequest) {
                        mActiveCamera.close();
                        transitionTo(mClosingState);
                    }
                    break;
                case MSG_CLOSE_CAMERA:
                    mActiveCamera.close(false);
                    transitionTo(mClosingState);
                    break;
                default:
                    LogUtil.d(TAG, "illegal message " + msgToString(msg.what) + " appear in " + getClass().getSimpleName());
                    break;
            }
            return HANDLED;
        }
    }

    private class ClosingState extends State {
        @Override
        public void enter() {
            LogUtil.d(TAG, "enter ClosingState" + mCameraId);
        }

        @Override
        public boolean processMessage(Message msg) {
            LogUtil.d(TAG, getClass().getSimpleName() + mCameraId + " processMessage " + msgToString(msg.what));
            if (hasTheOppositeMessage(msg.what)
                    || hasTheSameMessage(msg.what)) {
                return HANDLED;
            }
            switch (msg.what) {
                case MSG_CAMERA_CLOSED:
                    transitionTo(mClosedState);
                    break;
                case MSG_START_PREVIEW:
                case MSG_CLOSE_CAMERA:
                    deferMessage(msg);
                    break;
                case MSG_QB_OFF:
                    transitionTo(mQBOffState);
                    break;
                case MSG_SET_RESOLUTION:
                    handleSetParamMessage(msg);
                    break;
                default:
                    LogUtil.d(TAG, "illegal message " + msgToString(msg.what) + " appear in " + getClass().getSimpleName());
                    break;
            }
            return HANDLED;
        }
    }

    private class PreviewingState extends State {
        @Override
        public void enter() {
            LogUtil.d(TAG, "enter PreviewingState" + mCameraId);
        }

        @Override
        public boolean processMessage(Message msg) {
            LogUtil.d(TAG, getClass().getSimpleName() + mCameraId + " processMessage " + msgToString(msg.what));
            if ((hasTheOppositeMessage(msg.what)
                    || hasTheSameMessage(msg.what))) {
                return HANDLED;
            }
            switch (msg.what) {
                case MSG_CAMERA_CLOSED:
                    LogUtil.d(TAG, "camera closed when Previewing");
                    handleCameraException();
                    break;
                case MSG_START_PREVIEW:
                    mActiveCamera.startPreview(msg.arg1 == 1);
                    break;
                case MSG_STOP_PREVIEW:
                    mActiveCamera.stopPreview(msg.arg1 == 1);
                    break;
                case MSG_PREVIEW_STOPPED:
                    mActiveCamera.close();
                    transitionTo(mClosingState);
                    break;
                case MSG_QB_OFF:
                    sendMessage(MSG_RESTORE_CAMERA);
                    mActiveCamera.close();
                    transitionTo(mQBOffState);
                    break;
                case MSG_SET_RESOLUTION:
                    handleSetParamMessage(msg);
                    break;
                case MSG_RESTORE_CAMERA:
                    mayRestoreCamera();
                    break;
                case MSG_CLOSE_CAMERA:
                    mActiveCamera.close(false);
                    transitionTo(mClosingState);
                    break;
                default:
                    LogUtil.d(TAG, "illegal message " + msgToString(msg.what) + " appear in " + getClass().getSimpleName());
                    break;

            }
            return HANDLED;
        }
    }

    private class QBOffState extends State {
        boolean mIsCameraClosed;
        boolean mIsQbOn;
        @Override
        public void enter() {
            LogUtil.d(TAG, "enter QBOffState" + mCameraId);
            if (mIsCameraClosed) {
                releaseWakeLock();
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            LogUtil.d(TAG, getClass().getSimpleName() + mCameraId + " processMessage " + msgToString(msg.what));
            switch (msg.what) {
                case MSG_CAMERA_CLOSED:
                    releaseWakeLock();
                    if (mIsQbOn) {
                        transitionTo(mClosedState);
                        mIsCameraClosed = false;
                         mIsQbOn = false;
                    } else {
                        mIsCameraClosed = true;
                    }
                    break;
                case MSG_PREVIEW_STOPPED:
                    break;
                case MSG_STOP_PREVIEW:
                    deferMessage(msg);
                    break;
                case MSG_QB_ON:
                    if (mIsCameraClosed) {
                        transitionTo(mClosedState);
                        mIsQbOn = false;
                        mIsCameraClosed = false;
                    } else {
                        mIsQbOn = true;
                    }
                    break;
                case MSG_QB_OFF:
                    mIsQbOn = false;
                    break;
                case MSG_PREVIEWING:
                    sendMessage(MSG_START_PREVIEW);
                    break;
                default:
                    deferMessage(msg);
                    break;
            }
            return HANDLED;
        }
    }

    private void removeTheSameMessages(int what) {
        if (hasMessages(what)) {
            removeMessages(what);
        }
        if (hasDeferredMessages(what)) {
            removeDeferredMessages(what);
        }
    }

    private void removeTheOppositeMessages(int what) {
        if (hasMessages(-what)) {
            removeMessages(-what);
        }
        if (hasDeferredMessages(-what)) {
            removeDeferredMessages(-what);
        }
    }

    private boolean hasTheOppositeMessage(int what) {
        return hasTheSameMessage(-what);
    }

    private boolean hasTheSameMessage(int what) {
        if (hasMessages(what)
                || hasDeferredMessages(what)) {
            return true;
        } else {
            return false;
        }
    }

    public void openCamera(boolean manual) {
        LogUtil.d(TAG, "openCamera id: " + mCameraId + ", manual = " + manual);
        if (manual) {
            mActiveCamera.setCameraCloseManual(false);
        }
        sendMessage(MSG_OPEN_CAMERA);
    }

    public void closeCamera(boolean manual) {
        LogUtil.d(TAG, "closeCamera id: " + mCameraId + ", manual = " + manual);
        if (manual) {
            mActiveCamera.setCameraCloseManual(true);
        }
        sendMessage(MSG_CLOSE_CAMERA);
    }

    public void startPreview(Surface surface, int type) {
        LogUtil.d(TAG, "startPreview id:" + mCameraId);
        mActiveCamera.addPreviewSurface(surface, type);
        sendMessage(MSG_START_PREVIEW, 0);
    }

    public void stopPreview(Surface surface, int type) {
        LogUtil.d(TAG, "stopPreview id: " + mCameraId);
        mActiveCamera.removePreviewSurface(surface, type);
        sendMessage(MSG_STOP_PREVIEW, 0);
    }

    public void setResolution(int width, int height) {
        LogUtil.d(TAG, "setResolution ");
        sendMessage(MSG_SET_RESOLUTION, width, height);
    }

    public void handleQBoff() {
        LogUtil.w(TAG, "handleQBoff, mCameraId = " + mCameraId);
        acquireWakeLock();
        sendMessage(MSG_QB_OFF);
    }

    public void handleQBon() {
        LogUtil.w(TAG, "handleQBon, mCameraId = " + mCameraId);
        sendMessage(MSG_QB_ON);
    }

    /**
     * Acqurie wake lock before close camera,so CPU won't sleep before camera is closed.
     */
    private void acquireWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + mCameraId);
            LogUtil.d(TAG, "mWakeLock:" + mWakeLock);
        }
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            LogUtil.i(TAG, "acquire wake lock");
            mWakeLock.acquire();
        }
    }

    /**
     * Release wake lock after camera is closed,so CPU can go to sleep.
     */
    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            LogUtil.i(TAG, "release wake lock");
            mWakeLock.release();
        }
    }

    public boolean getPreviewState() {
        return mActiveCamera.isPreviewing();
    }

    public boolean isPreviewRequest() {
        return mActiveCamera.isPreviewRequest();
    }

    public Size getResolution() {
        return mActiveCamera.getVideoSize();
    }

    public List<Size> getResolutions() {
        return mActiveCamera.getResolutions();
    }

    public int getCameraState() {
        return mActiveCamera.getCameraState();
    }

    public boolean isCameraClosedManual() {
        return mActiveCamera.isCameraClosedManual();
    }

    public interface StateMachineCallback {
        void onOperationSuccess(String id, int operation);
        void onOperationFail(String id, int operation, int error);
        void onCameraStateChanged(String cameraId, int state);
        void onSignalStateChanged(String cameraId, int state);
    }

    public void registerCallback(StateMachineCallback callback) {
        mCallback = callback;
    }

    public void unregisterCallback(StateMachineCallback callback) {
        mCallback = null;
    }

    private CameraOperation.CameraOperationCallback mOperationCallback = new CameraOperation.CameraOperationCallback() {
        @Override
        public void onOperationSuccess(int op, String id) {
            LogUtil.d(TAG, "onOperationSuccess: " + Constants.operaToString(op) + ", id = " + id);
            switch (op) {
                case Operation.OPEN_CAMERA:
                    sendMessageAtFrontOfQueue(MSG_CAMERA_OPENED);
                    break;
                case Operation.CLOSE_CAMERA:
                    sendMessageAtFrontOfQueue(MSG_CAMERA_CLOSED);
                    break;
                case Operation.START_PREVIEW:
                    sendMessageAtFrontOfQueue(MSG_PREVIEWING);
                    break;
                case Operation.STOP_PREVIEW:
                    sendMessageAtFrontOfQueue(MSG_PREVIEW_STOPPED);
                    break;
                case Operation.SET_RESOLUTION:
                    //todo
                    break;
                default:
                    break;
            }
            mServiceThread.sendMessage(Message.obtain(mServiceThread, op, 0, 0, id));
        }

        @Override
        public void onOperationFailed(int op, String id, int extra) {
            LogUtil.d(TAG, "onOperationFailed: " + Constants.operaToString(op) + ", id = " + id);
            switch (op) {
                case Operation.OPEN_CAMERA:
                    sendMessageAtFrontOfQueue(MSG_CAMERA_CLOSED);
                    break;
                case Operation.CLOSE_CAMERA:
                    //todo
                    break;
                case Operation.START_PREVIEW:
                    break;
                case Operation.STOP_PREVIEW:
                    //todo
                    break;
                case Operation.SET_RESOLUTION:
                    break;
                default:
                    break;
            }
            mServiceThread.sendMessage(Message.obtain(mServiceThread, op, extra, 0, id));
        }

        @Override
        public void onCameraStateChanged(String cameraId, int state) {
            if (mCallback != null) {
                mServiceThread.post(() -> {
                    mCallback.onCameraStateChanged(cameraId, state);
                });
            }
        }

        @Override
        public void onSignalStateChanged(String cameraId, int state) {
            if (mCallback != null) {
                mServiceThread.post(() -> {
                    mCallback.onSignalStateChanged(cameraId, state);
                });
            }
        }

    };

    private final Handler mServiceThread = new Handler(Looper.myLooper()){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String id = (String)msg.obj;
            if (mCallback != null) {
                if (msg.arg1 != 0) {
                    mCallback.onOperationFail(id, msg.what, msg.arg1);
                } else {
                    mCallback.onOperationSuccess(id, msg.what);
                }
            }
        }
    };

    private void handleCameraException() {
        sendMessage(MSG_RESTORE_CAMERA);
        transitionTo(mOpeningState);
        mActiveCamera.open();
    }

    private void mayRestoreCamera() {
        if (mActiveCamera.isNeedRestoreCamera()) {
            mActiveCamera.restoreCamera();
        }
    }

    private String msgToString(int msg) {
        if (msg == MSG_OPEN_CAMERA) return "MSG_OPEN_CAMERA";
        if (msg == MSG_CLOSE_CAMERA) return "MSG_CLOSE_CAMERA";
        if (msg == MSG_START_PREVIEW) return "MSG_START_PREVIEW";
        if (msg == MSG_STOP_PREVIEW) return "MSG_STOP_PREVIEW";
        if (msg == MSG_CAMERA_OPENED) return "MSG_CAMERA_OPENED";
        if (msg == MSG_CAMERA_CLOSED) return "MSG_CAMERA_CLOSED";
        if (msg == MSG_SET_RESOLUTION) return "MSG_SET_RESOLUTION";
        if (msg == MSG_PREVIEWING) return "MSG_PREVIEWING";
        if (msg == MSG_PREVIEW_STOPPED) return "MSG_PREVIEW_STOPPED";
        if (msg == MSG_QB_ON) return "MSG_QB_ON";
        if (msg == MSG_QB_OFF) return "MSG_QB_OFF";
        if (msg == MSG_RESTORE_CAMERA) return "MSG_RESTORE_CAMERA";
        return "unknown";
    }

}
