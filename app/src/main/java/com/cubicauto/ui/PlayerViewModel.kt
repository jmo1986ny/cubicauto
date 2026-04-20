package com.cubicauto.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cubicauto.model.PlaybackState
import com.cubicauto.model.RepeatMode
import com.cubicauto.model.SpotifyConnectionState
import com.cubicauto.spotify.SpotifyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {

    private val _playback    = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _connection  = MutableStateFlow<SpotifyConnectionState>(SpotifyConnectionState.Disconnected)
    val connection: StateFlow<SpotifyConnectionState> = _connection.asStateFlow()

    private val _spectrum    = MutableStateFlow(FloatArray(32) { 0f })
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private val _positionMs  = MutableStateFlow(0L)
    val displayPositionMs: StateFlow<Long> = _positionMs.asStateFlow()

    init {
        SpotifyRepository.playbackState
            .onEach { state ->
                _playback.value   = state
                _positionMs.value = state.positionMs
            }
            .launchIn(viewModelScope)

        SpotifyRepository.connectionState
            .onEach { _connection.value = it }
            .launchIn(viewModelScope)

        // Tick playhead forward while playing
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (_playback.value.isPlaying) {
                    _positionMs.value = minOf(
                        _positionMs.value + 500L,
                        _playback.value.track.durationMs
                    )
                }
            }
        }

        // Animate spectrum bars
        viewModelScope.launch {
            val rng = java.util.Random()
            while (isActive) {
                delay(80)
                if (_playback.value.isPlaying) {
                    _spectrum.value = FloatArray(32) { i ->
                        val center = 16f
                        val dist   = Math.abs(i - center) / center
                        val base   = (1f - dist * 0.55f)
                        (base * (0.25f + rng.nextFloat() * 0.75f)).coerceIn(0.04f, 1f)
                    }
                } else {
                    _spectrum.value = FloatArray(32) { i ->
                        (_spectrum.value[i] * 0.88f).coerceAtLeast(0f)
                    }
                }
            }
        }
    }

    fun connect(context: Context)  = SpotifyRepository.connect(context)
    fun playPause() {
        if (_playback.value.isPlaying) SpotifyRepository.pause()
        else                           SpotifyRepository.resume()
    }
    fun skipNext()      = SpotifyRepository.skipNext()
    fun skipPrevious()  = SpotifyRepository.skipPrevious()
    fun seekTo(fraction: Float) {
        val ms = (fraction * _playback.value.track.durationMs).toLong().coerceAtLeast(0L)
        _positionMs.value = ms
        SpotifyRepository.seekTo(ms)
    }
    fun toggleShuffle() = SpotifyRepository.setShuffle(!_playback.value.shuffle)
    fun cycleRepeat() {
        val next = when (_playback.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        SpotifyRepository.setRepeat(next)
    }

    override fun onCleared() {
        super.onCleared()
        SpotifyRepository.disconnect()
    }
}
