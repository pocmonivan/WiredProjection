package com.autoai.wiredprojection.operator;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.utils.SurfaceUtils;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;

import com.autochips.backcar.BackCar;
import com.autoai.wiredprojection.util.Constants;
import com.autoai.wiredprojection.util.Constants.*;
import com.autoai.wiredprojection.util.LogUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;

public class CameraOperation {
    private String TAG = "CameraOperation";
    private final String mCameraId;
    private final Context mContext;
    private CameraDevice mCameraDevice;
    private CameraOperationCallback mCameraOperationCallback;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureResult.Key<int[]> mSourceInfoKey;
    private CaptureRequest.Key<int[]> mMirrorKey;
    private CaptureRequest.Key<int[]> mHueKey;
    private CaptureRequest.Key<int[]> mBrightnessKey;
    private CaptureRequest.Key<int[]> mSaturationKey;
    private CaptureRequest.Key<int[]> mContrastKey;
    private volatile boolean mPreviewRequested;
    private boolean mIsCharacterInited;

    private volatile boolean mIsPreviewing;
    private boolean mHavSignal;
    private boolean mRespondRVCFirstFrame;
    private final List<Surface> mPendingPreviewSurfaces = new CopyOnWriteArrayList<>();
    private final List<Surface> mCurrentPreviewSurfaces = new CopyOnWriteArrayList<>();
    private Surface mRemotePreviewSurface;
    private volatile boolean mIsRvcStarted;

    private static final int FRAMES_MAYBE_WRONG = 30;
    private int mFramesShouldSkipped = FRAMES_MAYBE_WRONG;

    private Size mVideoSize = new Size(Constants.DEFAULT_VIDEO_WIDTH, Constants.DEFAULT_VIDEO_HEIGHT);

    private int mMirrorVal;
    private int mHueValue;
    private int mBrightnessValue;
    private int mSaturationValue;
    private int mContrastValue;
    private Size[] mSupportedResolutions;

    private static final String ATC_SOURCEINFO_KEY_NAME = "com.atc.sourceinfo";
    private static final String ATC_MIRROR_KEY_NAME = "com.atc.mirror";
    private static final String ATC_HUE_KEY_NAME = "com.atc.color_process.hue";
    private static final String ATC_BRIGHTNESS_KEY_NAME = "com.atc.color_process.brightness";
    private static final String ATC_SATURATION_KEY_NAME = "com.atc.color_process.saturation";
    private static final String ATC_CONTRAST_KEY_NAME = "com.atc.color_process.contrast";

    private static final int SESSION_CLOSED = 0;
    private static final int SESSION_CREATING = 1;
    private static final int SESSION_CREATED = 2;
    private static final int SESSION_CLOSING = 3;
    private int mSessionState = SESSION_CLOSED;
    private int mCameraState = CameraState.CLOSED;
    private boolean mShouldUpdateSession = true;
    private Handler mCallerThreadHandler;
    private boolean mCameraClosedManual;
    public interface CameraOperationCallback {
        void onOperationSuccess(int op, String id);
        void onOperationFailed(int op, String id, int extra);
        void onCameraStateChanged(String cameraId, int state);
        void onSignalStateChanged(String cameraId, int state);
    }

    public CameraOperation(Context context, String cameraId, Looper looper) {
        mContext = context;
        TAG = TAG + cameraId;
        mCameraId = cameraId;
        getAtcCaptureKeys();
        mCallerThreadHandler = new Handler(looper);
        LogUtil.d(TAG, "finish construct handler");
        initData();
    }

    private void initData() {
        LogUtil.d(TAG, "initData begin");
        mBrightnessValue = Constants.DEFAULT_BRIGHTNESS_VALUE;
        mContrastValue = Constants.DEFAULT_CONTRAST_VALUE;
        mSaturationValue = Constants.DEFAULT_SATURATION_VALUE;
        mHueValue = Constants.DEFAULT_HUE_VALUE;
        mMirrorVal = 0;
        mCameraClosedManual = Constants.DEFAULT_STOP_PREVIEW;
        updateCameraCharacter();
        LogUtil.d(TAG, "initData end");
    }

