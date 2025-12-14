package org.arnor.extshare

object DualScreenHelper {

    fun attemptEnableDualScreen(): String {
        return try {
            val clazz = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val instance = clazz.getMethod("getInstance").invoke(null)
            val method = clazz.methods.firstOrNull {
                it.name == "toggleDualScreenMode" && (it.parameterTypes.isEmpty() ||
                    (it.parameterTypes.size == 1 && it.parameterTypes[0] == java.lang.Boolean.TYPE))
            }

            if (method != null) {
                val result = if (method.parameterTypes.isEmpty()) {
                    method.invoke(instance)
                } else {
                    method.invoke(instance, true)
                }
                "toggleDualScreenMode invoked (params=${method.parameterTypes.size}) result=$result"
            } else {
                val fallback = clazz.methods.firstOrNull {
                    it.name.startsWith("setDualScreen") || it.name.startsWith("enableDualScreen")
                }
                if (fallback != null) {
                    if (fallback.parameterTypes.size == 1 && fallback.parameterTypes[0] == java.lang.Boolean.TYPE) {
                        val result = fallback.invoke(instance, true)
                        "${fallback.name}(true) invoked result=$result"
                    } else {
                        "${fallback.name} present but unsupported params=${fallback.parameterTypes.joinToString { it.simpleName }}"
                    }
                } else {
                    "No dual-screen toggle method found on DisplayManagerGlobal."
                }
            }
        } catch (t: Throwable) {
            "Dual-screen call failed: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun describeDualMethods(): String {
        return try {
            val clazz = Class.forName("android.hardware.display.DisplayManagerGlobal")
            val names = clazz.methods.map { m ->
                "${m.name}(${m.parameterTypes.joinToString { it.simpleName }})"
            }.sorted()
            "DisplayManagerGlobal methods:\n${names.joinToString("\n")}"
        } catch (t: Throwable) {
            "Unable to inspect DisplayManagerGlobal: ${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
