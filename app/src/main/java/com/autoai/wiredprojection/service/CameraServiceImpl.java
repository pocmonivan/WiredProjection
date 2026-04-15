package com.autoai.wiredprojection.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import com.autoai.wiredprojection.ICameraService;
import com.autoai.wiredprojection.ICameraServiceCallback;
import com.autoai.wiredprojection.R;
import com.autoai.wiredprojection.activity.HdmiActivity;
import com.autoai.wiredprojection.bean.Resolution;
import com.autoai.wiredprojection.operator.AudioInManager;
import com.autoai.wiredprojection.operator.AudioInManagerListener;
import com.autoai.wiredprojection.operator.CameraStateMachine;
import com.autoai.wiredprojection.util.Constants;
import com.autoai.wiredprojection.util.LogUtil;
import com.autoai.wiredprojection.view.AutoToast;
import com.autoai.wiredprojection.view.ConfirmationDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CameraServiceImpl extends ICameraService.Stub implements CameraStateMachine.StateMachineCallback {
    private static final String TAG = "CameraServiceImpl";

    private static final String ACTION_QB_OFF = "autochips.intent.action.QB_POWEROFF";
    private static final String ACTION_QB_ON = "autochips.intent.action.QB_POWERON";

    private static final int SHOW_TOAST = 1;
    private static final int SHOW_HDMI_DIALOG = 2;
    private static final int HIDE_HDMI_DIALOG = 3;

    private Map<String, CameraStateMachine> mCameraStateMachineMap;
    private final AudioInManager mAudioInManager;
    private PowerManager.WakeLock mWakeLock;
    private final Context mContext;
    private boolean mHdmiPlugged;

    private ConfirmationDialog mHdmiDialog;

    private String mHdmiCameraId = Constants.CAMERA_ID_HDMI;
    private boolean mHasEnableHdmiAudio = false;

    private HdmiStatusObserver mHdmiStatusObserver;

    public CameraServiceImpl(Context context) {
        mContext = context;
        acquireWakeLock();
        mAudioInManager = new AudioInManager(context, new AudioInManagerListener() {
            @Override
            public void onStateChanged(boolean opened) {
                LogUtil.d(TAG, "AudioIn state changed, opened = " + opened);
            }

            @Override
            public void onFocusChanged(int focusChange) {
                LogUtil.d(TAG, "AudioIn focus changed = " + focusChange);
            }

            @Override
            public void onError(AudioInManager.ErrorCode code, String message) {
                LogUtil.w(TAG, "AudioIn error: " + code + ", " + message);
            }
        });
        mAudioInManager.initialize();
        mCameraStateMachineMap = new HashMap<>();
        initHdmiDialog();
        initCameraStateMachine();
        registerQBReceiver();

        handleHdmiStatusEvent();
        mHdmiStatusObserver = new HdmiStatusObserver();
        mHdmiStatusObserver.startPolling();
        releaseWakeLock();
    }

    /**
     * Acquire wake lock before service init.
     */
    private void acquireWakeLock() {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AtcCamera:CameraService");
            LogUtil.d(TAG, "mWakeLock:" + mWakeLock);
        }
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            LogUtil.i(TAG, "acquire wake lock");
            mWakeLock.acquire();
        }
    }

    /**
     * Release wake lock after service init.
     */
    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            LogUtil.i(TAG, "release wake lock");
            mWakeLock.release();
        }
    }

    private void initCameraStateMachine() {
        if (mHdmiCameraId != null) {
            getCameraStateMachine(mHdmiCameraId);
        }
    }

    private void handleHdmiPlugged() {
        LogUtil.d(TAG, "handleHdmiPlugged");
        if (!mHasEnableHdmiAudio) {
            enableHdmiAudio();
            mHasEnableHdmiAudio = true;
        }

        startHdmiProjection();
    }

    private void handleHdmiUnplugged() {
        LogUtil.d(TAG, "handleHdmiUnplugged");
        if (mHasEnableHdmiAudio) {
            disableHdmiAudio();
            mHasEnableHdmiAudio = false;
        }

        stopHdmiProjection();
    }

    private void startHdmiProjection() {
        LogUtil.d(TAG, "startHdmiProjection begin");

        if (mHdmiPlugged) {
            LogUtil.w(TAG, "start hdmi projection when has not stop it");
            return;
        }
        mHdmiPlugged = true;

        LogUtil.d(TAG, "Constants.sIsSvForeground=" + Constants.sIsSvForeground);
        if (!Constants.sIsSvForeground) {
            mHandler.sendEmptyMessage(SHOW_HDMI_DIALOG);
        }

        LogUtil.d(TAG, "startHdmiProjection end");
    }

    private void stopHdmiProjection() {
        LogUtil.d(TAG, "stopHdmiProjection begin");

        if (!mHdmiPlugged) {
            LogUtil.w(TAG, "didn't start hdmi projection, skip this signal");
            return;
        }
        mHdmiPlugged = false;

        LogUtil.d(TAG, "Constants.sIsSvForeground=" + Constants.sIsSvForeground);
        mHandler.sendEmptyMessage(HIDE_HDMI_DIALOG);

        if (Constants.sIsSvForeground) {
            mHandler.sendMessage(mHandler.obtainMessage(SHOW_TOAST, mContext.getString(R.string.device_lost_link)));
            notifyInfo(Constants.Event.HDMI_EXIT, -1, -1, mHdmiCameraId);
        }

        LogUtil.d(TAG, "stopHdmiProjection end");
    }

    private void registerQBReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_QB_OFF);
        intentFilter.addAction(ACTION_QB_ON);
        mContext.registerReceiver(mQBReceiver, intentFilter);
    }

    private void unregisterQBReceiver() {
        mContext.unregisterReceiver(mQBReceiver);
    }

    BroadcastReceiver mQBReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            LogUtil.i(TAG,"mQBReceiver - onReceive:" + action);
            if(ACTION_QB_ON.equals(action)) {
                notifyStateMachineQBon();
            } else if(ACTION_QB_OFF.equals(action)) {
                notifyStateMachineQBoff();
            }
        }
    };

    private void notifyStateMachineQBoff() {
        Set<Map.Entry<String, CameraStateMachine>> stateMachines = mCameraStateMachineMap.entrySet();
        for (Map.Entry<String, CameraStateMachine> entry : stateMachines) {
            entry.getValue().handleQBoff();
        }
    }

    private void notifyStateMachineQBon() {
        Set<Map.Entry<String, CameraStateMachine>> stateMachines = mCameraStateMachineMap.entrySet();
        for (Map.Entry<String, CameraStateMachine> entry : stateMachines) {
            entry.getValue().handleQBon();
        }
    }

    private void notifyInfo(int event, int arg1, int arg2, String cameraId) {
        final int cbCount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbCount; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onInfoNotify(event, arg1, arg2, cameraId);
            } catch (RemoteException e) {
                LogUtil.e(TAG, "notifyInfo failed: " + e);
            }
        }
        mCallbacks.finishBroadcast();
    }

    private CameraStateMachine getCameraStateMachine(String id) {
        synchronized (this) {
            CameraStateMachine csm = mCameraStateMachineMap.get(id);
            if (csm == null) {
                csm = new CameraStateMachine(mContext, id);
                mCameraStateMachineMap.put(id, csm);
                csm.registerCallback(this);
                csm.start();
            }
            return csm;
        }
    }

    @Override
    public void startPreview(String cameraId, Surface surface) {
        int callingPid = Binder.getCallingPid();
        int selfPid = android.os.Process.myPid();
        LogUtil.d(TAG, "startPreview, callingPid = " + callingPid + ", selfPid = " +selfPid);
        if (callingPid == selfPid) {
            startPreview(cameraId, surface, Constants.PreviewSurfaceType.LOCAL);
        } else {
            startPreview(cameraId, surface, Constants.PreviewSurfaceType.REMOTE);
        }
    }

    private void startPreview(String cameraId, Surface surface, int type) {
        LogUtil.d(TAG,"startPreview - cameraId:"+cameraId + ", type = " + type);
        getCameraStateMachine(cameraId).startPreview(surface, type);
    }

    @Override
    public void stopPreview(String cameraId, Surface surface) {
        int callingPid = Binder.getCallingPid();
        int selfPid = android.os.Process.myPid();
        LogUtil.d(TAG, "stopPreview, callingPid = " + callingPid + ", selfPid = " +selfPid);
        if (callingPid == selfPid) {
            stopPreview(cameraId, surface, Constants.PreviewSurfaceType.LOCAL);
        } else {
            stopPreview(cameraId, surface, Constants.PreviewSurfaceType.REMOTE);
        }
    }

    private void stopPreview(String cameraId, Surface surface, int type) {
        LogUtil.d(TAG,"stopPreview - cameraId:"+cameraId + ", type = " + type);
        getCameraStateMachine(cameraId).stopPreview(surface, type);
    }

    public boolean isPreviewing(String cameraId) {
        return getCameraStateMachine(cameraId).getPreviewState();
    }

    @Override
    public boolean isPreviewStarted(String cameraId) {
        return getCameraStateMachine(cameraId).isPreviewRequest();
    }

    @Override
    public void setResolution(String cameraId, int width, int height) {
        LogUtil.d(TAG,"setResolution - cameraId:"+cameraId+" width:"+width+" height:"+height);
        getCameraStateMachine(cameraId).setResolution(width, height);
    }

    @Override
    public List<Resolution> getResolutions(String cameraId) {
        LogUtil.d(TAG,"getResolution - cameraId:"+cameraId);
        List<Size> sizes = getCameraStateMachine(cameraId).getResolutions();
        ArrayList<Resolution> resolutions = new ArrayList<>(sizes.size());
        for (Size sz : sizes) {
            resolutions.add(new Resolution(sz.getWidth(), sz.getHeight()));
        }
        return resolutions;
    }

    @Override
    public Resolution getCurrentResolution(String cameraId) {
        LogUtil.d(TAG,"getCurrentResolution - cameraId:"+cameraId);
        Size size = getCameraStateMachine(cameraId).getResolution();
        return new Resolution(size.getWidth(), size.getHeight());
    }

    @Override
    public void setStoragePath(String path) {
        LogUtil.d(TAG,"setStoragePath ignored, storage feature removed: " + path);
    }

    @Override
    public int getCameraState(String cameraId) {
        return getCameraStateMachine(cameraId).getCameraState();
    }

    @Override
    public void openCamera(String cameraId, boolean manual) {
        getCameraStateMachine(cameraId).openCamera(manual);
    }

    @Override
    public void closeCamera(String cameraId, boolean manual) {
        getCameraStateMachine(cameraId).closeCamera(manual);
    }

    @Override
    public boolean isCameraClosedManual(String cameraId) {
        return getCameraStateMachine(cameraId).isCameraClosedManual();
    }

    @Override
    public void registerCameraServiceCallback(ICameraServiceCallback cb) {
        LogUtil.d(TAG,"registerCameraServiceCallback");
        if(cb != null) {
            mCallbacks.register(cb);
        }
    }

    @Override
    public void unregisterCameraServiceCallback(ICameraServiceCallback cb) {
        LogUtil.d(TAG,"unregisterCameraServiceCallback");
        if (cb != null) {
            mCallbacks.unregister(cb);
        }
    }

    @Override
    public void onOperationSuccess(String id, int operation) {
        updateStatus(id, operation);
    }

    private void updateStatus(String cameraId, int message) {
        if (mCallbacks != null) {
            mCallbacks.beginBroadcast();
            int N = mCallbacks.getRegisteredCallbackCount();
            for (int i = 0; i < N; i++) {
                ICameraServiceCallback callback = null;
                try {
                    callback = mCallbacks.getBroadcastItem(i);
                    if (callback == null) {
                        continue;
                    }
                    callback.onStatusChanged(cameraId, message);
                } catch (DeadObjectException e) {
                    if (callback != null)
                        mCallbacks.unregister(callback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            mCallbacks.finishBroadcast();
        } else {
            LogUtil.w(TAG,"updateStatus - Callbacks map no such camera id");
        }
    }

    @Override
    public void onOperationFail(String id, int operation, int error) {
        LogUtil.i(TAG, "onOperationFail -id:" + id + " operation:" + operation + " error:" + error);
        updateError(id, operation, error);
    }

    private void updateError(String cameraId, int message, int error) {
        if (mCallbacks != null) {
            mCallbacks.beginBroadcast();
            int N = mCallbacks.getRegisteredCallbackCount();
            for (int i = 0; i < N; i++) {
                ICameraServiceCallback callback = null;
                try {
                    callback = mCallbacks.getBroadcastItem(i);
                    if (callback == null) {
                        continue;
                    }
                    callback.onErrorOccurred(cameraId, message, error);
                } catch (DeadObjectException e) {
                    if (callback != null)
                        mCallbacks.unregister(callback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            mCallbacks.finishBroadcast();
        } else {
            LogUtil.w(TAG,"updateError - Callbacks map no such camera id");
        }
    }

    @Override
    public void onCameraStateChanged(String cameraId, int state) {
        LogUtil.d(TAG, "onCameraStateChanged - cameraId:" + cameraId
                + ",state = " + Constants.cameraStateToString(state));
        notifyCameraStateChanged(cameraId, state);
    }

    private void notifyCameraStateChanged(String cameraId, int state) {
        final int cbCount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbCount; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onCameraStateChanged(cameraId, state);
            } catch (RemoteException e) {
                LogUtil.e(TAG, "notifyCameraStateChanged failed: " + e);
            }
        }
        mCallbacks.finishBroadcast();
    }

    @Override
    public void onSignalStateChanged(String cameraId, int state) {
        LogUtil.d(TAG, "onSignalStateChanged - cameraId:" + cameraId
                + ",state = " + Constants.signalStateToString(state));
        notifySignalStateChanged(cameraId, state);
    }



    private void notifySignalStateChanged(String cameraId, int state) {
        final int cbCount = mCallbacks.beginBroadcast();
        for (int i = 0; i < cbCount; i++) {
            try {
                mCallbacks.getBroadcastItem(i).onSignalStateChanged(cameraId, state);
            } catch (RemoteException e) {
                LogUtil.e(TAG, "notifyCameraStateChanged failed: " + e);
            }
        }
        mCallbacks.finishBroadcast();
    }

    public void onDestroy() {
        LogUtil.d(TAG, "onDestroy");
        unregisterQBReceiver();
        mHdmiStatusObserver.stopPolling();
        mAudioInManager.release();
        mCameraStateMachineMap.clear();
    }

    private final RemoteCallbackList<ICameraServiceCallback> mCallbacks
            = new RemoteCallbackList<ICameraServiceCallback>() {
        @Override
        public void onCallbackDied(ICameraServiceCallback callback) {
            super.onCallbackDied(callback);
            LogUtil.w(TAG, "client die");
        }
    };

    public void enableHdmiAudio() {
        mAudioInManager.openAudioIn();
    }

    public void disableHdmiAudio() {
        mAudioInManager.closeAudioIn();
    }

    private void initHdmiDialog() {
        mHdmiDialog = new ConfirmationDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.new_device_detected))
                .setConfirmListener(new ConfirmationDialog.ConfirmListener() {
                    @Override
                    public void onConfirm() {
                        HdmiActivity.launch(mContext);
                    }

                    @Override
                    public void onCancel() {

                    }
                })
                .build();
    }

    /**
     * 检测到新设备接入提示框
     */
    private void showHdmiDialog() {
        if (mHdmiDialog != null && !mHdmiDialog.isShowing()) {
            mHdmiDialog.show();
        }
    }

    private void hideHdmiDialog() {
        if (mHdmiDialog != null && mHdmiDialog.isShowing()) {
            mHdmiDialog.dismiss();
        }
    }

    /**
     * 处理hdmi节点变化
     */
    private synchronized void handleHdmiStatusEvent() {
        boolean hdmiStatus = Constants.isHdmiPlugged();
        LogUtil.d(TAG, "handleHdmiStatusEvent, hdmiStatus=" + hdmiStatus);
        if(hdmiStatus) {
            handleHdmiPlugged();
        } else {
            handleHdmiUnplugged();
        }
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_TOAST:
                    String message = (String) msg.obj;
                    AutoToast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                    break;
                case SHOW_HDMI_DIALOG:
                    showHdmiDialog();
                    break;
                case HIDE_HDMI_DIALOG:
                    hideHdmiDialog();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };


    /**
     * 监听HDMI节点变化
     */
    private class HdmiStatusObserver {

        private boolean lastValue; // 上一次的节点值
        private HandlerThread handlerThread; // 用于轮询的 HandlerThread
        private Handler handler; // 用于处理轮询任务的 Handler
        private static final long POLLING_INTERVAL_MS = 100; // 轮询间隔

        public HdmiStatusObserver() {
            lastValue = Constants.isHdmiPlugged();
        }

        // 读取节点值的函数
        private boolean readNodeValue() {
            return Constants.isHdmiPlugged();
        }

        // 轮询任务
        private final Runnable pollingTask = new Runnable() {
            @Override
            public void run() {
                boolean currentValue = readNodeValue();
                if (currentValue != lastValue) {
                    LogUtil.d(TAG, "------hdmi status changed: " + currentValue);
                    if(currentValue) {
                        handleHdmiPlugged();
                    } else {
                        handleHdmiUnplugged();
                    }
                    lastValue = currentValue; // 更新上一次的值
                }

                // 再次调度任务
                if (handler != null) {
                    handler.postDelayed(this, POLLING_INTERVAL_MS);
                }
            }
        };

        // 启动轮询
        public void startPolling() {
            if (handlerThread == null) {
                handlerThread = new HandlerThread("NodePollerThread");
                handlerThread.start(); // 启动 HandlerThread
                handler = new Handler(handlerThread.getLooper());
                handler.post(pollingTask); // 开始轮询任务
            }
        }

        // 停止轮询
        public void stopPolling() {
            if (handlerThread != null) {
                handlerThread.quitSafely(); // 停止 HandlerThread
                handlerThread = null;
                handler = null;
            }
        }
    }

}
