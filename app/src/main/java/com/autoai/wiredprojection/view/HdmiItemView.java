package com.autoai.wiredprojection.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.autoai.wiredprojection.ICameraService;
import com.autoai.wiredprojection.R;
import com.autoai.wiredprojection.bean.Resolution;
import com.autoai.wiredprojection.util.Constants;
import com.autoai.wiredprojection.util.LogUtil;

import java.util.List;

public class HdmiItemView extends RelativeLayout {
    private static final String TAG = "HdmiItemView";

    private Context mContext;
    private String mCameraID = Constants.CAMERA_ID_HDMI;

    private List<Resolution> mResolutions;
    private Resolution mCurrentResolution;

    private SurfaceView mSurfaceView;
    private ImageView mIvExit;
    private View mNoSignalView;

    private ICameraService mCameraService;
    private boolean mIsSurfaceAvailable;

    private int mCameraState;
    private boolean mClosedManual;
    private boolean mPendingPreview;
    private boolean mPendingSetResolution;

    private static final int HIDE_EXIT_BTN = 1;

    public HdmiItemView(Context context) {
        this(context, null);
    }

    public HdmiItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HdmiItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        mContext = context;
        LayoutInflater.from(mContext).inflate(R.layout.hdmi_item_layout, this, true);
        mSurfaceView = findViewById(R.id.sv_preview);
        mNoSignalView = findViewById(R.id.item_no_signal);
        mIvExit = findViewById(R.id.iv_exit);

        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        mSurfaceView.setOnClickListener(mSurfaceViewClickListener);//显示退出按钮
        mIvExit.setOnClickListener(new ThrottleClickListener() {
            @Override
            protected void onSingleClick(View v) {
                ((Activity)mContext).finish();
            }

            @Override
            protected void onFastClick(View v) {

            }
        });
    }

    public void setCameraService(ICameraService service) {
        LogUtil.d(TAG, "setCameraService, mCameraId =" + mCameraID);
        mCameraService = service;
        mayStartPreview();
    }

    public void onResume() {
        LogUtil.d(TAG, "onResume, mCameraId =" + mCameraID);
        mayStartPreview();
    }

    public void onStop() {
        LogUtil.d(TAG, "onStop, mCameraId =" + mCameraID);
        myStopPreview();

        mHandler.removeCallbacksAndMessages(null);
        hideSettings(false);
    }

    private void myStopPreview() {
        if (TextUtils.isEmpty(mCameraID) || (mCameraService == null)) {
            LogUtil.w(TAG,"myStopPreview: mCameraId =" + mCameraID + ",mCameraService=" + mCameraService);
            return;
        }
        try {
            mCameraService.stopPreview(mCameraID, mSurfaceView.getHolder().getSurface());
        } catch (RemoteException e) {
            LogUtil.e(TAG,"myStopPreview fail:" + e);
        }
        mPendingPreview = false;
    }

    private void mayStartPreview() {
        if (TextUtils.isEmpty(mCameraID) || (mCameraService == null) || !mIsSurfaceAvailable || mClosedManual) {
            LogUtil.w(TAG,"mayStartPreview skip: mCameraId =" + mCameraID + ",mCameraService=" + mCameraService +
                    ",mIsSurfaceAvailable=" + mIsSurfaceAvailable + ", mClosedManual = " + mClosedManual);
            return;
        }
        try {
            mCameraService.startPreview(mCameraID, mSurfaceView.getHolder().getSurface());
        } catch (RemoteException e) {
            LogUtil.e(TAG,"mayStartPreview fail:" + e);
        }
        mPendingPreview = false;
    }

    public void setCurrentResolution(Resolution resolution) {
        mCurrentResolution = resolution;
        mSurfaceView.getHolder().setFixedSize(mCurrentResolution.getWidth(), mCurrentResolution.getHeight());
    }

    public Resolution getCurrentResolution() {
        return mCurrentResolution;
    }

    public void setResolutionList(List<Resolution> resolutionList) {
        mResolutions = resolutionList;
    }

    public List<Resolution> getResolutionList() {
        return mResolutions;
    }

    public void changeResolution(int index) {
        if (mCameraService == null) {
            LogUtil.w(TAG, "mCameraService null, skip changeResolution");
            return;
        }
        if (index >= mResolutions.size() || index < 0) {
            LogUtil.w(TAG, "index invalid, skip changeResolution");
            return;
        }
        mCurrentResolution = mResolutions.get(index);
        LogUtil.d(TAG, "changeResolution to: " + mCurrentResolution);
        mSurfaceView.getHolder().setFixedSize(mCurrentResolution.getWidth(), mCurrentResolution.getHeight());
        mPendingSetResolution = true;
    }

    private void notifyResolutionChanged(int width, int height) {
        try {
            mCameraService.setResolution(mCameraID, width, height);
        } catch (RemoteException e) {
            LogUtil.w(TAG, "setResolution fail:" +e);
        }
    }

    public void setSignalStatus(boolean on) {
        mNoSignalView.setVisibility(on ? GONE : VISIBLE);
    }

    public void handleCloseBtnClick() {
        LogUtil.d(TAG, "handleCloseBtnClick, camera id:" + mCameraID);
        if (mCameraService == null) {
            LogUtil.w(TAG, "mCameraService null, skip handleCloseBtnClick");
            return;
        }
        try {
            if (mCameraState == Constants.CameraState.OPENED) {
                mClosedManual = true;
                mPendingPreview = false;
                mCameraService.closeCamera(mCameraID, true);
            } else if (mCameraState == Constants.CameraState.CLOSED) {
                mClosedManual = false;
                mPendingPreview = true;
                mNoSignalView.setVisibility(GONE);
                mCameraService.openCamera(mCameraID, true);
            } else {
                LogUtil.d(TAG, "skip handleCloseBtnClick");
            }
        } catch (RemoteException e) {
            LogUtil.w(TAG, "handleCloseBtnClick fail:" +e);
        }
    }

    public void setCameraClosedStatus(boolean closeManual) {
        mClosedManual = closeManual;
    }

    public void setCameraState(int state) {
        mCameraState = state;
    }

    public int getCameraState() {
        return mCameraState;
    }

    public void changeCameraState(int state) {
        mCameraState = state;
        if (state == Constants.CameraState.OPENED) {
            LogUtil.d(TAG, "changeCameraState, mPendingPreview = " + mPendingPreview);
            if (mPendingPreview) {
                mayStartPreview();
            }
        } else if (state == Constants.CameraState.CLOSED) {
            if (mClosedManual) {
                LogUtil.d(TAG, "mSurfaceView set INVISIBLE, camera:" + mCameraID);
                mSurfaceView.setVisibility(INVISIBLE);
            }
        } else if (state == Constants.CameraState.OPENING) {
            if (LogUtil.DEBUG_UI) {
                LogUtil.d(TAG, "mSurfaceView set VISIBLE, camera:" + mCameraID);
            }
            mSurfaceView.setVisibility(VISIBLE);
        }
    }

    public void onBackPressed() {
        if (LogUtil.DEBUG_UI) {
            LogUtil.d(TAG, "onBackPressed");
        }
    }

    class SurfaceCallback implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            LogUtil.d(TAG, "surfaceCreated, mCameraID = " + mCameraID + ", surface = " + holder.getSurface());
            mIsSurfaceAvailable = true;
            mayStartPreview();
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            LogUtil.d(TAG, "surfaceChanged, width = " + width + ", height = " + height
                    + ",mCameraID = " + mCameraID);
            if (mPendingSetResolution) {
                notifyResolutionChanged(width, height);
                mPendingSetResolution = false;
            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            LogUtil.d(TAG, "surfaceDestroyed, surface = " + holder.getSurface());
            mIsSurfaceAvailable = false;
        }
    }


    private final ThrottleClickListener mSurfaceViewClickListener = new ThrottleClickListener() {
        @Override
        protected void onSingleClick(View v) {
            LogUtil.d(TAG, "SurfaceView Clicked");
            if (mIvExit.getVisibility() == View.GONE) {
                showSettings();
            }

            //显示退出按钮，3秒后隐藏
            if(mHandler.hasMessages(HIDE_EXIT_BTN)) {
                mHandler.removeMessages(HIDE_EXIT_BTN);
            }
            mHandler.sendEmptyMessageDelayed(HIDE_EXIT_BTN, 3000);
        }

        @Override
        protected void onFastClick(View v) {

        }
    };

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HIDE_EXIT_BTN:
                    hideSettings(true);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private void showSettings() {
        LogUtil.d(TAG, "showSettings");
        AlphaAnimation showAnim = new AlphaAnimation(0.0f, 1.0f);
        showAnim.setDuration(500);
        mIvExit.startAnimation(showAnim);
        mIvExit.setVisibility(View.VISIBLE);
    }

    private void hideSettings(boolean showAnim) {
        LogUtil.d(TAG, "hideSettings " + showAnim);
        if(showAnim) {
            AlphaAnimation hideAnim = new AlphaAnimation(1.0f, 0.0f);
            hideAnim.setDuration(500);
            mIvExit.startAnimation(hideAnim);
        }
        mIvExit.setVisibility(View.GONE);
    }
}
