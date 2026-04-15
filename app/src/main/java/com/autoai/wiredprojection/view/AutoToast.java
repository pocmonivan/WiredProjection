package com.autoai.wiredprojection.view;//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.autoai.wiredprojection.R;

import java.lang.reflect.Field;

public class AutoToast {
    private static Toast toast;
    private static AutoToast autoToast;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static Field sField_TN;
    private static Field sField_TN_Handler;

    private static void hook(Toast toast) {
        try {
            Object tn = sField_TN.get(toast);
            Handler preHandler = (Handler)sField_TN_Handler.get(tn);
            sField_TN_Handler.set(tn, new SafelyHandlerWarpper(preHandler));
        } catch (Exception var3) {
            Exception e = var3;
            e.printStackTrace();
        }

    }

    private AutoToast(final Context context, final CharSequence text, final int duration) {
        this.handler.post(new Runnable() {
            public void run() {
                if (AutoToast.toast == null || !AutoToast.toast.getView().isShown()) {
                    AutoToast.toast = Toast.makeText(context, (CharSequence)null, duration);
                    AutoToast.toast.setGravity(48, -65, -45);
                    LinearLayout rootView = (LinearLayout)AutoToast.toast.getView();
                    rootView.setBackgroundResource(R.drawable.auto_toast_background);
                    TextView textView = new TextView(context);
                    LinearLayout.LayoutParams llp;
//                    if (text.length() > 7) {
//                        llp = new LinearLayout.LayoutParams(700, 300);
//                    } else {
//                        llp = new LinearLayout.LayoutParams(400, 300);
//                    }

                    llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    llp.topMargin = 15;
                    llp.bottomMargin = 15;
                    llp.leftMargin = 50;
                    llp.rightMargin = 50;
                    textView.setLayoutParams(llp);
                    textView.setText(text);
                    textView.setTextSize(36.0F);
                    textView.setLineSpacing(20.0F, 1.0F);
                    textView.setTextColor(Color.parseColor("#FFFFFFFF"));
                    textView.setGravity(17);
                    textView.setBackgroundColor(Color.parseColor("#00000000"));
                    AutoToast.toast.setView(rootView);
                    rootView.removeAllViews();
                    rootView.addView(textView);
                    Log.d("AutoToast", "toast");
                }
            }
        });
    }

    public static AutoToast makeText(Context context, CharSequence text, int duration) {
        autoToast = new AutoToast(context, text, duration);
        return autoToast;
    }

    public void show() {
        this.handler.post(new Runnable() {
            public void run() {
                if (AutoToast.toast != null) {
                    AutoToast.hook(AutoToast.toast);
                    AutoToast.toast.show();
                }

            }
        });
    }

    static {
        try {
            sField_TN = Toast.class.getDeclaredField("mTN");
            sField_TN.setAccessible(true);
            sField_TN_Handler = sField_TN.getType().getDeclaredField("mHandler");
            sField_TN_Handler.setAccessible(true);
        } catch (Exception var1) {
            Exception e = var1;
            e.printStackTrace();
        }

    }

    private static class SafelyHandlerWarpper extends Handler {
        private Handler impl;

        public SafelyHandlerWarpper(Handler impl) {
            this.impl = impl;
        }

        public void dispatchMessage(Message msg) {
            try {
                super.dispatchMessage(msg);
            } catch (Exception var3) {
                Exception e = var3;
                e.printStackTrace();
            }

        }

        public void handleMessage(Message msg) {
            this.impl.handleMessage(msg);
        }
    }
}
