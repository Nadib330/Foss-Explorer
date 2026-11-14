package com.rpooi3.explorer.view;

import android.content.Context;
import android.content.Intent;
import android.net.http.SslCertificate;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.net.InetAddress;
import java.net.URL;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.unit.HelperUtils;

public class WebsiteInfoDialog {

    private static AlertDialog dialogInstance;
    private static String ipAddressV4 = "...";
    private static String ipAddressV6 = "...";

    public static void show(final Context context, final WebView webView, final String altText, String title, String url) {

        if (dialogInstance != null) {
            dialogInstance.cancel();
        }

        if (webView == null || context == null) return;

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_website_info, null);
        TextView textAltText = dialogView.findViewById(R.id.text_alt_text);
        View spacerAltText = dialogView.findViewById(R.id.spacer_alt_text);

        TextView textTitle = dialogView.findViewById(R.id.text_title);
        TextView textLink = dialogView.findViewById(R.id.text_link);
        final TextView textTechnicalBlock = dialogView.findViewById(R.id.text_technical_block);

        boolean hatAltText = altText != null && !altText.trim().isEmpty();

        // 1. altText verarbeiten
        if (hatAltText) {
            textAltText.setText(altText);
            textAltText.setVisibility(View.VISIBLE);
            spacerAltText.setVisibility(View.VISIBLE);
        } else {
            textAltText.setVisibility(View.GONE);
            spacerAltText.setVisibility(View.GONE);
        }

        // 2. Titel und Link setzen
        textTitle.setText(title);
        textLink.setText(url);

        if (url.startsWith("http")) {
            SpannableString spannable = new SpannableString(url);
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (dialogInstance != null) dialogInstance.dismiss();
                    NinjaWebView.getBrowserController().showOverflow(null, null, 1, HelperUtils.domain(url), url, null, null, 0);
                }
            }, 0, url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textLink.setText(spannable);
            textLink.setMovementMethod(LinkMovementMethod.getInstance());
        }

        // 3. Konstante Sicherheitsdaten auslesen
        final String protocol = url.startsWith("https") ? "HTTPS (Sicher)" : "HTTP (Unsicher)";

        // Zeichensatz über die WebSettings auslesen
        String rawEncoding = webView.getSettings().getDefaultTextEncodingName();
        final String encoding = (rawEncoding != null) ? rawEncoding.toUpperCase() : "UTF-8";

        SslCertificate cert = webView.getCertificate();
        final String issuer;
        final String validUntil;

        if (cert != null) {
            issuer = (cert.getIssuedBy() != null && !cert.getIssuedBy().getOName().isEmpty())
                    ? cert.getIssuedBy().getOName()
                    : (cert.getIssuedBy() != null ? cert.getIssuedBy().getUName() : "Unknown");

            java.util.Date expiryDate = cert.getValidNotAfterDate();
            validUntil = (expiryDate != null)
                    ? android.text.format.DateFormat.getMediumDateFormat(context).format(expiryDate)
                    : "Unknown";
        } else {
            issuer = "No certificate";
            validUntil = "N/A";
        }

        // Dynamische Pixeldichte für die hängende Einrückung berechnen
        final int indentGap = (int) (12 * context.getResources().getDisplayMetrics().density);

        final Runnable updateBlock = () -> {
            java.util.List<String> lineList = new java.util.ArrayList<>();
            lineList.add("Protocol: " + protocol);
            lineList.add("Encoding: " + encoding);
            if (cert != null) {
                lineList.add("Certificate: " + issuer);
                lineList.add("Expires: " + validUntil);
            }
            lineList.add("IPv4: " + ipAddressV4);
            lineList.add("IPv6: " + ipAddressV6);

            android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder();

            for (int i = 0; i < lineList.size(); i++) {
                int start = ssb.length();
                ssb.append(lineList.get(i));
                int end = ssb.length();

                // BulletSpan zeichnet den Punkt rein visuell, sodass er beim Kopieren ignoriert wird
                ssb.setSpan(
                        new android.text.style.BulletSpan(indentGap),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                if (i < lineList.size() - 1) {
                    ssb.append("\n");
                }
            }
            textTechnicalBlock.setText(ssb);
        };

        // 4. Asynchroner IP-Call für IPv4 UND IPv6
        ipAddressV4 = "...";
        ipAddressV6 = "...";

        if (url.startsWith("http")) {
            new Thread(() -> {
                try {
                    String host = new URL(url).getHost();
                    if (host.endsWith(".local") || host.endsWith(".onion")) {
                        ipAddressV4 = "Local / Tor Network";
                        ipAddressV6 = "N/A";
                    } else {
                        InetAddress[] addresses = InetAddress.getAllByName(host);

                        for (InetAddress addr : addresses) {
                            if (addr instanceof java.net.Inet4Address) {
                                assert ipAddressV4 != null;
                                if (ipAddressV4.equals("...")) {
                                    ipAddressV4 = addr.getHostAddress();
                                }
                            } else {
                                if (addr instanceof java.net.Inet6Address) {
                                    assert ipAddressV6 != null;
                                    if (ipAddressV6.equals("...")) {
                                        ipAddressV6 = addr.getHostAddress();
                                    }
                                }
                            }
                        }

                        assert ipAddressV4 != null;
                        if (ipAddressV4.equals("...")) ipAddressV4 = "Not supported";
                        assert ipAddressV6 != null;
                        if (ipAddressV6.equals("...")) ipAddressV6 = "Not supported";
                    }
                } catch (Exception e) {
                    ipAddressV4 = context.getString(R.string.app_error);
                    ipAddressV6 = context.getString(R.string.app_error);
                }
                textTechnicalBlock.post(updateBlock);
            }).start();
        } else {
            ipAddressV4 = "Local Resource";
            ipAddressV6 = "N/A";
        }

        // 5. Layout initial aktualisieren
        updateBlock.run();

        // 6. M3 Dialog erzeugen mit dynamischer Button-Logik
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(dialogView);

        if (hatAltText) {
            builder.setNegativeButton(R.string.app_cancel, null);
            builder.setPositiveButton(R.string.menu_share_altText, (dialog, which) -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, altText);
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.menu_share_altText)));
            });
        } else {
            builder.setPositiveButton(R.string.app_cancel, null);
        }

        dialogInstance = builder.create();

        // ERZWINGEN DER M3 DIVIDER BEI CUSTOM VIEWS
        final NestedScrollView scrollView = dialogView.findViewById(R.id.dialog_scrollview);
        if (scrollView != null) {
            scrollView.setScrollIndicators(
                    View.SCROLL_INDICATOR_TOP | View.SCROLL_INDICATOR_BOTTOM,
                    View.SCROLL_INDICATOR_TOP | View.SCROLL_INDICATOR_BOTTOM
            );
        }

        dialogInstance.show();

        HelperUtils.setupCardView(HelperUtils.getActivity(context), dialogView, title, url, webView, null, dialogInstance, true);
        HelperUtils.setupDialog(context, dialogInstance);
    }
}