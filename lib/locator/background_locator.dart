import 'dart:async';
import 'dart:ui';
import 'package:hms_background_location/locator/auto_stop_handler.dart';
import 'package:hms_background_location/locator/keys.dart';
import 'package:hms_background_location/locator/location_dto.dart';
import 'package:hms_background_location/locator/location_settings.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

class BackgroundLocator {
  static const MethodChannel _channel = MethodChannel(Keys.CHANNEL_ID);
  static const MethodChannel _backgroundChannel =
      MethodChannel(Keys.BACKGROUND_CHANNEL_ID);

  static Future<void> initialize() async {
    WidgetsFlutterBinding.ensureInitialized();

    _backgroundChannel.setMethodCallHandler((MethodCall call) async {
      if (Keys.BCM_SEND_LOCATION == call.method) {
        final Map<dynamic, dynamic> args = call.arguments;
        final Function? callback = PluginUtilities.getCallbackFromHandle(
            CallbackHandle.fromRawHandle(args[Keys.ARG_CALLBACK]));
        final LocationDto location =
            LocationDto.fromJson(args[Keys.ARG_LOCATION]);
        callback?.call(location);
      } else if (Keys.BCM_NOTIFICATION_CLICK == call.method) {
        final Map<dynamic, dynamic> args = call.arguments;
        final Function? notificationCallback =
            PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(
                args[Keys.ARG_NOTIFICATION_CALLBACK]));
        notificationCallback?.call();
      } else if (Keys.BCM_INIT == call.method) {
        final Map<dynamic, dynamic> args = call.arguments;
        final Function? initCallback = PluginUtilities.getCallbackFromHandle(
            CallbackHandle.fromRawHandle(args[Keys.ARG_INIT_CALLBACK]));
        Map<dynamic, dynamic> data = args[Keys.ARG_INIT_DATA_CALLBACK];
        initCallback?.call(data);
      } else if (Keys.BCM_DISPOSE == call.method) {
        final Map<dynamic, dynamic> args = call.arguments;
        final Function? disposeCallback = PluginUtilities.getCallbackFromHandle(
            CallbackHandle.fromRawHandle(args[Keys.ARG_DISPOSE_CALLBACK]));
        disposeCallback?.call();
      }
    });
  }

  static Future<void> registerLocationUpdate(
    void Function(LocationDto) callback, {
    void Function(Map<String, dynamic>)? initCallback,
    Map<String, dynamic> initDataCallback = const {},
    void Function()? disposeCallback,
    void Function()? androidNotificationCallback,
    LocationSettings? settings,
  }) async {
    final _settings = settings ?? LocationSettings();
    if (_settings.autoStop == true) {
      WidgetsBinding.instance.addObserver(AutoStopHandler());
    }

    final args = {
      Keys.ARG_CALLBACK:
          PluginUtilities.getCallbackHandle(callback)?.toRawHandle(),
      Keys.ARG_SETTINGS: _settings.toMap()
    };
    if (androidNotificationCallback != null) {
      args[Keys.ARG_NOTIFICATION_CALLBACK] =
          PluginUtilities.getCallbackHandle(androidNotificationCallback)
              ?.toRawHandle();
    }

    if (initCallback != null) {
      args[Keys.ARG_INIT_CALLBACK] =
          PluginUtilities.getCallbackHandle(initCallback)?.toRawHandle();
    }
    if (disposeCallback != null) {
      args[Keys.ARG_DISPOSE_CALLBACK] =
          PluginUtilities.getCallbackHandle(disposeCallback)?.toRawHandle();
    }
    args[Keys.ARG_INIT_DATA_CALLBACK] = initDataCallback;

    if (androidNotificationCallback != null) {
      args[Keys.ARG_NOTIFICATION_CALLBACK] =
          PluginUtilities.getCallbackHandle(androidNotificationCallback)
              ?.toRawHandle();
    }

    await _channel.invokeMethod(
        Keys.METHOD_PLUGIN_REGISTER_LOCATION_UPDATE, args);
  }

  static Future<void> unRegisterLocationUpdate() async {
    await _channel.invokeMethod(Keys.METHOD_PLUGIN_UN_REGISTER_LOCATION_UPDATE);
  }

  static Future<bool?> isRegisterLocationUpdate() async {
    return await _channel
        .invokeMethod<bool>(Keys.METHOD_PLUGIN_IS_REGISTER_LOCATION_UPDATE);
  }
}
