package com.rpooi3.explorer.view;

import android.content.Context;
import android.widget.Toast;

/**
 * Short messages at the bottom of the screen.
 *
 * The name and the third parameter are left from upstream's "cat mode", which
 * added a cat emoji to the message. Foss Explorer no longer has that mode;
 * the parameter is accepted and ignored so that the many callers stay as
 * they are.
 */
public class CatToast {

    public static void show(Context context, int stringResId) { show(context, context.getString(stringResId)); }

    public static void show(Context context, int stringResId, int ignored) { show(context, context.getString(stringResId)); }

    public static void show(Context context, String text, int ignored) { show(context, text); }

    public static void show(Context context, String text) {
        Toast.makeText(context, null == text ? "" : text, Toast.LENGTH_SHORT).show();
    }
}
