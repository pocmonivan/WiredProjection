// ICameraServiceCallback.aidl
package com.autoai.wiredprojection;

// Declare any non-default types here with import statements

interface ICameraServiceCallback {
    oneway void onStatusChanged(String cameraId, int message);
    oneway void onErrorOccurred(String cameraId, int message, int error);
    oneway void onCameraStateChanged(String cameraId, int cameraState);
    oneway void onSignalStateChanged(String cameraId, int signalState);
    oneway void onInfoNotify(int event, int arg1, int arg2, String cameraId);
}
