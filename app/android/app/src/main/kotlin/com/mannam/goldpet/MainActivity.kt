package com.mannam.goldpet

import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterFragmentActivity() {
    private val CHANNEL = "com.mannam.goldpet/file_provider"
    private val DEEP_LINK_CHANNEL = "com.mannam.goldpet/deep_link"
    private var walkNotificationHelper: WalkNotificationHelper? = null
    private var deepLinkChannel: MethodChannel? = null
    private var initialDeepLink: String? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // FileProvider MethodChannel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "getContentUri") {
                val filePath = call.argument<String>("filePath")
                if (filePath != null) {
                    try {
                        val file = File(filePath)
                        val contentUri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.fileprovider",
                            file
                        )
                        result.success(contentUri.toString())
                    } catch (e: Exception) {
                        result.error("FILE_PROVIDER_ERROR", e.message, null)
                    }
                } else {
                    result.error("INVALID_PATH", "File path is null", null)
                }
            } else {
                result.notImplemented()
            }
        }

        // Walk notification MethodChannel (custom RemoteViews widget + foreground service)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.mannam.goldpet/walk_notification").setMethodCallHandler { call, result ->
            when (call.method) {
                "startForeground" -> {
                    if (walkNotificationHelper == null) {
                        walkNotificationHelper = WalkNotificationHelper(this)
                    }
                    val serviceIntent = Intent(this, WalkForegroundService::class.java)
                    ContextCompat.startForegroundService(this, serviceIntent)
                    result.success(null)
                }
                "show" -> {
                    if (walkNotificationHelper == null) {
                        walkNotificationHelper = WalkNotificationHelper(this)
                    }
                    val time = call.argument<String>("time") ?: "00:00:00"
                    val distance = call.argument<String>("distance") ?: "0.00"
                    val duration = call.argument<String>("duration") ?: "00:00"
                    val calories = call.argument<String>("calories") ?: "0"
                    walkNotificationHelper!!.show(time, distance, duration, calories)
                    result.success(null)
                }
                "cancel" -> {
                    walkNotificationHelper?.cancel()
                    stopService(Intent(this, WalkForegroundService::class.java))
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }

        // Deep link MethodChannel
        deepLinkChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DEEP_LINK_CHANNEL).also { channel ->
            channel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "getInitialLink" -> {
                        val link = initialDeepLink
                        initialDeepLink = null
                        result.success(link)
                    }
                    else -> result.notImplemented()
                }
            }
        }

        // Cold start: check if launched via deep link
        handleDeepLinkIntent(intent, isColdStart = true)
    }

    private fun isOurDeepLink(uri: android.net.Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme == "goldpet") return true
        if (uri.scheme == "https") {
            val host = uri.host ?: return false
            return host == "app.mannamsquare.com" || host == "app.goldpet.com"
        }
        return false
    }

    override fun onNewIntent(intent: Intent) {
        // For our deep links, skip FlutterActivity.onNewIntent() which
        // triggers Flutter's navigation system and pushes '/' on the Navigator,
        // covering the current screen. We handle these via MethodChannel only.
        setIntent(intent)
        if (!isOurDeepLink(intent.data)) {
            super.onNewIntent(intent)
        }
        handleDeepLinkIntent(intent, isColdStart = false)
    }

    private fun handleDeepLinkIntent(intent: Intent, isColdStart: Boolean) {
        val uri = intent.data ?: return
        if (!isOurDeepLink(uri)) return

        // For goldpet:// scheme, only handle walk/spot hosts (not Naver OAuth callbacks)
        if (uri.scheme == "goldpet") {
            val host = uri.host ?: return
            if (host != "walk" && host != "spot") return
        }

        val uriString = uri.toString()
        // Always buffer as fallback — handles Activity recreation where
        // onNewIntent() fires before Dart MethodCallHandler is registered.
        initialDeepLink = uriString

        if (!isColdStart) {
            // Also try immediate push for truly warm cases (Dart ready)
            deepLinkChannel?.invokeMethod("onDeepLink", uriString)
        }
    }
}
