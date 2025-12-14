package org.arnor.extshare

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import android.graphics.PixelFormat
import android.view.SurfaceView

class MirrorPresentation(
    context: Context,
    display: Display,
    private val onSurfaceReady: (SurfaceHolder) -> Unit,
    private val onSurfaceDestroyed: () -> Unit
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.presentation_mirror)

        val surfaceView = findViewById<SurfaceView>(R.id.mirrorSurface)
        surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "Surface created on display ${display.displayId} size=${holder.surfaceFrame}")
                onSurfaceReady(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed on display ${display.displayId} size=${width}x${height}")
                onSurfaceReady(holder)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "Surface destroyed on display ${display.displayId}")
                onSurfaceDestroyed()
            }
        })
    }

    companion object {
        private const val TAG = "MirrorPresentation"
    }
}
