package com.autoai.wiredprojection.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import com.autoai.wiredprojection.R;
import com.autoai.wiredprojection.databinding.DialogConfirmationBinding;
import com.autoai.wiredprojection.util.LogUtil;

public class ConfirmationDialog extends Dialog implements View.OnClickListener {
    private static final String TAG = ConfirmationDialog.class.getSimpleName();

    private String mDlgTitle;
    private String mDlgContent;
    private ConfirmListener mDlgConfirmListener;

    private DialogConfirmationBinding binding;

    public ConfirmationDialog(@NonNull Context context) {
        super(context, R.style.NormalDialog);

        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        // 设置窗口属性，让对话框不隐藏导航栏和状态栏
        params.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        window.setAttributes(params);
        window.setGravity(Gravity.CENTER);
    }

    /**
     * Builder to help construct {@link ConfirmationDialog}.
     */
    public static class Builder {
        private final Context mContext;
        private String mTitle;
        private String mContent;
        private ConfirmListener mConfirmListener;

        public Builder(Context context) {
            mContext = context;
        }

        /**
         * Sets the title.
         */
        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the content.
         */
        public Builder setContent(String content) {
            mContent = content;
            return this;
        }

        /**
         * Sets the positive button label.
         */
        public Builder setConfirmListener(ConfirmListener confirmListener) {
            mConfirmListener = confirmListener;
            return this;
        }

        public ConfirmationDialog build() {
            return ConfirmationDialog.init(this);
        }
    }

    /**
     * Constructs the dialog fragment from the arguments provided in the {@link Builder}
     */
    private static ConfirmationDialog init(Builder builder) {
        ConfirmationDialog confirmationDialog = new ConfirmationDialog(builder.mContext);
        confirmationDialog.setTitle(builder.mTitle);
        confirmationDialog.setContent(builder.mContent);
        confirmationDialog.setConfirmListener(builder.mConfirmListener);
        return confirmationDialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.d(TAG, "onCreate, mDlgTitle=" + mDlgTitle + ", mDlgContent=" + mDlgContent);

        binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_confirmation, null, false);
        setCanceledOnTouchOutside(false);
        setCancelable(false);
        setContentView(binding.getRoot());
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (TextUtils.isEmpty(mDlgTitle)) {
            binding.title.setVisibility(View.GONE);
        } else {
            binding.title.setVisibility(View.VISIBLE);
            binding.title.setText(mDlgTitle);
        }

        if (TextUtils.isEmpty(mDlgContent)) {
            binding.content.setVisibility(View.GONE);
        } else {
            binding.content.setVisibility(View.VISIBLE);
            binding.content.setText(mDlgContent);
        }

        PressAnimation.addPressAnimation(binding.tvLeft, binding.tvRight);
        binding.tvLeft.setOnClickListener(this);
        binding.tvRight.setOnClickListener(this);
    }

    @Override
    public void show() {
        super.show();
        getWindow().setLayout((int) getContext().getResources().getDimension(R.dimen.auto_dlg_width)
                , (int) getContext().getResources().getDimension(R.dimen.auto_confirm_dlg_height));
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (mDlgConfirmListener != null) {
            if (id == R.id.tv_left) {
                dismiss();
                mDlgConfirmListener.onConfirm();
            } else if (id == R.id.tv_right) {
                dismiss();
                mDlgConfirmListener.onCancel();
            }
        }
    }

    /** Sets the listener which listens to a click on the positive button. */
    private void setTitle(String title) {
        mDlgTitle = title;
    }

    /** Sets the listener which listens to a click on the positive button. */
    private void setContent(String content) {
        mDlgContent = content;
    }

    /**
     * Sets the listener which listens to a click on the positive button.
     */
    private void setConfirmListener(ConfirmListener confirmListener) {
        mDlgConfirmListener = confirmListener;
    }

    /**
     * Listens to the confirmation action.
     */
    public interface ConfirmListener {
        void onConfirm();
        void onCancel();
    }
}
