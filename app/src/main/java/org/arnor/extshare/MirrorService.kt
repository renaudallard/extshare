package org.arnor.extshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class MirrorService : Service(), DisplayManager.DisplayListener {

    private val channelId = "mirror_channel"
    private val notificationId = 42

    private lateinit var displayManager: DisplayManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MirrorPresentation? = null
    private var targetDisplayId: Int? = null

    override fun onCreate() {
        super.onCreate()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(this, null)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!ProjectionStore.hasGrant(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning.get()) {
            isRunning.set(true)
        }
        val dualMsg = DualScreenHelper.attemptEnableDualScreen()
        Log.d(TAG, "Dual screen toggle attempt: $dualMsg")
        startForeground(notificationId, buildNotification())
        startProjection()
        updateTileState()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(this)
        releaseAll()
        isRunning.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateTileState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProjection() {
        if (mediaProjection != null) return
        val grant = ProjectionStore.loadGrant(this)
        val manager = getSystemService(MediaProjectionManager::class.java)
        if (grant == null || manager == null) {
            stopSelf()
            return
        }
        mediaProjection = manager.getMediaProjection(grant.resultCode, grant.dataIntent)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, null)
        startPresentation()
    }

    private fun startPresentation() {
        val display = chooseExternalDisplay()
        if (display == null) {
            Log.w(TAG, "No external display found; waiting for one to appear.")
            targetDisplayId = null
            return
        }
        targetDisplayId = display.displayId
        presentation?.dismiss()
        presentation = MirrorPresentation(
            context = this,
            display = display,
            onSurfaceReady = { holder -> attachVirtualDisplay(holder) },
            onSurfaceDestroyed = { detachVirtualDisplay() }
        )
        try {
            presentation?.show()
            Log.d(TAG, "Presentation shown on display ${display.displayId} ${display.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show presentation on display ${display.displayId}", t)
            stopSelf()
        }
    }

    private fun attachVirtualDisplay(holder: SurfaceHolder) {
        val projection = mediaProjection ?: return
        val targetDisplay = targetDisplayId?.let { displayManager.getDisplay(it) }
        val sourceMetrics = mainDisplayMetrics() ?: return
        val targetMetrics = displayMetricsFor(targetDisplay) ?: sourceMetrics

        // Match the surface to the external panel; capture uses the main display size.
        holder.setFixedSize(
            targetMetrics.widthPixels.coerceAtLeast(1),
            targetMetrics.heightPixels.coerceAtLeast(1)
        )

        detachVirtualDisplay()
        Log.d(
            TAG,
            "Creating virtual display source=${sourceMetrics.widthPixels}x${sourceMetrics.heightPixels}@" +
                "${sourceMetrics.densityDpi} targetSurface=${targetMetrics.widthPixels}x${targetMetrics.heightPixels} " +
                "displayId=$targetDisplay"
        )
        virtualDisplay = projection.createVirtualDisplay(
            "mirror-virtual-display",
            sourceMetrics.widthPixels,
            sourceMetrics.heightPixels,
            sourceMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE,
            holder.surface,
            object : VirtualDisplay.Callback() {
                override fun onStopped() {
                    Log.w(TAG, "Virtual display stopped")
                    stopSelf()
                }
            },
            null
        )
        if (virtualDisplay == null) {
            Log.w(TAG, "Failed to create virtual display; stopping.")
            stopSelf()
        }
    }

    private fun detachVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun releaseAll() {
        detachVirtualDisplay()
        presentation?.dismiss()
        presentation = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun chooseExternalDisplay(): Display? {
        val probe = DisplayProbe.collect(this)
        Log.d(TAG, "Display probe:\n${probe.report}")
        val external = DisplayProbe.pickExternalDisplay(this)
        if (external != null) {
            Log.d(TAG, "Using external display ${external.displayId} ${external.name}")
            return external
        }
        Log.w(TAG, "No external/non-default display found.")
        return null
    }

    private fun displayMetricsFor(display: Display?): DisplayMetrics? {
        if (display == null) return null
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics
    }

    private fun mainDisplayMetrics(): DisplayMetrics? {
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Foreground service for mirroring"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setOngoing(true)
            .build()
    }

    override fun onDisplayAdded(displayId: Int) {
        if (targetDisplayId == null) {
            startPresentation()
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (displayId == targetDisplayId) {
            stopSelf()
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        if (displayId == targetDisplayId) {
            startPresentation()
        }
    }

    private fun updateTileState() {
        TileService.requestListeningState(
            this,
            ComponentName(this, MirrorTileService::class.java)
        )
    }

    companion object {
        private const val TAG = "MirrorService"
        val isRunning: AtomicBoolean = AtomicBoolean(false)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MirrorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MirrorService::class.java))
        }
    }
}
