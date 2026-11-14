package com.rpooi3.explorer.database;

import com.rpooi3.explorer.unit.HelperUtils;

public class Record {

    private long iconColor;
    private String title;
    private String url;
    private long time;

    // Leerer Konstruktor ruft den Hauptkonstruktor mit sicheren Standardwerten auf
    public Record() {
        this("", "", 0L, 0L);
    }

    public Record(String title, String url, long time, long iconColor) {
        // Falls null übergeben wird, direkt in leere Strings umwandeln (NPE-Schutz)
        this.title = title != null ? title : "";
        this.url = url != null ? url : "";
        this.time = time;
        this.iconColor = iconColor;
    }

    public long getIconColor() {
        return iconColor;
    }

    public void setIconColor(long iconColor) {
        this.iconColor = iconColor;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String getUrl() {
        return url;
    }

    public String getDomain() {
        if (url == null || url.isEmpty()) return "";
        return HelperUtils.domain(url);
    }

    public void setUrl(String url) {
        this.url = url != null ? url : "";
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}