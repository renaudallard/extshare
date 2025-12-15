package org.arnor.extshare

import android.util.Log
import android.view.Display

object DisplayPowerToggler {
    private const val TAG = "DisplayPowerToggler"

    /**
     * Tries hidden SurfaceControl / DisplayManagerGlobal hooks to nudge a display into ON state.
     */
    fun forceOn(display: Display): String {
        val attempts = mutableListOf<String>()
        attempts += surfaceControlToggle(display)
        attempts += displayManagerToggle(display.displayId)
        val output = attempts.filter { it.isNotBlank() }
        return if (output.isEmpty()) "no candidate methods" else output.joinToString("; ")
    }

    private fun surfaceControlToggle(display: Display): String {
        val uniqueId = runCatching { display::class.java.getMethod("getUniqueId").invoke(display) as? String }.getOrNull()
        val physicalId = uniqueId?.substringAfter(':', "")?.toLongOrNull()
        return try {
            val sc = Class.forName("android.view.SurfaceControl")
            val getToken = sc.methods.firstOrNull { m ->
                val name = m.name.lowercase()
                name.contains("physical") && name.contains("displaytoken") && m.parameterTypes.size == 1
            }
            val token = when (getToken?.parameterTypes?.firstOrNull()) {
                Long::class.javaPrimitiveType, java.lang.Long::class.java ->
                    physicalId?.let { getToken?.invoke(null, it) }
                Int::class.javaPrimitiveType, java.lang.Integer::class.java ->
                    physicalId?.toInt()?.let { getToken?.invoke(null, it) }
                else -> null
            }
            val resolvedToken = token
            val resolvedGetter = getToken
            if (resolvedToken == null || resolvedGetter == null) {
                return "SurfaceControl: no physical token (uniqueId=$uniqueId, physicalId=$physicalId)"
            }

            val setMode = sc.getMethod(
                "setDisplayPowerMode",
                Class.forName("android.os.IBinder"),
                Int::class.javaPrimitiveType
            )
            val result = setMode.invoke(null, resolvedToken, 2 /*ON*/)
            "SurfaceControl.setDisplayPowerMode -> $result via ${resolvedGetter.name} using physicalId=$physicalId"
        } catch (t: Throwable) {
            "SurfaceControl toggle failed: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun displayManagerToggle(displayId: Int): String {
        return try {
            val clazz = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val methods = clazz.methods
            val methodList = methods.joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
            Log.d(TAG, "DMG methods: $methodList")
            val candidates = methods.filter { m ->
                val name = m.name.lowercase()
                name.contains("display") && (name.contains("state") || name.contains("enable") || name.contains("power"))
            }
            val attempts = mutableListOf<String>()
            for (m in candidates) {
                val params = m.parameterTypes
                val args = buildArgs(displayId, params) ?: continue
                try {
                    val signature = params.map { it.simpleName }.joinToString(",")
                    val result = m.invoke(instance, *args.toTypedArray())
                    val msg = "${m.name}($signature) -> $result"
                    attempts.add(msg)
                    Log.d(TAG, msg)
                } catch (t: Throwable) {
                    attempts.add("${m.name} failed: ${t.javaClass.simpleName}")
                }
            }
            attempts.joinToString("; ").ifBlank { "DisplayManagerGlobal: no candidate methods" }
        } catch (t: Throwable) {
            "DisplayManagerGlobal toggle failed: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun buildArgs(displayId: Int, params: Array<Class<*>>): List<Any>? {
        val args = mutableListOf<Any>()
        for ((index, p) in params.withIndex()) {
            when {
                p == Int::class.javaPrimitiveType || p == Integer::class.java -> {
                    val value = if (index == 0) displayId else 2 // try ON
                    args.add(value)
                }
                p == java.lang.Boolean.TYPE || p == java.lang.Boolean::class.java -> args.add(true)
                else -> return null
            }
        }
        return args
    }
}
