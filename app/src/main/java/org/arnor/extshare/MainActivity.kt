package org.arnor.extshare

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import org.arnor.extshare.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                ProjectionStore.saveGrant(this, result.resultCode, result.data!!)
                MirrorService.start(this)
                Toast.makeText(this, "Mirroring enabled.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission is required to mirror the screen.", Toast.LENGTH_SHORT).show()
            }
            renderState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.requestPermission.setOnClickListener {
            requestMediaProjection()
        }

        binding.startButton.setOnClickListener {
            if (ProjectionStore.hasGrant(this)) {
                MirrorService.start(this)
            } else {
                requestMediaProjection()
            }
            renderState()
        }

        binding.stopButton.setOnClickListener {
            MirrorService.stop(this)
            renderState()
        }

        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
    }

    private fun requestMediaProjection() {
        val manager: MediaProjectionManager? = getSystemService()
        if (manager == null) {
            Toast.makeText(this, "MediaProjection unavailable on this device.", Toast.LENGTH_LONG).show()
            return
        }
        requestProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun renderState() {
        val hasGrant = ProjectionStore.hasGrant(this)
        val running = MirrorService.isRunning.get()

        binding.requestPermission.isEnabled = true
        binding.startButton.isEnabled = hasGrant && !running
        binding.stopButton.isEnabled = running

        binding.statusLabel.text = when {
            running -> getString(R.string.status_running)
            hasGrant -> getString(R.string.status_ready)
            else -> getString(R.string.status_missing_permission)
        }

        binding.statusDetails.text = when {
            running -> "Your main display should now be mirrored onto the external display."
            hasGrant -> "Tap Start or use the Quick Settings tile to mirror whenever the cover display is available."
            else -> "Tap Grant screen capture to allow mirroring to the external/cover display."
        }
    }
}
