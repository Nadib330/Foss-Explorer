/*
 * Explorer: the warning for a certificate that cannot be trusted.
 * Own code, GPL-3.0.
 *
 * 18.0.0 beta 4 replaced the small dialog of FOSS Browser with a page in the shape of Firefox's
 * "Warning: Potential Security Risk Ahead". That page was handed to the WebView as a page of its
 * own, which was a mistake: the answer to the certificate stays pending while such a page is up, so
 * the WebView started the same load again and again — the warning reloaded by itself, and the back
 * arrow, which went back to the very same address, landed on the warning again. 18.0.0 beta 5
 * makes the warning a screen of Explorer's own (`view_security_warning.xml`), drawn over the page:
 * nothing is loaded while it is up, nothing reloads behind it, and the back arrow leaves it.
 */
package com.rpooi3.explorer.browser

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.widget.TextView
import com.rpooi3.explorer.R

object SecurityWarning {

    private const val TAG = "SecurityWarning"

    /**
     * The code Firefox would name: the errors of the system are NSS's in Firefox, so the warning
     * reads the same as the one of the browser whose shape it has.
     */
    @JvmStatic
    fun errorCode(primaryError: Int): String = when (primaryError) {
        SslError.SSL_EXPIRED -> "SEC_ERROR_EXPIRED_CERTIFICATE"
        SslError.SSL_IDMISMATCH -> "SSL_ERROR_BAD_CERT_DOMAIN"
        SslError.SSL_NOTYETVALID -> "SEC_ERROR_EXPIRED_CERTIFICATE"
        SslError.SSL_DATE_INVALID -> "SEC_ERROR_EXPIRED_CERTIFICATE"
        SslError.SSL_UNTRUSTED -> "SEC_ERROR_UNKNOWN_ISSUER"
        else -> "SEC_ERROR_UNKNOWN_ISSUER"
    }

    /** One paragraph saying what is wrong with the certificate, in the words Firefox uses. */
    @JvmStatic
    fun reason(context: Context, primaryError: Int): String = when (primaryError) {
        SslError.SSL_EXPIRED -> context.getString(R.string.ssl_reason_expired)
        SslError.SSL_IDMISMATCH -> context.getString(R.string.ssl_reason_mismatch)
        SslError.SSL_NOTYETVALID, SslError.SSL_DATE_INVALID -> context.getString(R.string.ssl_reason_date)
        SslError.SSL_UNTRUSTED -> context.getString(R.string.ssl_reason_untrusted)
        else -> context.getString(R.string.ssl_reason_untrusted)
    }

    /** The name of the site the warning is about, without `www.`. */
    @JvmStatic
    fun hostOf(url: String?): String = try {
        val host = Uri.parse(url.orEmpty()).host
        if (host.isNullOrEmpty()) url.orEmpty() else host.removePrefix("www.")
    } catch (e: Exception) {
        url.orEmpty()
    }

    /**
     * The warning, ready to be put over the page.
     *
     * @param onGoBack "Go Back": nothing of the site is loaded
     * @param onAccept "Accept the Risk and Continue": the site is loaded after all
     */
    @JvmStatic
    fun view(
        context: Context,
        url: String?,
        primaryError: Int,
        onGoBack: Runnable,
        onAccept: Runnable,
    ): View {
        val root = View.inflate(context, R.layout.view_security_warning, null)
        val host = hostOf(url)
        root.findViewById<TextView>(R.id.warning_body).text = context.getString(R.string.ssl_body, host)
        root.findViewById<TextView>(R.id.warning_reason).text = reason(context, primaryError)
        root.findViewById<TextView>(R.id.warning_code).text =
            context.getString(R.string.ssl_error_code) + ": " + errorCode(primaryError)

        val more = root.findViewById<View>(R.id.warning_more)
        root.findViewById<View>(R.id.warning_advanced).setOnClickListener {
            more.visibility = if (more.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.findViewById<View>(R.id.warning_go_back).setOnClickListener { onGoBack.run() }
        root.findViewById<View>(R.id.warning_accept).setOnClickListener { onAccept.run() }
        return root
    }
}
