package com.background.hms_gms

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.Handler
import androidx.core.app.JobIntentService
import com.background.hms_gms.Keys.Companion.ARG_ACCURACY
import com.background.hms_gms.Keys.Companion.ARG_ALTITUDE
import com.background.hms_gms.Keys.Companion.ARG_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_HEADING
import com.background.hms_gms.Keys.Companion.ARG_IS_MOCKED
import com.background.hms_gms.Keys.Companion.ARG_LATITUDE
import com.background.hms_gms.Keys.Companion.ARG_LOCATION
import com.background.hms_gms.Keys.Companion.ARG_LONGITUDE
import com.background.hms_gms.Keys.Companion.ARG_SPEED
import com.background.hms_gms.Keys.Companion.ARG_SPEED_ACCURACY
import com.background.hms_gms.Keys.Companion.ARG_TIME
import com.background.hms_gms.Keys.Companion.BACKGROUND_CHANNEL_ID
import com.background.hms_gms.Keys.Companion.BCM_SEND_LOCATION
import com.background.hms_gms.Keys.Companion.CALLBACK_HANDLE_KEY
import com.huawei.hms.location.LocationResult
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import okhttp3.FormBody
import io.flutter.embedding.android.FlutterView
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class LocatorService : MethodChannel.MethodCallHandler, JobIntentService() {
    private var backgroundChannel: MethodChannel? = null
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    companion object {
        @JvmStatic
        val SHARED_PREFERENCES_FLUTTER = "FlutterSharedPreferences"

        @JvmStatic
        private val JOB_ID = 1000

        @JvmStatic
        private var backgroundFlutterView: FlutterView? = null

        @JvmStatic
        private var sBackgroundFlutterEngine: FlutterEngine? = null

        @JvmStatic
        private val serviceStarted = AtomicBoolean(true)

        @JvmStatic
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, LocatorService::class.java, JOB_ID, work)
        }

        @JvmStatic
        fun flutterEngine(flutterEngine: FlutterEngine) {
            sBackgroundFlutterEngine = flutterEngine
        }

        private const val BOOKING_IN_QUEUE_DETAILS = 6
        private const val BOOKING_GET_INFORMATION_STATUS = 1
        private const val NEW_BOOKING_INVITATION = 0
    }

    override fun onCreate() {
        super.onCreate()
        startLocatorService(this)
        initPreference()
    }

    private fun initPreference() {
        preferences = getSharedPreferences(SHARED_PREFERENCES_FLUTTER, Context.MODE_PRIVATE)
    }

    private fun startLocatorService(context: Context) {
        this.context = context
        if (sBackgroundFlutterEngine != null) {
            synchronized(serviceStarted) {
                IsolateHolderService.setBackgroundFlutterViewManually(backgroundFlutterView)
                backgroundChannel = sBackgroundFlutterEngine?.dartExecutor?.binaryMessenger?.let {
                    MethodChannel(
                        it,
                        BACKGROUND_CHANNEL_ID
                    )
                }
                backgroundChannel?.setMethodCallHandler(this)
                serviceStarted.set(false)
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {}

    override fun onHandleWork(intent: Intent) {
        if (LocationResult.hasResult(intent)) {
            val location = LocationResult.extractResult(intent)?.lastLocation
            location?.let {
                sendLocation(it)
                sendLocationEventChannel(it)
            }
        }
    }

    private fun sendLocation(location: Location) {
        val formBody = FormBody.Builder()
            .add("lat", location.latitude.toString())
            .add("lon", location.longitude.toString())
            .add("course", location.bearing.toString())
            .add("mock_location", location.isFromMockProvider.toString())
            .add("accuracy", location.accuracy.toString())

        println(location.latitude.toString())
        println(formBody)
    }

    private fun sendLocationEventChannel(location: Location) {
        val speedAccuracy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.speedAccuracyMetersPerSecond
        } else {
            0f
        }

        val isMocked = location.isFromMockProvider

        val locationMap: HashMap<Any, Any> =
            hashMapOf(
                ARG_IS_MOCKED to isMocked,
                ARG_LATITUDE to location.latitude,
                ARG_LONGITUDE to location.longitude,
                ARG_ACCURACY to location.accuracy,
                ARG_ALTITUDE to location.altitude,
                ARG_SPEED to location.speed,
                ARG_SPEED_ACCURACY to speedAccuracy,
                ARG_HEADING to location.bearing,
                ARG_TIME to location.time.toDouble()
            )

        val callback =
            BackgroundLocatorPlugin.getCallbackHandle(context, CALLBACK_HANDLE_KEY) as Long

        val result: HashMap<Any, Any> = hashMapOf(
            ARG_CALLBACK to callback,
            ARG_LOCATION to locationMap
        )

        Handler(mainLooper).post {
            backgroundChannel?.invokeMethod(BCM_SEND_LOCATION, result)
        }
    }
}