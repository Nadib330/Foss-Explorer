package com.rpooi3.explorer.unit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.rpooi3.explorer.database.Record;
import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.database.RecordUnit;

public class BackupUtils {
    private static final String TAG = "BackupUtils";

    // 18.0.0 beta 3, issue 8: a backup is the backup of one profile.
    //
    // Every method here is a thin way into com.rpooi3.explorer.profile.ProfileBackup, because what
    // a profile has on disk is known there: the databases carry its id in their names, its
    // preferences end in @<id>, and its folder is files/profiles/<id> (files/ for the first one).
    // Before this the backup took the databases of the whole app and the restore dropped them in
    // whatever profile was in front, which is how two profiles could share a history.

    /** Everything the profile in front has: its databases, its preferences and its own folder. */
    public static boolean backupAllData(Context context, Uri targetUri) {
        return com.rpooi3.explorer.profile.ProfileBackup.backup(
                context,
                targetUri,
                com.rpooi3.explorer.profile.Profiles.current(context),
                true);
    }

    /** Puts a backup into the profile in front; no other profile is touched. */
    public static boolean restoreAllData(Context context, Uri sourceUri) {
        final com.rpooi3.explorer.profile.ProfileBackup.Restored restored =
                com.rpooi3.explorer.profile.ProfileBackup.restore(context, sourceUri);
        Log.i(TAG, "Restored " + restored.getFiles() + " files from the backup of the profile "
                + restored.getFromProfile() + " into the profile in front");
        return restored.getFound();
    }

    /** The databases of the profile in front: history, bookmarks, downloads, icons. */
    public static boolean backupDatabasesOnly(Context context, Uri targetUri) {
        return com.rpooi3.explorer.profile.ProfileBackup.backup(
                context,
                targetUri,
                com.rpooi3.explorer.profile.Profiles.current(context),
                false);
    }

    /** The databases of a backup, renamed for the profile in front. */
    public static boolean restoreDatabasesOnly(Context context, Uri sourceUri) {
        return com.rpooi3.explorer.profile.ProfileBackup.restore(context, sourceUri).getFound();
    }

    public static boolean exportBookmarksToUri(Context context, Uri targetUri) {
        RecordAction action = new RecordAction(context);
        try {
            action.open(false);
            List<Record> list = action.listBookmark(context, false, 0);
            action.close();

            try (OutputStream outputStream = context.getContentResolver().openOutputStream(targetUri);
                 OutputStreamWriter osw = new OutputStreamWriter(outputStream);
                 java.io.BufferedWriter writerTxt = new java.io.BufferedWriter(osw)) {

                for (Record record : list) {
                    String line = "<dt><a href=\"" + record.getUrl() + "\">" + record.getTitle() + "</a></dt>";
                    writerTxt.write(line);
                    writerTxt.newLine();
                }
                writerTxt.flush();
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Fehler beim Lesezeichen-Export", e);
            return false;
        } finally {
            try { action.close(); } catch (Exception ignored) {}
        }
    }
    public static boolean importBookmarksFromUri(Context context, Uri sourceUri) {
        List<Record> list = new ArrayList<>();
        RecordAction action = new RecordAction(context);
        // Regex sucht flexibel nach <dt><a href="...">Titel</a>, unabhängig von Groß-/Kleinschreibung und Leerzeichen
        java.util.regex.Pattern linePattern = java.util.regex.Pattern.compile(
                "<dt>\\s*<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             InputStreamReader isr = new InputStreamReader(inputStream);
             BufferedReader reader = new BufferedReader(isr)) {

            if (inputStream == null) return false;
            action.open(true);
            String line;

            while ((line = reader.readLine()) != null) {
                java.util.regex.Matcher matcher = linePattern.matcher(line);

                if (matcher.find()) {
                    String url = matcher.group(1);     // Extrahiert die URL aus dem href-Attribut
                    String title = matcher.group(2);   // Extrahiert den Text zwischen <a> und </a>

                    if (title == null || url == null || title.trim().isEmpty() || url.trim().isEmpty()) {
                        continue;
                    }
                    Record record = new Record();
                    record.setTitle(title.trim());
                    record.setUrl(url.trim());
                    record.setIconColor(1);

                    if (!action.checkUrl(url.trim(), RecordUnit.TABLE_BOOKMARK)) {
                        list.add(record);
                    }
                }
            }
            list.sort(Comparator.comparing(Record::getTitle));
            for (Record record : list) {
                action.addBookmark(record);
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Fehler beim optimierten Lesezeichen-Import", e);
            return false;
        } finally {
            try { action.close(); } catch (Exception ignored) {}
        }
    }
    public static void triggerAppRestart(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            Runtime.getRuntime().exit(0);
        }
    }
    private static void zipFileOrDirectory(File fileToZip, String baseName, ZipOutputStream zipOut) {
        String name = fileToZip.getName();

        if (name.equals("lib") || name.equals("cache") || name.equals("Cache") ||
                name.equals("code_cache") || name.endsWith("-journal") ||
                name.endsWith("-wal") || name.endsWith("-shm") || name.endsWith(".log") ||
                name.endsWith(".html") || name.endsWith(".txt")) {
            return;
        }

        if (fileToZip.isDirectory()) {
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    String entryName = baseName.isEmpty() ? childFile.getName() : baseName + "/" + childFile.getName();
                    zipFileOrDirectory(childFile, entryName, zipOut);
                }
            }
        } else {
            try (FileInputStream fis = new FileInputStream(fileToZip)) {
                ZipEntry zipEntry = new ZipEntry(baseName);
                zipOut.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
                zipOut.closeEntry();
            } catch (IOException e) {
                Log.w(TAG, "Datei übersprungen: " + fileToZip.getAbsolutePath());
            }
        }
    }
    private static void deleteRecursive(Path path) throws IOException {
        if (Files.exists(path)) {
            try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new RuntimeException("Fehler beim Löschen von: " + p, e);
                            }
                        });
            }
        }
    }
}