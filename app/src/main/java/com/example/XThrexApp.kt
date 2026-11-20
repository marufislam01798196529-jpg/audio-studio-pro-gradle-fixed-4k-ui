package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.local.AppPreferences
import com.example.data.local.XThrexDatabase
import com.example.engine.MemoryGuard
import com.example.engine.SkillCatalog
import com.example.engine.WorkspaceManager
import com.example.security.KeystoreSecretVault
import com.example.workers.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class XThrexApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: XThrexDatabase
        private set
    lateinit var secretVault: KeystoreSecretVault
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var workspaceManager: WorkspaceManager
        private set

    val trimMemoryEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    override fun onCreate() {
        super.onCreate()
        database = XThrexDatabase.getInstance(this)
        secretVault = KeystoreSecretVault(this)
        preferences = AppPreferences(this)
        workspaceManager = WorkspaceManager(this)

        NotificationHelper.ensureChannels(this)

        appScope.launch {
            database.skillDao().insertAllIgnore(SkillCatalog.defaultSkills)
            val current = preferences.settingsFlow.first()
            if (!current.autoLowMemoryApplied) {
                val mem = MemoryGuard.inspect(this@XThrexApp)
                if (mem.isLowRamDevice) {
                    preferences.setLowMemoryMode(true)
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            trimMemoryEvents.tryEmit(level)
        }
    }
}
