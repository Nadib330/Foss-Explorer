/*
 * Foss Explorer: "Lock Incognito tabs when you leave Explorer".
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.result.ActivityResultLauncher
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.R

/**
 * Private tabs are locked when the app is left and opened again with the
 * screen lock of the device (fingerprint, face, PIN, pattern or password),
 * as Chrome does it for Incognito tabs.
 */
object IncognitoLock {

    const val KEY = "incognito_lock"

    /** Private tabs are open and the app was left since they were last unlocked. */
    @JvmStatic
    var locked: Boolean = false

    /** Whether the device has a screen lock; without one there is nothing to lock with. */
    @JvmStatic
    fun canLock(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    /** Whether the setting is on and can work. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false) && canLock(context)

    /** The app goes to the background. */
    @JvmStatic
    fun onLeave(context: Context, hasPrivateTabs: Boolean) {
        if (hasPrivateTabs && isEnabled(context)) locked = true
    }

    /**
     * Asks for the screen lock. [onUnlocked] runs if the user passes.
     * Android 11 and later show the system's prompt (fingerprint or face,
     * with the PIN as the other way); older versions show the lock screen's
     * own check, whose result comes back through [launcher].
     */
    @JvmStatic
    fun authenticate(activity: Activity, launcher: ActivityResultLauncher<Intent>, onUnlocked: Runnable) {
        val title = activity.getString(R.string.cr_incognito_unlock)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                    .build()
                    .authenticate(
                        CancellationSignal(),
                        activity.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                                onUnlocked.run()
                            }
                        },
                    )
                return
            } catch (e: RuntimeException) {
                // No prompt on this device: fall through to the lock screen's check.
            }
        }
        val keyguard = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        @Suppress("DEPRECATION")
        val intent = keyguard?.createConfirmDeviceCredentialIntent(title, null)
        // Null means the screen lock was removed in the meantime: nothing protects the tabs any more.
        if (intent == null) onUnlocked.run() else launcher.launch(intent)
    }
}
