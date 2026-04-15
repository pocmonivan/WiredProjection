package com.autoai.wiredprojection.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

import com.autoai.wiredprojection.util.LogUtil;
import com.autoai.wiredprojection.util.NotificationUtil;

import androidx.annotation.Nullable;

public class CameraService extends Service {
    private static final String TAG = "CameraService";

    private CameraServiceImpl mCameraServiceImpl;

    public static Intent getStartIntent(Context context) {
        return new Intent(context, CameraService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.d(TAG, "onCreate");
        startForegroundNotification();
        mCameraServiceImpl = new CameraServiceImpl(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mCameraServiceImpl;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d(TAG, "onDestroy");
        mCameraServiceImpl.onDestroy();
    }

    private void startForegroundNotification() {
        NotificationUtil.initChannel(this);
        Notification notification = NotificationUtil.getDefaultNotification(this);
        startForeground(NotificationUtil.NOTIFY_ID, notification);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getResources().updateConfiguration(newConfig, null);
    }
}
