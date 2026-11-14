package com.rpooi3.explorer.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;

import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.preferences.BasePreferenceFragment;
import com.rpooi3.explorer.unit.BackupUtils;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.view.CatToast;

public class Fragment_settings_Backup extends BasePreferenceFragment {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Preference prefBackupAll;
    private Preference prefRestoreAll;
    private Preference prefBackupDb;
    private Preference prefRestoreDb;
    private Preference prefExportBookmarks;
    private Preference prefImportBookmarks;

    // 1. Launcher: Alles Sichern
    private final ActivityResultLauncher<Intent> createFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        startRunningState();
                        executorService.execute(() -> {
                            boolean success = BackupUtils.backupAllData(requireContext(), uri);
                            mainHandler.post(() -> {
                                stopRunningState();
                                CatToast.show(getContext(), success ? R.string.app_done : R.string.app_error, success ? 1 : 2);
                            });
                        });
                    }
                }
            }
    );

    // 2. Launcher: Alles Wiederherstellen
    private final ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        View rootView = requireView();
                        Snackbar snackbar = Snackbar.make(rootView, R.string.hint_database, Snackbar.LENGTH_SHORT);
                        HelperUtils.makeSnackbarRound(snackbar);
                        snackbar.setAction(getString(R.string.app_ok), v -> {
                            startRunningState();
                            executorService.execute(() -> {
                                boolean success = BackupUtils.restoreAllData(requireContext(), uri);
                                mainHandler.post(() -> {
                                    stopRunningState();
                                    if (success) {
                                        CatToast.show(getContext(), getString(R.string.menu_restart), 1);
                                        mainHandler.postDelayed(() -> BackupUtils.triggerAppRestart(requireActivity()), 1000);
                                    } else {
                                        CatToast.show(getContext(), getString(R.string.app_error), 2);
                                    }
                                });
                            });
                        });
                        snackbar.show();
                    }
                }
            }
    );

    // 3. Launcher: Datenbank Sichern
    private final ActivityResultLauncher<Intent> createDbBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        startRunningState();
                        executorService.execute(() -> {
                            boolean success = BackupUtils.backupDatabasesOnly(requireContext(), uri);
                            mainHandler.post(() -> {
                                stopRunningState();
                                CatToast.show(getContext(), success ? R.string.app_done : R.string.app_error, success ? 1 : 2);
                            });
                        });
                    }
                }
            }
    );
    // 4. Launcher: Datenbank Wiederherstellen
    private final ActivityResultLauncher<Intent> openDbRestoreLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        View rootView = requireView();
                        Snackbar snackbar = Snackbar.make(rootView, R.string.hint_database, Snackbar.LENGTH_SHORT);
                        HelperUtils.makeSnackbarRound(snackbar);
                        snackbar.setAction(getString(R.string.app_ok), v -> {
                            startRunningState();
                            executorService.execute(() -> {
                                boolean success = BackupUtils.restoreDatabasesOnly(requireContext(), uri);
                                mainHandler.post(() -> {
                                    stopRunningState();
                                    CatToast.show(getContext(), success ? R.string.menu_restart : R.string.app_error, success ? 1 : 2);
                                });
                            });
                        });
                        snackbar.show();
                    }
                }
            }
    );

    // 5. Launcher: Lesezeichen exportieren
    private final ActivityResultLauncher<Intent> exportBookmarksLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        startRunningState();
                        executorService.execute(() -> {
                            boolean success = BackupUtils.exportBookmarksToUri(requireContext(), uri);
                            mainHandler.post(() -> {
                                stopRunningState();
                                CatToast.show(getContext(), success ? R.string.app_done : R.string.app_error, success ? 1 : 2);
                            });
                        });
                    }
                }
            }
    );

    // 6. Launcher: Lesezeichen importieren
    private final ActivityResultLauncher<Intent> importBookmarksLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        View rootView = requireView();
                        Snackbar snackbar = Snackbar.make(rootView, R.string.hint_database, Snackbar.LENGTH_SHORT);
                        HelperUtils.makeSnackbarRound(snackbar);
                        snackbar.setAction(getString(R.string.app_ok), v -> {
                            startRunningState();
                            executorService.execute(() -> {
                                boolean success = BackupUtils.importBookmarksFromUri(requireContext(), uri);
                                mainHandler.post(() -> {
                                    stopRunningState();
                                    CatToast.show(getContext(), success ? R.string.app_done : R.string.app_error, success ? 1 : 2);
                                });
                            });
                        });
                        snackbar.show();
                    }
                }
            }
    );

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preference_backup, rootKey);

        // Hier werden die Felder zugewiesen, damit wir sie später deaktivieren können
        prefBackupAll = findPreference("pref_backup_all");
        prefRestoreAll = findPreference("pref_restore_all");
        prefBackupDb = findPreference("pref_backup_db");
        prefRestoreDb = findPreference("pref_restore_db");
        prefExportBookmarks = findPreference("pref_export_bookmarks");
        prefImportBookmarks = findPreference("pref_import_bookmarks");

        if (prefBackupAll != null) {
            prefBackupAll.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, "Foss_Explorer_backup_full.zip");
                createFileLauncher.launch(intent);
                return true;
            });
        }

        if (prefRestoreAll != null) {
            prefRestoreAll.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                openFileLauncher.launch(intent);
                return true;
            });
        }

        if (prefBackupDb != null) {
            prefBackupDb.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, "Foss_Explorer_backup_databases.zip");
                createDbBackupLauncher.launch(intent);
                return true;
            });
        }

        if (prefRestoreDb != null) {
            prefRestoreDb.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip");
                openDbRestoreLauncher.launch(intent);
                return true;
            });
        }

        if (prefExportBookmarks != null) {
            prefExportBookmarks.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/html");
                intent.putExtra(Intent.EXTRA_TITLE, "Foss_Explorer_backup_bookmarks.html");
                exportBookmarksLauncher.launch(intent);
                return true;
            });
        }

        if (prefImportBookmarks != null) {
            prefImportBookmarks.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/html");
                importBookmarksLauncher.launch(intent);
                return true;
            });
        }
    }

    private void startRunningState() {
        setPreferencesEnabled(false);
    }

    private void stopRunningState() {
        setPreferencesEnabled(true);
    }

    // Hilfsmethode, um alle Klickfelder gleichzeitig zu sperren oder freizugeben
    private void setPreferencesEnabled(boolean enabled) {
        if (prefBackupAll != null) prefBackupAll.setEnabled(enabled);
        if (prefRestoreAll != null) prefRestoreAll.setEnabled(enabled);
        if (prefBackupDb != null) prefBackupDb.setEnabled(enabled);
        if (prefRestoreDb != null) prefRestoreDb.setEnabled(enabled);
        if (prefExportBookmarks != null) prefExportBookmarks.setEnabled(enabled);
        if (prefImportBookmarks != null) prefImportBookmarks.setEnabled(enabled);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
