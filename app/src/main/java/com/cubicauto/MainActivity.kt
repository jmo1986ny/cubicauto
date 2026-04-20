package com.cubicauto

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.cubicauto.service.CubicMediaService
import com.cubicauto.ui.CubicPlayerScreen
import com.cubicauto.ui.PlayerViewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var mediaBrowser: MediaBrowserCompat

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.scanLocalMedia(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setContext(this)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, CubicMediaService::class.java),
            ConnectionCallback(), null
        )

        setContent {
            val sysUi = rememberSystemUiController()
            SideEffect {
                sysUi.setSystemBarsColor(Color.Black, darkIcons = false)
            }
            CubicPlayerScreen(viewModel = viewModel)
        }

        requestMediaPermissions()
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
        viewModel.connect(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        MediaControllerCompat.getMediaController(this)
            ?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private fun requestMediaPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            viewModel.scanLocalMedia(applicationContext)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private inner class ConnectionCallback : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val controller = MediaControllerCompat(this@MainActivity, mediaBrowser.sessionToken)
            MediaControllerCompat.setMediaController(this@MainActivity, controller)
            controller.registerCallback(controllerCallback)
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onSessionDestroyed() { mediaBrowser.disconnect() }
    }
}
