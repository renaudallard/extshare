package org.arnor.extshare

import android.util.Log

object DisplayPowerToggler {
    private const val TAG = "DisplayPowerToggler"

    /**
     * Tries hidden SurfaceControl and DisplayManagerGlobal hooks to nudge a display into ON state.
     */
    fun forceOn(displayId: Int): String {
        val attempts = mutableListOf<String>()
        attempts += surfaceControlToggle(displayId)
        attempts += displayManagerToggle(displayId)
        val output = attempts.filter { it.isNotBlank() }
        return if (output.isEmpty()) "no candidate methods" else output.joinToString("; ")
    }

    private fun surfaceControlToggle(displayId: Int): String {
        return try {
            val sc = Class.forName("android.view.SurfaceControl")
            val getToken = sc.methods.firstOrNull { m ->
                m.name.lowercase().contains("displaytoken") && m.parameterTypes.size == 1
            }
            val token = when (getToken?.parameterTypes?.firstOrNull()) {
                Long::class.javaPrimitiveType, java.lang.Long::class.java ->
                    getToken?.invoke(null, displayId.toLong())
                Int::class.javaPrimitiveType, java.lang.Integer::class.java ->
                    getToken?.invoke(null, displayId)
                else -> null
            }
            if (token == null || getToken == null) return "SurfaceControl: no token method"

            val setMode = sc.getMethod(
                "setDisplayPowerMode",
                Class.forName("android.os.IBinder"),
                Int::class.javaPrimitiveType
            )
            val result = setMode.invoke(null, token, 2 /*ON*/)
            "SurfaceControl.setDisplayPowerMode -> $result via ${getToken.name}"
        } catch (t: Throwable) {
            "SurfaceControl toggle failed: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun displayManagerToggle(displayId: Int): String {
        return try {
            val clazz = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val methods = clazz.methods.filter { m ->
                val name = m.name.lowercase()
                name.contains("display") && (name.contains("state") || name.contains("enable") || name.contains("power"))
            }
            val attempts = mutableListOf<String>()
            for (m in methods) {
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
