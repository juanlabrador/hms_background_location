package com.background.hms_gms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.MethodChannel
import com.background.hms_gms.Keys.Companion.ARG_DISPOSE_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_INIT_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_INIT_DATA_CALLBACK
import com.background.hms_gms.Keys.Companion.BACKGROUND_CHANNEL_ID
import com.background.hms_gms.Keys.Companion.BCM_DISPOSE
import com.background.hms_gms.Keys.Companion.BCM_INIT
import com.background.hms_gms.Keys.Companion.CHANNEL_ID
import com.background.hms_gms.Keys.Companion.DISPOSE_CALLBACK_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.INIT_CALLBACK_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.INIT_DATA_CALLBACK_KEY
import com.background.hms_gms.Keys.Companion.NOTIFICATION_ACTION
import io.flutter.embedding.android.FlutterView
import androidx.core.content.ContextCompat
import com.hms.background.hms_background_location.R


class IsolateHolderService : Service() {
    companion object {
        @JvmStatic
        val ACTION_SHUTDOWN = "SHUTDOWN"

        @JvmStatic
        val ACTION_START = "START"

        @JvmStatic
        private val WAKELOCK_TAG = "IsolateHolderService::WAKE_LOCK"

        @JvmStatic
        var backgroundFlutterView: FlutterView? = null

        @JvmStatic
        fun setBackgroundFlutterViewManually(view: FlutterView?) {
            backgroundFlutterView = view
            sendInit()
        }

        @JvmStatic
        var isRunning = false

        @JvmStatic
        var isSendedInit = false

        @JvmStatic
        var instance: Context? = null

        @JvmStatic
        fun sendInit() {
            if (backgroundFlutterView != null && instance != null && !isSendedInit) {
                val context = instance
                val initCallback =
                    BackgroundLocatorPlugin.getCallbackHandle(context!!, INIT_CALLBACK_HANDLE_KEY)
                if (initCallback != null) {
                    val initialDataMap =
                        BackgroundLocatorPlugin.getDataCallback(context, INIT_DATA_CALLBACK_KEY)
                    val backgroundChannel = MethodChannel(
                        backgroundFlutterView!!.binaryMessenger,
                        BACKGROUND_CHANNEL_ID
                    )
                    Handler(context.mainLooper)
                        .post {
                            backgroundChannel.invokeMethod(
                                BCM_INIT,
                                hashMapOf(
                                    ARG_INIT_CALLBACK to initCallback,
                                    ARG_INIT_DATA_CALLBACK to initialDataMap
                                )
                            )
                        }
                }
                isSendedInit = true
            }
        }
    }

    private var notificationChannelName = "picap_notifications"
    private var wakeLockTime = 60 * 60 * 1000L

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun start() {
        if (isRunning) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, notificationChannelName,
                NotificationManager.IMPORTANCE_LOW
            )

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.action = NOTIFICATION_ACTION

        val pendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.driver_mode_enable))
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(ContextCompat.getColor(this, R.color.color_accent))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                setReferenceCounted(false)
                acquire(wakeLockTime)
            }
        }

        instance = this
        sendInit()

        // Starting Service as foreground with a notification prevent service from closing
        startForeground(1, notification)

        isRunning = true
    }

    private fun stop() {
        instance = null
        isRunning = false
        isSendedInit = false
        if (backgroundFlutterView != null) {
            val context = this
            val disposeCallback =
                BackgroundLocatorPlugin.getCallbackHandle(context, DISPOSE_CALLBACK_HANDLE_KEY)
            if (disposeCallback != null && backgroundFlutterView != null) {
                val backgroundChannel = MethodChannel(
                    backgroundFlutterView!!.binaryMessenger,
                    BACKGROUND_CHANNEL_ID
                )
                Handler(context.mainLooper)
                    .post {
                        backgroundChannel.invokeMethod(
                            BCM_DISPOSE,
                            hashMapOf(ARG_DISPOSE_CALLBACK to disposeCallback)
                        )
                    }
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_SHUTDOWN) {
            shutdownHolderService()
        } else if (intent.action == ACTION_START) {
            startHolderService()
        }
        return START_STICKY
    }

    private fun startHolderService() {
        wakeLockTime = 60 * 60 * 1000L
        start()
    }

    private fun shutdownHolderService() {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                if (isHeld) {
                    release()
                }
            }
        }
        stopForeground(true)
        stopSelf()
        stop()
    }
}