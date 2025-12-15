package org.arnor.extshare

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

data class DisplayProbeResult(
    val primaryList: List<Display>,
    val presentationList: List<Display>,
    val globalIds: IntArray?,
    val report: String
)

object DisplayProbe {
    private const val TAG = "DisplayProbe"

    fun collect(context: Context): DisplayProbeResult {
        val dm = context.getSystemService(DisplayManager::class.java)
        val displays = dm?.displays?.toList().orEmpty()
        val presentation = dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.toList().orEmpty()
        val globalIds = readDisplayManagerGlobalIds()
        val manualIds = listOf(1, 2, 3).mapNotNull { id ->
            dm?.getDisplay(id)?.let { id to it }
        }

        val report = buildString {
            appendLine("All displays (${displays.size}):")
            displays.forEach { d ->
                appendLine(" - id=${d.displayId}, name=${d.name}, flags=${d.flags}, state=${safeState(d)}, category=${if (presentation.contains(d)) "presentation" else "default"}")
            }
            appendLine("Presentation displays (${presentation.size}): ${presentation.joinToString { it.displayId.toString() }}")
            appendLine("DisplayManagerGlobal#getDisplayIds(): ${globalIds?.contentToString() ?: "n/a"}")
            appendLine("Manual getDisplay(1..3): ${manualIds.joinToString { "${it.first}:${it.second.name}" }}")
        }

        return DisplayProbeResult(displays, presentation, globalIds, report)
    }

    fun pickExternalDisplay(context: Context): Display? {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return null
        val probe = collect(context)

        // Prefer presentation category first
        val presentation = probe.presentationList.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (presentation != null) return presentation

        // Fallback to any non-default display in standard list
        val fromList = probe.primaryList.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (fromList != null) return fromList

        // Finally, try DisplayManagerGlobal IDs
        probe.globalIds?.forEach { id ->
            if (id != Display.DEFAULT_DISPLAY) {
                val d = dm.getDisplay(id)
                if (d != null) {
                    Log.d(TAG, "Using display from DisplayManagerGlobal ids: $id (${d.name})")
                    return d
                }
            }
        }

        // Manual fallback: common secondary IDs
        listOf(1, 2, 3).forEach { id ->
            if (id != Display.DEFAULT_DISPLAY) {
                val d = dm.getDisplay(id)
                if (d != null) {
                    Log.d(TAG, "Using manual display id=$id (${d.name})")
                    return d
                }
            }
        }
        return null
    }

    private fun readDisplayManagerGlobalIds(): IntArray? {
        return try {
            val clazz = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val method = clazz.getMethod("getDisplayIds")
            method.invoke(instance) as? IntArray
        } catch (t: Throwable) {
            null
        }
    }

    private fun safeState(display: Display): Int = try {
        display.state
    } catch (_: Exception) {
        -1
    }
}
