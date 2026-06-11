package com.clawd.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.clawd.mobile.service.AppForegroundTracker
import com.clawd.mobile.service.ClawdForegroundService
import com.clawd.mobile.ui.navigation.ClawdNavHost
import com.clawd.mobile.ui.theme.ClawdDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var foregroundTracker: AppForegroundTracker

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i("ClawdMain", "POST_NOTIFICATIONS permission granted")
        } else {
            Log.w("ClawdMain", "POST_NOTIFICATIONS permission denied — event notifications will not appear")
            Toast.makeText(
                this,
                "通知权限被拒绝，任务完成时将无法收到提醒。请在系统设置中开启通知权限。",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        startForegroundService()

        setContent {
            ClawdDarkTheme {
                ClawdNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foregroundTracker.onActivityResumed()
    }

    override fun onPause() {
        super.onPause()
        foregroundTracker.onActivityPaused()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification tap — already in the singleTask activity
    }

    private fun startForegroundService() {
        val intent = Intent(this, ClawdForegroundService::class.java).apply {
            action = ClawdForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Already granted, nothing to do
                    Log.d("ClawdMain", "POST_NOTIFICATIONS already granted")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // User previously denied — show rationale before asking again
                    Toast.makeText(
                        this,
                        "需要通知权限才能在任务完成、出错时提醒你喔～",
                        Toast.LENGTH_LONG
                    ).show()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // First time asking
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
