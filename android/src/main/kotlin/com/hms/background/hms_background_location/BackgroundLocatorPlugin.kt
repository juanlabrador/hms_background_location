package com.background.hms_gms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import com.background.hms_gms.Keys.Companion.ARG_ACCURACY
import com.background.hms_gms.Keys.Companion.ARG_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_INIT_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_INIT_DATA_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_DISPOSE_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_CALLBACK_DISPATCHER
import com.background.hms_gms.Keys.Companion.ARG_NOTIFICATION_CHANNEL_NAME
import com.background.hms_gms.Keys.Companion.ARG_INTERVAL
import com.background.hms_gms.Keys.Companion.ARG_NOTIFICATION_CALLBACK
import com.background.hms_gms.Keys.Companion.ARG_NOTIFICATION_ICON
import com.background.hms_gms.Keys.Companion.ARG_NOTIFICATION_ICON_COLOR
import com.background.hms_gms.Keys.Companion.ARG_NOTIFICATION_MSG
import com.background.hms_gms.Keys.Companion.ARG_NOTIFICATION_TITLE
import com.background.hms_gms.Keys.Companion.ARG_SETTINGS
import com.background.hms_gms.Keys.Companion.ARG_WAKE_LOCK_TIME
import com.background.hms_gms.Keys.Companion.CALLBACK_DISPATCHER_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.CALLBACK_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.BCM_NOTIFICATION_CLICK
import com.background.hms_gms.Keys.Companion.INIT_CALLBACK_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.INIT_DATA_CALLBACK_KEY
import com.background.hms_gms.Keys.Companion.DISPOSE_CALLBACK_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.BACKGROUND_CHANNEL_ID
import com.background.hms_gms.Keys.Companion.CHANNEL_ID
import com.background.hms_gms.Keys.Companion.METHOD_PLUGIN_INITIALIZE_SERVICE
import com.background.hms_gms.Keys.Companion.METHOD_PLUGIN_IS_REGISTER_LOCATION_UPDATE
import com.background.hms_gms.Keys.Companion.METHOD_PLUGIN_REGISTER_LOCATION_UPDATE
import com.background.hms_gms.Keys.Companion.METHOD_PLUGIN_UN_REGISTER_LOCATION_UPDATE
import com.background.hms_gms.Keys.Companion.NOTIFICATION_ACTION
import com.background.hms_gms.Keys.Companion.NOTIFICATION_CALLBACK_HANDLE_KEY
import com.background.hms_gms.Keys.Companion.SHARED_PREFERENCES_KEY
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.huawei.hms.location.FusedLocationProviderClient
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationServices

