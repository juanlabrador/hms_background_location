package co.picap.passenger

import android.content.Intent
import android.os.Bundle
import com.background.hms_gms.BackgroundLocatorPlugin
import com.background.hms_gms.LocatorService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugins.GeneratedPluginRegistrant


class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        FlutterLoader().startInitialization(this)
        super.onCreate(savedInstanceState)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        flutterEngine.plugins.add(BackgroundLocatorPlugin())
        GeneratedPluginRegistrant.registerWith(flutterEngine)
        LocatorService.flutterEngine(flutterEngine)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }
}