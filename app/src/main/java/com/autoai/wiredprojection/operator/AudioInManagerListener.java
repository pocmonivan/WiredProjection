package com.autoai.wiredprojection.operator;

public interface AudioInManagerListener {
    void onStateChanged(boolean opened);

    void onFocusChanged(int focusChange);

    void onError(AudioInManager.ErrorCode code, String message);
}
