package com.autoai.wiredprojection.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.autoai.wiredprojection.ICameraService;
import com.autoai.wiredprojection.ICameraServiceCallback;
import com.autoai.wiredprojection.R;
import com.autoai.wiredprojection.bean.Resolution;
import com.autoai.wiredprojection.service.CameraService;
import com.autoai.wiredprojection.util.Constants;
import com.autoai.wiredprojection.util.LogUtil;
import com.autoai.wiredprojection.view.ProjectionItemView;

import java.util.ArrayList;
import java.util.List;

public class ProjectionActivity extends AppCompatActivity {
    private static final String TAG = "ProjectionActivity";

    private ICameraService mCameraService;

    private ProjectionItemView mProjectionItemView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setAppFullScreen();
        setContentView(R.layout.activity_projection);
        initView();
        bindCameraService();
    }

    private void setAppFullScreen() {
        View decorView = getWindow().getDecorView();
        int uiOption = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOption);
        decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                LogUtil.d(TAG, "onSystemUiVisibilityChange, visibility:" + visibility);
                decorView.setSystemUiVisibility(uiOption);
            }
        });
    }

    private void initView() {
        mProjectionItemView = findViewById(R.id.projection_item_view);
        boolean isProjectionPlugged = Constants.isProjectionPlugged();
        LogUtil.d(TAG, "initView, isProjectionPlugged=" + isProjectionPlugged);;
        mProjectionItemView.setSignalStatus(isProjectionPlugged);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtil.d(TAG, "onResume");
        Constants.sIsSvForeground = true;
        notifyCameraItemResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogUtil.d(TAG, "onPause");
        Constants.sIsSvForeground = false;
        notifyCameraItemStop();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        LogUtil.d(TAG, "onBackPressed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "onDestroy");
        unbindCameraService();
    }

    private void notifyCameraItemResume() {
        if (mProjectionItemView != null) {
            mProjectionItemView.onResume();
        }
    }

    private void notifyCameraItemStop() {
        if (mProjectionItemView != null) {
            mProjectionItemView.onStop();
        }
    }

    private void bindCameraService() {
        startForegroundService(CameraService.getStartIntent(this));
        bindService(CameraService.getStartIntent(this), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindCameraService() {
        if (mCameraService != null) {
            try {
                mCameraService.unregisterCameraServiceCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            unbindService(mServiceConnection);
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtil.d(TAG, "onServiceConnected");
            mCameraService = ICameraService.Stub.asInterface(service);
            try {
                mCameraService.registerCameraServiceCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            initCameraItemData(mProjectionItemView);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtil.w(TAG, "onServiceDisconnected");
            mCameraService = null;
        }
    };

    private void initCameraItemData(ProjectionItemView item) {
        String cameraId = Constants.CAMERA_ID_PROJECTION;
        Resolution resolution;
        List<Resolution> resolutionList;
        int cameraState;
        boolean closeManual;
        try {
            resolution = mCameraService.getCurrentResolution(cameraId);
            resolutionList = mCameraService.getResolutions(cameraId);
            cameraState = mCameraService.getCameraState(cameraId);
            closeManual = mCameraService.isCameraClosedManual(cameraId);
        } catch (RemoteException e) {
            LogUtil.w(TAG, "initCameraItemData fail, cameraId = " + cameraId);
            resolution = new Resolution(Constants.DEFAULT_VIDEO_WIDTH, Constants.DEFAULT_VIDEO_HEIGHT);
            resolutionList = new ArrayList<>();
            cameraState = Constants.CameraState.CLOSED;
            closeManual = Constants.DEFAULT_STOP_PREVIEW;
        }
        LogUtil.d(TAG, "resolution = " + resolution.toString());
        item.setCurrentResolution(resolution);
        item.setResolutionList(resolutionList);
        item.setCameraState(cameraState);
        item.setCameraClosedStatus(closeManual);
        item.setCameraService(mCameraService);
    }

    private void handleCameraError(String cameraId, int message, int error) {
        switch (error) {
            case Constants.CallBackMsg.ERROR_CAMERA_NOT_ACCESS:
            case Constants.CallBackMsg.ERROR_CAMERA_NOT_AVAILABLE:
                if (cameraId.equals(Constants.CAMERA_ID_PROJECTION) && mProjectionItemView != null) {
                    //TODO
                    mProjectionItemView.setSignalStatus(false);
                }
                break;
            default:
                break;
        }
    }
    private ICameraServiceCallback.Stub mCallback = new ICameraServiceCallback.Stub() {
        @Override
        public void onStatusChanged(String cameraId, int message) throws RemoteException {
            LogUtil.d(TAG, "onStatusChanged, cameraId = " + cameraId + ", message = "
                    + Constants.operaToString(message));
        }

        @Override
        public void onCameraStateChanged(String cameraId, int cameraState) throws RemoteException {
            LogUtil.d(TAG, "onCameraStateChanged, cameraId = " + cameraId
                    + ", cameraState = " + Constants.cameraStateToString(cameraState));

            if (cameraId.equals(Constants.CAMERA_ID_PROJECTION) && mProjectionItemView != null) {
                //TODO
                mProjectionItemView.changeCameraState(cameraState);
            }
        }

        @Override
        public void onErrorOccurred(String cameraId, int message, int error) throws RemoteException {
            LogUtil.d(TAG, "onErrorOccurred, cameraId = " + cameraId + ", message = "
                    + Constants.operaToString(message) + ",error= " + Constants.callbackToString(error));
            handleCameraError(cameraId, message, error);
        }

        @Override
        public void onSignalStateChanged(String cameraId, int signalState) throws RemoteException {
            LogUtil.d(TAG, "onSignalStateChanged, cameraId = " + cameraId
                    + ", signalState = " + Constants.signalStateToString(signalState));
            if (cameraId.equals(Constants.CAMERA_ID_PROJECTION) && mProjectionItemView != null) {
                //TODO
                mProjectionItemView.setSignalStatus(signalState == Constants.SignalState.SIGNAL_COME);
            }
        }

        @Override
        public void onInfoNotify(int event, int arg1, int arg2, String cameraId) throws RemoteException {
            LogUtil.d(TAG, "onInfoNotify, event = " + event + ", arg1 = " + arg1
                    + ", arg2 = " + arg2 + ", cameraId = " + cameraId);
            if (event == Constants.Event.PROJECTION_EXIT) {
                if (mCameraService != null) {
                    try{
                        mCameraService.unregisterCameraServiceCallback(mCallback);
                    } catch (RemoteException e) {
                        LogUtil.w(TAG, "unregisterCameraServiceCallback fail:" + e);
                    }
                } else {
                    LogUtil.e(TAG, "onInfoNotify, mCameraService is null");
                }
                finish();
            }
        }
    };

    public static void launch(Context context) {
        LogUtil.d(TAG, "launch Constants.sIsSvForeground: " + Constants.sIsSvForeground);

        if (Constants.sIsSvForeground) {
            return;
        }

        Intent intent = new Intent(context, ProjectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }
}