package com.sensoria.app

import android.app.Application
import android.util.Log
import com.google.firebase.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics
import com.sensoria.app.data.ble.SensorConnectionManager
import com.sensoria.app.data.ble.sensoria.SensoriaSdkAdapter
import io.sensoria.sdk.SensoriaSdk

import timber.log.Timber

class EurostarsApp : Application() {

    val sensorConnectionManager: SensorConnectionManager by lazy {
        SensorConnectionManager(this)
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }

        // Initialize Sensoria SDK if enabled
        if (SensoriaSdkAdapter.USE_SENSORIA_SDK) {
            try {
                if (!SensoriaSdk.isInitialized()) {
                    // trace=false, log=true, level=VERBOSE, save=false (don't save SDK logs to files by default)
                    SensoriaSdk.initialize(false, true, Log.VERBOSE, false)
                    Timber.i("Sensoria SDK initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Sensoria SDK")
            }
        }
    }

    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) return

            val composedMessage = buildString {
                tag?.let { append("[$it] ") }
                append(message)
            }

            val crashlytics = Firebase.crashlytics
            crashlytics.log(composedMessage)
            t?.let { crashlytics.recordException(it) }
        }
    }
}

