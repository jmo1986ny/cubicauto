package com.cubicauto

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.cubicauto.service.CubicMediaService
import com.cubicauto.ui.CubicPlayerScreen
import com.cubicauto.ui.PlayerViewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, CubicMediaService::class.java),
            ConnectionCallback(),
            null
        )

        setContent {
            val sysUi = rememberSystemUiController()
            SideEffect {
                sysUi.setSystemBarsColor(Color(0xFF0A0A0C), darkIcons = false)
            }
            CubicPlayerScreen(viewModel = viewModel)
        }
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