class BackgroundLocatorPlugin
    : MethodCallHandler, FlutterPlugin, PluginRegistry.NewIntentListener, ActivityAware {
    private lateinit var locatorClient: FusedLocationProviderClient
    private var context: Context? = null
    private var activity: Activity? = null

    companion object {
        @JvmStatic
        private var channel: MethodChannel? = null

        @JvmStatic
        private fun registerLocator(
            context: Context,
            client: FusedLocationProviderClient,
            args: Map<Any, Any>,
            result: Result?
        ) {

            if (IsolateHolderService.isRunning) {
                Log.d("BackgroundLocatorPlugin", "Locator service is already running")
                return
            }

            val callbackHandle = args[ARG_CALLBACK] as Long
            setCallbackHandle(context, CALLBACK_HANDLE_KEY, callbackHandle)

            val notificationCallback = args[ARG_NOTIFICATION_CALLBACK] as? Long
            setCallbackHandle(context, NOTIFICATION_CALLBACK_HANDLE_KEY, notificationCallback)

            val initCallback = args[ARG_INIT_CALLBACK] as? Long
            setCallbackHandle(context, INIT_CALLBACK_HANDLE_KEY, initCallback)

            val disposeCallback = args[ARG_DISPOSE_CALLBACK] as? Long
            setCallbackHandle(context, DISPOSE_CALLBACK_HANDLE_KEY, disposeCallback)

            val settings = args[ARG_SETTINGS] as Map<*, *>

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_DENIED
            ) {
                val msg = "'registerLocator' requires the ACCESS_FINE_LOCATION permission."
                result?.error(msg, null, null)
            }

            if (args.containsKey(ARG_INIT_DATA_CALLBACK)) {
                val initialDataMap = args[ARG_INIT_DATA_CALLBACK] as Map<*, *>
                setDataCallback(context, INIT_DATA_CALLBACK_KEY, initialDataMap)
            }

            startIsolateService(context, settings)

            client.requestLocationUpdates(
                getLocationRequest(settings),
                getLocatorPendingIndent(context)
            )
        }

        @JvmStatic
        private fun startIsolateService(context: Context, settings: Map<*, *>) {
            val intent = Intent(context, IsolateHolderService::class.java)
            intent.action = IsolateHolderService.ACTION_START
            intent.putExtra(
                ARG_NOTIFICATION_CHANNEL_NAME,
                settings[ARG_NOTIFICATION_CHANNEL_NAME] as String
            )
            intent.putExtra(ARG_NOTIFICATION_TITLE, settings[ARG_NOTIFICATION_TITLE] as String)
            intent.putExtra(ARG_NOTIFICATION_MSG, settings[ARG_NOTIFICATION_MSG] as String)
            intent.putExtra(ARG_NOTIFICATION_ICON, settings[ARG_NOTIFICATION_ICON] as String)
            intent.putExtra(
                ARG_NOTIFICATION_ICON_COLOR,
                settings[ARG_NOTIFICATION_ICON_COLOR] as Long
            )

            if (settings.containsKey(ARG_WAKE_LOCK_TIME)) {
                intent.putExtra(ARG_WAKE_LOCK_TIME, settings[ARG_WAKE_LOCK_TIME] as Int)
            }

            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        private fun stopIsolateService(context: Context) {
            val intent = Intent(context, IsolateHolderService::class.java)
            intent.action = IsolateHolderService.ACTION_SHUTDOWN
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        private fun initializeService(context: Context, args: Map<Any, Any>) {
            val callbackHandle: Long = args[ARG_CALLBACK_DISPATCHER] as Long
            setCallbackDispatcherHandle(context, callbackHandle)
        }

        @JvmStatic
        private fun getLocatorPendingIndent(context: Context): PendingIntent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, LocatorBroadcastReceiver::class.java),
                    PendingIntent.FLAG_MUTABLE
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, LocatorBroadcastReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
        }

        @JvmStatic
        private fun getLocationRequest(settings: Map<*, *>): LocationRequest {
            val locationRequest = LocationRequest()
            val interval: Long = (settings[ARG_INTERVAL] as Int * 1000).toLong()
            locationRequest.interval = interval
            locationRequest.fastestInterval = interval
            locationRequest.maxWaitTime = interval
            val accuracyKey = settings[ARG_ACCURACY] as Int
            locationRequest.priority = getAccuracy(accuracyKey)
            return locationRequest
        }

        @JvmStatic
        private fun getAccuracy(key: Int): Int {
            return when (key) {
                0 -> LocationRequest.PRIORITY_NO_POWER
                1 -> LocationRequest.PRIORITY_LOW_POWER
                2 -> LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                3 -> LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                4 -> LocationRequest.PRIORITY_HIGH_ACCURACY
                else -> LocationRequest.PRIORITY_HIGH_ACCURACY
            }
        }

        @JvmStatic
        private fun removeLocator(
            context: Context,
            client: FusedLocationProviderClient
        ) {
            if (!IsolateHolderService.isRunning) {
                Log.d("BackgroundLocatorPlugin", "Locator service is not running, nothing to stop")
                return
            }
            client.removeLocationUpdates(getLocatorPendingIndent(context))
            stopIsolateService(context)
        }

        @JvmStatic
        private fun isRegisterLocator(result: Result?) {
            if (IsolateHolderService.isRunning) {
                result?.success(true)
            } else {
                result?.success(false)
            }
            return
        }

        @JvmStatic
        private fun setCallbackDispatcherHandle(context: Context, handle: Long) {
            context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .edit()
                .putLong(CALLBACK_DISPATCHER_HANDLE_KEY, handle)
                .apply()
        }

        @JvmStatic
        fun setCallbackHandle(context: Context, key: String, handle: Long?) {
            if (handle == null) {
                context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                    .edit()
                    .remove(key)
                    .apply()
                return
            }

            context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .edit()
                .putLong(key, handle)
                .apply()
        }

        @JvmStatic
        fun setDataCallback(context: Context, key: String, data: Map<*, *>?) {
            if (data == null) {
                context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                    .edit()
                    .remove(key)
                    .apply()
                return
            }
            val dataStr = Gson().toJson(data)
            context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                .edit()
                .putString(key, dataStr)
                .apply()
        }

        @JvmStatic
        fun getCallbackHandle(context: Context, key: String): Long? {
            val sharedPreferences =
                context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
            if (sharedPreferences.contains(key)) return sharedPreferences.getLong(key, 0L)
            return null
        }

        @JvmStatic
        fun getDataCallback(context: Context, key: String): Map<*, *> {
            val initialDataStr =
                context.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                    .getString(key, null)
            val type = object : TypeToken<Map<*, *>>() {}.type
            return Gson().fromJson(initialDataStr, type)
        }

        @JvmStatic
        fun registerAfterBoot(context: Context) {
            val settings = PreferencesManager.getSettings(context)

            val plugin = BackgroundLocatorPlugin()
            plugin.context = context
            plugin.locatorClient = LocationServices.getFusedLocationProviderClient(context)

            initializeService(context, settings)
            registerLocator(
                context,
                plugin.locatorClient,
                settings, null
            )
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            METHOD_PLUGIN_INITIALIZE_SERVICE -> {
                val args: Map<Any, Any> = call.arguments()!!
                PreferencesManager.saveCallbackDispatcher(context!!, args)
                initializeService(context!!, args)
                result.success(true)
            }
            METHOD_PLUGIN_REGISTER_LOCATION_UPDATE -> {
                val args: Map<Any, Any>? = call.arguments()
                if (args != null) {
                    PreferencesManager.saveSettings(context!!, args)
                    registerLocator(
                        context!!,
                        locatorClient,
                        args,
                        result
                    )
                }
            }
            METHOD_PLUGIN_UN_REGISTER_LOCATION_UPDATE -> removeLocator(context!!, locatorClient)
            METHOD_PLUGIN_IS_REGISTER_LOCATION_UPDATE -> isRegisterLocator(result)
            else -> result.notImplemented()
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        onAttachedToEngine(binding.applicationContext, binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}

    private fun onAttachedToEngine(context: Context, messenger: BinaryMessenger) {
        this.context = context
        locatorClient = LocationServices.getFusedLocationProviderClient(context)
        channel = MethodChannel(messenger, CHANNEL_ID)
        channel?.setMethodCallHandler(this)
    }

    override fun onDetachedFromActivity() {}

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {}

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addOnNewIntentListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {}

    override fun onNewIntent(intent: Intent): Boolean {
        if (intent.action != NOTIFICATION_ACTION) {
            return false
        }
        val notificationCallback = getCallbackHandle(activity!!, NOTIFICATION_CALLBACK_HANDLE_KEY)
        if (notificationCallback != null && IsolateHolderService.backgroundFlutterView != null) {
            val backgroundChannel = MethodChannel(
                IsolateHolderService.backgroundFlutterView!!.binaryMessenger,
                BACKGROUND_CHANNEL_ID
            )
            Handler(activity?.mainLooper!!)
                .post {
                    backgroundChannel.invokeMethod(
                        BCM_NOTIFICATION_CLICK,
                        hashMapOf(ARG_NOTIFICATION_CALLBACK to notificationCallback)
                    )
                }
        }
        return true
    }
}
