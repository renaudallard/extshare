package org.arnor.extshare

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

data class ProjectionGrant(val resultCode: Int, val dataIntent: Intent)

object ProjectionStore {
    private const val PREFS_NAME = "mirror_prefs"
    private const val KEY_RESULT_CODE = "result_code"
    private const val KEY_INTENT_URI = "result_intent_uri"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveGrant(context: Context, resultCode: Int, intent: Intent) {
        val uri = intent.toUri(Intent.URI_INTENT_SCHEME)
        prefs(context).edit()
            .putInt(KEY_RESULT_CODE, resultCode)
            .putString(KEY_INTENT_URI, uri)
            .apply()
    }

    fun clearGrant(context: Context) {
        prefs(context).edit().remove(KEY_RESULT_CODE).remove(KEY_INTENT_URI).apply()
    }

    fun loadGrant(context: Context): ProjectionGrant? {
        val stored = prefs(context)
        val resultCode = stored.getInt(KEY_RESULT_CODE, Int.MIN_VALUE)
        val uri = stored.getString(KEY_INTENT_URI, null) ?: return null
        if (resultCode == Int.MIN_VALUE) return null
        return try {
            val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            ProjectionGrant(resultCode, intent)
        } catch (_: Exception) {
            null
        }
    }

    fun hasGrant(context: Context): Boolean = loadGrant(context) != null
}
