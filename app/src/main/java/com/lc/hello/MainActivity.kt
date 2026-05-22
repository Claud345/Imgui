package com.lc.hello

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.lc.hello.service.FloatingWindowService

class MainActivity : ComponentActivity() {

    private val overlayPermissionRequestCode = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()

        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        } else {
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, overlayPermissionRequestCode)
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
