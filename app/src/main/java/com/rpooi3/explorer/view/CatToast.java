package com.rpooi3.explorer.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Short messages at the bottom of the screen.
 *
 * The name and the third parameter are left from upstream's "cat mode", which
 * added a cat emoji to the message. Foss Explorer no longer has that mode;
 * the parameter is accepted and ignored so that the many callers stay as
 * they are.
 *
 * 17.3.3: a toast can only be shown from the main thread, and the install of an extension
 * answers from a thread of its own. A call from another thread is handed to the main one
 * instead of ending the app with "Can't toast on a thread that has not called Looper.prepare()".
 */
public class CatToast {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void show(Context context, int stringResId) { show(context, context.getString(stringResId)); }

    public static void show(Context context, int stringResId, int ignored) { show(context, context.getString(stringResId)); }

    public static void show(Context context, String text, int ignored) { show(context, text); }

    public static void show(final Context context, final String text) {
        if (null == context) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showNow(context, text);
            return;
        }
        final Context application = context.getApplicationContext();
        MAIN.post(() -> showNow(null != application ? application : context, text));
    }

    private static void showNow(Context context, String text) {
        try {
            Toast.makeText(context, null == text ? "" : text, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            // A toast is a courtesy; it never takes the app down with it.
        }
    }
}
