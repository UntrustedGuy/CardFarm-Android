package io.github.untrustedguy.cardfarm

import android.app.Application
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.DefaultLogListener

class CardFarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Route JavaSteam's internal logging to logcat for debugging.
        LogManager.addListener(DefaultLogListener())
    }
}
