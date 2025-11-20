package com.sensars.eurostars

import android.app.Application
import android.util.Log
import com.google.firebase.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.crashlytics
import com.sensars.eurostars.data.ble.SensorConnectionManager
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

