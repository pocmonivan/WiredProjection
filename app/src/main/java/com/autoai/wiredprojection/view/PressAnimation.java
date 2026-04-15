package com.autoai.wiredprojection.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.MotionEvent;
import android.view.View;

/**
 * 按键动画
 * 来源：http://10.5.1.53:8090/confluence/pages/viewpage.action?pageId=97989705
 *
 * @Date 2023/2/27
 */
public class PressAnimation {
    private static boolean isContract = false;
    public static void addPressAnimation(View... views) {
        for (View view : views) {
            ObjectAnimator contractX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.90f);
            ObjectAnimator contractY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.90f);
            ObjectAnimator expandX = ObjectAnimator.ofFloat(view, "scaleX", 0.90f, 1f);
            ObjectAnimator expandY = ObjectAnimator.ofFloat(view, "scaleY", 0.90f, 1f);
            AnimatorSet contract = new AnimatorSet();
            contract.play(contractX).with(contractY);
            contract.setDuration(100);
            AnimatorSet expand = new AnimatorSet();
            expand.play(expandX).with(expandY);
            expand.setDuration(100);

            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    int action = event.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN:
                            contract.start();
                            isContract = true;
                            break;
                        case MotionEvent.ACTION_UP:
                            expand.start();
                            isContract = false;
                            break;
                        case MotionEvent.ACTION_OUTSIDE:
                        case MotionEvent.ACTION_CANCEL:
                            if (isContract) {
                                expand.start();
                                isContract = false;
                            }
                            break;
                        default:
                            break;
                    }
                    return false;
                }
            });
        }
    }

    public static void doContractAnimation(View view) {
        ObjectAnimator contractX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f);
        ObjectAnimator contractY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f);
        AnimatorSet contract = new AnimatorSet();
        contract.play(contractX).with(contractY);
        contract.setDuration(100);
        contract.start();
    }

    public static void doExpandAnimation(View view) {
        ObjectAnimator expandX = ObjectAnimator.ofFloat(view, "scaleX", 0.9f, 1f);
        ObjectAnimator expandY = ObjectAnimator.ofFloat(view, "scaleY", 0.9f, 1f);
        AnimatorSet expand = new AnimatorSet();
        expand.play(expandX).with(expandY);
        expand.setDuration(100);
        expand.start();
    }
}