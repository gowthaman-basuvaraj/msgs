package the.waste.fellow.sms.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A tiny persistent FIFO queue for messages awaiting upload, so nothing is lost across
 * process death / offline periods. Backed by its own SharedPreferences file and guarded by
 * an in-process lock (the app is single-process). Each entry gets a monotonic id so the
 * worker can remove exactly the entries it successfully uploaded even if new ones arrive
 * mid-drain.
 */
class PendingSyncStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Entry(val id: Long, val sender: String, val text: String, val date: Long)

    fun add(sender: String, text: String, date: Long) = synchronized(lock) {
        val array = readArray()
        val nextId = prefs.getLong(KEY_SEQ, 0L) + 1
        array.put(
            JSONObject()
                .put("id", nextId)
                .put("sender", sender)
                .put("text", text)
                .put("date", date)
        )
        prefs.edit().putString(KEY_ITEMS, array.toString()).putLong(KEY_SEQ, nextId).apply()
    }

    fun all(): List<Entry> = synchronized(lock) {
        val array = readArray()
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Entry(o.optLong("id"), o.optString("sender"), o.optString("text"), o.optLong("date"))
        }
    }

    fun remove(ids: Collection<Long>) = synchronized(lock) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        val kept = JSONArray()
        val array = readArray()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (o.optLong("id") !in idSet) kept.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, kept.toString()).apply()
    }

    fun size(): Int = synchronized(lock) { readArray().length() }

    private fun readArray(): JSONArray = try {
        val json = prefs.getString(KEY_ITEMS, null)
        if (json.isNullOrBlank()) JSONArray() else JSONArray(json)
    } catch (e: Exception) {
        JSONArray()
    }

    companion object {
        private const val PREFS_NAME = "sms_sync_queue"
        private const val KEY_ITEMS = "items"
        private const val KEY_SEQ = "seq"
        private val lock = Any()
    }
}
