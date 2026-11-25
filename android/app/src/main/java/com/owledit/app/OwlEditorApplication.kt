package com.owledit.app

import android.app.Application
import android.content.Intent
import com.owledit.core.data.ProfileRepository
import com.owledit.core.data.SettingsStore
import com.owledit.core.nativebridge.RustCoreEngine
import com.owledit.core.root.RootBridge
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class OwlEditorApplication : Application() {

    lateinit var rustCore: RustCoreEngine
        private set
    lateinit var rootBridge: RootBridge
        private set
    lateinit var profileRepo: ProfileRepository
        private set
    lateinit var settingsStore: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        rustCore = RustCoreEngine()
        rootBridge = RootBridge(this)
        profileRepo = ProfileRepository(this)
        settingsStore = SettingsStore(this)

        installCrashHandler()
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = "Thread: ${thread.name}\n\n${sw}"
                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra(CrashActivity.EXTRA_TRACE, trace)
                }
                startActivity(intent)
                Thread.sleep(300)
                exitProcess(10)
            } catch (_: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
