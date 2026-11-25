package com.mtsgi.vaunch

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.OnBackPressedCallback
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(PERF_TAG, "MainActivity.onCreate savedState=${savedInstanceState != null} ts=$startedAt")

        registerPlugin(LauncherPlugin::class.java)
        super.onCreate(savedInstanceState)

        // ホームアプリとしての最小挙動: 戻る操作を常に無効化して画面を維持する
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Intentionally ignored on home screen
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(
            PERF_TAG,
            "MainActivity.onNewIntent action=${intent.action} categories=${intent.categories} ts=${SystemClock.elapsedRealtime()}",
        )
    }

    override fun onResume() {
        super.onResume()
        Log.i(PERF_TAG, "MainActivity.onResume ts=${SystemClock.elapsedRealtime()}")
    }

    companion object {
        private const val PERF_TAG = "VaunchPerf"
    }
}
