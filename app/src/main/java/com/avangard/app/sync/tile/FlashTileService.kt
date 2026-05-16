package com.avangard.app.sync.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.avangard.app.MainActivity
import com.avangard.app.R
import com.avangard.app.core.domain.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile that mirrors the active-focus state. Tap launches the
 * pulpit — the tile deliberately doesn't try to recreate Hostage Logic in
 * the shade; it's a shortcut, not a parallel control surface.
 *
 * State source: SessionRepository.observeActiveFocus(). When a row is open,
 * the tile is ACTIVE and labelled accordingly; otherwise INACTIVE.
 */
@AndroidEntryPoint
class FlashTileService : TileService() {

    @Inject lateinit var sessions: SessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watch?.cancel()
        watch = scope.launch {
            sessions.observeActiveFocus().collectLatest { focus ->
                updateTile(active = focus != null)
            }
        }
    }

    override fun onStopListening() {
        watch?.cancel()
        watch = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        // Android 14+ requires a PendingIntent for startActivityAndCollapse.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            if (active) R.string.tile_flash_active else R.string.tile_flash_idle
        )
        tile.updateTile()
    }
}
