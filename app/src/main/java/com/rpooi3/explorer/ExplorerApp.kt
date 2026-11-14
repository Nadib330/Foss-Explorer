package com.rpooi3.explorer

import android.app.Application
import androidx.preference.PreferenceManager
import com.rpooi3.explorer.home.HomePage
import com.rpooi3.explorer.theme.ExplorerTheme

/**
 * Application entry point of Foss Explorer.
 *
 * The light/dark preference has to be in place before the first activity is
 * created, otherwise that activity would be inflated with the wrong resources.
 * Settings carried over from an older version are brought up to date here as
 * well, before anything reads them.
 */
class ExplorerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ExplorerTheme.applyNightMode(this)
        HomePage.migrate(PreferenceManager.getDefaultSharedPreferences(this))
    }
}
