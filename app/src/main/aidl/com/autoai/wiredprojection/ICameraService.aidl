// ICameraService.aidl
package com.autoai.wiredprojection;

import com.autoai.wiredprojection.ICameraServiceCallback;
import com.autoai.wiredprojection.bean.Resolution;
import java.util.List;
import android.view.Surface;

interface ICameraService {
    void startPreview(String cameraId, in Surface surface);
    void stopPreview(String cameraId, in Surface surface);
    boolean isPreviewStarted(String cameraId);

    void setResolution(String cameraId, int width, int height);
    List<Resolution> getResolutions(String cameraId);
    Resolution getCurrentResolution(String cameraId);

    void setStoragePath(String path);
    int getCameraState(String cameraId);
    void openCamera(String cameraId, boolean manual);
    void closeCamera(String cameraId, boolean manual);
    boolean isCameraClosedManual(String cameraId);
    void registerCameraServiceCallback(ICameraServiceCallback cb);
    void unregisterCameraServiceCallback(ICameraServiceCallback cb);
}
