package com.autoai.wiredprojection.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.autoai.wiredprojection.service.CameraService;
import com.autoai.wiredprojection.util.LogUtil;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        LogUtil.d(TAG, "action: " + action);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            context.startForegroundService(CameraService.getStartIntent(context));
        }
    }
}