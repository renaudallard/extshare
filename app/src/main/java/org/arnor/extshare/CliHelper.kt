package org.arnor.extshare

import android.content.Context
import android.util.Log

object CliHelper {
    private const val TAG = "CliHelper"

    /**
     * Try to wake the cover/CLI display using Motorola's CLIManager (if present).
     * Returns a human-readable summary of what was attempted.
     */
    fun wakeCover(context: Context): String {
        return try {
            val clazz = Class.forName("motorola.core_services.cli.CLIManager")
            val instance = obtainInstance(clazz, context)
            val modeClass = Class.forName("motorola.core_services.cli.CLIManager\$DisplayMode")
            val modes = modeClass.enumConstants?.map { it.toString() }.orEmpty()
            val fullMode = modeClass.enumConstants?.firstOrNull { it.toString().contains("FULL", ignoreCase = true) }
                ?: modeClass.enumConstants?.firstOrNull()

            val sb = StringBuilder()
            sb.append("CLIManager present, instance=${instance != null}, modes=$modes; ")

            // Call any setters that take DisplayMode
            clazz.methods.filter { m ->
                m.name.startsWith("set", ignoreCase = true) &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == modeClass
            }.forEach { m ->
                try {
                    val res = if (instance != null && fullMode != null) m.invoke(instance, fullMode) else "noInstOrMode"
                    sb.append("${m.name}=$res; ")
                } catch (t: Throwable) {
                    sb.append("${m.name} failed: ${t.javaClass.simpleName}; ")
                }
            }

            // Call any boolean toggles like enableCliDisplay(boolean)
            clazz.methods.filter { m ->
                m.name.lowercase().contains("enable") && m.parameterTypes.size == 1 &&
                    (m.parameterTypes[0] == java.lang.Boolean.TYPE || m.parameterTypes[0] == java.lang.Boolean::class.java)
            }.forEach { m ->
                try {
                    val res = if (instance != null) m.invoke(instance, true) else m.invoke(null, true)
                    sb.append("${m.name}(true)=$res; ")
                } catch (t: Throwable) {
                    sb.append("${m.name} failed: ${t.javaClass.simpleName}; ")
                }
            }

            val methodsSummary = clazz.methods.joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
            Log.d(TAG, "CLIManager methods: $methodsSummary")
            Log.d(TAG, sb.toString())
            sb.toString()
        } catch (t: Throwable) {
            "CLIManager not available: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun obtainInstance(clazz: Class<*>, context: Context): Any? {
        val zero = clazz.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.isEmpty() }
        if (zero != null) {
            runCatching { return zero.invoke(null) }.onFailure { }
        }
        val withCtx = clazz.methods.firstOrNull {
            it.name == "getInstance" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0].isAssignableFrom(context.javaClass)
        } ?: clazz.methods.firstOrNull {
            it.name == "getInstance" && it.parameterTypes.size == 1 &&
                it.parameterTypes[0].isAssignableFrom(Context::class.java)
        }
        if (withCtx != null) {
            runCatching { return withCtx.invoke(null, context) }.onFailure { }
        }
        return null
    }
}