    private void updateCameraCharacter() {
        LogUtil.d(TAG, "updateCameraCharacter begin");
        mSupportedResolutions = getSupportResolutions();
        if (mSupportedResolutions != null && updateVideoSize()) {
            mIsCharacterInited = true;
        }
        LogUtil.d(TAG, "updateCameraCharacter end");
    }

    private boolean updateVideoSize() {
        Size maxSize = getMaximumResolution();
        if (isSizeValid(maxSize)) {
            mVideoSize = maxSize;
            return true;
        }
        return false;
    }

    private boolean isSizeValid(Size size) {
        if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) {
            return false;
        }
        return true;
    }

    private Size getMaximumResolution() {
        Size[] sizes = mSupportedResolutions;
        Size maxSize = new Size(0, 0);
        if (sizes != null && sizes.length > 0) {
            for (Size size : sizes) {
                if (size.getWidth() > maxSize.getWidth()) {
                    maxSize = size;
                } else if (size.getWidth() == maxSize.getWidth() && size.getHeight() > maxSize.getHeight()) {
                    maxSize = size;
                }
            }
        }
        LogUtil.d(TAG, "getMaximumResolution is (" + maxSize.getWidth() + "," + maxSize.getHeight() + ")");
        return maxSize;
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback(){
        @Override
        public void onClosed(CameraDevice camera) {
            super.onClosed(camera);
            LogUtil.d(TAG, "onClosed, CameraDevice:" + camera);
            mCameraDevice = null;
            mIsPreviewing = false;
            notifyOperationCallback(Operation.CLOSE_CAMERA, true, 0);
            notifyCameraState(CameraState.CLOSED);
        }

        @Override
        public void onOpened(CameraDevice camera) {
            LogUtil.d(TAG, "onOpened, CameraDevice:" + camera);
            mCameraDevice = camera;
            if (!mIsCharacterInited) {
                updateCameraCharacter();
            }
            notifyOperationCallback(Operation.OPEN_CAMERA, true, 0);
            notifyCameraState(CameraState.OPENED);
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            LogUtil.d(TAG, "camera disconnected, CameraDevice:" + camera);
            close();
            notifyOperationCallback(Operation.SEND_INFO, false, CallBackMsg.ERROR_CAMERA_NOT_AVAILABLE);
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            LogUtil.d(TAG, "camera open error: " + error + ",camera = " + camera
                    + ",mCameraDevice = " + mCameraDevice);
            if (error == CameraDevice.StateCallback.ERROR_CAMERA_SERVICE
                || error == CameraDevice.StateCallback.ERROR_CAMERA_DEVICE) {
            }
            if (mCameraDevice == null) {
                camera.close();
                notifyCameraState(CameraState.CLOSING);
                notifyOperationCallback(Operation.OPEN_CAMERA, false, CallBackMsg.ERROR_CAMERA_NOT_ACCESS);
            }
            close();
            notifyOperationCallback(Operation.SEND_INFO, false, CallBackMsg.ERROR_CAMERA_NOT_ACCESS);
        }
    };

    private void notifyOperationCallback(int operation, boolean success, int error) {
        if (mCameraOperationCallback != null) {
            if (success) {
                mCameraOperationCallback.onOperationSuccess(operation, mCameraId);
            }else {
                mCameraOperationCallback.onOperationFailed(operation, mCameraId, error);
            }
        }
    }

    public void open() {
        int count = 3;
        boolean success = false;
        int errorCode = 0;
        LogUtil.d(TAG,"open camera");
        if (mCameraDevice != null) {
            LogUtil.w(TAG, "camera has been opened!");
            notifyOperationCallback(Operation.OPEN_CAMERA, true, 0);
            notifyCameraState(CameraState.OPENED);
            return;
        }
        while(count-- > 0) {
            try {
                CameraManager cm = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
                cm.openCamera(mCameraId, mStateCallback, null);
                success = true;
            } catch (CameraAccessException e) {
                LogUtil.d(TAG, "openCamera: " + e.toString());
                errorCode = CallBackMsg.ERROR_CAMERA_NOT_ACCESS;
            } catch (IllegalArgumentException e) {
                LogUtil.d(TAG, "openCamera: " + e.toString());
                errorCode = CallBackMsg.ERROR_CAMERA_NOT_AVAILABLE;
            } catch (SecurityException e) {
                LogUtil.d(TAG, "do not have permission to open camera");
                errorCode = CallBackMsg.ERROR_CAMERA_PERMISSION_NOT_ALLOWED;
            }
            if (!success && count > 0) {
                try{
                    Thread.sleep(500);
                }catch (Exception e) {
                    LogUtil.w(TAG, "Thread sleep fail: " + e);
                }
            } else {
                break;
            }
        }
        if (!success) {
            notifyOperationCallback(Operation.OPEN_CAMERA, false, errorCode);
        }
        if (success) {
            notifyCameraState(CameraState.OPENING);
        }
    }

    public void close() {
        close(true);
    }

    public void close(boolean keepState) {
        LogUtil.d(TAG, "close camera " + mCameraId);
        mIsPreviewing = false;
        if (mCaptureSession != null) {
            closeSession();
            mCaptureSession = null;
        }
        mSessionState = SESSION_CLOSED;
        if (mCameraDevice != null) {
            LogUtil.d(TAG, "close start");
            mCameraDevice.close();
            LogUtil.d(TAG, "close end");
            mCameraDevice = null;
            notifyCameraState(CameraState.CLOSING);
        }
        if (!keepState) {
            mPendingPreviewSurfaces.clear();
            mCurrentPreviewSurfaces.clear();
            mRemotePreviewSurface = null;
            mPreviewRequested = false;
        }
    }

    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {

        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (mRespondRVCFirstFrame) {
                mRespondRVCFirstFrame = false;
                LogUtil.d(TAG, "first frame with rvc mode");
                new BackCar().sendRVCCompleted();
            }
            if (mSourceInfoKey != null) {
                int[] sourceInfo = result.get(mSourceInfoKey);
                if (sourceInfo != null && sourceInfo.length >= 2) {
                    if (sourceInfo[0] == 0 && sourceInfo[1] == 0) {
                        if (mFramesShouldSkipped == 0) {
                            notifyOperationCallback(Operation.SEND_INFO, false, CallBackMsg.INFO_NO_SIGNAL);
                            notifySignalState(SignalState.NO_SIGNAL);
                            mFramesShouldSkipped--;
                        } else if (mFramesShouldSkipped < 0) {
                            //do nothing
                        } else {
                            mFramesShouldSkipped--;
                        }
                        mHavSignal = false;
                    } else if (sourceInfo[0] > 0 && sourceInfo[1] > 0 && !mHavSignal) {
                        LogUtil.d(TAG, "signal comes, width = " + sourceInfo[0] + ", height = " + sourceInfo[1]);
                        notifyOperationCallback(Operation.SEND_INFO, false, CallBackMsg.INFO_SIGNAL_COME);
                        notifySignalState(SignalState.SIGNAL_COME);
                        mFramesShouldSkipped = FRAMES_MAYBE_WRONG;
                        mHavSignal = true;
                    } else {
                        mFramesShouldSkipped = FRAMES_MAYBE_WRONG;
                    }
                }
            }
        }
    };

    private void notifySignalState(int state) {
        if (mCameraOperationCallback != null) {
            mCameraOperationCallback.onSignalStateChanged(mCameraId, state);
        }
    }

    private void notifyCameraState(int state) {
        mCameraState = state;
        if (mCameraOperationCallback != null) {
            mCameraOperationCallback.onCameraStateChanged(mCameraId, state);
        }
    }

    public void setCameraCloseManual(boolean closeManual) {
        mCameraClosedManual = closeManual;
    }

    public boolean isCameraClosedManual() {
        return mCameraClosedManual;
    }

    public boolean isNeedRestoreCamera() {
        LogUtil.d(TAG, "isNeedRestoreCamera, mPreviewRequested=" + mPreviewRequested
                + ",mIsPreviewing=" + mIsPreviewing);
        return mPreviewRequested != mIsPreviewing;
    }

    public void restoreCamera() {
        LogUtil.d(TAG, "restoreCamera");
        mShouldUpdateSession = true;
        updateSessionAsync();
    }

    private void  getAtcCaptureKeys() {
        LogUtil.d(TAG, "getAtcCaptureKeys begin");
        try {
            CameraManager cm = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            List<CaptureRequest.Key<?>> requestKeyList = cm.getCameraCharacteristics(mCameraId)
                    .getAvailableCaptureRequestKeys();
            for (CaptureRequest.Key<?> key : requestKeyList) {
                if (ATC_BRIGHTNESS_KEY_NAME.equals(key.getName())) {
                    mBrightnessKey = (CaptureRequest.Key<int[]>)key;
                    LogUtil.d(TAG, "Camera " + mCameraId + " support brightness setting");
                } else if (ATC_SATURATION_KEY_NAME.equals(key.getName())) {
                    mSaturationKey = (CaptureRequest.Key<int[]>)key;
                    LogUtil.d(TAG, "Camera " + mCameraId + " support saturation setting");
                } else if (ATC_CONTRAST_KEY_NAME.equals(key.getName())) {
                    mContrastKey = (CaptureRequest.Key<int[]>)key;
                    LogUtil.d(TAG, "Camera " + mCameraId + " support contrast setting");
                } else if (ATC_HUE_KEY_NAME.equals(key.getName())) {
                    mHueKey = (CaptureRequest.Key<int[]>) key;
                    LogUtil.d(TAG, "Camera " + mCameraId + " support hue setting");
                } else if (ATC_MIRROR_KEY_NAME.equals(key.getName())) {
                    mMirrorKey = (CaptureRequest.Key<int[]>)key;
                    LogUtil.d(TAG, "Camera " + mCameraId + " support mirror");
                }
            }
            List<CaptureResult.Key<?>> resultKeyList = cm.getCameraCharacteristics(mCameraId)
                    .getAvailableCaptureResultKeys();
            for (CaptureResult.Key<?> key : resultKeyList) {
                if (ATC_SOURCEINFO_KEY_NAME.equals(key.getName())) {
                    mSourceInfoKey = (CaptureResult.Key<int[]>)key;
                    LogUtil.d(TAG, "Camera " + mCameraId + " support sourceinfo");
                }
            }
        } catch (Exception e) {
            LogUtil.d(TAG, "getAtcCaptureKeys fail: " + e.toString());
        }
        LogUtil.d(TAG, "getAtcCaptureKeys end");
    }

    public Size[] getSupportResolutions() {
        try {
            CameraManager cm = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = cm.getCameraCharacteristics(mCameraId)
                                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return null;
            } else {
                return map.getOutputSizes(SurfaceTexture.class);
            }
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void addPreviewSurface(Surface surface, int type) {
        if (surface == null || !surface.isValid()) {
            LogUtil.w(TAG, "surface invalid,don't add!");
            return;
        }
        if (mPendingPreviewSurfaces.contains(surface)) {
            LogUtil.w(TAG, "This surface has been added before!");
            return;
        }
        LogUtil.d(TAG, "addPreviewSurface, surface = " + surface + ", type = " +type);
        if (type == Constants.PreviewSurfaceType.REMOTE) {
            mRemotePreviewSurface = surface;
        } else {
            mPendingPreviewSurfaces.add(surface);
        }
        mShouldUpdateSession = true;
    }

    public synchronized void removePreviewSurface(Surface surface, int type) {
        LogUtil.d(TAG, "removePreviewSurface, surface = " + surface + ",type = " +type);
        if (type == Constants.PreviewSurfaceType.REMOTE) {
            LogUtil.d(TAG, "remove mRemotePreviewSurface");
            mRemotePreviewSurface = null;
            mShouldUpdateSession = true;
            return;
        }
        if (!mPendingPreviewSurfaces.contains(surface)) {
            LogUtil.w(TAG, "doesn't contains this surface!");
            return;
        }
        mPendingPreviewSurfaces.remove(surface);
        mShouldUpdateSession = true;
    }

    public synchronized void startPreview(boolean isFromRVC) {
        LogUtil.d(TAG, "startPreview, isFromRVC = " + isFromRVC);
        if (mPendingPreviewSurfaces.size() == 0 && mRemotePreviewSurface == null) {
            LogUtil.d(TAG, "no pending preview");
            notifyOperationCallback(Operation.START_PREVIEW, false, CallBackMsg.ERROR_UNKNOWN);
            return;
        }
        mPreviewRequested = true;
        if (isFromRVC) {
            mIsRvcStarted = true;
            mRespondRVCFirstFrame = true;
        }
        updateSessionAsync();
    }

    public synchronized void stopPreview(boolean isFromRVC) {
        LogUtil.d(TAG, "stopPreview, isFromRVC = " + isFromRVC
         + ", mPreviewRequested = " + mPreviewRequested);
        if (!mPreviewRequested) {
            notifyOperationCallback(Operation.STOP_PREVIEW, true, 0);
            return;
        }
        if (isFromRVC) {
            mIsRvcStarted = false;
        }
        LogUtil.d(TAG, "mCaptureSession = " + mCaptureSession + ", mSessionState = " + mSessionState);
        updateSessionAsync();
    }

    private void updateSessionAsync() {
        mCallerThreadHandler.removeCallbacks(mSessionUpdater);
        mCallerThreadHandler.post(mSessionUpdater);
    }

    private final Runnable mSessionUpdater = new Runnable() {
        @Override
        public void run() {
            if (!updateSessionIfNecessary()) {
                if (!sendRepeatingRequest()) {
                    LogUtil.e(TAG, "sendRepeatingRequest failed.");
                }
            }
        }
    };

    /**
     * update CameraCaptureSession if need. Called after surface list change.
     *
     * @return false if unnecessary to update Session, otherwise true.
     */
    private boolean updateSessionIfNecessary() {
        LogUtil.d(TAG, "updateSessionIfNecessary, mShouldUpdateSession = " + mShouldUpdateSession);
        //Surfaces not changed, don't update session
        synchronized (CameraOperation.this) {
            if (!mShouldUpdateSession && mSessionState == SESSION_CREATED) {
                LogUtil.d(TAG, "don't need to update session");
                return false;
            }
        }

        //Unnecessary to update Session, if no operation request
        if (!mPreviewRequested) {
            LogUtil.d(TAG, "don't update session, no operation request");
            return false;
        }

        if (mSessionState == SESSION_CREATING || mSessionState == SESSION_CLOSING) {
            LogUtil.d(TAG, "need to update session, mSessionState = " + mSessionState);
            return true;
        }

        if (mCameraDevice == null) {
            LogUtil.d(TAG, "don't update session, mCameraDevice null");
            return true;
        }

        if (mCaptureSession != null) {
            LogUtil.d(TAG, "updateSessionIfNecessary, closeSession");
            closeSession();
        }

        synchronized (CameraOperation.this) {
            mShouldUpdateSession = false;
            mSessionState = SESSION_CREATING;
        }
        try {
            mCameraDevice.createCaptureSessionByOutputConfigurations(
                    prepareOutputConfigurations(), mSessionStateCallback,
                    mCallerThreadHandler);
        } catch (CameraAccessException e) {
            LogUtil.e(TAG, "createCaptureSessionByOutputConfigurations failed. e:" + e);
            close();
        }
        return true;
    }

    private void closeSession() {
        if (mCaptureSession != null) {
            LogUtil.d(TAG, "closeSession");
            mCaptureSession.close();
            mSessionState = SESSION_CLOSED;
            mCaptureSession = null;
        }
        mHavSignal = false;
        mIsPreviewing = false;
        mShouldUpdateSession = true;
    }

    private CameraCaptureSession.StateCallback mSessionStateCallback
            = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            LogUtil.d(TAG, "onConfigured, session = " + session);
            synchronized (CameraOperation.this) {
                if (mCameraDevice != null) {
                    mSessionState = SESSION_CREATED;
                    mCaptureSession = session;
                    if (mShouldUpdateSession) {
                        updateSessionIfNecessary();
                        return;
                    }
                    checkAndExecRequests();
                }
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            LogUtil.e(TAG, "session onConfigureFailed, ");
            synchronized (CameraOperation.this) {
                mSessionState = SESSION_CLOSED;
                mCaptureSession = null;
            }
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            synchronized (CameraOperation.this) {
                LogUtil.d(TAG, "session onClosed, session = " + session
                        + ",mCameraDevice = " + mCameraDevice
                        + ",mCaptureSession = " + mCaptureSession);
            }
        }
    };

    private boolean sendRepeatingRequest() {
        if (mCaptureSession == null) {
            LogUtil.w(TAG, "mCaptureSession is not ready.");
            return false;
        }

        if (mCameraDevice == null) {
            LogUtil.w(TAG, "sendRepeatingRequest mCameraDevice is null.");
            return false;
        }

        try {
            setUpCaptureRequestBuilder();
            LogUtil.d(TAG, "setRepeatingRequest");
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), mCaptureCallback, null);
            mFramesShouldSkipped = FRAMES_MAYBE_WRONG;
        } catch (CameraAccessException | IllegalStateException
                | IllegalArgumentException e) {
            LogUtil.e(TAG, "sendRepeatingRequest fail:" + e);
            return false;
        }
        return true;
    }

    private void setUpCaptureRequestBuilder() throws CameraAccessException {
        mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        for (Surface surface : mCurrentPreviewSurfaces) {
            mCaptureRequestBuilder.addTarget(surface);
        }
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        if (mIsRvcStarted) {
            if (mMirrorKey != null) {
                mCaptureRequestBuilder.set(mMirrorKey, new int[]{mMirrorVal, 0, 0, 0});
            }
            if (mHueKey != null) {
                LogUtil.d(TAG, "set hue key, value = " + mHueValue);
                mCaptureRequestBuilder.set(mHueKey, new int[]{mHueValue, 0, 0, 0});
            }
            if (mBrightnessKey != null) {
                LogUtil.d(TAG, "set brightness key, value = " + mBrightnessValue);
                mCaptureRequestBuilder.set(mBrightnessKey, new int[]{mBrightnessValue, 0, 0, 0});
            }
            if (mSaturationKey != null) {
                LogUtil.d(TAG, "set saturation key, value = " + mSaturationValue);
                mCaptureRequestBuilder.set(mSaturationKey, new int[]{mSaturationValue, 0, 0, 0});
            }
            if (mContrastKey != null) {
                LogUtil.d(TAG, "set contrast key, value = " + mContrastValue);
                mCaptureRequestBuilder.set(mContrastKey, new int[]{mContrastValue, 0, 0, 0});
            }
        }
    }

    private void checkAndExecRequests() {
        int previewNum = mCurrentPreviewSurfaces.size();
        StringBuilder builder = new StringBuilder("checkAndExecRequests");
        builder.append(", mPreviewRequested = " + mPreviewRequested)
                .append(", previewNum = " + previewNum + "\n");
        LogUtil.d(TAG, builder.toString());
        boolean needPreview = (mPreviewRequested && previewNum > 0);
        if (needPreview) {
            boolean repeatRet = sendRepeatingRequest();
            if (needPreview && repeatRet) {
                mIsPreviewing = true;
                notifyOperationCallback(Operation.START_PREVIEW, true, 0);
            } else if (needPreview && !repeatRet) {
                if (!mIsPreviewing) {
                    notifyOperationCallback(Operation.START_PREVIEW, false, CallBackMsg.ERROR_UNKNOWN);
                }
            }
        }
        if (!needPreview) {
            notifyOperationCallback(Operation.STOP_PREVIEW, true, 0);
        }
    }

    private List<OutputConfiguration> prepareOutputConfigurations() {
        LogUtil.d(TAG, "prepareOutputConfigurations");
        List<OutputConfiguration> outputConfigurations = new ArrayList<>();
        OutputConfiguration previewConfig = getPreviewConfiguration();
        if (previewConfig != null) {
            outputConfigurations.add(previewConfig);
        }
        LogUtil.d(TAG, "outputConfigurations size = " + outputConfigurations.size());
        return outputConfigurations;
    }

    private boolean checkSurfaceValid(Surface surface) {
        if (surface == null) {
            return false;
        }
        boolean valid = surface.isValid();
        if (valid) {
            try {
                Size size = SurfaceUtils.getSurfaceSize(surface);
                LogUtil.d(TAG, "Surface width:" + size.getWidth() + ", height:" + size.getHeight() + ", surface: " + surface);
            } catch (IllegalArgumentException e) {
                LogUtil.e(TAG, "getSurfaceSize fail, surface :" + surface + " ,reason:" + e);
                valid = false;
            }
        }
        return valid;
    }

    private OutputConfiguration getPreviewConfiguration() {
        OutputConfiguration configuration = null;
        int previewNum = mPendingPreviewSurfaces.size();
        LogUtil.d(TAG, "prepare preview configuration, previewNum = " + previewNum);
        if (previewNum == 0) {
            mPreviewRequested = false;
        }
        LogUtil.d(TAG,"prepare preview surfaces");
        mCurrentPreviewSurfaces.clear();
        for (Surface surface : mPendingPreviewSurfaces) {
            configuration = updatePreviewConfiguration(configuration, surface);
        }
        LogUtil.d(TAG, "mRemotePreviewSurface = " + mRemotePreviewSurface);
        if (mRemotePreviewSurface != null) {
            configuration = updatePreviewConfiguration(configuration, mRemotePreviewSurface);
        }
        LogUtil.d(TAG, "prepare preview configuration finish");
        return configuration;
    }

    private OutputConfiguration updatePreviewConfiguration(OutputConfiguration configuration, Surface surface) {
        boolean valid = checkSurfaceValid(surface);

        if (valid && configuration == null) {
            configuration = new OutputConfiguration(surface);
            mCurrentPreviewSurfaces.add(surface);
        } else if (valid && configuration != null) {
            configuration.enableSurfaceSharing();
            try {
                configuration.addSurface(surface);
                mCurrentPreviewSurfaces.add(surface);
            } catch (IllegalArgumentException e) {
                LogUtil.e(TAG, "addSurface fail : " + e +", surface = " + surface);
            }
        } else if (!valid) {
            LogUtil.w(TAG, "found invalid surface : " + surface);
        }
        return configuration;
    }

    public void setResolution(int width, int height) {
        LogUtil.d(TAG, "setResolution, width = " + width + ", height = " + height);
        if (mVideoSize.getWidth() == width && mVideoSize.getHeight() == height) {
            notifyOperationCallback(Operation.SET_RESOLUTION, true, 0);
            return;
        }
        Size resolution = new Size(width, height);
        mVideoSize = resolution;
        mShouldUpdateSession = true;
        updateSessionAsync();
        notifyOperationCallback(Operation.SET_RESOLUTION, true, 0);
    }

    public synchronized boolean isPreviewing() {
        return mIsPreviewing;
    }

    public boolean isPreviewRequest() {
        return mPreviewRequested;
    }

    public Size getVideoSize() {
        return mVideoSize;
    }

    public List<Size> getResolutions() {
        if (!mIsCharacterInited) {
            updateCameraCharacter();
        }
        Size[] sizes = mSupportedResolutions;
        if (sizes == null) {
            return new ArrayList<Size>();
        } else {
            return Arrays.asList(sizes);
        }
    }

    public int getCameraState() {
        return mCameraState;
    }

    public void registerCameraOperationCallback(CameraOperationCallback callback){
        mCameraOperationCallback = callback;
    }

    public void unregisterCameraOperationCallback() {
        mCameraOperationCallback = null;
    }

}
