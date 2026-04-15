package com.autoai.wiredprojection.util;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

public class LogUtil {
    private static final String TAG = "[WiredProjection]";
    private static final String SEPARATOR = "- ";
    private static final boolean FORCE_DEBUG = true;
    public static final boolean DEBUG_UI = false;

    private LogUtil() {}

    public static void v(@NonNull String tag, @NonNull String msg, @NonNull Object... args) {
        println(Log.VERBOSE, TAG, tag, msg, args);
    }

    public static void d(@NonNull String tag, @NonNull String msg, @NonNull Object... args) {
        println(Log.DEBUG, TAG, tag, msg, args);
    }

    public static void i(@NonNull String tag, @NonNull String msg, @NonNull Object... args) {
        println(Log.INFO, TAG, tag, msg, args);
    }

    public static void w(@NonNull String tag, @NonNull String msg, @NonNull Object... args) {
        println(Log.WARN, TAG, tag, msg, args);
    }

    public static void e(@NonNull String tag, @NonNull String msg, @NonNull Object... args) {
        println(Log.ERROR, TAG, tag, msg, args);
    }

    public static void e(@NonNull String tag, @NonNull String msg, @NonNull Throwable throwable) {
        if (!TextUtils.isEmpty(msg)) {
            println(
                    Log.ERROR,
                    TAG,
                    tag,
                    msg + "\n" + Log.getStackTraceString(throwable)
            );
        }
    }

    private static void println(
            int level,
            @NonNull String tag,
            @NonNull String localTag,
            @NonNull String msg,
            @NonNull Object... args
            ) {
        String formattedMsg;
        boolean hasArgs = args == null || args.length > 0;
        if ((level >= Log.INFO) || Log.isLoggable(localTag, level) || FORCE_DEBUG) {
            formattedMsg = SEPARATOR + localTag;
            if (!TextUtils.isEmpty(msg)) {
                formattedMsg += SEPARATOR + (hasArgs ? String.format(msg, args) : msg);
            }
            Log.println(level, tag, formattedMsg);
        }
    }
}
