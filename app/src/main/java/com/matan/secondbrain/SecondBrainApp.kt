package com.matan.secondbrain

import android.app.Application
import com.matan.secondbrain.data.AppDatabase
import com.matan.secondbrain.data.Repository
import com.matan.secondbrain.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SecondBrainApp : Application() {
    /** Outlives screens, so saving and enrichment finish even after the share sheet closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this, AppDatabase.create(this), SettingsStore(this), appScope)
    }
}
