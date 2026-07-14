package io.github.untrustedguy.cardfarm

import android.app.Application
import android.util.Log
import `in`.dragonbra.javasteam.util.log.DefaultLogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class CardFarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MUST run before any Steam/crypto usage.
        //
        // JavaSteam's CryptoHelper looks up the security provider named "BC" and,
        // on Android, requires org.bouncycastle.jce.provider.BouncyCastleProvider
        // to exist on the classpath. Android only ships a stripped-down BC under
        // com.android.org.bouncycastle registered as "BC", which lacks the ciphers
        // JavaSteam needs. We remove that stub and install the full BouncyCastle
        // provider (from the bundled bcprov-jdk18on) at the highest priority, so
        // CryptoHelper's static initializer succeeds and all crypto resolves to it.
        try {
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (t: Throwable) {
            Log.e("CardFarmApp", "Failed to install BouncyCastle provider", t)
        }

        // Route JavaSteam's internal logging to logcat for debugging.
        LogManager.addListener(DefaultLogListener())
    }
}
