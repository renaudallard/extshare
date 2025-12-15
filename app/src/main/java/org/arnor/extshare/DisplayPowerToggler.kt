package org.arnor.extshare

import android.util.Log

object DisplayPowerToggler {
    private const val TAG = "DisplayPowerToggler"

    /**
     * Tries a handful of hidden DisplayManagerGlobal methods to nudge a display into ON state.
     */
    fun forceOn(displayId: Int): String {
        val attempts = mutableListOf<String>()
        return try {
            val clazz = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val methods = clazz.methods.filter { m ->
                val name = m.name.lowercase()
                name.contains("display") && (name.contains("state") || name.contains("enable") || name.contains("power"))
            }
            for (m in methods) {
                val params = m.parameterTypes
                val args = buildArgs(displayId, params) ?: continue
                try {
                    val result = m.invoke(instance, *args.toTypedArray())
                    val signature = params.map { it.simpleName }.joinToString(",")
                    val msg = "${m.name}($signature) -> $result"
                    attempts.add(msg)
                    Log.d(TAG, msg)
                } catch (t: Throwable) {
                    attempts.add("${m.name} failed: ${t.javaClass.simpleName}")
                }
            }
            val output = if (attempts.isEmpty()) listOf("no candidate methods") else attempts
            output.joinToString("; ")
        } catch (t: Throwable) {
            "reflection failed: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun buildArgs(displayId: Int, params: Array<Class<*>>): List<Any>? {
        val args = mutableListOf<Any>()
        for ((index, p) in params.withIndex()) {
            when {
                p == Int::class.javaPrimitiveType || p == Integer::class.java -> {
                    val value = when (index) {
                        0 -> displayId
                        else -> 2 // try ON
                    }
                    args.add(value)
                }
                p == java.lang.Boolean.TYPE || p == java.lang.Boolean::class.java -> {
                    args.add(true)
                }
                else -> return null
            }
        }
        return args
    }
}
