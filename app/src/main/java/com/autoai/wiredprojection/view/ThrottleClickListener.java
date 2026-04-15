package com.autoai.wiredprojection.view;

import android.view.View;

/**
 * 自定义过滤快速点击
 */
public abstract class ThrottleClickListener implements View.OnClickListener {

    public static final int TIME_INTERVAL = 1000;

    private long mLastClickTime;

    public ThrottleClickListener() {

    }

    @Override
    public void onClick(View v) {
        long nowTime = System.currentTimeMillis();
        if (nowTime - mLastClickTime >= TIME_INTERVAL){
            // 单次点击事件
            onSingleClick(v);
            mLastClickTime = nowTime;
        } else {
            // 快速点击事件
            onFastClick(v);
        }
    }

    protected abstract void onSingleClick(View v);
    protected abstract void onFastClick(View v);
}

