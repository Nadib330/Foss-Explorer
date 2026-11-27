package com.rpooi3.explorer.database;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.rpooi3.explorer.R;
import com.rpooi3.explorer.unit.HelperUtils;

public class FaviconHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "favicon.db";
    private static final String TABLE_FAVICON = "Favicon";
    private static final String DOMAIN = "domain";
    private static final String IMAGE = "image";
    private static final String TIMESTAMP = "timestamp";

    private static final String CREATE_TABLE_FAVICON = "CREATE TABLE " + TABLE_FAVICON + "(" +
            DOMAIN + " TEXT," +
            IMAGE + " BLOB," +
            TIMESTAMP + " INTEGER DEFAULT 0);";

    // OPTIMIERT: CachedThreadPool lädt alle Icons gleichzeitig parallel (keine Warteschlange beim Öffnen)
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FaviconHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static byte[] convertBytes(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return new byte[0];
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public static Bitmap getBitmap(byte[] byteimage) {
        if (byteimage == null || byteimage.length == 0) return null;
        return BitmapFactory.decodeByteArray(byteimage, 0, byteimage.length);
    }

    // Unveränderte Funktionalität: Lädt das Icon asynchron im Hintergrund
    public static void setFavicon(Context context, View view, String url, int id, int idImage) {
        final ImageView faviconView = view.findViewById(id);
        if (faviconView == null) return;
        if (!Objects.equals(url, faviconView.getTag())) {
            faviconView.setTag(url);
            faviconView.setImageResource(idImage);
        }

        if (url == null || url.isEmpty()) return;

        executor.execute(() -> {
            try (FaviconHelper faviconHelper = new FaviconHelper(context)) {
                String domain = HelperUtils.domain(url);
                Bitmap bitmap = null;
                boolean isExpired = false;

                try (Cursor cursor = faviconHelper.getReadableDatabase().query(TABLE_FAVICON,
                        new String[]{IMAGE, TIMESTAMP}, DOMAIN + " = ?", new String[]{domain},
                        null, null, null, null)) {

                    if (cursor.moveToFirst()) {
                        long lastUpdated = cursor.getLong(cursor.getColumnIndexOrThrow(TIMESTAMP));

                        if (System.currentTimeMillis() - lastUpdated > 1000 * 60 * 60 * 24) {
                            isExpired = true;
                        } else {
                            bitmap = getBitmap(cursor.getBlob(cursor.getColumnIndexOrThrow(IMAGE)));
                        }
                    }
                }

                if (isExpired) {
                    faviconHelper.deleteFavicon(domain);
                    Log.d("FaviconHelper", "Icon abgelaufen für: " + domain);
                }

                final Bitmap finalBitmap = bitmap;
                mainHandler.post(() -> {
                    if (url.equals(faviconView.getTag())) {
                        if (finalBitmap != null && !finalBitmap.isRecycled()) {
                            faviconView.setImageBitmap(finalBitmap);
                        } else {
                            faviconView.setImageResource(idImage);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("FaviconHelper", "Error loading favicon asynchronously", e);
            }
        });
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_FAVICON);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVICON);
            onCreate(db);
        }
    }

    public synchronized void addFavicon(Context context, String url, Bitmap bitmap) throws SQLiteException {
        if (url == null) return;

        String domain = HelperUtils.domain(url).trim();
        SQLiteDatabase database = this.getWritableDatabase();
        database.delete(TABLE_FAVICON, DOMAIN + " = ?", new String[]{domain});

        if (bitmap == null || bitmap.isRecycled()) {
            if (context != null) {
                bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_image_broken);
            } else {
                return;
            }
        }

        ContentValues values = new ContentValues();
        values.put(DOMAIN, domain);
        values.put(IMAGE, convertBytes(bitmap));
        values.put(TIMESTAMP, System.currentTimeMillis());
        database.insert(TABLE_FAVICON, null, values);
        cleanUpFaviconDB(context);
    }

    public synchronized void deleteFavicon(String domain) throws SQLiteException {
        if (domain == null) return;
        this.getWritableDatabase().delete(TABLE_FAVICON, DOMAIN + " = ?", new String[]{domain.trim()});
    }

    public synchronized Bitmap getFavicon(String url) {
        if (url == null) return null;
        String domain = HelperUtils.domain(url);

        try (Cursor cursor = this.getReadableDatabase().query(TABLE_FAVICON,
                new String[]{IMAGE}, DOMAIN + " = ?", new String[]{domain},
                null, null, null, null)) {

            if (cursor.moveToFirst()) {
                return getBitmap(cursor.getBlob(cursor.getColumnIndexOrThrow(IMAGE)));
            }
        } catch (Exception e) {
            Log.e("FaviconHelper", "Error querying favicon", e);
        }
        return null;
    }

    public synchronized List<String> getAllFaviconDomains() {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = this.getReadableDatabase().query(TABLE_FAVICON,
                new String[]{DOMAIN}, null, null, null, null, null)) {

            if (cursor.moveToFirst()) {
                int domainIdx = cursor.getColumnIndexOrThrow(DOMAIN);
                do {
                    result.add(cursor.getString(domainIdx));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("FaviconHelper", "Error getting all domains", e);
        }
        return result;
    }

    public void cleanUpFaviconDB(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();

        executor.execute(() -> {
            try {
                List<String> faviconDomains = getAllFaviconDomains();
                if (faviconDomains.isEmpty()) return;

                RecordAction action = new RecordAction(appContext);
                Activity activityContext = (context instanceof Activity) ? (Activity) context : null;
                List<Record> allEntries = action.listEntries(activityContext);

                if (allEntries == null) return;

                Set<String> activeDomains = allEntries.stream()
                        .filter(entry -> entry != null && !entry.getDomain().isEmpty())
                        .map(Record::getDomain)
                        .collect(Collectors.toSet());

                for (String faviconDomain : faviconDomains) {
                    if (!activeDomains.contains(faviconDomain)) {
                        deleteFavicon(faviconDomain);
                        Log.d("Favicon delete", faviconDomain);
                    }
                }
            } catch (Exception e) {
                Log.e("FaviconHelper", "Error in cleanup thread", e);
            }
        });
    }
}
