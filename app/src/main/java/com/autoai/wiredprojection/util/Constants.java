package com.autoai.wiredprojection.util;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Constants {

    public static final String CAMERA_ID_PROJECTION = "10";

    public static final boolean DEFAULT_STOP_PREVIEW = false;
    public static final int DEFAULT_VIDEO_WIDTH = 1280;
    public static final int DEFAULT_VIDEO_HEIGHT = 720;

    //PQ
    public static final int DEFAULT_HUE_VALUE = 0;
    public static final int DEFAULT_BRIGHTNESS_VALUE = 0;
    public static final int DEFAULT_SATURATION_VALUE = 128;
    public static final int DEFAULT_CONTRAST_VALUE = 128;

    public static final String SYS_PROJECTION_STATUS = "/sys/KCbox_E1_IF/KCbox_E1_linkA_status";
    public static final String SYS_PROJECTION_PLUGGED = "linkA:1";

    public static boolean sIsSvForeground = false;

    public static boolean isProjectionPlugged() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(SYS_PROJECTION_STATUS));
            String status = reader.readLine();
            reader.close();
            if(!TextUtils.isEmpty(status) && status.trim().equals(SYS_PROJECTION_PLUGGED)) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public interface Operation {
        int OPEN_CAMERA = 1;
        int CLOSE_CAMERA = 2;
        int START_PREVIEW = 3;
        int STOP_PREVIEW = 6;
        int SET_RESOLUTION = 13;
        int SEND_INFO = 14;
    }

    public interface CallBackMsg {
        int ERROR_UNKNOWN = -1;
        int ERROR_CAMERA_NOT_AVAILABLE = 6;
        int ERROR_CAMERA_NOT_ACCESS = 7;
        int ERROR_CAMERA_PERMISSION_NOT_ALLOWED = 8;
        int INFO_NO_SIGNAL = 15;
        int INFO_SIGNAL_COME = 18;
    }

    public interface CameraState {
        int CLOSED = 0;
        int OPENING = 1;
        int OPENED = 2;
        int CLOSING = 3;
    }

    public interface SignalState {
        int NO_SIGNAL = 0;
        int SIGNAL_COME = 1;
    }

    public interface PreviewSurfaceType {
        int LOCAL = 0; //for dvr preview
        int REMOTE = 2; //for other process
    }

    public interface Event {
        int PROJECTION_EXIT = 8;
    }

    public static String operaToString(int operation) {
        if (operation == Operation.OPEN_CAMERA) return "OPEN_CAMERA";
        if (operation == Operation.CLOSE_CAMERA) return "CLOSE_CAMERA";
        if (operation == Operation.START_PREVIEW) return "START_PREVIEW";
        if (operation == Operation.STOP_PREVIEW) return "STOP_PREVIEW";
        if (operation == Operation.SET_RESOLUTION) return "SET_RESOLUTION";
        if (operation == Operation.SEND_INFO) return "SEND_INFO";
        return "unknown_operation";
    }

    public static String cameraStateToString(int state) {
        if (state == CameraState.CLOSED) return "CLOSED";
        if (state == CameraState.OPENING) return "OPENING";
        if (state == CameraState.OPENED) return "OPENED";
        if (state == CameraState.CLOSING) return "CLOSING";
        return "unknown_state";
    }

    public static String signalStateToString(int state) {
        if (state == SignalState.NO_SIGNAL) return "NO_SIGNAL";
        if (state == SignalState.SIGNAL_COME) return "SIGNAL_COME";
        return "unknown_state";
    }

    public static String callbackToString(int callback) {
        if (callback == CallBackMsg.ERROR_UNKNOWN) return "ERROR_UNKNOWN";
        if (callback == CallBackMsg.ERROR_CAMERA_NOT_AVAILABLE) return "ERROR_CAMERA_NOT_AVAILABLE";
        if (callback == CallBackMsg.ERROR_CAMERA_NOT_ACCESS) return "ERROR_CAMERA_NOT_ACCESS";
        if (callback == CallBackMsg.ERROR_CAMERA_PERMISSION_NOT_ALLOWED) return "ERROR_CAMERA_PERMISSION_NOT_ALLOWED";
        if (callback == CallBackMsg.INFO_NO_SIGNAL) return "INFO_NO_SIGNAL";
        if (callback == CallBackMsg.INFO_SIGNAL_COME) return "INFO_SIGNAL_COME";
        return "unknown_callback";
    }
}
