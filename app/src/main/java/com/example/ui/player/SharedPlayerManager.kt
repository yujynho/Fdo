package com.example.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.components.PlayerFactory

class SharedPlayerManager(private val context: Context) {
    private var _exoPlayer: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    fun getPlayer(): ExoPlayer {
        val existing = _exoPlayer
        if (existing != null) return existing

        val newPlayer = PlayerFactory.createPlayer(context)
        _exoPlayer = newPlayer
        return newPlayer
    }

    fun stopPlayer() {
        _exoPlayer?.stop()
        _exoPlayer?.clearMediaItems()
    }

    fun release() {
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
