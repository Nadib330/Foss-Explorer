package com.rpooi3.explorer.browser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class AdBlock {
    private static final String TAG = "AdBlock";
    private static final String FILE_NAME = "hosts.txt";

    // volatile garantiert threadübergreifende Sichtbarkeit beim Tausch der Referenz
    private static volatile Set<String> hosts = new HashSet<>();
    private static volatile boolean isLoading = false;

    @SuppressLint("ConstantLocale")
    private static final Locale locale = Locale.getDefault();

    // Zentraler SingleThreadExecutor für alle Hintergrund-I/O-Operationen dieser Klasse
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AdBlock(Context context) {
        // Schutz vor Memory Leaks: Globalen ApplicationContext erzwingen
        Context appContext = context.getApplicationContext();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(appContext);

        // Prüfen, ob eine Einstellung auf '_adBlock' endet und aktiv ('true') ist
        boolean isEnabled = false;
        for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
            if (entry.getKey().endsWith("_adBlock") && entry.getValue() instanceof Boolean) {
                if ((Boolean) entry.getValue()) {
                    isEnabled = true;
                    break;
                }
            }
        }

        // Falls AdBlock inaktiv ist, wird das Set atomar geleert und abgebrochen
        if (!isEnabled) {
            hosts = new HashSet<>();
            return;
        }

        File file = new File(appContext.getFilesDir(), FILE_NAME);

        if (!file.exists()) {
            Log.d(TAG, "Hosts file does not exist, copying from assets...");
            try {
                AssetManager manager = appContext.getAssets();
                try (InputStream in = manager.open(FILE_NAME);
                     OutputStream out = new FileOutputStream(file)) {
                    copyFile(in, out);
                }
                triggerHostsDownload(appContext);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy asset file", e);
            }
        }

        Calendar time = Calendar.getInstance();
        // Zeitsteuerung: Bei aktiver Dateneinsparung alle 7 Tage ein Update, sonst täglich
        int days = sp.getBoolean("sp_savedata", false) ? -7 : -1;
        time.add(Calendar.DAY_OF_YEAR, days);

        Date lastModified = new Date(file.lastModified());
        if (lastModified.before(time.getTime()) || getHostsDate(appContext).isEmpty()) {
            triggerHostsDownload(appContext);
        }

        if (hosts.isEmpty() && !isLoading) {
            loadHosts(appContext);
        }
    }

    public static String getHostsDate(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return "";
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Date:")) {
                    // Findet das Datum unabhängig von Kommentarzeichen am Zeilenanfang
                    int index = line.indexOf("Date:") + 5;
                    return line.substring(index).trim();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Error getting hosts date", e);
        }
        return "";
    }
    private static void loadHosts(final Context context) {
        if (isLoading) return;
        isLoading = true;

        executor.execute(() -> {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.exists()) {
                isLoading = false;
                return;
            }

            // Lokales Set befüllen, um unvollständige Zustände im RAM zu verhindern
            Set<String> localHosts = new HashSet<>();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int read = fis.read(bytes);
                if (read != -1) {
                    String dataString = new String(bytes, StandardCharsets.UTF_8);
                    try (BufferedReader reader = new BufferedReader(new StringReader(dataString))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("#") || line.isEmpty() || line.equals("localhost")) {
                                continue;
                            }
                            localHosts.add(line.toLowerCase(locale));
                        }
                    }
                    // Atomarer Austausch: Null Ausfallzeit während des Surfens
                    hosts = localHosts;
                    Log.d(TAG, "AdBlock hosts loaded successfully. Total domains: " + hosts.size());
                }
            } catch (IOException e) {
                Log.w(TAG, "Error loading banner hosts", e);
            } finally {
                isLoading = false;
            }
        });
    }

    private void downloadHosts(final Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        String hostURL = sp.getString("ab_hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts");

        File tempFile = new File(context.getFilesDir(), "temp.txt");
        File outFile = new File(context.getFilesDir(), FILE_NAME);

        try {
            Log.d(TAG, "Starting AdBlock hosts download...");
            URL url = new URL(hostURL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(5000);

            try (InputStream is = connection.getInputStream();
                 BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 8);
                 FileOutputStream outStream = new FileOutputStream(tempFile)) {

                byte[] buff = new byte[1024 * 8];
                int len;
                while ((len = inStream.read(buff)) != -1) {
                    outStream.write(buff, 0, len);
                }
                outStream.flush();
            }

            // Parsen & Bereinigen der Rohdaten
            try (BufferedReader reader = new BufferedReader(new FileReader(tempFile));
                 FileWriter out = new FileWriter(outFile)) {

                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // BEHEBT DEN BUG: Zeilen mit dem Änderungsdatum werden absichtlich behalten!
                    if (line.contains("Date:")) {
                        out.write(line + "\n");
                        continue;
                    }

                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("0.0.0.0 ")) {
                        line = line.substring(8).trim();
                    } else if (line.startsWith("127.0.0.1 ")) {
                        line = line.substring(10).trim();
                    }
                    out.write(line + "\n");
                }
            }

            loadHosts(context);
            Log.w(TAG, "AdBlock hosts file updated on storage.");

        } catch (IOException e) {
            Log.w(TAG, "Error updating AdBlock hosts", e);
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    // Performantes Extrahieren der Root-Domain ohne Objekt-Erzeugungs-Overhead
    private static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "";

        int start = 0;
        if (url.startsWith("https://")) start = 8;
        else if (url.startsWith("http://")) start = 7;

        // Ende der Domain ermitteln (erster Slash nach dem Protokoll)
        int end = url.indexOf('/', start);
        if (end == -1) end = url.length();

        // Eventuelle Port-Zuweisungen (z.B. :8080) sicher abschneiden
        int portIndex = url.indexOf(':', start);
        if (portIndex != -1 && portIndex < end) {
            end = portIndex;
        }

        String domain = url.substring(start, end).toLowerCase(locale);

        // www.-Präfix entfernen für exakten Abgleich im HashSet
        if (domain.startsWith("www.")) {
            domain = domain.substring(4);
        }
        return domain;
    }

    public void triggerHostsDownload(Context context) {
        executor.execute(() -> downloadHosts(context));
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024 * 8];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public boolean isAd(String url) {
        if (url == null || url.isEmpty() || hosts.isEmpty()) {
            return false;
        }
        String domain = extractDomain(url);
        return hosts.contains(domain);
    }
}