package com.mtsgi.vaunch

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@CapacitorPlugin(name = "Launcher")
class LauncherPlugin : Plugin() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "vaunch-launcher-worker").apply { isDaemon = true }
    }

    override fun handleOnDestroy() {
        worker.shutdownNow()
        super.handleOnDestroy()
    }

    @PluginMethod
    fun getApps(call: PluginCall) {
        worker.execute {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                if (launcherApps == null) {
                    call.reject("LauncherApps service is unavailable.", "SERVICE_UNAVAILABLE")
                    return@execute
                }

                val selfPackage = context.packageName
                val userHandle = Process.myUserHandle()
                val activityList = launcherApps.getActivityList(null, userHandle)
                val packageManager = context.packageManager

                val appsArray = JSArray()
                var webApkCount = 0

                for (info in activityList) {
                    val pkg = info.applicationInfo.packageName
                    if (pkg == selfPackage) {
                        continue
                    }

                    val componentName = info.componentName.flattenToString()
                    val label = info.label?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                    val isWebApk = isLikelyWebApkPackage(pkg)

                    val appObj = JSObject().apply {
                        put("packageName", pkg)
                        put("componentName", componentName)
                        put("label", label)
                        // 本 PoC ではアイコン抽出・表示を省略する
                        put("icon", JSONObject.NULL)
                        put("isWebApk", isWebApk)
                    }

                    if (isWebApk) {
                        webApkCount += 1
                        val webApkObj = inspectWebApkMetadata(packageManager, pkg)
                        appObj.put("webApk", webApkObj)
                    }

                    appsArray.put(appObj)
                }

                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                Log.i(
                    PERF_TAG,
                    "native getApps count=${appsArray.length()} webApkCount=$webApkCount elapsedMs=$elapsedMs",
                )

                val result = JSObject().apply {
                    put("apps", appsArray)
                }
                call.resolve(result)
            } catch (e: Exception) {
                Log.e(PERF_TAG, "native getApps failed", e)
                call.reject(
                    "Failed to enumerate launchable apps: ${e.message ?: e.javaClass.simpleName}",
                    "QUERY_FAILED",
                    e,
                )
            }
        }
    }

    @PluginMethod
    fun launchApp(call: PluginCall) {
        val rawComponent = call.getString("componentName")?.trim()
        if (rawComponent.isNullOrEmpty()) {
            call.reject("componentName is required.", "INVALID_COMPONENT")
            return
        }

        val component = ComponentName.unflattenFromString(rawComponent)
        if (component == null) {
            call.reject("Invalid componentName: $rawComponent", "INVALID_COMPONENT")
            return
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps == null) {
            call.reject("LauncherApps service is unavailable.", "SERVICE_UNAVAILABLE")
            return
        }

        val userHandle = Process.myUserHandle()
        try {
            val candidates = launcherApps.getActivityList(component.packageName, userHandle)
            val exists = candidates.any { it.componentName == component }
            if (!exists) {
                call.reject("Target app component was not found: $rawComponent", "APP_NOT_FOUND")
                return
            }

            launcherApps.startMainActivity(component, userHandle, null, null)
            call.resolve()
        } catch (e: ActivityNotFoundException) {
            call.reject("Activity not found: ${e.message ?: rawComponent}", "APP_NOT_FOUND", e)
        } catch (e: SecurityException) {
            call.reject("Permission denied to start app: ${e.message ?: rawComponent}", "SECURITY_ERROR", e)
        } catch (e: IllegalArgumentException) {
            call.reject("Invalid launch component: ${e.message ?: rawComponent}", "INVALID_COMPONENT", e)
        } catch (e: Exception) {
            call.reject("Failed to start app: ${e.message ?: e.javaClass.simpleName}", "LAUNCH_FAILED", e)
        }
    }

    @PluginMethod
    fun isDefaultHome(call: PluginCall) {
        try {
            val result = JSObject().apply {
                put("value", checkIsDefaultHome())
            }
            call.resolve(result)
        } catch (e: Exception) {
            call.reject(
                "Failed to check default home status: ${e.message ?: e.javaClass.simpleName}",
                "CHECK_HOME_FAILED",
                e,
            )
        }
    }

    @PluginMethod
    fun requestDefaultHome(call: PluginCall) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            call.reject("RoleManager.ROLE_HOME requires Android 10 or higher.", "ROLE_UNAVAILABLE")
            return
        }

        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            call.reject("RoleManager.ROLE_HOME is not available on this device.", "ROLE_UNAVAILABLE")
            return
        }

        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            call.resolve()
            return
        }

        try {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            startActivityForResult(call, intent, "handleHomeRoleResult")
        } catch (e: Exception) {
            call.reject(
                "Failed to launch home role request: ${e.message ?: e.javaClass.simpleName}",
                "ROLE_REQUEST_FAILED",
                e,
            )
        }
    }

    @ActivityCallback
    private fun handleHomeRoleResult(call: PluginCall?, result: ActivityResult) {
        Log.i(PERF_TAG, "requestDefaultHome callback resultCode=${result.resultCode}")
        // 承認・キャンセルいずれでも完了として返し、JS 側で isDefaultHome() を再取得する
        call?.resolve()
    }

    private fun checkIsDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    private fun isLikelyWebApkPackage(packageName: String): Boolean {
        return packageName.lowercase(Locale.ROOT).startsWith(WEBAPK_PACKAGE_PREFIX)
    }

    private fun inspectWebApkMetadata(packageManager: PackageManager, packageName: String): JSObject {
        val webApkObj = JSObject()
        val metadataObj = JSObject()

        try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }

            val metaData: Bundle? = appInfo.metaData
            if (metaData == null || metaData.isEmpty) {
                Log.i(WEBAPK_TAG, "package=$packageName metadata=empty")
                webApkObj.put("metadata", metadataObj)
                return webApkObj
            }

            val keySummaries = mutableListOf<String>()
            for (key in metaData.keySet().sorted()) {
                @Suppress("DEPRECATION")
                val rawValue = metaData.get(key)
                val typeName = rawValue?.javaClass?.simpleName ?: "null"
                val valueStr = rawValue?.toString() ?: "null"

                val entryObj = JSObject().apply {
                    put("type", typeName)
                    put("value", valueStr)
                }
                metadataObj.put(key, entryObj)
                keySummaries.add("$key($typeName)=$valueStr")
            }

            val startUrl = readMetadataString(metaData, META_KEY_START_URL)
            val scope = readMetadataString(metaData, META_KEY_SCOPE)
            val themeColor = readMetadataString(metaData, META_KEY_THEME_COLOR)

            if (startUrl != null) {
                webApkObj.put("startUrl", startUrl)
            }
            if (scope != null) {
                webApkObj.put("scope", scope)
            }
            if (themeColor != null) {
                webApkObj.put("themeColor", themeColor)
            }
            webApkObj.put("metadata", metadataObj)

            Log.i(
                WEBAPK_TAG,
                "package=$packageName count=${keySummaries.size} startUrl=$startUrl scope=$scope themeColor=$themeColor entries=[${keySummaries.joinToString("; ")}]",
            )
        } catch (e: Exception) {
            Log.w(WEBAPK_TAG, "package=$packageName metadata_error=${e.javaClass.simpleName}: ${e.message}")
            webApkObj.put("metadata", metadataObj)
        }

        return webApkObj
    }

    private fun readMetadataString(bundle: Bundle, key: String): String? {
        if (!bundle.containsKey(key)) {
            return null
        }
        @Suppress("DEPRECATION")
        val value = bundle.get(key) ?: return null
        return value.toString().takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PERF_TAG = "VaunchPerf"
        private const val WEBAPK_TAG = "VaunchWebApk"
        private const val WEBAPK_PACKAGE_PREFIX = "org.chromium.webapk."
        private const val META_KEY_START_URL = "org.chromium.webapk.shell_apk.startUrl"
        private const val META_KEY_SCOPE = "org.chromium.webapk.shell_apk.scope"
        private const val META_KEY_THEME_COLOR = "org.chromium.webapk.shell_apk.themeColor"
    }
}
