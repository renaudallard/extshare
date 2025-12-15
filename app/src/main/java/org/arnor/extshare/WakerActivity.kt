package org.arnor.extshare

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager

class WakerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make this window as unobtrusive as possible but bright enough to wake the panel.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        setContentView(View(this).apply { setBackgroundColor(0xFFFFFFFF.toInt()) })
        Log.d(TAG, "WakerActivity on display ${display?.displayId}")

        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 350L)
    }

    companion object {
        private const val TAG = "WakerActivity"

        fun launch(context: Context, displayId: Int) {
            val intent = Intent(context, WakerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            try {
                context.startActivity(intent, opts.toBundle())
                Log.d(TAG, "Launched waker activity on display $displayId")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to launch waker activity on display $displayId: ${t.message}")
            }
        }
    }
}
