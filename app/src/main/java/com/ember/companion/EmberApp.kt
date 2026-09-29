package com.ember.companion

import android.app.Application
import com.ember.companion.core.Diag
import com.ember.companion.data.AppContainer

/**
 * Ember ships with no analytics, no ads, and no crash reporting. The only
 * long-lived state is the local database and the user's own settings.
 */
class EmberApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Installed before anything else so a failure during startup is still captured.
        Diag.install(this)
        Diag.log("Ember ${BuildConfig.VERSION_NAME} started, api ${android.os.Build.VERSION.SDK_INT}")
        container = AppContainer(this)
    }
}
