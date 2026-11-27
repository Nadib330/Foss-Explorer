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

    public static boolean backupAllData(Context context, Uri targetUri) {
        File appDataDir = context.getFilesDir().getParentFile();
        if (appDataDir == null || !appDataDir.exists()) {
            return false;
        }
        try {
            android.webkit.CookieManager.getInstance().flush();
        } catch (Exception ignored) {}

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(targetUri);
             ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            zipFileOrDirectory(appDataDir, "", zipOut);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Fehler beim Komplett-Backup", e);
            return false;
        }
    }

    public static boolean restoreAllData(Context context, Uri sourceUri) {
        File appDataDir = context.getFilesDir().getParentFile();
        if (appDataDir == null) return false;

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) return false;

            File[] children = appDataDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!child.getName().equals("lib")) {
                        try {
                            deleteRecursive(child.toPath());
                        } catch (IOException e) {
                            System.err.println("Fehler beim Löschen von " + child.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }

            try (ZipInputStream zipIn = new ZipInputStream(inputStream)) {
                ZipEntry entry = zipIn.getNextEntry();
                boolean dataExtracted = false;

                while (entry != null) {
                    String entryName = entry.getName();
                    if (!entryName.isEmpty()) {
                        File outFile = new File(appDataDir, entryName);
                        if (entry.isDirectory()) {
                            Files.createDirectories(outFile.toPath());
                        } else {
                            File parent = outFile.getParentFile();
                            if (parent != null) {
                                Files.createDirectories(parent.toPath());
                            }
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = zipIn.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            dataExtracted = true;
                        }
                    }
                    zipIn.closeEntry();
                    entry = zipIn.getNextEntry();
                }
                return dataExtracted;
            }
        } catch (IOException e) {
            Log.e(TAG, "Fehler beim Komplett-Restore", e);
            return false;
        }
    }
    public static boolean backupDatabasesOnly(Context context, Uri targetUri) {
        File dbDir = new File(context.getFilesDir().getParentFile(), "databases");
        if (!dbDir.exists()) {
            Log.w(TAG, "Datenbank-Verzeichnis existiert nicht.");
            return false;
        }

        // Filtert alle Dateien, die mit "Ninja4.db" beginnen
        // 15.0.0: history and bookmarks are in places.sqlite; an old Ninja4.db is taken along if one is still there.
        com.rpooi3.explorer.database.places.PlacesDatabase.get(context).getWritableDatabase().rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).close();
        File[] dbFiles = dbDir.listFiles((dir, name) -> name.startsWith("Ninja4.db") || name.startsWith("places.sqlite"));
        if (dbFiles == null || dbFiles.length == 0) {
            Log.w(TAG, "Keine passenden Ninja4.db Dateien zum Sichern gefunden.");
            return false;
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(targetUri);
             ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

            for (File file : dbFiles) {
                // Speichert die Dateien im Zip unter "databases/Dateiname"
                zipFileOrDirectory(file, "databases/" + file.getName(), zipOut);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Fehler beim Datenbank-Backup", e);
            return false;
        }
    }
    public static boolean restoreDatabasesOnly(Context context, Uri sourceUri) {
        File appDataDir = context.getFilesDir().getParentFile();
        File dbDir = new File(appDataDir, "databases");
        if (appDataDir == null) return false;

        if (!dbDir.exists()) {
            if (!dbDir.mkdirs()) {
                Log.e(TAG, "Datenbank-Verzeichnis konnte nicht erstellt werden.");
                return false;
            }
        } else {
            com.rpooi3.explorer.database.places.PlacesDatabase.release();
            File[] oldFiles = dbDir.listFiles((dir, name) -> name.startsWith("Ninja4.db") || name.startsWith("places.sqlite"));
            if (oldFiles != null) {
                for (File oldFile : oldFiles) {
                    if (!oldFile.delete()) {
                        Log.w(TAG, "Alte Datei konnte nicht gelöscht werden: " + oldFile.getName());
                    }
                }
            }
        }

        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri)) {
            if (inputStream == null) return false;

            try (ZipInputStream zipIn = new ZipInputStream(inputStream)) {
                ZipEntry entry = zipIn.getNextEntry();
                boolean dataExtracted = false;
                while (entry != null) {
                    String entryName = entry.getName();
                    if (entryName.startsWith("databases/Ninja4.db") || entryName.startsWith("databases/places.sqlite")) {
                        File outFile = new File(appDataDir, entryName);
                        if (!entry.isDirectory()) {
                            File parent = outFile.getParentFile();
                            if (parent != null) {
                                Files.createDirectories(parent.toPath());
                            }
                            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = zipIn.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            dataExtracted = true;
                        }
                    }
                    zipIn.closeEntry();
                    entry = zipIn.getNextEntry();
                }

                // Wenn der Restore erfolgreich war, passen wir die Version an und löschen Einträge
                // (Only a backup of a version before 15.0.0 has this file; its content is taken over at the next start.)
                if (dataExtracted && context.getDatabasePath("Ninja4.db").isFile()) {
                    try {
                        // 1. ABSOLUTEN PFAD VERWENDEN: Holt den exakten Pfad zur entpackten Ninja4.db
                        File dbFile = context.getDatabasePath("Ninja4.db");
                        android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null);
                        // 2. Alte Tabellen entfernen
                        db.execSQL("DROP TABLE IF EXISTS GRID");
                        db.execSQL("DROP TABLE IF EXISTS JAVASCRIPT");
                        db.execSQL("DROP TABLE IF EXISTS COOKIE");
                        db.delete("REMOTE", null, null);
                        // 4. Version auf 5 zwingen
                        db.setVersion(5);
                        db.close();
                        Log.i(TAG, "Datenbank-Restore erfolgreich: v5 erzwungen und STANDARD geleert.");
                    } catch (Exception dbEx) {
                        Log.e(TAG, "Fehler beim Bereinigen der Datenbank nach dem Restore", dbEx);
                    }
                }
                return dataExtracted;
            }
        } catch (IOException e) {
            Log.e(TAG, "Fehler beim Datenbank-Restore", e);
            return false;
        }
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