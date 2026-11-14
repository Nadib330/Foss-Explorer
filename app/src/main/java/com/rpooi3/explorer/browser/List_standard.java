package com.rpooi3.explorer.browser;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import com.rpooi3.explorer.database.RecordAction;
import com.rpooi3.explorer.unit.HelperUtils;
import com.rpooi3.explorer.database.RecordUnit;

public class List_standard {

    private static final List<String> listStandard = new ArrayList<>();
    private final Context context;

    public List_standard(Context context) {
        this.context = context;
        loadDomains(context);
    }

    private synchronized static void loadDomains(Context context) {
        RecordAction action = new RecordAction(context);
        action.open(false);
        listStandard.clear();
        listStandard.addAll(action.listDomains(RecordUnit.TABLE_STANDARD));
        action.close();
    }

    public boolean isWhite(String url) {
        for (String domain : listStandard) {
            if (url != null && HelperUtils.domain(url).equals(domain)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void addDomain(String domain) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.addDomain(domain, RecordUnit.TABLE_STANDARD);
        action.close();
        listStandard.add(domain);
    }

    public synchronized void removeDomain(String domain) {
        RecordAction action = new RecordAction(context);
        action.open(true);
        action.deleteDomain(domain, RecordUnit.TABLE_STANDARD);
        action.close();
        listStandard.remove(domain);
    }

    public synchronized void clearDomains() {
        RecordAction action = new RecordAction(context);
        action.open(true);
        // 1. Löscht alle Einträge der Tabelle in der Datenbank
        action.clearTable(RecordUnit.TABLE_STANDARD);
        action.close();

        // 2. Leert die Liste im Arbeitsspeicher
        listStandard.clear();
    }

}
